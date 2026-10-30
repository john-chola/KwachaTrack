#!/usr/bin/env python3
"""Generates SYNTHETIC parser fixtures for development.

The message wording here is invented. Once you have real messages, add them as
hand-written fixture files next to these (anonymised: fake names, numbers and IDs)
and delete the synthetic ones you no longer need.

Expected values are computed from the inputs, independently of the regexes in
templates.json, so the tests genuinely check the templates.

Usage: python3 tools/gen_fixtures.py
"""
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "core/parser/src/test/resources/fixtures"
LUSAKA = timezone(timedelta(hours=2))  # Zambia has no daylight saving
RECEIVED = 1_761_741_000_000  # fixed "received at" so tests are repeatable
rng = random.Random(42)

NAMES = ["JOHN BANDA", "MARY MWANSA", "CHANDA-PHIRI GRACE", "MWILA O'BRIEN", "ST. ANNE'S CLINIC",
         "KABWE TUCKSHOP", "NAMWINGA & SONS", "LUBINDA M.", "BWALYA CHILESHE", "Ruth Zulu"]
AGENTS = ["KANYAMA AGENT 12", "CHILENJE MOBILE SHOP", "EAST PARK KIOSK", "MANDA HILL AGENT"]
BILLERS = ["ZESCO", "LUSAKA WATER", "DSTV", "GOTV", "UNZA FEES"]
MERCHANTS = ["SHOPRITE MANDA HILL", "PICK N PAY", "PUMA LONGACRES", "HUNGRY LION CAIRO RD"]


def amount():
    whole = rng.choice([5, 20, 50, 150, 999, 1250, 4800, 12500, 250000])
    cents = rng.choice([0, 0, 50, 5, 99])
    return whole * 100 + cents


def fmt(ngwee, commas=True):
    whole, cents = divmod(ngwee, 100)
    w = f"{whole:,}" if commas else str(whole)
    return f"{w}.{cents:02d}"


def phone():
    return "09" + "".join(str(rng.randint(0, 9)) for _ in range(8))


def when():
    base = datetime(2026, 1, 1, tzinfo=LUSAKA)
    return base + timedelta(days=rng.randint(0, 300), hours=rng.randint(0, 23), minutes=rng.randint(0, 59))


def ms(dt):
    return int(dt.timestamp() * 1000)


def txid(prefix):
    return prefix + "".join(rng.choice("0123456789ABCDEF") for _ in range(10))


def record(sender, body, type_, amt, fee, cp, bal, tid, ts):
    cp = cp.strip().rstrip(".,;").strip() if cp else cp  # the parser trims trailing punctuation
    exp = [f"type={type_}", f"amount={amt}", f"fee={fee}", f"counterparty={cp if cp is not None else ''}",
           f"balance={bal if bal is not None else ''}", f"txnId={tid}", f"timestamp={ts}"]
    return f"sender: {sender}\nreceived: {RECEIVED}\nbody: {body}\nexpect: {'; '.join(exp)}\n"


def mtn(type_):
    amt, fee, bal, d = amount(), rng.choice([0, 250, 1000, 375]), amount() * 3, when()
    ds = d.strftime("%d/%m/%Y %H:%M")
    tid = txid("MP")
    c = rng.random() > 0.3
    sender = rng.choice(["MoMo", "MOMO", "MTN MoMo"])
    if type_ == "SENT":
        cp = rng.choice(NAMES)
        body = f"You have sent ZMW {fmt(amt, c)} to {cp} {phone()} on {ds}. Fee: ZMW {fmt(fee)}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    elif type_ == "RECEIVED":
        cp, fee = rng.choice(NAMES), 0
        body = f"You have received ZMW {fmt(amt, c)} from {cp} {phone()} on {ds}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    elif type_ == "CASH_OUT":
        cp = rng.choice(AGENTS)
        body = f"Cash out of ZMW {fmt(amt, c)} at agent {cp} on {ds} was successful. Fee: ZMW {fmt(fee)}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    elif type_ == "CASH_IN":
        cp, fee = rng.choice(AGENTS), 0
        body = f"Cash in of ZMW {fmt(amt, c)} from agent {cp} on {ds}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    elif type_ == "AIRTIME":
        cp, fee = phone(), 0
        body = f"You have bought airtime of ZMW {fmt(amt, c)} for {cp} on {ds}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    elif type_ == "BILL_PAYMENT":
        cp = rng.choice(BILLERS)
        body = f"Payment of ZMW {fmt(amt, c)} to {cp} account {rng.randint(10000, 99999)} on {ds} was successful. Fee: ZMW {fmt(fee)}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    else:
        cp, fee = rng.choice(MERCHANTS), 0
        body = f"You have paid ZMW {fmt(amt, c)} to merchant {cp} on {ds}. Balance: ZMW {fmt(bal, c)}. ID: {tid}."
    return record(sender, body, type_, amt, fee, cp, bal, tid, ms(d.replace(second=0)))


