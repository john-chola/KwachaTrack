# Replacing the synthetic SMS formats with real ones

The templates in `core/parser/src/main/resources/templates.json` and the fixtures in `core/parser/src/test/resources/fixtures/` are **invented**. They prove the pipeline works (the tests pass at 100%), but real MoMo, Airtel Money and Zamtel messages will be worded differently. This is the most important job before 1.0.0, and it is week 1–2 of the plan.

## 1. Collect

- Aim for 30–50 messages per provider and per type: sent, received, cash out, cash in, airtime, bill payment, merchant payment.
- Ask family and friends, with their consent. Include failed and reversed transactions, promotions and balance checks: these become **negative** fixtures that must not parse.
- Note the **exact sender ID** each provider uses (what appears as the sender in the SMS app). Put those in the `senders` list in `templates.json`.

## 2. Anonymise

Before anything goes into Git, replace:

- people's names with fake ones (keep the shape: two words, apostrophes, hyphens),
- phone numbers with fake ones of the same length,
- transaction IDs with fake ones of the same format,
- amounts and balances if they're identifying.

Keep everything else exactly as it was: spacing, punctuation, capitalisation and date format matter to the regex.

## 3. Write fixtures

Add a hand-written file per provider and type, for example `fixtures/mtn/sent_real.txt`:

```
sender: MoMo
received: 1761741000000
body: <the anonymised message, on one line>
expect: type=SENT; amount=125000; fee=250; counterparty=JOHN BANDA; balance=4198; txnId=ABC123; timestamp=1767881580000
```

- Amounts are in **ngwee** (K1,250.00 → `125000`).
- `timestamp` is the date in the message as epoch milliseconds in Lusaka time. If the message has no date, use the `received` value.
- For a message that must not parse, write `expect: FAILURE`.

Once you have real files, delete the synthetic ones (or stop running `tools/gen_fixtures.py`).

## 4. Write templates

For each format, add a template with named groups:

| Group | Required | Notes |
|---|---|---|
| `amount` | yes | e.g. `(?<amount>[\d,]+(?:\.\d{1,2})?)` |
| `txnId` | yes | |
| `fee`, `balance` | no | same shape as amount |
| `counterparty` | no | use a lazy `.+?` followed by fixed text |
| `date` | no | needs a `dateFormat` (Java `DateTimeFormatter` pattern; several can be separated by `\|`) |

Order matters: more specific templates go first. Matching is case-insensitive and whitespace is collapsed before matching.

## 5. Run the gate

```bash
./gradlew :core:parser:test
```

`FixtureAccuracyTest` prints the accuracy and the first failures, fails below 95%, fails if any template has no fixture, and fails if any negative fixture parses. When you change a template, bump `"version"` in `templates.json`.
