"""Check compatibility matrix validation and centralized CI inputs."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('build_targets',Path(__file__).resolve().parents[1]/'build-targets.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BuildTargetsTest(unittest.TestCase):
    def load(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'gradle.properties'
            path.write_text(text)
            return module.load_targets(path)

    def test_repository_has_all_requested_targets_and_consistent_pins(self):
        self.assertEqual(module.load_targets(),['26.1.2','26.2','26.3'])

    def test_duplicate_targets_are_rejected(self):
        with self.assertRaises(ValueError):self.load('supported_minecraft_versions=26.3,26.3\n')

    def test_missing_loader_pin_is_rejected(self):
        with self.assertRaises(KeyError):self.load('supported_minecraft_versions=26.3\n')

    def test_mismatched_loader_cannot_build_another_game(self):
        with self.assertRaises(ValueError):self.load('supported_minecraft_versions=26.3\nneo_version.26.3=26.2.0.93\n')

    def test_dynamic_loader_versions_are_rejected(self):
        with self.assertRaises(ValueError):self.load('supported_minecraft_versions=26.3\nneo_version.26.3=26.3.+\n')

    def test_invalid_target_is_rejected(self):
        with self.assertRaises(ValueError):self.load('supported_minecraft_versions=26.3;false\n')

    def test_unsupported_default_is_rejected(self):
        with self.assertRaises(ValueError):self.load('supported_minecraft_versions=26.3\nneo_version.26.3=26.3.0.99\nminecraft_version=26.2\n')
