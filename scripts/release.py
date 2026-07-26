#!/usr/bin/env python3
"""Release discovery and publication helpers for Sorty."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SEMVER_PATTERN = re.compile(
    r"^(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)"
    r"(?P<beta>-beta\.(?P<beta_number>[1-9]\d*))?$"
)
TAG_PATTERN = re.compile(
    r"^sorty-(?P<version>\d+\.\d+\.\d+(?:-beta\.[1-9]\d*)?)\+"
    r"(?P<minecraft>[0-9A-Za-z][0-9A-Za-z._+-]*)-"
    r"(?P<loader>[a-z0-9][a-z0-9._-]*)$"
)
LOADER_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._-]*$")
FABRIC_API_PROJECT_ID = "P7dR8mSH"


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class SemVer:
    major: int
    minor: int
    patch: int
    beta: int | None = None

    @classmethod
    def parse(cls, value: str) -> SemVer:
        match = SEMVER_PATTERN.fullmatch(value)
        if not match:
            raise ReleaseError(
                f"Unsupported mod_version {value!r}; expected X.Y.Z or X.Y.Z-beta.N"
            )
        return cls(
            int(match["major"]),
            int(match["minor"]),
            int(match["patch"]),
            int(match["beta_number"]) if match["beta_number"] else None,
        )

    @property
    def is_beta(self) -> bool:
        return self.beta is not None

    def _key(self) -> tuple[int, int, int, int, int]:
        # A stable version sorts after all betas with the same numeric core.
        return (
            self.major,
            self.minor,
            self.patch,
            0 if self.is_beta else 1,
            self.beta or 0,
        )

    def __lt__(self, other: SemVer) -> bool:
        if not isinstance(other, SemVer):
            return NotImplemented
        return self._key() < other._key()

    def __str__(self) -> str:
        base = f"{self.major}.{self.minor}.{self.patch}"
        return f"{base}-beta.{self.beta}" if self.is_beta else base


@dataclass(frozen=True)
class ReleaseIdentity:
    minecraft_version: str
    mod_version: SemVer
    mod_loader: str = "fabric"

    @property
    def tag(self) -> str:
        return f"sorty-{self.mod_version}+{self.minecraft_version}-{self.mod_loader}"

    @property
    def title(self) -> str:
        return (
            f"Sorty {self.mod_version} for Minecraft {self.minecraft_version} "
            f"({self.mod_loader})"
        )

    @property
    def primary_artifact(self) -> str:
        return f"{self.tag}.jar"

    @property
    def sources_artifact(self) -> str:
        return f"{self.tag}-sources.jar"

    @property
    def release_type(self) -> str:
        return "beta" if self.mod_version.is_beta else "release"


def run_git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode:
        raise ReleaseError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout


def parse_properties(content: str) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in content.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def github_request(
    repository: str, path: str, *, token: str, method: str = "GET"
) -> Any:
    url = f"https://api.github.com/repos/{repository}/{path.lstrip('/')}"
    request = urllib.request.Request(
        url,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "sorty-release-workflow",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def github_releases(repository: str, token: str) -> list[dict[str, Any]]:
    releases: list[dict[str, Any]] = []
    page = 1
    while True:
        batch = github_request(
            repository, f"releases?per_page=100&page={page}", token=token
        )
        releases.extend(batch)
        if len(batch) < 100:
            return releases
        page += 1


def modrinth_versions(project_id: str, token: str | None) -> list[dict[str, Any]]:
    url = (
        "https://api.modrinth.com/v2/project/"
        f"{urllib.parse.quote(project_id, safe='')}/version"
    )
    headers = {"User-Agent": "maksimir-software-lab/sorty-release-workflow"}
    if token:
        headers["Authorization"] = token
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise ReleaseError(
                f"Modrinth project {project_id!r} does not exist or is inaccessible"
            ) from error
        raise


def parse_tag(tag: str) -> ReleaseIdentity | None:
    match = TAG_PATTERN.fullmatch(tag)
    if not match:
        return None
    return ReleaseIdentity(
        match["minecraft"], SemVer.parse(match["version"]), match["loader"]
    )


def release_candidate(
    identity: ReleaseIdentity,
    *,
    published_github_tags: Iterable[str],
    all_github_tags: Iterable[str],
    modrinth_version_numbers: Iterable[str],
    published_modrinth_version_numbers: Iterable[str] = (),
    listed_modrinth_version_numbers: Iterable[str] = (),
) -> tuple[str, str | None]:
    published: dict[str, SemVer] = {}
    for tag in set(published_github_tags) | set(published_modrinth_version_numbers):
        parsed = parse_tag(tag)
        if (
            parsed
            and parsed.minecraft_version == identity.minecraft_version
            and parsed.mod_loader == identity.mod_loader
        ):
            published[tag] = parsed.mod_version

    ordered = sorted(published.items(), key=lambda item: item[1])
    latest = ordered[-1] if ordered else None
    previous = next(
        (tag for tag, version in reversed(ordered) if version < identity.mod_version),
        None,
    )
    exact_github = identity.tag in set(all_github_tags)
    exact_modrinth = identity.tag in set(modrinth_version_numbers)
    github_published = identity.tag in set(published_github_tags)
    modrinth_listed = identity.tag in set(listed_modrinth_version_numbers)

    if latest and identity.mod_version < latest[1]:
        raise ReleaseError(
            f"{identity.tag} regresses from latest published {latest[0]}"
        )

    if (
        latest
        and identity.mod_version._key() == latest[1]._key()
        and github_published
        and modrinth_listed
    ):
        return "complete", previous

    if latest and identity.mod_version._key() == latest[1]._key():
        return "resume", previous

    if exact_github or exact_modrinth:
        return "resume", previous
    return "new", previous


def tag_target(tag: str) -> str | None:
    result = subprocess.run(
        ["git", "rev-list", "-n", "1", tag],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip() if result.returncode == 0 else None


def discover(args: argparse.Namespace) -> int:
    github_token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not github_token:
        raise ReleaseError("GH_TOKEN is required")

    releases = github_releases(args.repository, github_token)
    published_tags = {
        release["tag_name"] for release in releases if not release.get("draft", False)
    }
    all_release_tags = {release["tag_name"] for release in releases}
    modrinth = modrinth_versions(
        args.modrinth_project_id, os.environ.get("MODRINTH_TOKEN")
    )
    modrinth_numbers = {version["version_number"] for version in modrinth}
    published_modrinth_numbers = {
        version["version_number"]
        for version in modrinth
        if version.get("status") in {"listed", "archived", "unlisted"}
    }
    listed_modrinth_numbers = {
        version["version_number"]
        for version in modrinth
        if version.get("status") == "listed"
    }

    refs = run_git(
        "for-each-ref",
        "--format=%(refname:short)%00%(objectname)",
        "refs/remotes/origin/mc/*",
    )
    candidates: list[dict[str, Any]] = []
    skipped: list[dict[str, str]] = []
    errors: list[str] = []

    for line in refs.splitlines():
        if not line:
            continue
        remote_ref, sha = line.split("\0", 1)
        branch = remote_ref.removeprefix("origin/")
        try:
            properties = parse_properties(run_git("show", f"{sha}:gradle.properties"))
            minecraft_version = properties.get("minecraft_version", "")
            raw_mod_version = properties.get("mod_version", "")
            mod_loader = properties.get("mod_loader", "")
            if branch != f"mc/{minecraft_version}":
                raise ReleaseError(
                    f"{branch}: branch must match minecraft_version "
                    f"(expected mc/{minecraft_version})"
                )
            if not LOADER_PATTERN.fullmatch(mod_loader):
                raise ReleaseError(
                    f"{branch}: invalid mod_loader {mod_loader!r}; "
                    "expected a lowercase loader slug"
                )
            identity = ReleaseIdentity(
                minecraft_version, SemVer.parse(raw_mod_version), mod_loader
            )
            existing_target = tag_target(identity.tag)
            if existing_target and existing_target != sha:
                raise ReleaseError(
                    f"{identity.tag} points to {existing_target}, not branch head {sha}"
                )
            state, previous_tag = release_candidate(
                identity,
                published_github_tags=published_tags,
                all_github_tags=all_release_tags,
                modrinth_version_numbers=modrinth_numbers,
                published_modrinth_version_numbers=published_modrinth_numbers,
                listed_modrinth_version_numbers=listed_modrinth_numbers,
            )
            if state == "complete":
                skipped.append({"branch": branch, "reason": "already published"})
                continue
            candidates.append(
                {
                    "branch": branch,
                    "sha": sha,
                    "minecraft_version": minecraft_version,
                    "mod_version": str(identity.mod_version),
                    "mod_loader": identity.mod_loader,
                    "tag": identity.tag,
                    "title": identity.title,
                    "release_type": identity.release_type,
                    "primary_artifact": identity.primary_artifact,
                    "sources_artifact": identity.sources_artifact,
                    "previous_tag": previous_tag or "",
                    "state": state,
                }
            )
        except ReleaseError as error:
            errors.append(str(error))

    result = {"candidates": candidates, "skipped": skipped, "errors": errors}
    Path(args.output).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    if errors:
        raise ReleaseError("Release discovery rejected one or more branches")
    return 0


def verify_artifacts(args: argparse.Namespace) -> int:
    identity = ReleaseIdentity(
        args.minecraft_version, SemVer.parse(args.mod_version), args.mod_loader
    )
    expected = {identity.primary_artifact, identity.sources_artifact}
    actual = {path.name for path in Path(args.directory).glob("*.jar")}
    if actual != expected:
        raise ReleaseError(
            f"Expected artifacts {sorted(expected)}, found {sorted(actual)}"
        )
    print(
        json.dumps(
            {"primary": identity.primary_artifact, "sources": identity.sources_artifact}
        )
    )
    return 0


def write_notes(args: argparse.Namespace) -> int:
    revision_range = (
        f"{args.previous_tag}..{args.sha}" if args.previous_tag else args.sha
    )
    subjects = [
        line
        for line in run_git(
            "log", "--reverse", "--format=- %s (`%h`)", revision_range
        ).splitlines()
        if line
    ]
    content = "\n".join(subjects) if subjects else "- No user-facing changes recorded."
    Path(args.output).write_text(content + "\n", encoding="utf-8")
    return 0


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def encode_multipart(
    fields: dict[str, str], files: dict[str, tuple[Path, str]]
) -> tuple[bytes, str]:
    boundary = f"sorty-{uuid.uuid4().hex}"
    body = bytearray()
    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        body.extend(value.encode())
        body.extend(b"\r\n")
    for name, (path, content_type) in files.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(
            (
                f'Content-Disposition: form-data; name="{name}"; '
                f'filename="{path.name}"\r\n'
            ).encode()
        )
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
        body.extend(path.read_bytes())
        body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), boundary


def modrinth_metadata(
    identity: ReleaseIdentity, minecraft_version: str, changelog: str
) -> dict[str, Any]:
    return {
        "name": identity.title,
        "version_number": identity.tag,
        "changelog": changelog,
        "dependencies": (
            [
                {
                    "project_id": FABRIC_API_PROJECT_ID,
                    "dependency_type": "required",
                }
            ]
            if identity.mod_loader == "fabric"
            else []
        ),
        "game_versions": [minecraft_version],
        "version_type": identity.release_type,
        "loaders": [identity.mod_loader],
        "featured": False,
        "status": "listed",
    }


def modify_modrinth_version(
    version_id: str, token: str, metadata: dict[str, Any]
) -> None:
    body = json.dumps(metadata).encode()
    request = urllib.request.Request(
        "https://api.modrinth.com/v2/version/"
        f"{urllib.parse.quote(version_id, safe='')}",
        data=body,
        method="PATCH",
        headers={
            "Authorization": token,
            "Content-Type": "application/json",
            "User-Agent": "maksimir-software-lab/sorty-release-workflow",
        },
    )
    try:
        with urllib.request.urlopen(request):
            pass
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise ReleaseError(
            f"Modrinth update failed with HTTP {error.code}: {detail}"
        ) from error


def sync_modrinth(args: argparse.Namespace) -> int:
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        raise ReleaseError("MODRINTH_TOKEN is required")
    identity = ReleaseIdentity(
        args.minecraft_version, SemVer.parse(args.mod_version), args.mod_loader
    )
    primary = Path(args.primary)
    sources = Path(args.sources)
    metadata = modrinth_metadata(
        identity,
        args.minecraft_version,
        Path(args.notes).read_text(encoding="utf-8"),
    )

    versions = modrinth_versions(args.project_id, token)
    existing = next(
        (version for version in versions if version["version_number"] == identity.tag),
        None,
    )
    if existing:
        expected_hashes = {
            primary.name: sha512(primary),
            sources.name: sha512(sources),
        }
        actual_hashes = {
            file["filename"]: file.get("hashes", {}).get("sha512")
            for file in existing.get("files", [])
        }
        if actual_hashes != expected_hashes:
            raise ReleaseError(
                f"Modrinth version {identity.tag} exists with different artifacts"
            )
        status = existing.get("status")
        if status not in {"draft", "unlisted", "listed"}:
            raise ReleaseError(
                f"Modrinth version {identity.tag} has unsupported status {status!r}; "
                "refusing to publish it"
            )
        modify_modrinth_version(existing["id"], token, metadata)
        print(f"Updated and listed Modrinth version {identity.tag}")
        return 0

    metadata.update(
        {
            "project_id": args.project_id,
            "file_parts": ["primary", "sources"],
            "primary_file": "primary",
            "file_types": {"sources": "sources-jar"},
        }
    )
    body, boundary = encode_multipart(
        {"data": json.dumps(metadata)},
        {
            "primary": (primary, "application/java-archive"),
            "sources": (sources, "application/java-archive"),
        },
    )
    request = urllib.request.Request(
        "https://api.modrinth.com/v2/version",
        data=body,
        method="POST",
        headers={
            "Authorization": token,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "maksimir-software-lab/sorty-release-workflow",
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            created = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise ReleaseError(
            f"Modrinth upload failed with HTTP {error.code}: {detail}"
        ) from error
    print(f"Created Modrinth version {created['version_number']}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    discover_parser = subparsers.add_parser("discover")
    discover_parser.add_argument("--repository", required=True)
    discover_parser.add_argument("--modrinth-project-id", required=True)
    discover_parser.add_argument("--output", required=True)
    discover_parser.set_defaults(handler=discover)

    verify_parser = subparsers.add_parser("verify-artifacts")
    verify_parser.add_argument("--directory", required=True)
    verify_parser.add_argument("--minecraft-version", required=True)
    verify_parser.add_argument("--mod-version", required=True)
    verify_parser.add_argument("--mod-loader", required=True)
    verify_parser.set_defaults(handler=verify_artifacts)

    notes_parser = subparsers.add_parser("notes")
    notes_parser.add_argument("--sha", required=True)
    notes_parser.add_argument("--previous-tag", default="")
    notes_parser.add_argument("--output", required=True)
    notes_parser.set_defaults(handler=write_notes)

    modrinth_parser = subparsers.add_parser("sync-modrinth")
    modrinth_parser.add_argument("--project-id", required=True)
    modrinth_parser.add_argument("--minecraft-version", required=True)
    modrinth_parser.add_argument("--mod-version", required=True)
    modrinth_parser.add_argument("--mod-loader", required=True)
    modrinth_parser.add_argument("--primary", required=True)
    modrinth_parser.add_argument("--sources", required=True)
    modrinth_parser.add_argument("--notes", required=True)
    modrinth_parser.set_defaults(handler=sync_modrinth)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        return args.handler(args)
    except (ReleaseError, urllib.error.URLError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
