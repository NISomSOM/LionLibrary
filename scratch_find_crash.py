import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
log_file = r'c:\Users\nisom\Work\LionLibrary\References\vivo-V2515-Android-16_2026-07-17_165216.logcat'

try:
    with open(log_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
except Exception as e:
    print(f"Error reading JSON: {e}")
    sys.exit(1)

all_msgs = data.get('logcatMessages', [])
if not all_msgs:
    print("No logcat messages found")
    sys.exit(0)

print(f"Total messages: {len(all_msgs)}")

fatal_logs = []
for m in all_msgs:
    log_level = m['header'].get('logLevel', '')
    msg = m.get('message', '')
    if log_level == 'FATAL' or 'FATAL EXCEPTION' in msg or 'AndroidRuntime' in m['header']['tag']:
        fatal_logs.append(m)

if fatal_logs:
    print("\nFound FATAL logs:")
    for m in fatal_logs:
        print(f"[{m['header']['tag']}] {m['message']}")
else:
    print("\nNo FATAL EXCEPTION found. Looking for gam.lionlibrary ERRORs...")
    for m in all_msgs:
        if m['header']['tag'] == 'gam.lionlibrary' and m['header'].get('logLevel') == 'ERROR':
            print(f"[ERROR] {m['message']}")
            
    print("\nLooking for libc crash...")
    for m in all_msgs:
        if 'DEBUG' in m['header']['tag'] and 'signal' in m.get('message', ''):
            print(f"[{m['header']['tag']}] {m['message']}")
