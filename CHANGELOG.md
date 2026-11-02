# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com) and the project uses [Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.1.0] - 2026-11-02

First test build for real phones. SMS templates are still synthetic.

### Added
- SMS import from the inbox on first run, and automatic capture of new messages.
- Template-driven parser for MTN MoMo, Airtel Money and Zamtel Kwacha (synthetic templates; replace with real formats before 1.0.0).
- Duplicate detection by provider and transaction ID.
- Rule-based categories that learn from "apply to all" corrections.
- Review queue for unrecognised messages; paste and share import.
- Dashboard, transaction list with search and filters, transaction detail, insights charts.
- CSV export, provider toggles, privacy policy screen.
- Encrypted database (SQLCipher, key protected by Android Keystore); no internet permission.
