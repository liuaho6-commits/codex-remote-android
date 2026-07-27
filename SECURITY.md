# Security Policy

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub Security Advisories:

https://github.com/liuaho6-commits/codex-remote-android/security/advisories/new

Do not open a public issue for credentials, authentication bypasses, host-key
verification problems, or other vulnerabilities that could put users at risk.

## Scope

The Android client stores SSH secrets using Android Keystore-backed encryption,
pins SSH host keys, and communicates with Codex app-server through SSH stdio.
The remote host, remote Codex installation, and third-party SSH infrastructure
remain outside this project's security boundary.
