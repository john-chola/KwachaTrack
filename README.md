# KwachaTrack

[![CI](https://github.com/OWNER/kwachatrack/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/kwachatrack/actions/workflows/ci.yml)

An Android app that reads your **MTN MoMo, Airtel Money and Zamtel Kwacha** SMS messages and shows where your money goes, with no manual entry.

> **Privacy promise.** KwachaTrack has no internet permission. It reads only mobile money messages, stores them in an encrypted database on your phone, and cannot send anything anywhere. You can check this yourself in `app/src/main/AndroidManifest.xml`.

<!-- Add screenshots and a 60-second demo video here before release. -->

## What it does

- **Imports your history** on first run and **captures new messages** as they arrive.
- **Parses** each message into provider, type, amount, fee, counterparty, balance, transaction ID and date.
- **Skips duplicates** using the provider's transaction ID.
- **Categorises** automatically (Food, Transport, Utilities…) and **learns** when you correct it.
- **Dashboard and insights**: money in, money out, fees, top categories, balance trend, six-month comparison.
- **Review queue** for messages it didn't understand; **paste or share** messages in if you don't grant SMS access.
- **CSV export** and per-provider on/off switches.

## Download

Get the latest APK from [Releases](https://github.com/OWNER/kwachatrack/releases). On your phone, allow "Install unknown apps" for your browser or file manager when prompted.

## Build it yourself

Requirements: Android Studio (latest stable), JDK 17, Android SDK 36. A machine with 8 GB of RAM or more is comfortable.

```bash
git clone https://github.com/OWNER/kwachatrack.git
cd kwachatrack
./gradlew :core:parser:test :core:domain:test   # fast JVM tests, no emulator
./gradlew assembleDebug                          # APK in app/build/outputs/apk/debug/
```

Or open the folder in Android Studio and press Run.

## Project layout

```
app/                 Application, navigation, DI wiring, manifest
core/domain/         Pure Kotlin: models, use cases, repository interfaces
core/parser/         Pure Kotlin: SMS parsers, templates.json, fixtures
core/data/           Room + SQLCipher, SMS reader/receiver, WorkManager, repositories
core/ui/             Theme, shared Compose components, charts
feature/onboarding   Permission flow and first import
feature/dashboard    Home screen
feature/transactions List, detail, review queue, paste import
feature/insights     Charts
feature/settings     Providers, rules, CSV export, privacy
docs/                Architecture, decisions, test plan, privacy policy, SMS sample guide
tools/               Fixture generator
```

The domain and parser modules have no Android dependencies, so their tests run on a plain JVM in seconds. See [docs/architecture.md](docs/architecture.md) for the design and the reasons behind it.

## Status: before version 1.0.0

The SMS templates and test fixtures in this repo are **synthetic**: they show the approach but are not the providers' real wording. Before tagging 1.0.0, collect real messages and rewrite the templates; [docs/sms-samples.md](docs/sms-samples.md) walks through it. The sender IDs in `templates.json` also need confirming from real phones.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and update `CHANGELOG.md`.
2. Run the manual checklist in [docs/test-plan.md](docs/test-plan.md).
3. `git tag v1.0.0 && git push origin v1.0.0`. GitHub Actions builds, signs and attaches the APK.

Signing needs four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Create the keystore once with:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kwachatrack
base64 -w0 release.jks   # paste the output into KEYSTORE_BASE64
```

Never commit the keystore, and back it up in two safe places: without it you can't ship updates.

## Why not the Play Store (yet)

Google Play only allows SMS permissions for default SMS apps and a short list of approved exceptions. A Play build would have to rely on paste and share import only. GitHub Releases (and later F-Droid) keep the full automatic experience.

## Licence

MIT. See [LICENSE](LICENSE).
