"""Read the punch log of a ZKTeco attendance device and write it to a CSV file.

Started by the Java application (PythonPunchPuller) once per device, but it can also be run by hand. Based on the
original Lay_ChamCong_ZK.py, with these differences:
  - IP, port and output file are command line arguments, not edited in the code;
  - only the punches from --since onwards are written (the device always returns its whole log, the application
    only needs the newest part);
  - the file is written to a temporary name and then renamed, so a reader never sees a half written file;
  - exit code 0 = success, 1 = failure (the reason is printed to stderr).

The script only READS from the device, it never deletes or changes anything on it.

Output columns: uid,user_id,punch_time,verify,punch,tail_hex
  uid       record number inside the device (can restart if the device log is cleared)
  user_id   code of the person on the device
  punch_time  "YYYY-MM-DD HH:MM:SS", device local time
  verify    how the person was verified (device specific code)
  punch     in/out flag of the device (255 on devices that do not report it)
  tail_hex  the unused rest of the 49 byte record, kept for diagnosis

Examples:
  python zk_pull_punches.py --ip 10.34.1.4 --out-logs punches.csv
  python zk_pull_punches.py --ip 10.34.1.4 --out-logs punches.csv --since "2026-10-10 00:00:00"
"""
import argparse
import csv
import os
import sys
from datetime import datetime
from struct import unpack

from zk import ZK, const

TIME_FORMAT = "%Y-%m-%d %H:%M:%S"
LOG_HEADER = ["uid", "user_id", "punch_time", "verify", "punch", "tail_hex"]
USER_HEADER = ["uid", "user_id", "name", "privilege", "password", "group_id", "card"]

# The FACEPRO1-P (firmware 6.60) returns 49 byte records while pyzk only knows 8/16/40 byte ones, so the records
# are cut here: uid(2) + user_id(24) + verify(1) + time(4) + punch(1) + tail(17).
RECORD = 49

# pyzk keeps its time decoder private; requirements.txt pins the pyzk version this was written against
decode_time = ZK._ZK__decode_time


def read_raw_logs(conn):
    """Yield every record of the device log as [uid, user_id, punch_time, verify, punch, tail_hex].
    punch_time is 'RAW:<hex>' when the device returns a time that cannot be decoded."""
    conn.read_sizes()
    data, size = conn.read_with_buffer(const.CMD_ATTLOG_RRQ)
    if size < 4:
        return
    total = unpack("I", data[:4])[0]
    if total % RECORD != 0:
        raise RuntimeError("Data size %d is not a multiple of %d" % (total, RECORD))
    body = data[4:]
    for i in range(total // RECORD):
        r = body[i * RECORD:(i + 1) * RECORD]
        uid, user_id, verify, ts, punch = unpack("<H24sB4sB", r[:32])
        user_id = user_id.split(b"\x00")[0].decode(errors="ignore")
        try:
            punch_time = decode_time(conn, ts).strftime(TIME_FORMAT)
        except ValueError:
            punch_time = "RAW:" + ts.hex()
        yield [uid, user_id, punch_time, verify, punch, r[32:].hex()]


def write_csv(path, header, rows):
    """Write to a temporary file and rename it: the target is always complete or missing, never half written."""
    tmp = path + ".tmp"
    with open(tmp, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    os.replace(tmp, path)


def parse_args():
    p = argparse.ArgumentParser(description="Read the punch log of a ZKTeco device (read only).")
    p.add_argument("--ip", required=True, help="IP address of the device")
    p.add_argument("--port", type=int, default=4370)
    p.add_argument("--password", type=int, default=0, help="connection password of the device (default 0)")
    p.add_argument("--timeout", type=int, default=15, help="seconds to wait for the connection")
    p.add_argument("--out-logs", required=True, help="CSV file for the punches")
    p.add_argument("--out-users", help="CSV file for the users of the device (optional)")
    p.add_argument("--since", help="only punches from this time on, as 'YYYY-MM-DD HH:MM:SS'")
    args = p.parse_args()
    if args.since:
        try:
            datetime.strptime(args.since, TIME_FORMAT)
        except ValueError:
            p.error("--since must look like 'YYYY-MM-DD HH:MM:SS', got: %r" % args.since)
    return args


def main():
    # the Java side reads our output as UTF-8, avoid the Windows console code page
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    args = parse_args()

    conn = None
    try:
        zk = ZK(args.ip, port=args.port, timeout=args.timeout, password=args.password, force_udp=False,
                ommit_ping=True)
        conn = zk.connect()
        print("Connected:", conn.get_device_name(), "| Serial:", conn.get_serialnumber())

        if args.out_users:
            users = conn.get_users()
            write_csv(args.out_users, USER_HEADER,
                      [[u.uid, u.user_id, u.name, u.privilege, u.password, u.group_id, u.card] for u in users])
            print("Users:", len(users), "->", args.out_users)

        total = kept = unreadable = 0
        rows = []
        for row in read_raw_logs(conn):
            total += 1
            if row[2].startswith("RAW:"):
                # an undecodable time cannot be compared with --since, so it is left out of the file
                unreadable += 1
                continue
            # 'YYYY-MM-DD HH:MM:SS' strings compare in the same order as the times they stand for
            if args.since and row[2] < args.since:
                continue
            rows.append(row)
            kept += 1
        write_csv(args.out_logs, LOG_HEADER, rows)
        print("Punches: total=%d kept=%d unreadable_time=%d -> %s" % (total, kept, unreadable, args.out_logs))
    except Exception as e:
        print("ERROR:", e.__class__.__name__, e, file=sys.stderr)
        sys.exit(1)
    finally:
        if conn:
            conn.disconnect()


if __name__ == "__main__":
    main()