def airtel(type_):
    amt, fee, bal, d = amount(), rng.choice([0, 250, 1000, 500]), amount() * 2, when()
    ds = d.strftime("%d-%m-%y %H:%M")
    tid = f"PP{d.strftime('%y%m%d')}.{d.strftime('%H%M')}.{txid('A')[:7]}"
    sender = rng.choice(["AirtelMoney", "Airtel Money"])
    ph = rng.choice([f" ({phone()})", f" {phone()}", ""])
    ts = ms(d.replace(second=0))
    if type_ == "SENT":
        cp = rng.choice(NAMES)
        body = f"Txn ID: {tid}. You have sent K{fmt(amt)} to {cp}{ph}. Charge K{fmt(fee)}. Bal: K{fmt(bal)}. {ds}"
    elif type_ == "RECEIVED":
        cp, fee, ts = rng.choice(NAMES), 0, RECEIVED
        body = f"Txn ID: {tid}. Received K{fmt(amt)} from {cp}{ph}. Bal: K{fmt(bal)}. Thank you for using Airtel Money."
    elif type_ == "CASH_OUT":
        cp = rng.choice(AGENTS)
        body = f"Txn ID: {tid}. Withdrawal of K{fmt(amt)} at agent {cp}. Charge K{fmt(fee)}. Bal: K{fmt(bal)}. {ds}"
    elif type_ == "CASH_IN":
        cp, fee = rng.choice(AGENTS), 0
        body = f"Txn ID: {tid}. Deposit of K{fmt(amt)} from agent {cp}. Bal: K{fmt(bal)}. {ds}"
    elif type_ == "AIRTIME":
        cp, fee = phone(), 0
        body = f"Txn ID: {tid}. Airtime purchase of K{fmt(amt)} for {cp}. Bal: K{fmt(bal)}. {ds}"
    else:
        cp = rng.choice(MERCHANTS)
        body = f"Txn ID: {tid}. Paid K{fmt(amt)} to {cp}. Charge K{fmt(fee)}. Bal: K{fmt(bal)}. {ds}"
    return record(sender, body, type_, amt, fee, cp, bal, tid, ts)


def zamtel(type_):
    amt, fee, bal = amount(), rng.choice([0, 300, 1500]), amount() * 2
    tid = txid("ZK")
    sender = rng.choice(["Zamtel", "ZamtelKwacha"])
    sp = rng.choice([" ", ""])
    tail = f" Transaction ID {tid}. Your new balance is ZMW{sp}{fmt(bal)}."
    if type_ == "SENT":
        cp, fee = rng.choice(NAMES), 0
        body = f"Dear Customer, you have transferred ZMW{sp}{fmt(amt)} to {cp} {phone()}.{tail}"
    elif type_ == "RECEIVED":
        cp, fee = rng.choice(NAMES), 0
        body = f"Dear Customer, you have received ZMW{sp}{fmt(amt)} from {cp} {phone()}.{tail}"
    elif type_ == "CASH_OUT":
        cp = rng.choice(AGENTS)
        body = f"Dear Customer, you have withdrawn ZMW{sp}{fmt(amt)} at agent {cp}. Fee ZMW{sp}{fmt(fee)}.{tail}"
    elif type_ == "AIRTIME":
        cp, fee = phone(), 0
        body = f"Dear Customer, airtime of ZMW{sp}{fmt(amt)} bought for {cp}.{tail}"
    else:
        cp = rng.choice(BILLERS)
        body = f"Dear Customer, you have paid ZMW{sp}{fmt(amt)} to {cp}. Fee ZMW{sp}{fmt(fee)}.{tail}"
    return record(sender, body, type_, amt, fee, cp, bal, tid, RECEIVED)


PLAN = {
    "mtn": (mtn, ["SENT", "RECEIVED", "CASH_OUT", "CASH_IN", "AIRTIME", "BILL_PAYMENT", "MERCHANT_PAYMENT"]),
    "airtel": (airtel, ["SENT", "RECEIVED", "CASH_OUT", "CASH_IN", "AIRTIME", "MERCHANT_PAYMENT"]),
    "zamtel": (zamtel, ["SENT", "RECEIVED", "CASH_OUT", "AIRTIME", "BILL_PAYMENT"]),
}

NEGATIVE = [
    ("MoMo", "Transaction of ZMW 50.00 to JOHN BANDA failed. Insufficient funds. ID: MP0000000001."),
    ("MoMo", "Y'ello! Dial *115# to buy bundles with MoMo and win big this festive season."),
    ("MoMo", "Your MoMo balance is ZMW 1,250.00."),
    ("AirtelMoney", "Txn ID: PP261001.0900.A00001. Reversal of K100.00 from JOHN BANDA has been processed."),
    ("AirtelMoney", "Your Airtel Money PIN was changed successfully."),
    ("Zamtel", "Dear Customer, your transaction could not be completed. Please try again."),
    ("MoMo", "You have sent ZMW 0.00 to JOHN BANDA 0961234567 on 01/10/2026 09:00. Fee: ZMW 0.00. Balance: ZMW 10.00. ID: MP0000000002."),
]


def main():
    for provider, (fn, types) in PLAN.items():
        (OUT / provider).mkdir(parents=True, exist_ok=True)
        for t in types:
            records = [fn(t) for _ in range(40)]
            header = "# SYNTHETIC fixtures generated by tools/gen_fixtures.py. Replace with real, anonymised SMS.\n\n"
            (OUT / provider / f"{t.lower()}.txt").write_text(header + "\n".join(records))
    neg = "# Messages that must NOT parse into a transaction.\n\n" + "\n".join(
        f"sender: {s}\nreceived: {RECEIVED}\nbody: {b}\nexpect: FAILURE\n" for s, b in NEGATIVE)
    (OUT / "negative.txt").write_text(neg)
    print("fixtures written to", OUT)


if __name__ == "__main__":
    main()
