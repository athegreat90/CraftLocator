#!/usr/bin/env python3
"""Install checksum-pinned onboarding tools without changing system packages."""
import hashlib
import os
from pathlib import Path
import shlex
import tarfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path('/workspace/.craftlocator-tools')
JDK = ROOT / 'jdk-25.0.5.0.3'
GRADLE = ROOT / 'gradle-9.2.1'
GRADLE_HOME = Path('/workspace/.gradle')
ARTIFACTS = (
    ('jdk25.tar.gz',
     'https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz',
     '6deafad80454d1f968105b1f582d89b8c2e88a5dfc98116a8226e0ec2fd5df8e', JDK),
    ('gradle-9.2.1-bin.zip',
     'https://services.gradle.org/distributions/gradle-9.2.1-bin.zip',
     '72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f', GRADLE),
)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, url, expected, destination in ARTIFACTS:
        archive = ROOT / name
        if not archive.exists():
            temporary = archive.with_suffix(archive.suffix + '.download')
            urllib.request.urlretrieve(url, temporary)
            with temporary.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != expected:
                    temporary.unlink()
                    raise RuntimeError(f'Checksum mismatch for {name}; refusing installation')
            temporary.replace(archive)
        with archive.open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != expected:
                raise RuntimeError(f'Checksum mismatch for cached {name}; refusing installation')
        if not destination.exists():
            if name.endswith('.zip'):
                with zipfile.ZipFile(archive) as source:
                    source.extractall(ROOT)
            else:
                with tarfile.open(archive) as source:
                    source.extractall(ROOT, filter='data')
        print(f'Verified {name}')
    (GRADLE / 'bin/gradle').chmod(0o755)

    trust = Path('/etc/ssl/certs/java/cacerts')
    if not trust.is_file():
        raise RuntimeError('System Java trust store is required; do not disable TLS verification')
    properties = {'systemProp.javax.net.ssl.trustStore': str(trust)}
    for protocol in ('http', 'https'):
        proxy = os.environ.get(protocol.upper() + '_PROXY')
        if proxy:
            parsed = urllib.parse.urlsplit(proxy)
            if parsed.username or parsed.password:
                raise RuntimeError('Authenticated proxy needs platform integration; do not persist credentials')
            if not parsed.hostname:
                raise RuntimeError('Invalid platform proxy hostname')
            properties[f'systemProp.{protocol}.proxyHost'] = parsed.hostname
            properties[f'systemProp.{protocol}.proxyPort'] = str(parsed.port or 80)

    GRADLE_HOME.mkdir(exist_ok=True)
    config = GRADLE_HOME / 'gradle.properties'
    existing = config.read_text() if config.exists() else ''
    begin = '# BEGIN CraftLocator cloud proxy\n'
    end = '# END CraftLocator cloud proxy\n'
    if begin in existing:
        before, rest = existing.split(begin, 1)
        _, after = rest.split(end, 1)
        existing = before + after
    # Do not overwrite unrelated user settings or conflicting proxy configuration.
    for line in existing.splitlines():
        key, separator, value = line.partition('=')
        if separator and key.strip() in properties:
            if value.strip() != properties[key.strip()]:
                raise RuntimeError(f'Existing Gradle setting conflicts: {key.strip()}')
            del properties[key.strip()]
    block = ''.join(f'{key}={value}\n' for key, value in properties.items())
    config.write_text(existing.rstrip() + '\n' + begin + block + end)
    activation = (
        f'export JAVA_HOME={shlex.quote(str(JDK))}\n'
        f'export GRADLE_USER_HOME={shlex.quote(str(GRADLE_HOME))}\n'
        f'export PATH={shlex.quote(str(JDK / "bin"))}:{shlex.quote(str(GRADLE / "bin"))}:"$PATH"\n'
    )
    (ROOT / 'activate.sh').write_text(activation)
    print('Tool activation: source /workspace/.craftlocator-tools/activate.sh')


if __name__ == '__main__':
    main()
