# Releasing Sorty

Sorty releases are built from dedicated Minecraft branches. `main` is active
development and is never released directly.

## Repository setup

Create the Modrinth project manually, then configure these GitHub repository
settings:

- Variable `MODRINTH_PROJECT_ID`: the Modrinth project ID or slug.
- Actions secret `MODRINTH_TOKEN`: a Modrinth token with permission to create
  and edit versions for that project.

The release workflow uses GitHub's built-in token to create tags and releases.
Its publishing job requests only `contents: write`.

The release tooling is run with
[uv](https://docs.astral.sh/uv/). To execute its tests locally:

```powershell
uv run --frozen scripts/test_release.py
```

Project tool versions are pinned in `mise.toml`. With
[mise-en-place](https://mise.jdx.dev/) installed, provision the complete local
toolchain and validate it with:

```powershell
mise install
mise current
mise run lint-python
mise run test-release
mise run check-actions
```

Java, Python, uv, and actionlint are managed by mise. Gradle remains managed by
the checked-in Gradle wrapper.

## Prepare a Minecraft release branch

When `main` is release-ready for a Minecraft version, create and push a branch
whose name matches `minecraft_version` in `gradle.properties`:

```powershell
git switch -c mc/26.2
git push -u origin mc/26.2
```

Every supported Minecraft version has one forward-moving Sorty version line.
Do not use `main` or a second branch for the same Minecraft version.

## Choose a Sorty version

Set `mod_version` and `mod_loader` in the applicable `mc/*` branch and commit
the change. The loader is a lowercase slug such as `fabric` or `neoforge`.
Sorty's current branch uses:

```properties
mod_loader=fabric
```

Stable releases use plain semantic versions:

```properties
mod_version=1.0.1
```

Beta releases for a future minor or major version use a numbered beta suffix:

```properties
mod_version=1.1.0-beta.1
```

Supported progression includes:

```text
1.0.0
1.0.1
1.1.0-beta.1
1.1.0-beta.2
1.1.0
2.0.0-beta.1
2.0.0
```

Versions must move forward. Once `1.1.0-beta.1` has been published for a
Minecraft-version and loader combination, that release line cannot publish a
later `1.0.x` maintenance release. Alpha, release-candidate, and other version
suffixes are rejected.

## Preview and publish

Open the repository's **Actions** tab, select **release**, and run the workflow.

1. Run it in `preview` mode first. Preview fetches all `mc/*` branches, runs
   tests and builds every candidate, and reports what would be published
   without creating tags or releases.
2. Run it again in `publish` mode. The workflow snapshots each branch head,
   builds that exact commit, creates the tag and draft GitHub release, uploads
   the JARs to GitHub and Modrinth, and finally publishes the GitHub release.

Tags, Modrinth version numbers, and artifact names follow
`<mod-name>-<semver>+<minecraft-version>-<mod-loader>`. For example:

```text
Stable tag:     sorty-1.0.1+26.2-fabric
Stable JAR:     sorty-1.0.1+26.2-fabric.jar
Stable sources: sorty-1.0.1+26.2-fabric-sources.jar

Beta tag:       sorty-1.1.0-beta.1+26.2-fabric
Beta JAR:       sorty-1.1.0-beta.1+26.2-fabric.jar
Beta sources:   sorty-1.1.0-beta.1+26.2-fabric-sources.jar
```

Plain SemVer versions become normal GitHub and Modrinth releases.
`-beta.N` versions become GitHub prereleases and Modrinth beta versions.
Release comparisons and notes are isolated by both Minecraft version and mod
loader. Release notes are built from commit subjects since the preceding tag
for the same Minecraft-version and loader combination.

## Retry a failed release

The workflow is designed to resume safely. It accepts an existing matching tag,
draft GitHub release, GitHub assets, or Modrinth version. Existing files must
match the newly built files exactly; conflicting tags or artifacts stop the
workflow instead of being overwritten.

If Modrinth publication fails, correct the configuration or transient problem
and dispatch `publish` again. The GitHub release remains a draft until Modrinth
has accepted the matching version.
