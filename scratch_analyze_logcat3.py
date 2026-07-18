import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
log_file = r'c:\Users\nisom\Work\LionLibrary\References\vivo-V2515-Android-16_2026-07-17_164356.logcat'

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

first_ts_float = all_msgs[0]['header']['timestamp']['seconds'] + (all_msgs[0]['header']['timestamp']['nanos'] / 1e9)
print(f"Total messages: {len(all_msgs)}")

vlc_msgs = []
warmup_logs = []

for m in all_msgs:
    tag = m['header']['tag']
    msg = m.get('message', '')
    if tag == 'gam.lionlibrary' or 'vlc' in tag.lower() or 'libvlc' in msg.lower():
        vlc_msgs.append(m)
    if 'warmup' in msg.lower():
        warmup_logs.append(m)

print(f"\nWarmup Logs: {len(warmup_logs)}")
for m in warmup_logs:
    ts = m['header']['timestamp']
    msg_ts = ts['seconds'] + (ts['nanos'] / 1e9)
    print(f"  [+{msg_ts - first_ts_float:6.2f}s] {m['header']['tag']:20s} [{m['header']['logLevel']}] {m['message']}")

print(f"\nTimeline (App and VLC):")
for m in vlc_msgs:
    ts = m['header']['timestamp']
    msg_ts = ts['seconds'] + (ts['nanos'] / 1e9)
    time_delta = msg_ts - first_ts_float
    if 'LibVLC warmup' in m['message'] or 'Fontconfig' in m['message'] or 'AssetManager' in m['message'] or 'JNI' in m['message'] or 'request 1' in m['message']:
        print(f"  [+{time_delta:6.2f}s] {m['header']['tag']:25s} [{m['header']['logLevel']:5s}] {m['message'][:200]}")
        
denials = [m for m in all_msgs if 'denied' in m.get('message', '') and 'link' in m.get('message', '')]
if denials:
    print(f"\nSELinux link denials: {len(denials)}")
    for m in denials[:5]:
        ts = m['header']['timestamp']
        msg_ts = ts['seconds'] + (ts['nanos'] / 1e9)
        print(f"  [+{msg_ts - first_ts_float:6.2f}s] {m['message'][:200]}")
