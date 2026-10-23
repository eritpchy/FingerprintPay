#!/usr/bin/env python3
import hashlib
import io
import pathlib
import sys
import zipfile

PAYLOAD = 'payload.bin'


def read_file(archive, name):
    if name.startswith('lib/') and name.endswith('.so'):
        data = archive.read(name + '.head') + archive.read(PAYLOAD) + archive.read(name + '.tail')
    elif name.startswith('webroot/'):
        with zipfile.ZipFile(io.BytesIO(archive.read('resources.apk'))) as resources:
            data = resources.read('assets/' + name[len('webroot/'):])
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
        elif not name.startswith('webroot/') or name.endswith('.sha256sum'):
            entries[name] = data
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(name, data)
    with zipfile.ZipFile(destination) as archive:
        for path in files:
            name = path.relative_to(source).as_posix()
            if not name.endswith('.sha256sum') and read_file(archive, name) != path.read_bytes():
                raise ValueError('Module round-trip mismatch: ' + name)


if __name__ == '__main__':
    pack(*sys.argv[1:])
