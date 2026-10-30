# Test plan

## Automated (runs in CI on every push)

| Level | What | Where | Target |
|---|---|---|---|
| Parser | Every template against every fixture, edge cases, loader validation | `core/parser/src/test` | 95%+ fixture accuracy, every template covered |
| Domain | Categorisation, deduplication, import summary, recategorise, monthly totals, filters, CSV, money format | `core/domain/src/test` | 80%+ line coverage (Kover gate) |
| Data | DAOs, seeding, dedup, rules, review queue (Robolectric, in-memory Room) | `core/data/src/test` | All DAOs |
| Build | Lint (ktlint), debug APK | CI | Green |

Still to add as the app grows: ViewModel tests with Turbine, Compose UI tests for onboarding and the recategorise flow, and a Room migration test for each new schema version.

## Manual (before every release)

Test on at least two physical phones with different Android versions (one Android 8–10, one Android 13+), and with real SIMs where possible.

### Install and onboarding
- [ ] Fresh install of the release APK from GitHub Releases works.
- [ ] Onboarding explains the privacy promise before asking for SMS access.
- [ ] Granting SMS access imports history; the progress bar moves; the summary shows sensible counts.
- [ ] Denying SMS access shows the explanation and the paste option still works.
- [ ] "Import manually instead" goes to the paste screen.

### Import
- [ ] A new MoMo, Airtel and Zamtel transaction appears on the dashboard within a few seconds of the SMS arriving, with the app closed.
- [ ] Re-running import does not create duplicates.
- [ ] Sharing an SMS from the messaging app opens the paste screen pre-filled.
- [ ] A non-provider SMS (from a friend) never appears anywhere in the app.
- [ ] Turning a provider off in Settings stops new imports from it.

### Categories and review
- [ ] Changing a category with "apply to all" updates past transactions from that counterparty and future ones.
- [ ] The rule appears in Settings and can be deleted.
- [ ] Unrecognised messages appear in the review queue; Ignore removes them; Report opens the share sheet.

### Screens
- [ ] Dashboard totals match a hand count for one month.
- [ ] Transactions: month arrows, search, provider and category chips work; days are grouped newest first.
- [ ] Insights charts render; empty months show the empty state.
- [ ] CSV export opens correctly in a spreadsheet app; amounts are numbers.

### Accessibility and appearance
- [ ] TalkBack reads amounts, buttons and charts meaningfully.
- [ ] Largest system font size: nothing is cut off on the main screens.
- [ ] Dark mode and (Android 12+) dynamic colour look right.

### Security
- [ ] Release build: screenshots of the app are blocked and the recent-apps preview is blank.
- [ ] `adb backup` / cloud backup does not include the database.
- [ ] App info in Android settings shows no internet permission.

### Performance
- [ ] First import of ~5,000 SMS completes in under 10 seconds on a mid-range phone.
