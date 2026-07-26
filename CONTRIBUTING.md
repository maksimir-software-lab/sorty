# Contributing to Sorty

Contributions are welcome. For code changes, open a focused pull request
against `main` and briefly explain what changed and how you tested it.

## Development

The complete toolchain is pinned in `mise.toml`:

```powershell
mise install
mise exec -- .\gradlew.bat build
mise run test-release
```

Gradle can also be run directly with JDK 25:

```powershell
.\gradlew.bat build
```

Keep commit messages short and descriptive. Do not use Conventional Commits.
Do not change `mod_version` during ordinary development.

## Formatting and linting

Format Java and project files with Spotless:

```powershell
.\gradlew.bat spotlessApply
```

Format Python release tooling with Ruff:

```powershell
uv run --frozen ruff format scripts
```

Before opening a pull request, ensure formatting and linting are clean:

```powershell
.\gradlew.bat spotlessCheck
mise run lint-python
mise run check-actions
```

Commit any changes produced by the formatters. All three checks must complete
without errors before you open the pull request.

## Branches

`main` targets the next Minecraft version. Each supported Minecraft version has
a dedicated `mc/<minecraft-version>` branch, such as `mc/26.2`. Releases and
version-specific maintenance changes belong on these branches. Cherry-pick
fixes between branches when they apply to more than one supported version.

## Releasing

When `main` is ready for release, create `mc/<minecraft-version>` from it and
push the branch:

```powershell
git switch -c mc/26.2
git push -u origin mc/26.2
```

On that branch, set `mod_version` and `mod_loader` in `gradle.properties`.
Versions must move forward and use either `X.Y.Z` or `X.Y.Z-beta.N`; the loader
must be a lowercase slug such as `fabric`. Commit and push the release
preparation.

In GitHub Actions, run the **release** workflow in `preview` mode first. If the
preview is correct, run it again in `publish` mode. Publishing creates the tag,
GitHub release, JAR assets, and Modrinth version.

Release identifiers use:

```text
sorty-<semver>+<minecraft-version>-<mod-loader>
```

For example, `sorty-1.0.1+26.2-fabric`. Beta versions are published as
prereleases. If publication fails, fix the problem and rerun `publish`; the
workflow safely resumes matching release state and rejects conflicting
artifacts.
