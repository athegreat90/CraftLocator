#!/usr/bin/env python3
"""Read and validate the compatibility matrix from centralized version pins."""
import json
from pathlib import Path
import re
import sys


def load_targets(path=Path('gradle.properties')):
    properties = dict(line.split('=', 1) for line in path.read_text().splitlines()
                      if line and not line.startswith('#') and '=' in line)
    targets = properties['supported_minecraft_versions'].split(',')
    if not targets or len(set(targets)) != len(targets):
        raise ValueError('Minecraft targets must be nonempty and unique')
    for version in targets:
        if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', version):
            raise ValueError('Invalid Minecraft target: ' + version)
        neo = properties['neo_version.' + version]
        if not re.fullmatch(r'\d+(?:\.\d+){3}', neo) or not neo.startswith(version + '.'):
            raise ValueError('Missing or incompatible NeoForge pin: ' + version)
    if properties['minecraft_version'] not in targets:
        raise ValueError('Default Minecraft version must be supported')
    return targets


if __name__ == '__main__':
    versions = load_targets()
    if sys.argv[1:] == ['--lines']:
        print('\n'.join(versions))
    elif not sys.argv[1:]:
        print(json.dumps(versions))
    else:
        raise SystemExit('Usage: build-targets.py [--lines]')
