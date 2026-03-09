# Releasing & Versioning (Reference Snapshot)

This document is a snapshot used by the `attendance-release-manager` skill to ensure its internal logic remains synchronized with the project's official release guidelines.

## 1. Release Channels
| Channel | Tag Example | Update Logic |
| :--- | :--- | :--- |
| **Mainline** | `v1.0.0` | Only mainline updates. |
| **Beta** | `v1.0.0-beta.1` | Both mainline and newer beta updates. |

## 2. Mandatory Pre-Release Steps
1.  Update `app/build.gradle.kts`:
    *   Increment `versionCode`.
    *   Update `versionName` to match the target version.
2.  Commit the changes with `chore: bump version to ...`.
3.  Push to `main`.

## 3. Tagging
*   Trigger release by pushing a tag `v*`.
*   Hyphen in tag = Pre-release on GitHub.
