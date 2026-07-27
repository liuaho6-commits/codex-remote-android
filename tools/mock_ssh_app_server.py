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


RICH_MARKDOWN_REPLY = r"""## 远端渲染检查

**粗体应该直接显示为粗体**，而不是保留星号。

行内公式：$E = mc^2$，以及块级公式：

$$
\int_0^1 x^2\,dx = \frac{1}{3}
$$

```kotlin
fun answer(): Int {
    return 42
}
```

| 项目 | 状态 |
| --- | --- |
| Markdown | 已渲染 |
| LaTeX | 已渲染 |

""" + "\n\n".join(
    f"第 {index} 段用于验证长回复的绝对底部滚动。这里保留足够的正文高度，确保最后一条消息远高于一个手机屏幕。"
    for index in range(1, 25)
)


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
        self.pending_approval_turn: tuple[str, str] | None = None
        self.goals: dict[str, dict[str, Any]] = {}

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
                            "model": "gpt-5.6-sol",
                            "displayName": "GPT-5.6-Sol",
                            "description": "Local end-to-end QA model",
                            "isDefault": True,
                            "defaultReasoningEffort": "ultra",
                            "supportedReasoningEfforts": [
                                {"reasoningEffort": "medium", "description": "Balanced"},
                                {"reasoningEffort": "high", "description": "Deep"},
                                {"reasoningEffort": "ultra", "description": "Maximum"},
                            ],
                            "inputModalities": ["text", "image"],
                            "serviceTiers": [
                                {"id": "fast", "name": "Fast", "description": "Priority processing"}
                            ],
                            "defaultServiceTier": "fast",
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
            self.result(
                message,
                {
                    "data": [
                        {
                            "id": "team-safe",
                            "description": "QA custom profile from the remote host",
                            "allowed": True,
                        }
                    ],
                    "nextCursor": None,
                },
            )
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
            thread_id = message["params"]["threadId"]
            turns = self.thread_turns(thread_id)
            recent_start = max(0, len(turns) - 5)
            self.result(
                message,
                {
                    "thread": {"id": thread_id, "cwd": "/workspace/demo"},
                    "model": "gpt-5.6-sol",
                    "reasoningEffort": "ultra",
                    "serviceTier": "fast",
                    "collaborationMode": {"mode": "default"},
                    "approvalPolicy": "on-request",
                    "approvalsReviewer": "user",
                    "activePermissionProfile": {"id": ":workspace"},
                    "initialTurnsPage": {"data": list(reversed(turns[recent_start:]))},
                    "turnsBackwardsCursor": f"history-{recent_start}" if recent_start > 0 else None,
                },
            )
            self.notification(
                "thread/tokenUsage/updated",
                {
                    "threadId": thread_id,
                    "tokenUsage": {
                        "total": {
                            "totalTokens": 114000,
                            "inputTokens": 108000,
                            "outputTokens": 6000,
                        },
                        "modelContextWindow": 200000,
                    },
                },
            )
        elif method == "thread/read":
            self.result(message, {"thread": self.thread_detail(message["params"]["threadId"])})
        elif method == "thread/goal/get":
            thread_id = message.get("params", {}).get("threadId", "")
            self.result(message, {"goal": self.goals.get(thread_id)})
        elif method == "thread/goal/set":
            self.set_thread_goal(message)
        elif method == "thread/goal/clear":
            thread_id = message.get("params", {}).get("threadId", "")
            cleared = self.goals.pop(thread_id, None) is not None
            self.result(message, {"cleared": cleared})
            if cleared:
                self.notification("thread/goal/cleared", {"threadId": thread_id})
        elif method == "thread/turns/list":
            params = message.get("params", {})
            turns = self.thread_turns(params.get("threadId", "thread-demo-primary"))
            cursor = params.get("cursor", "history-0")
            try:
                end = int(cursor.rsplit("-", 1)[1])
            except (IndexError, ValueError):
                end = 0
            start = max(0, end - 5)
            self.result(
                message,
                {
                    "data": list(reversed(turns[start:end])),
                    "nextCursor": f"history-{start}" if start > 0 else None,
                },
            )
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
            turn_id, thread_id = self.pending_approval_turn
            self.finish_approval_turn(turn_id, thread_id)
            self.pending_approval_turn = None
        elif "id" in message:
            self.send({"id": message["id"], "error": {"message": f"Unsupported method: {method}"}})

    def set_thread_goal(self, request: dict[str, Any]) -> None:
        params = request.get("params", {})
        thread_id = params.get("threadId", "")
        current = self.goals.get(thread_id)
        objective = params.get("objective", current.get("objective") if current else None)
        if not isinstance(objective, str) or not objective.strip():
            self.send(
                {
                    "id": request["id"],
                    "error": {"code": -32600, "message": "goal objective must not be empty"},
                }
            )
            return
        if len(objective) > 4000:
            self.send(
                {
                    "id": request["id"],
                    "error": {"code": -32600, "message": "goal objective must be at most 4000 characters"},
                }
            )
            return

        now = int(time.time())
        goal = {
            "threadId": thread_id,
            "objective": objective.strip(),
            "status": params.get("status", current.get("status", "active") if current else "active"),
            "tokenBudget": params.get("tokenBudget", current.get("tokenBudget") if current else None),
            "tokensUsed": current.get("tokensUsed", 0) if current else 0,
            "timeUsedSeconds": current.get("timeUsedSeconds", 0) if current else 0,
            "createdAt": current.get("createdAt", now) if current else now,
            "updatedAt": now,
        }
        self.goals[thread_id] = goal
        self.result(request, {"goal": goal})
        self.notification("thread/goal/updated", {"threadId": thread_id, "goal": goal})

    def handle_turn(self, request: dict[str, Any]) -> None:
        self.turn_counter += 1
        turn_id = f"turn-{self.turn_counter}"
        thread_id = request.get("params", {}).get("threadId", "thread-android")
        prompt = "\n".join(
            item.get("text", "") for item in request.get("params", {}).get("input", []) if item.get("type") == "text"
        )
        self.result(request, {"turn": {"id": turn_id}})
        self.notification(
            "turn/started",
            {"threadId": thread_id, "turn": {"id": turn_id, "status": "inProgress"}},
        )
        self.notification(
            "item/started",
            {
                "threadId": thread_id,
                "item": {
                    "type": "userMessage",
                    "id": f"user-{self.turn_counter}",
                    "content": [{"type": "text", "text": prompt}],
                },
            },
        )
        if prompt.strip().lower() == "request approval":
            self.pending_approval_turn = (turn_id, thread_id)
            self.send(
                {
                    "id": 9001,
                    "method": "item/commandExecution/requestApproval",
                    "params": {"command": "git status --short", "reason": "Verify Android approval UI"},
                }
            )
            return
        self.finish_turn(turn_id, thread_id, prompt)

    def finish_turn(self, turn_id: str, thread_id: str, prompt: str) -> None:
        reasoning = {
            "type": "reasoning",
            "id": f"reasoning-{self.turn_counter}",
            "summary": ["检查远端项目并规划验证步骤。"],
        }
        self.notification("item/started", {"threadId": thread_id, "item": reasoning})
        for command_index, (command_text, output) in enumerate(
            (("pwd", "/workspace/demo\n"), ("git status --short", " M app/src/Main.kt\n")),
            start=1,
        ):
            command = {
                "type": "commandExecution",
                "id": f"command-{self.turn_counter}-{command_index}",
                "command": command_text,
                "status": "inProgress",
                "aggregatedOutput": output,
            }
            self.notification("item/started", {"threadId": thread_id, "item": command})
        file_change = {
            "type": "fileChange",
            "id": f"files-{self.turn_counter}",
            "status": "inProgress",
            "changes": [
                {
                    "path": "app/src/Main.kt",
                    "kind": {"type": "update"},
                    "diff": "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n@@ -1 +1,2 @@\n-old line\n+new line\n+second line",
                },
                {
                    "path": "app/src/Status.kt",
                    "kind": {"type": "add"},
                    "diff": "--- /dev/null\n+++ b/app/src/Status.kt\n@@ -0,0 +1 @@\n+val ready = true",
                },
            ],
        }
        self.notification("item/started", {"threadId": thread_id, "item": file_change})
        item_id = f"agent-{self.turn_counter}"
        reply = f"收到：{prompt}\n\n{RICH_MARKDOWN_REPLY}"
        self.notification(
            "item/started",
            {"threadId": thread_id, "item": {"type": "agentMessage", "id": item_id, "text": ""}},
        )
        for delta in (f"收到：{prompt}\n\n", RICH_MARKDOWN_REPLY):
            time.sleep(0.15)
            self.notification(
                "item/agentMessage/delta",
                {"threadId": thread_id, "itemId": item_id, "delta": delta},
            )
        self.notification(
            "item/completed",
            {"threadId": thread_id, "item": {"type": "agentMessage", "id": item_id, "text": reply}},
        )
        self.notification(
            "thread/tokenUsage/updated",
            {
                    "threadId": thread_id,
                    "tokenUsage": {
                        "total": {
                            "totalTokens": 128000,
                            "inputTokens": 120000,
                            "outputTokens": 8000,
                        },
                        "modelContextWindow": 200000,
                    },
            },
        )
        self.notification(
            "turn/completed",
            {"threadId": thread_id, "turn": {"id": turn_id, "status": "completed"}},
        )

    def finish_approval_turn(self, turn_id: str, thread_id: str) -> None:
        command_id = f"command-{self.turn_counter}"
        command = {
            "type": "commandExecution",
            "id": command_id,
            "command": "git status --short",
            "status": "completed",
            "aggregatedOutput": " M demo.txt\n",
        }
        self.notification("item/started", {"threadId": thread_id, "item": command})
        self.notification("item/completed", {"threadId": thread_id, "item": command})
        self.finish_turn(turn_id, thread_id, "request approval")

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
    def thread_turns(thread_id: str) -> list[dict[str, Any]]:
        turns = []
        for index in range(1, 25):
            if index == 24:
                items = [
                    {
                        "type": "userMessage",
                        "id": "old-user-rich",
                        "content": [{"type": "text", "text": "Show rich rendering and a long response"}],
                    },
                    {
                        "type": "reasoning",
                        "id": "old-reasoning-rich",
                        "summary": ["Verified Markdown, LaTeX, commands and changed files."],
                    },
                    {
                        "type": "commandExecution",
                        "id": "old-command-rich-1",
                        "command": "pwd",
                        "status": "completed",
                        "aggregatedOutput": "/workspace/demo\n",
                    },
                    {
                        "type": "commandExecution",
                        "id": "old-command-rich-2",
                        "command": "git status --short",
                        "status": "completed",
                        "aggregatedOutput": " M app/src/Main.kt\n",
                    },
                    {
                        "type": "fileChange",
                        "id": "old-files-rich",
                        "status": "completed",
                        "changes": [
                            {
                                "path": "app/src/Main.kt",
                                "kind": {"type": "update"},
                                "diff": "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n@@ -1 +1,2 @@\n-old\n+new\n+next",
                            }
                        ],
                    },
                    {
                        "type": "agentMessage",
                        "id": "old-agent-rich",
                        "text": RICH_MARKDOWN_REPLY,
                    },
                ]
            else:
                items = [
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
                ]
            turns.append(
                {
                    "id": f"turn-existing-{index}",
                    "items": items,
                }
            )
        return turns

    @classmethod
    def thread_detail(cls, thread_id: str) -> dict[str, Any]:
        return {
            "id": thread_id,
            "cwd": "/workspace/demo",
            "turns": cls.thread_turns(thread_id),
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
