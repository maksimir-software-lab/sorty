#!/usr/bin/env python3

import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from release import (
    FABRIC_API_PROJECT_ID,
    ReleaseError,
    ReleaseIdentity,
    SemVer,
    modrinth_metadata,
    parse_properties,
    parse_tag,
    release_candidate,
    sha512,
    sync_modrinth,
)


class SemVerTest(unittest.TestCase):
    def test_stable_and_beta_ordering(self):
        values = [
            SemVer.parse("1.0.0"),
            SemVer.parse("1.1.0-beta.1"),
            SemVer.parse("1.1.0-beta.2"),
            SemVer.parse("1.1.0"),
            SemVer.parse("2.0.0-beta.1"),
            SemVer.parse("2.0.0"),
        ]
        shuffled = [values[3], values[0], values[5], values[2], values[1], values[4]]
        self.assertEqual(values, sorted(shuffled))

    def test_rejects_unsupported_versions(self):
        for value in [
            "v1.0.0",
            "1.0",
            "1.0.0-alpha.1",
            "1.0.0-rc.1",
            "1.0.0-b1",
            "1.0.0b1",
            "1.0.0.b1",
            "1.0.0-beta.0",
            "01.0.0",
        ]:
            with self.subTest(value=value), self.assertRaises(ReleaseError):
                SemVer.parse(value)


class IdentityTest(unittest.TestCase):
    def test_stable_names(self):
        identity = ReleaseIdentity("26.2", SemVer.parse("1.0.1"))
        self.assertEqual("sorty-1.0.1+26.2-fabric", identity.tag)
        self.assertEqual("sorty-1.0.1+26.2-fabric.jar", identity.primary_artifact)
        self.assertEqual(
            "sorty-1.0.1+26.2-fabric-sources.jar", identity.sources_artifact
        )
        self.assertEqual("release", identity.release_type)

    def test_beta_names(self):
        identity = ReleaseIdentity("26.2", SemVer.parse("1.1.0-beta.1"))
        self.assertEqual("sorty-1.1.0-beta.1+26.2-fabric", identity.tag)
        self.assertEqual("beta", identity.release_type)
        self.assertEqual(identity, parse_tag(identity.tag))

    def test_loader_is_part_of_identity(self):
        identity = ReleaseIdentity("26.2", SemVer.parse("1.0.1"), mod_loader="neoforge")
        self.assertEqual("sorty-1.0.1+26.2-neoforge", identity.tag)
        self.assertEqual(identity, parse_tag(identity.tag))


class CandidateTest(unittest.TestCase):
    def candidate(
        self,
        current,
        published=(),
        all_tags=(),
        modrinth=(),
        published_modrinth=(),
        listed_modrinth=(),
    ):
        return release_candidate(
            ReleaseIdentity("26.2", SemVer.parse(current)),
            published_github_tags=published,
            all_github_tags=all_tags,
            modrinth_version_numbers=modrinth,
            published_modrinth_version_numbers=published_modrinth,
            listed_modrinth_version_numbers=listed_modrinth,
        )

    def test_first_release(self):
        self.assertEqual(("new", None), self.candidate("1.0.0"))

    def test_new_beta_and_beta_promotion(self):
        self.assertEqual(
            ("new", "sorty-1.0.1+26.2-fabric"),
            self.candidate("1.1.0-beta.1", ["sorty-1.0.1+26.2-fabric"]),
        )
        self.assertEqual(
            ("new", "sorty-1.1.0-beta.2+26.2-fabric"),
            self.candidate("1.1.0", ["sorty-1.1.0-beta.2+26.2-fabric"]),
        )

    def test_complete_release_is_skipped(self):
        tag = "sorty-1.0.1+26.2-fabric"
        self.assertEqual(
            ("complete", None),
            self.candidate(
                "1.0.1",
                [tag],
                [tag],
                [tag],
                published_modrinth=[tag],
                listed_modrinth=[tag],
            ),
        )

    def test_partial_release_is_resumed(self):
        tag = "sorty-1.0.1+26.2-fabric"
        self.assertEqual(("resume", None), self.candidate("1.0.1", [tag], [tag], []))
        self.assertEqual(("resume", None), self.candidate("1.0.1", [], [], [tag]))

    def test_regression_is_rejected(self):
        with self.assertRaises(ReleaseError):
            self.candidate("1.0.2", ["sorty-1.1.0-beta.1+26.2-fabric"])

    def test_modrinth_release_also_prevents_regression(self):
        with self.assertRaises(ReleaseError):
            self.candidate(
                "1.0.2",
                modrinth=["sorty-1.1.0-beta.1+26.2-fabric"],
                published_modrinth=["sorty-1.1.0-beta.1+26.2-fabric"],
            )

    def test_resume_uses_preceding_tag_for_notes(self):
        current = "sorty-1.1.0-beta.1+26.2-fabric"
        previous = "sorty-1.0.1+26.2-fabric"
        self.assertEqual(
            ("resume", previous),
            self.candidate(
                "1.1.0-beta.1",
                published=[previous, current],
                all_tags=[previous, current],
            ),
        )

    def test_github_draft_with_listed_modrinth_is_resumed(self):
        tag = "sorty-1.0.1+26.2-fabric"
        self.assertEqual(
            ("resume", None),
            self.candidate(
                "1.0.1",
                published=[],
                all_tags=[tag],
                modrinth=[tag],
                published_modrinth=[tag],
                listed_modrinth=[tag],
            ),
        )

    def test_github_published_with_modrinth_draft_is_resumed(self):
        tag = "sorty-1.0.1+26.2-fabric"
        self.assertEqual(
            ("resume", None),
            self.candidate(
                "1.0.1",
                published=[tag],
                all_tags=[tag],
                modrinth=[tag],
            ),
        )

    def test_identical_versions_on_other_minecraft_lines_do_not_interfere(self):
        self.assertEqual(
            ("new", None),
            self.candidate("1.0.0", ["sorty-1.0.0+26.1-fabric"]),
        )

    def test_identical_versions_on_other_loaders_do_not_interfere(self):
        self.assertEqual(
            ("new", None),
            self.candidate("1.0.0", ["sorty-1.0.0+26.2-neoforge"]),
        )


