import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
log_file = r'c:\Users\nisom\Work\LionLibrary\References\vivo-V2515-Android-16_2026-07-17_172053.logcat'

with open(log_file, 'r', encoding='utf-8') as f:
    data = json.load(f)

all_msgs = data.get('logcatMessages', [])
first_ts = all_msgs[0]['header']['timestamp']['seconds'] + all_msgs[0]['header']['timestamp']['nanos'] / 1e9

# Find the EXACT moment player opened (look for VLCVideoLayout, SurfaceView for our app, orientation change)
print("=== PLAYER OPEN MOMENT ===")
for m in all_msgs:
    tag = m['header']['tag']
    msg = m.get('message', '')
    ts = m['header']['timestamp']['seconds'] + m['header']['timestamp']['nanos'] / 1e9
    delta = ts - first_ts
    if 'SurfaceView' in msg and 'singam' in msg:
        print(f"[+{delta:6.2f}s] [{tag}] {msg[:200]}")
    if 'ROTATION' in msg and 'Relayout' in msg:
        print(f"[+{delta:6.2f}s] [{tag}] {msg[:200]}")
    if 'PlayerScreen' in msg:
        print(f"[+{delta:6.2f}s] [{tag}] {msg[:200]}")
    if 'libOpenSLES' in tag:
        print(f"[+{delta:6.2f}s] [{tag}] {msg[:200]}")
    if 'VLC/JNI' in tag:
        print(f"[+{delta:6.2f}s] [{tag}] {msg[:200]}")

# Now find ALL messages from our app around the time video is played
print("\n=== APP MESSAGES 55-97s ===")
for m in all_msgs:
    tag = m['header']['tag']
    msg = m.get('message', '')
    ts = m['header']['timestamp']['seconds'] + m['header']['timestamp']['nanos'] / 1e9
    delta = ts - first_ts
    if 55 <= delta <= 97:
        # Only show messages from our app or VLC
        if 'singam' in msg or 'VLC' in tag or tag == 'gam.lionlibrary' or tag == 'LibVlcProvider' or 'libOpenSLES' in tag or 'libvlc' in msg:
            print(f"[+{delta:6.2f}s] [{tag:25s}] {msg[:250]}")
