#!/usr/bin/env python3
import hashlib
import json
import pathlib
import struct
import sys
import zipfile

PAYLOAD = 'payload.bin'


def verify_shared_dex(archive, expected=None):
    payload = archive.read(PAYLOAD)
    if not payload.startswith(b'FPDEX001') or len(payload) < 12:
        raise ValueError('Invalid shared DEX payload')
    count = struct.unpack_from('<I', payload, 8)[0]
    if not 0 < count <= 64:
        raise ValueError('Invalid shared DEX count')
    offset, dex = 12, []
    for _ in range(count):
        if len(payload) - offset < 4:
            raise ValueError('Truncated shared DEX payload')
        size = struct.unpack_from('<I', payload, offset)[0]
        offset += 4
        data = payload[offset:offset + size]
        if len(data) != size or size < 112 or not data.startswith(b'dex\n'):
            raise ValueError('Invalid shared DEX file')
        dex.append(data)
        offset += size
    if offset != len(payload) or (expected is not None and dex != expected):
        raise ValueError('Shared DEX payload does not match APK')
    manifest = json.loads(archive.read('payload-manifest.json'))
    if len(manifest) != len(dex) or any(
            item['size'] != len(data) or item['sha256'] != hashlib.sha256(data).hexdigest()
            for item, data in zip(manifest, dex)):
        raise ValueError('Shared DEX manifest mismatch')
    copies = [0] * len(dex)
    for name in archive.namelist():
        if name.endswith(('.apk', '.jar', '.dex')):
            raise ValueError('Unexpected standalone DEX container: ' + name)
        if name.endswith('.sha256sum'):
            continue
        data = archive.read(name)
        for i, item in enumerate(dex):
            copies[i] += data.count(item)
    if any(count != 1 for count in copies):
        raise ValueError('Each DEX must occur exactly once in the module ZIP')


def read_file(archive, name):
    if name.startswith('lib/') and name.endswith('.so'):
        data = archive.read(name + '.head') + archive.read(PAYLOAD) + archive.read(name + '.tail')
    else:
        data = archive.read(name)
    if hashlib.sha256(data).hexdigest() != archive.read(name + '.sha256sum').decode().strip():
        raise ValueError('Module file hash mismatch: ' + name)
    return data


def pack(source, destination, payload):
    if sys.platform != 'linux':
        raise SystemExit('Use the remote Linux builder')
    source, destination = pathlib.Path(source), pathlib.Path(destination)
    files = sorted(p for p in source.rglob('*') if p.is_file())
    shared = pathlib.Path(payload).read_bytes()
    if not shared.startswith(b'FPDEX001') or len(shared) < 12:
        raise ValueError('Invalid shared DEX payload')
    entries = {PAYLOAD: shared, PAYLOAD + '.sha256sum': hashlib.sha256(shared).hexdigest().encode()}
    for path in files:
        name = path.relative_to(source).as_posix()
        data = path.read_bytes()
        if name.startswith('lib/') and name.endswith('.so'):
            offset = data.find(shared)
            if offset < 0 or data.find(shared, offset + 1) >= 0:
                raise ValueError('Expected one embedded DEX payload: ' + name)
            for suffix, part in [('.head', data[:offset]), ('.tail', data[offset + len(shared):])]:
                entries[name + suffix] = part
                entries[name + suffix + '.sha256sum'] = hashlib.sha256(part).hexdigest().encode()
        else:
            entries[name] = data
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(name, data)
    with zipfile.ZipFile(destination) as archive:
        verify_shared_dex(archive)
        for path in files:
            name = path.relative_to(source).as_posix()
            if not name.endswith('.sha256sum') and read_file(archive, name) != path.read_bytes():
                raise ValueError('Module round-trip mismatch: ' + name)


if __name__ == '__main__':
    pack(*sys.argv[1:])
