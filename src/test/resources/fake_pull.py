"""Stand-in for zk_pull_punches.py in tests: same arguments, but "reads the device" from fake_data.csv next to
this script (a full log, like the real device returns) instead of connecting to anything."""
import argparse
import csv
import os
import sys
import time

p = argparse.ArgumentParser()
p.add_argument("--ip", required=True)
p.add_argument("--port", type=int, default=4370)
p.add_argument("--password", type=int, default=0)
p.add_argument("--out-logs", required=True)
p.add_argument("--since")
args = p.parse_args()

if args.ip == "fail":
    print("ERROR: ConnectionError simulated", file=sys.stderr)
    sys.exit(1)

here = os.path.dirname(os.path.abspath(__file__))
sleep_file = os.path.join(here, "fake_sleep.txt")
if os.path.exists(sleep_file):
    time.sleep(float(open(sleep_file).read()))
with open(os.path.join(here, "fake_data.csv"), encoding="utf-8", newline="") as f:
    reader = csv.reader(f)
    header = next(reader)
    rows = [r for r in reader if not r[2].startswith("RAW:") and (not args.since or r[2] >= args.since)]

with open(args.out_logs, "w", newline="", encoding="utf-8-sig") as f:
    w = csv.writer(f)
    w.writerow(header)
    w.writerows(rows)
print("Punches: kept=%d since=%s" % (len(rows), args.since))