class PropertiesTest(unittest.TestCase):
    def test_parses_gradle_properties(self):
        properties = parse_properties(
            "# comment\n"
            "minecraft_version=26.2\n"
            "mod_version = 1.0.0\n"
            "mod_loader=fabric\n"
        )
        self.assertEqual("26.2", properties["minecraft_version"])
        self.assertEqual("1.0.0", properties["mod_version"])
        self.assertEqual("fabric", properties["mod_loader"])


class ModrinthMetadataTest(unittest.TestCase):
    def test_fabric_api_is_a_required_dependency(self):
        identity = ReleaseIdentity("26.2", SemVer.parse("1.0.1"))
        metadata = modrinth_metadata(identity, "26.2", "Changes")
        self.assertEqual(
            [
                {
                    "project_id": FABRIC_API_PROJECT_ID,
                    "dependency_type": "required",
                }
            ],
            metadata["dependencies"],
        )
        self.assertEqual("listed", metadata["status"])

    def test_matching_draft_is_promoted_to_listed(self):
        identity = ReleaseIdentity("26.2", SemVer.parse("1.0.1"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / identity.primary_artifact
            sources = root / identity.sources_artifact
            notes = root / "notes.md"
            primary.write_bytes(b"primary")
            sources.write_bytes(b"sources")
            notes.write_text("Changes\n", encoding="utf-8")
            existing = {
                "id": "version-id",
                "version_number": identity.tag,
                "status": "draft",
                "files": [
                    {
                        "filename": primary.name,
                        "hashes": {"sha512": sha512(primary)},
                    },
                    {
                        "filename": sources.name,
                        "hashes": {"sha512": sha512(sources)},
                    },
                ],
            }
            args = SimpleNamespace(
                project_id="project-id",
                minecraft_version="26.2",
                mod_version="1.0.1",
                mod_loader="fabric",
                primary=str(primary),
                sources=str(sources),
                notes=str(notes),
            )
            with (
                patch.dict(os.environ, {"MODRINTH_TOKEN": "token"}),
                patch("release.modrinth_versions", return_value=[existing]),
                patch("release.modify_modrinth_version") as modify,
            ):
                self.assertEqual(0, sync_modrinth(args))

            modify.assert_called_once()
            version_id, token, metadata = modify.call_args.args
            self.assertEqual("version-id", version_id)
            self.assertEqual("token", token)
            self.assertEqual("listed", metadata["status"])
            self.assertEqual(
                FABRIC_API_PROJECT_ID, metadata["dependencies"][0]["project_id"]
            )


if __name__ == "__main__":
    unittest.main()
