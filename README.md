<div align="center">

# 📨 Mega SMS Router (MSR)

**Route approved SMS verification codes to your own HTTP APIs — privately, transparently, with no backend.**

[![Build MSR APK](../../actions/workflows/build-apk.yml/badge.svg)](../../actions/workflows/build-apk.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

[فارسی](README_FA.md) · [Security](SECURITY.md) · [Contributing](CONTRIBUTING.md) · [Changelog](CHANGELOG.md)

</div>

> **Status: early release (v1.0.0).** The source has been written and reviewed but has not yet been verified on a physical device. Please test with your own services first and report issues.

## What it does

MSR is an Android app for **developers** who integrate SMS-based workflows with their own servers. When an SMS arrives from a sender **you explicitly approved**, MSR extracts the code and sends a configurable HTTP request to one or more endpoints **you defined**.

It is deliberately *not* a general SMS forwarder: there is no "forward everything" mode, nothing is sent until you enable forwarding, and there is no developer-operated server or telemetry.

## Features

| Area | Highlights |
|---|---|
| **Sender rules** | Exact sender, optional phone-number normalisation (`+98…` = `09…`), keyword filter, per-rule enable/disable, manual sample-message tester (no real SMS needed) |
| **Code extraction** | Default 4–8 digit code, custom regex + capture group, Persian / Arabic / English digits, regex timeout guard |
| **API destinations** | Multiple independent profiles: GET / POST / PUT / PATCH / DELETE, headers, Bearer / Basic / API-key auth, timeouts, retries, test request |
| **Bodies** | JSON, form-urlencoded, plain text, query parameters for GET. JSON is parsed and re-serialised — never string-concatenated |
| **Routing** | Rule → destination(s); one SMS → many destinations; overlapping rules never cause duplicate requests |
| **Delivery** | WorkManager, network constraint, bounded exponential back-off, retries only for transient errors, `Idempotency-Key` support |
| **Privacy** | Secrets encrypted with Android Keystore (AES-256-GCM), no SMS bodies in history, HTTPS by default, global pause, one-tap data wipe |

### Placeholders

`{{code}}` `{{sender}}` `{{message}}` `{{timestamp}}` (epoch ms) `{{destination_id}}` `{{message_id}}`

```json
{ "code": "{{code}}", "from": "{{sender}}", "id": "{{message_id}}" }
```

Only the fields a destination's template actually uses are kept in the delivery queue.

## Install

1. Open the **[Releases](../../releases)** page (or the latest successful run in **Actions → Artifacts**) and download `app-debug.apk`.
2. Allow installing from this source on your phone and install the APK.
3. Open the app once, grant the **Receive SMS** permission.
4. Create a destination → create a rule → press **Enable forwarding**.

> The published APK is a **debug-signed** build intended for development and testing.

## Build from source

No Android Studio required — the repository ships a GitHub Actions workflow:

1. Fork or push this repository to GitHub.
2. **Actions → Build MSR APK → Run workflow**.
3. Download the `msr-debug-apk` artifact.

Locally: JDK 17, Android SDK (API 35) and Gradle 8.9, then

```bash
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest    # JVM unit + MockWebServer tests
gradle lintDebug
```

Optional release signing uses **your own** keystore via environment variables `MSR_KEYSTORE_PATH`, `MSR_KEYSTORE_PASSWORD`, `MSR_KEY_ALIAS`, `MSR_KEY_PASSWORD`. No key is stored in this repository.

## Architecture

```
sms/         SmsReceiver (lightweight) · SmsAssembler (multipart) · SmsMessageProcessor
domain/      parsing · routing · validation · models
network/     RequestBuilder · BodyRenderer · ApiClient (OkHttp) · RetryPolicy
background/  WorkManager scheduler · DeliveryWorker · DeliveryExecutor
data/        Room entities / DAOs / repository
security/    Keystore cipher · secret redaction
ui/          Jetpack Compose screens (Persian, RTL)
```

SMS reception and HTTP delivery are separate responsibilities: the receiver only performs quick local work, WorkManager performs the network call. No always-on foreground service is used.

## Delivery guarantees & limitations

- Delivery is **at-least-once**, not exactly-once. Use `Idempotency-Key` on your server to de-duplicate.
- Android or OEM battery restrictions and *Force stop* can prevent SMS reception. Uninterrupted operation cannot be promised on all devices.
- Keystore encryption does not protect against a rooted or compromised device.
- Google Play distribution of SMS apps requires policy approval; this project targets direct installation for developers.
- The UI is Persian (RTL) only for now; strings live in `strings.xml` for future localisation.

## Responsible use

Use MSR only with SMS **you are entitled to process** (your own number / services you control). Do not use it to collect other people's authentication codes. See [SECURITY.md](SECURITY.md).

## Permissions

`RECEIVE_SMS`, `INTERNET` — nothing else.

## License

[MIT](LICENSE)
