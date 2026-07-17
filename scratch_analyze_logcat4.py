import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
log_file = r'c:\Users\nisom\Work\LionLibrary\References\vivo-V2515-Android-16_2026-07-17_164356.logcat'

with open(log_file, 'r', encoding='utf-8') as f:
    data = json.load(f)

msgs = data.get('logcatMessages', [])
first_ts = msgs[0]['header']['timestamp']['seconds'] + msgs[0]['header']['timestamp']['nanos'] / 1e9

for m in msgs:
    ts = m['header']['timestamp']['seconds'] + m['header']['timestamp']['nanos'] / 1e9
    time_delta = ts - first_ts
    if 60 <= time_delta <= 63:
        print(f"+{time_delta:.2f}s {m['header']['tag']} {m.get('message', '')[:200]}")
