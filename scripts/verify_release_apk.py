"""Check the universal ARM validation APK without installing it or handling signing secrets."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
assert apk.is_file(), f'Build assembleRelease first: {apk}'

def load_alignments(data):
    assert data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian ELF'
    wide = data[4] == 2
    offset = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if wide else 42)
    return [struct.unpack_from('<Q' if wide else '<I', data, offset + i * size + (48 if wide else 28))[0]
            for i in range(count) if struct.unpack_from('<I', data, offset + i * size)[0] == 1]

native = []
with zipfile.ZipFile(apk) as archive, apk.open('rb') as raw:
    for item in archive.infolist():
        if not item.filename.startswith('lib/') or not item.filename.endswith('.so'):
            continue
        abi = item.filename.split('/')[1]
        assert abi in {'armeabi-v7a', 'arm64-v8a'}, f'Unexpected release ABI: {abi}'
        alignments = load_alignments(archive.read(item))
        assert alignments and min(alignments) >= (16384 if abi == 'arm64-v8a' else 4096), item.filename
        raw.seek(item.header_offset + 26)
        name_size, extra_size = struct.unpack('<HH', raw.read(4))
        data_offset = item.header_offset + 30 + name_size + extra_size
        assert item.compress_type == zipfile.ZIP_STORED, f'Unexpected compressed native library: {item.filename}'
        assert data_offset % 16384 == 0, f'Native ZIP offset is not 16 KB aligned: {item.filename}'
        native.append(dict(name=item.filename, elf_alignment=min(alignments), zip_offset=data_offset))
    assert {entry['name'].split('/')[1] for entry in native} == {'armeabi-v7a', 'arm64-v8a'}
    assets = set(archive.namelist())
    for name in ['THIRD_PARTY_NOTICES.md', 'licenses/Apache-2.0.txt', 'licenses/LGPL-2.1.txt', 'licenses/Inter-OFL.txt']:
        assert 'assets/' + name in assets, f'Missing notice: {name}'

with apk.open('rb') as source:
    digest = hashlib.file_digest(source, 'sha256').hexdigest()
report = dict(apk=str(apk), sha256=digest, bytes=apk.stat().st_size,
              native_libraries=native, verification='ELF/ZIP alignment and packaged notices; does not verify production signing')
output = ROOT / 'build/release-apk-verification.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(f'PASS universal ARM APK: {len(native)} native libraries, ARM64 ELF + native ZIP 16 KB alignment, notices included')
print(f'SHA-256 {report["sha256"]}')
