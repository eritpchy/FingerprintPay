#!/usr/bin/env python3
import hashlib
import json
import pathlib
import re
import struct
import sys
import zipfile

apk, output = map(pathlib.Path, sys.argv[1:])
output.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(apk) as source:
    dex_names = sorted((n for n in source.namelist() if n.startswith('classes') and n.endswith('.dex')), key=lambda n: int(n[7:-4] or '1'))
    if not dex_names:
        raise SystemExit('No DEX in payload')
    manifest = []
    with (output / 'payload.bin').open('wb') as payload:
        payload.write(b'FPDEX001' + struct.pack('<I', len(dex_names)))
        for name in dex_names:
            data = source.read(name)
            payload.write(struct.pack('<I', len(data)))
            payload.write(data)
            manifest.append({'name': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    with zipfile.ZipFile(output / 'resources.apk', 'w', zipfile.ZIP_STORED) as resources:
        for info in source.infolist():
            signature = re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))', info.filename, re.IGNORECASE)
            if info.filename not in dex_names and not signature:
                resources.writestr(info, source.read(info.filename))
(output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
