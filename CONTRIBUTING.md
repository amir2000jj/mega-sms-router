# Contributing

Thanks for helping improve MSR!

1. Fork, create a branch (`feature/short-name`).
2. Keep the privacy rules: no "forward all" behaviour, no telemetry, no secrets in logs, no disabled TLS checks.
3. Add or update unit tests (`app/src/test`) for logic changes.
4. Run `gradle testDebugUnitTest lintDebug assembleDebug` (or let the GitHub Action do it).
5. Open a pull request describing the change and how you tested it.

Never commit keystores, tokens, or real SMS content. Use fake codes such as `000000` in tests.
