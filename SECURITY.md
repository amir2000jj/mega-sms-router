# Security Policy

## Intended use
MSR processes authentication codes. It must only be used with SMS you are authorised to handle (your own number or services you control). Covert collection of other people's codes is explicitly out of scope and unsupported.

## Design guarantees
- Forwarding is off by default and requires explicit sender rules.
- HTTPS required by default; TLS validation is never disabled.
- Secrets are encrypted with the Android Keystore; they are excluded from history and logs.
- No telemetry and no developer-operated backend.

## Reporting a vulnerability
Please **do not open a public issue** for security problems. Use GitHub's *Security → Report a vulnerability* (private advisory) on this repository. Include steps to reproduce and the affected version. Never include real tokens or real verification codes in reports.

## Known limits
Keystore protection is weaker on rooted/compromised devices. See the README for delivery limitations.
