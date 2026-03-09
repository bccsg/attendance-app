---
name: attendance-release-manager
description: Automates the Attendance app release process. Use when preparing a new version, bumping version numbers, or creating release tags.
---

# Attendance Release Manager

## Overview
This skill guides the preparation and execution of a new application release. It ensures that internal versioning matches release tags and adheres to Semantic Versioning (SemVer) standards.

## Workflow

### 1. Synchronization Check
Always begin by reading `@docs/RELEASING.md` and comparing it with `references/releasing_reference.md` within this skill.
- If they differ significantly, inform the user and suggest updating the skill's reference or logic.

### 2. Current State Assessment
Retrieve the current versioning information from the project:
- **`versionCode`** and **`versionName`** from `app/build.gradle.kts`.
- Latest git tags using `git tag --sort=-v:refname | head -n 5`.

### 3. Version Suggestion
Determine if the user wants a **Mainline** (Stable) or **Beta** (Pre-release) release.
- **Mainline Suggestion**: If current is `1.0.0`, suggest `1.0.1` (Patch), `1.1.0` (Minor), or `2.0.0` (Major).
- **Beta Suggestion**: If current is `1.0.0-beta.1`, suggest `1.0.0-beta.2`. If current is `1.0.0`, suggest `1.1.0-beta.1`.
- **Note**: `versionCode` must always be incremented by at least 1.

Present the suggestions to the user and await confirmation.

### 4. Codebase Update
Once the version is confirmed:
1.  Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2.  Commit the change:
    ```bash
    git add app/build.gradle.kts
    git commit -m "chore: bump version to <versionName>"
    ```
3.  Instruct the user to push to `main` before tagging.

### 5. Release Instructions
After the commit is pushed, provide the user with the exact commands to trigger the GitHub Actions release:
```bash
git tag v<versionName>
git push origin v<versionName>
```

## Guidelines
- **SemVer Compliance**: Always use `io.github.z4kn4fein:semver` rules.
- **Match Tags**: `versionName` in code MUST match the tag name (without the `v` prefix).
- **GitHub Status**: Tags with hyphens (e.g., `-beta.1`) automatically become "Pre-releases" on GitHub.
