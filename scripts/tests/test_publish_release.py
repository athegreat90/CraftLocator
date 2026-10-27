"""Exercise release side effects with a fake gh CLI and real temporary Git history."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'publish-release.sh'
FAKE_GH = r'''#!/usr/bin/env python3
import json,os,pathlib,sys
root=pathlib.Path(os.environ['FAKE_ROOT'])
a=sys.argv[1:]
with (root/'calls.jsonl').open('a') as f:f.write(json.dumps(a)+'\n')
state=os.environ.get('FAKE_RELEASE','missing')
if a[0]=='api':
    if state=='forbidden':
        print('gh: Forbidden (HTTP 403)',file=sys.stderr);sys.exit(1)
    if state=='missing':
        print('gh: Not Found (HTTP 404)',file=sys.stderr);sys.exit(1)
    print(json.dumps({'draft':state=='draft','html_url':'https://github.com/test/repo/releases/tag/test'}))
elif a[:2]==['release','create']:
    notes=pathlib.Path(a[a.index('--notes-file')+1]).read_text()
    (root/'notes.md').write_text(notes)
elif a[:2]==['release','upload']:
    for p in a[3:5]:
        path=pathlib.Path(p);(root/path.name).write_bytes(path.read_bytes())
    if os.environ.get('FAKE_UPLOAD_FAILURE')=='1':sys.exit(1)
elif a[:2]==['release','view']:
    print('https://github.com/test/repo/releases/tag/test')
'''


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release-test@example.invalid')
        (self.repo / 'gradle.properties').write_text('mod_version=0.1.0\n')
        self.commit('Initial project')
        self.parent = self.git('rev-parse', 'HEAD').strip()
        (self.repo / 'change.txt').write_text('second change\n')
        self.commit('Add new resource source')
        self.sha = self.git('rev-parse', 'HEAD').strip()
        self.artifacts = self.root / 'artifacts/build/libs'
        self.artifacts.mkdir(parents=True)
        self.jar = self.artifacts / 'craftlocator-0.1.0.jar'
        self.jar.write_bytes(b'tested-jar-bytes')
        executable = self.root / 'bin/gh'
        executable.parent.mkdir()
        executable.write_text(FAKE_GH)
        executable.chmod(0o755)
        self.env = dict(os.environ, PATH=str(executable.parent) + os.pathsep + os.environ['PATH'],
                        GITHUB_SHA=self.sha, GITHUB_REPOSITORY='test/repo',
                        RUNNER_TEMP=str(self.root), FAKE_ROOT=str(self.root))

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True)

    def commit(self, title):
        self.git('add', '.')
        self.git('commit', '-qm', title)

    def run_release(self, **env):
        return subprocess.run(['bash', str(SCRIPT), str(self.root / 'artifacts')], cwd=self.repo,
                              env=dict(self.env, **env), text=True, capture_output=True)

    def calls(self):
        p = self.root / 'calls.jsonl'
        return [json.loads(line) for line in p.read_text().splitlines()] if p.exists() else []

    def test_first_release_targets_exact_commit_and_publishes_after_upload(self):
        result = self.run_release()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        self.assertEqual([c[:2] for c in calls[1:]],
                         [['release', 'create'], ['release', 'upload'], ['release', 'edit'], ['release', 'view']])
        create = calls[1]
        self.assertEqual(create[2], 'main-' + self.sha)
        self.assertEqual(create[create.index('--target') + 1], self.sha)
        self.assertIn('--draft', create)
        self.assertIn('--draft=false', calls[3])
        notes = (self.root / 'notes.md').read_text()
        self.assertIn('Initial project', notes)
        self.assertIn('Add new resource source', notes)
        checksum = (self.root / 'craftlocator-0.1.0.jar.sha256').read_text()
        self.assertEqual(checksum, hashlib.sha256(self.jar.read_bytes()).hexdigest() + '  craftlocator-0.1.0.jar\n')

    def test_notes_include_only_changes_since_previous_release(self):
        self.git('tag', 'main-' + self.parent, self.parent)
        result = self.run_release()
        self.assertEqual(result.returncode, 0, result.stderr)
        notes = (self.root / 'notes.md').read_text()
        self.assertNotIn('Initial project', notes)
        self.assertIn('Add new resource source', notes)
        self.assertIn('/compare/main-' + self.parent + '...main-' + self.sha, notes)

    def test_published_release_is_not_changed_on_rerun(self):
        result = self.run_release(FAKE_RELEASE='published')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.calls()), 1)
        self.assertIn('already published', result.stdout)

    def test_existing_draft_is_resumed_without_duplicate_creation(self):
        result = self.run_release(FAKE_RELEASE='draft')
        self.assertEqual(result.returncode, 0, result.stderr)
        commands = [c[:2] for c in self.calls()]
        self.assertNotIn(['release', 'create'], commands)
        self.assertIn(['release', 'upload'], commands)
        self.assertIn(['release', 'edit'], commands)

    def test_upload_failure_does_not_publish_draft(self):
        result = self.run_release(FAKE_UPLOAD_FAILURE='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(['release', 'edit'], [c[:2] for c in self.calls()])

    def test_missing_tested_jar_prevents_any_release_request(self):
        self.jar.unlink()
        result = self.run_release()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])

    def test_api_permission_error_is_not_treated_as_missing_release(self):
        result = self.run_release(FAKE_RELEASE='forbidden')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.calls()), 1)
        self.assertIn('403', result.stderr)

    def test_checkout_mismatch_prevents_publication(self):
        result = self.run_release(GITHUB_SHA=self.parent)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])


if __name__ == '__main__':
    unittest.main()
