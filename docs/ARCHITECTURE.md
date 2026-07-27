# Architecture

## Source audit

This client follows the public Codex app-server contract rather than embedding
an agent on Android.

- Codex source inspected at `openai/codex` commit
  `61a44880a85d2fd0d8770908dea5733495e571c8` (2026-07-26).
- Protocol definitions come from `codex-rs/app-server-protocol`.
- Server behavior comes from `codex-rs/app-server`.
- Desktop behavior and command registration were audited from Codex Desktop
  `26.721.4979.0`; Android keeps only actions that can operate on the SSH host.
- `codex-rs/app-server-daemon/README.md` explicitly describes SSH-launched
  app-server instances used by desktop and mobile remote clients.
- `codex-rs/app-server-protocol/src/protocol/v2/thread.rs` defines host-wide
  `thread/list` discovery, `cwd` filtering and opaque cursor pagination.
- `codex-rs/app-server/tests/suite/v2/thread_list.rs` verifies `nextCursor`
  pagination and shows that `cwd: None` does not restrict results to one project.
- Desktop sends an empty `sourceKinds` filter for its user-facing thread list;
  Android mirrors that behavior and follows every `nextCursor` page.

## Runtime flow

```text
Android UI
   |
   | SSH handshake (verify/pin SHA-256 host key)
   v
Remote login shell
   |
   | codex app-server --listen stdio://
   v
JSONL transport over SSH stdin/stdout
   |
   +-- initialize / initialized
   +-- thread/list (all cursor pages, no cwd filter)
   +-- thread/start, thread/resume, thread/fork, thread/compact/start
   +-- thread/archive, thread/unarchive, thread/delete
   +-- thread/goal/get, thread/goal/set, thread/goal/clear
   +-- thread/metadata/update (remote pin state)
   +-- turn/start, turn/steer, turn/interrupt
   +-- review/start, collaborationMode/list
   +-- account/read, account/login/start
   +-- model/list (all cursor pages)
   +-- skills/list, plugin/installed, mcpServerStatus/list
   +-- mcpServer/oauth/login, config/mcpServer/reload
   +-- feedback/upload (remote thread id and remote logs)
   +-- item and turn streaming notifications
   +-- command, file-change, permission and user-input approvals
```

Agent execution, repository access, authentication, tools and approvals remain
owned by the remote Codex installation. Android has no local agent runtime.

## Host-wide discovery

A saved connection stores only SSH host, authentication and platform details.
On connection, the client requests every page of non-archived interactive
threads with the same empty `sourceKinds` filter as Desktop. It deliberately
omits the optional `cwd` field from `thread/list`, follows `nextCursor` until it
is null, and deduplicates thread IDs across overlapping pages.

Projects are a UI projection of the returned threads grouped by normalized
`Thread.cwd`; they are not stored in the SSH connection. Resuming a conversation
uses that thread's own `cwd`. Starting a conversation uses the selected imported
project's path.

The model picker is populated exclusively from the remote `model/list` catalog,
including each model's reasoning choices, input modalities, service tiers and
defaults. Plan mode comes from `collaborationMode/list`; permissions come from
`permissionProfile/list`. Text, structured skill/plugin mentions and image data
are sent as official `UserInput` objects. Follow-up messages sent while a turn
is active use `turn/steer` with the active `expectedTurnId`.

Existing remote API-key and ChatGPT accounts are read through `account/read`.
When the remote host requires ChatGPT authentication, the Android client starts
the official device-code flow through `account/login/start`; credentials remain
owned by the remote Codex installation.

Archived conversations are fetched on demand with the same host-wide,
cursor-paginated `thread/list` query and `archived: true`. Restoring and
permanently deleting them use `thread/unarchive` and `thread/delete`; deletion
is always guarded by a confirmation dialog. Context compaction is available
both as `/compact` and as an explicit current-task menu action.

The MCP status view can reload the remote MCP configuration and start the same
OAuth request used by Desktop. Android opens the returned authorization URL in
the system browser and waits for `mcpServer/oauthLogin/completed`; SSH hosts
whose OAuth provider redirects to loopback must configure a reachable
`mcp_oauth_callback_url` or an SSH tunnel on the remote host.

## Security model

- Passwords, private keys and passphrases are encrypted with an AES-GCM key
  generated inside Android Keystore.
- Android backup and device transfer are disabled for all app data domains.
- A new SSH host is rejected before authentication and its SHA-256 fingerprint
  is shown for explicit confirmation. A changed key is always blocked.
- app-server uses stdio inside SSH. No app-server TCP listener is exposed.
- The remote thread starts with `workspace-write` sandboxing and `on-request`
  approval by default. Named permission profiles are loaded from the host.
  Explicit full access maps to the app-server `dangerFullAccess` policy.

## Compatibility boundary

The app uses stable app-server methods and tolerant JSON parsing. Unknown item
types are ignored, while unknown server-initiated requests are surfaced rather
than automatically approved. Because schemas are tied to the installed Codex
version, the Android protocol layer should be tested whenever the remote Codex
installation is upgraded across major protocol changes.

Current SSH connection setup supports direct password and private-key hosts.
OpenSSH config expansion, ProxyJump, hardware-backed SSH agents and managed
Remote Control relay pairing are not implemented.

SSHJ's `curve25519` key-exchange factories are excluded on Android because the
platform JCA does not expose the `X25519` key-pair generator expected by SSHJ.
The client retains the interoperable ECDH and DH group14 families rather than
failing before host-key verification.
