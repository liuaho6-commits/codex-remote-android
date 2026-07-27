#!/usr/bin/env python3
"""Local SSH and Codex app-server test double for Android end-to-end QA."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import socket
import threading
import time
from pathlib import Path
from typing import Any

import paramiko


class EventLog:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.lock = threading.Lock()

    def write(self, event: str, **details: Any) -> None:
        record = {"time": time.time(), "event": event, **details}
        line = json.dumps(record, ensure_ascii=True, separators=(",", ":"))
        with self.lock:
            with self.path.open("a", encoding="utf-8") as stream:
                stream.write(line + "\n")
        print(line, flush=True)


class MockSshServer(paramiko.ServerInterface):
    def __init__(self, username: str, password: str, events: EventLog) -> None:
        self.username = username
        self.password = password
        self.events = events
        self.exec_event = threading.Event()
        self.command = ""

    def get_allowed_auths(self, username: str) -> str:
        return "password"

    def check_auth_password(self, username: str, password: str) -> int:
        accepted = username == self.username and password == self.password
        self.events.write("auth_password", username=username, accepted=accepted)
        return paramiko.AUTH_SUCCESSFUL if accepted else paramiko.AUTH_FAILED

    def check_channel_request(self, kind: str, chanid: int) -> int:
        self.events.write("channel_request", kind=kind, channel_id=chanid)
        return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_exec_request(self, channel: paramiko.Channel, command: bytes) -> bool:
        self.command = command.decode("utf-8", errors="replace")
        self.events.write("exec_request", command=self.command)
        self.exec_event.set()
        return True


class MockAppServer:
    def __init__(self, channel: paramiko.Channel, events: EventLog) -> None:
        self.channel = channel
        self.events = events
        self.buffer = b""
        self.threads = [
            {
                "id": "thread-demo-primary",
                "name": "Demo project status",
                "cwd": "/workspace/demo",
                "updatedAt": 1785100300,
            },
            {
                "id": "thread-demo-secondary",
                "name": "Demo project tests",
                "cwd": "/workspace/demo/",
                "updatedAt": 1785100200,
            },
            {
                "id": "thread-api",
                "name": "API project deployment",
                "cwd": "/workspace/api",
                "updatedAt": 1785100100,
            },
        ]
        self.turn_counter = 0
        self.pending_approval_turn: str | None = None

    def send(self, message: dict[str, Any]) -> None:
        self.events.write("server_message", message=message)
        payload = json.dumps(message, ensure_ascii=False, separators=(",", ":")) + "\n"
        self.channel.sendall(payload.encode("utf-8"))

    def result(self, request: dict[str, Any], result: dict[str, Any]) -> None:
        self.send({"id": request["id"], "result": result})

    def notification(self, method: str, params: dict[str, Any]) -> None:
        self.send({"method": method, "params": params})

    def run(self) -> None:
        self.channel.settimeout(1.0)
        while not self.channel.closed:
            try:
                chunk = self.channel.recv(65536)
            except socket.timeout:
                continue
            if not chunk:
                break
            self.buffer += chunk
            while b"\n" in self.buffer:
                raw, self.buffer = self.buffer.split(b"\n", 1)
                if not raw.strip():
                    continue
                try:
                    message = json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError) as error:
                    self.events.write("invalid_client_message", error=str(error))
                    continue
                self.events.write("client_message", message=message)
                self.handle(message)

    def handle(self, message: dict[str, Any]) -> None:
        method = message.get("method")
        if method == "initialize":
            self.result(message, {"serverInfo": {"name": "mock-codex", "version": "1.0"}})
        elif method == "initialized":
            return
        elif method == "account/read":
            self.result(
                message,
                {
                    "account": {"type": "apiKey", "email": "qa@example.invalid", "planType": "test"},
                    "requiresOpenaiAuth": False,
                },
            )
        elif method == "model/list":
            self.result(
                message,
                {
                    "data": [
                        {
                            "model": "gpt-5.2-codex",
                            "displayName": "GPT-5.2 Codex",
                            "description": "Local end-to-end QA model",
                            "isDefault": True,
                        }
                    ]
                },
            )
        elif method == "thread/list":
            params = message.get("params", {})
            if "cwd" in params:
                self.events.write("protocol_violation", detail="thread/list must not contain cwd")
            cursor = params.get("cursor")
            if cursor == "page-2":
                page = self.threads[2:]
                next_cursor = None
            else:
                page = self.threads[:2]
                next_cursor = "page-2" if len(self.threads) > 2 else None
            self.result(
                message,
                {"data": [self.thread_summary(thread) for thread in page], "nextCursor": next_cursor},
            )
        elif method == "collaborationMode/list":
            self.result(
                message,
                {
                    "data": [
                        {"name": "Default", "mode": "default"},
                        {"name": "Plan", "mode": "plan"},
                    ]
                },
            )
        elif method == "permissionProfile/list":
            self.result(message, {"data": [], "nextCursor": None})
        elif method == "skills/list":
            cwds = message.get("params", {}).get("cwds", ["/workspace/demo"])
            self.result(
                message,
                {
                    "data": [
                        {
                            "cwd": cwd,
                            "skills": [
                                {
                                    "name": "qa-check",
                                    "displayName": "QA Check",
                                    "description": "Exercise the remote skill composer input.",
                                    "path": "/skills/qa-check/SKILL.md",
                                    "enabled": True,
                                }
                            ],
                        }
                        for cwd in cwds
                    ]
                },
            )
        elif method == "plugin/installed":
            self.result(
                message,
                {
                    "marketplaces": [
                        {
                            "name": "qa",
                            "plugins": [
                                {
                                    "id": "qa-plugin@qa",
                                    "name": "qa-plugin",
                                    "installed": True,
                                    "enabled": True,
                                    "interface": {
                                        "displayName": "QA Plugin",
                                        "shortDescription": "Exercise installed plugin mentions.",
                                    },
                                }
                            ],
                        }
                    ]
                },
            )
        elif method == "thread/resume":
            self.result(message, {"thread": {"id": message["params"]["threadId"]}})
        elif method == "thread/read":
            self.result(message, {"thread": self.thread_detail(message["params"]["threadId"])})
        elif method == "fs/readDirectory":
            path = message.get("params", {}).get("path", "")
            entries_by_path = {
                "/workspace/demo": [
                    {"fileName": "src", "isDirectory": True, "isFile": False},
                    {"fileName": "README.md", "isDirectory": False, "isFile": True},
                ],
                "/workspace/demo/src": [
                    {"fileName": "ui", "isDirectory": True, "isFile": False},
                    {"fileName": "Main.kt", "isDirectory": False, "isFile": True},
                ],
                "/workspace/demo/src/ui": [
                    {"fileName": "Workspace.kt", "isDirectory": False, "isFile": True},
                ],
            }
            self.result(message, {"entries": entries_by_path.get(path, [])})
        elif method == "thread/start":
            thread_id = "thread-android"
            if not any(thread["id"] == thread_id for thread in self.threads):
                self.threads.insert(
                    0,
                    {
                        "id": thread_id,
                        "name": "Android SSH QA",
                        "cwd": message.get("params", {}).get("cwd", ""),
                        "updatedAt": 1785100400,
                    },
                )
            self.result(message, {"thread": {"id": thread_id}})
        elif method == "turn/start":
            self.handle_turn(message)
        elif method == "turn/interrupt":
            self.result(message, {})
            self.notification("turn/completed", {"turn": {"id": message["params"]["turnId"], "status": "interrupted"}})
        elif method is None and message.get("id") == 9001 and self.pending_approval_turn is not None:
            self.events.write("approval_response", result=message.get("result", {}))
            self.finish_approval_turn(self.pending_approval_turn)
            self.pending_approval_turn = None
        elif "id" in message:
            self.send({"id": message["id"], "error": {"message": f"Unsupported method: {method}"}})

    def handle_turn(self, request: dict[str, Any]) -> None:
        self.turn_counter += 1
        turn_id = f"turn-{self.turn_counter}"
        prompt = "\n".join(
            item.get("text", "") for item in request.get("params", {}).get("input", []) if item.get("type") == "text"
        )
        self.result(request, {"turn": {"id": turn_id}})
        self.notification("turn/started", {"turn": {"id": turn_id, "status": "inProgress"}})
        self.notification(
            "item/started",
            {"item": {"type": "userMessage", "id": f"user-{self.turn_counter}", "content": [{"text": prompt}]}},
        )
        if prompt.strip().lower() == "request approval":
            self.pending_approval_turn = turn_id
            self.send(
                {
                    "id": 9001,
                    "method": "item/commandExecution/requestApproval",
                    "params": {"command": "git status --short", "reason": "Verify Android approval UI"},
                }
            )
            return
        self.finish_turn(turn_id, prompt)

    def finish_turn(self, turn_id: str, prompt: str) -> None:
        item_id = f"agent-{self.turn_counter}"
        reply = f"SSH app-server is working. Android sent: {prompt}"
        self.notification("item/started", {"item": {"type": "agentMessage", "id": item_id, "text": ""}})
        for delta in ("SSH app-server is working. ", f"Android sent: {prompt}"):
            time.sleep(0.15)
            self.notification("item/agentMessage/delta", {"itemId": item_id, "delta": delta})
        self.notification("item/completed", {"item": {"type": "agentMessage", "id": item_id, "text": reply}})
        self.notification(
            "turn/diff/updated",
            {"diff": "diff --git a/demo.txt b/demo.txt\n--- a/demo.txt\n+++ b/demo.txt\n@@ -0,0 +1 @@\n+remote qa\n"},
        )
        self.notification("turn/completed", {"turn": {"id": turn_id, "status": "completed"}})

    def finish_approval_turn(self, turn_id: str) -> None:
        command_id = f"command-{self.turn_counter}"
        command = {
            "type": "commandExecution",
            "id": command_id,
            "command": "git status --short",
            "status": "completed",
            "aggregatedOutput": " M demo.txt\n",
        }
        self.notification("item/started", {"item": command})
        self.notification("item/completed", {"item": command})
        self.finish_turn(turn_id, "request approval")

    @staticmethod
    def thread_summary(thread: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": thread["id"],
            "name": thread["name"],
            "preview": "Remote thread loaded over SSH",
            "cwd": thread["cwd"],
            "updatedAt": thread["updatedAt"],
            "status": {"type": "idle"},
        }

    @staticmethod
    def thread_detail(thread_id: str) -> dict[str, Any]:
        turns = []
        for index in range(1, 25):
            turns.append(
                {
                    "id": f"turn-existing-{index}",
                    "items": [
                        {
                            "type": "userMessage",
                            "id": f"old-user-{index}",
                            "content": [{"type": "text", "text": f"Status request {index}"}],
                        },
                        {
                            "type": "reasoning",
                            "id": f"old-reasoning-{index}",
                            "summary": [f"Checked remote state for request {index}."],
                        },
                        {
                            "type": "agentMessage",
                            "id": f"old-agent-{index}",
                            "text": f"Remote thread response {index}.",
                        },
                    ],
                }
            )
        return {
            "id": thread_id,
            "cwd": "/workspace/demo",
            "turns": turns,
        }


def host_fingerprint(key: paramiko.PKey) -> str:
    digest = hashlib.sha256(key.asbytes()).digest()
    return "SHA256:" + base64.b64encode(digest).decode("ascii").rstrip("=")


def load_or_create_host_key(path: Path) -> paramiko.RSAKey:
    if path.exists():
        return paramiko.RSAKey.from_private_key_file(str(path))
    key = paramiko.RSAKey.generate(2048)
    key.write_private_key_file(str(path))
    return key


def handle_client(
    client: socket.socket,
    peer: tuple[str, int],
    host_key: paramiko.PKey,
    username: str,
    password: str,
    events: EventLog,
) -> None:
    events.write("connection_open", peer=f"{peer[0]}:{peer[1]}")
    transport = paramiko.Transport(client)
    transport.get_security_options().kex = ("diffie-hellman-group14-sha256",)
    transport.add_server_key(host_key)
    server = MockSshServer(username, password, events)
    try:
        transport.start_server(server=server)
        while transport.is_active():
            channel = transport.accept(1)
            if channel is None:
                continue
            if not server.exec_event.wait(15):
                channel.send_stderr(b"Expected an exec request\n")
                channel.send_exit_status(1)
                channel.close()
                continue
            command = server.command
            server.exec_event.clear()
            time.sleep(0.05)
            if "__CODEX_POSIX__" in command:
                channel.sendall(b"__CODEX_POSIX__")
                channel.send_exit_status(0)
                channel.close()
                continue
            if "codex --version" in command:
                channel.sendall(b"codex-cli 0.0.0-mock\n")
                channel.send_exit_status(0)
                channel.close()
                continue
            if "codex app-server --listen stdio://" in command:
                MockAppServer(channel, events).run()
                continue
            channel.send_stderr(b"codex command not found\n")
            channel.send_exit_status(127)
            channel.close()
    except (EOFError, OSError, paramiko.SSHException) as error:
        events.write("connection_error", error=type(error).__name__, detail=str(error))
    finally:
        transport.close()
        client.close()
        events.write("connection_closed", peer=f"{peer[0]}:{peer[1]}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=22222)
    parser.add_argument("--username", default="codex-test")
    parser.add_argument("--password", default="codex-test")
    parser.add_argument("--state-dir", type=Path, required=True)
    args = parser.parse_args()

    args.state_dir.mkdir(parents=True, exist_ok=True)
    events_path = args.state_dir / "events.jsonl"
    events_path.write_text("", encoding="utf-8")
    events = EventLog(events_path)
    host_key = load_or_create_host_key(args.state_dir / "host_rsa_key")

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind((args.host, args.port))
    listener.listen(20)
    events.write(
        "ready",
        host=args.host,
        port=args.port,
        username=args.username,
        fingerprint=host_fingerprint(host_key),
    )
    try:
        while True:
            client, peer = listener.accept()
            threading.Thread(
                target=handle_client,
                args=(client, peer, host_key, args.username, args.password, events),
                daemon=True,
            ).start()
    except KeyboardInterrupt:
        pass
    finally:
        listener.close()


if __name__ == "__main__":
    main()
