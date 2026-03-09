# Releasing & Versioning

This document describes the release process, versioning strategy, and update logic for the Attendance application.

## 1. Release Channels
The application differentiates between two release channels using Semantic Versioning (SemVer):

| Channel | Tag Example | GitHub Status | Update Logic |
| :--- | :--- | :--- | :--- |
| **Mainline** | `v1.0.0`, `v1.1.2` | Stable | Users on mainline only see mainline updates. |
| **Beta** | `v1.0.0-beta.1` | Pre-release | Users on beta see both mainline and newer beta updates. |

### Versioning Strategy
*   **Mainline**: Use standard `MAJOR.MINOR.PATCH` format.
*   **Beta**: Append a hyphen followed by a pre-release identifier and a dot-separated incrementing number (e.g., `-beta.1`, `-beta.2`).
    *   *Note*: The SemVer library correctly sorts these, so `1.0.0-beta.2` is recognized as an update over `1.0.0-beta.1`.

---

## 2. Automated Releases
Releases are automated via GitHub Actions and triggered by pushing a git tag starting with `v`.

### Mandatory Pre-Release Steps
Before tagging a release, you **must** update the versioning information in the codebase to ensure the APK identifies itself correctly.

1.  **Update `app/build.gradle.kts`**:
    *   **`versionCode`**: Increment this integer (e.g., from `1` to `2`). It must always increase for every release.
    *   **`versionName`**: Set this to the target version string (e.g., `"1.1.0"` or `"1.1.0-beta.1"`). This **must match** the git tag you intend to create (excluding the `v` prefix).
2.  **Commit the changes**:
    ```bash
    git add app/build.gradle.kts
    git commit -m "chore: bump version to 1.1.0-beta.1"
    git push origin main
    ```

### How to Trigger a Release
Only after the version bump has been merged/pushed to the main branch should you create the tag:

1.  **Create a tag**:
    *   For a Beta: `git tag v1.1.0-beta.1`
    *   For a Mainline: `git tag v1.1.0`
2.  **Push the tag**:
    *   `git push origin v1.1.0-beta.1` (or the specific tag name)

The GitHub Actions workflow will:
1.  Build a signed release APK using stored secrets.
2.  Create a GitHub Release.
3.  **Automatically determine status**: If the tag contains a hyphen (`-`), the release is marked as a **Pre-release** on GitHub. Otherwise, it is marked as **Latest (Stable)**.

---

## 3. In-App Updates
The application periodically checks the GitHub API for new releases.

*   **Detection**: Uses the `AppUpdateManager` which compares the current version against the latest releases on GitHub using standard SemVer rules.
*   **Mainline Users**: Will only be prompted to update if a newer **Mainline** release is found.
*   **Beta Users**: Will be prompted to update if a newer **Mainline** OR **Beta** release is found (mainline updates are always prioritized).
*   **UI**:
    *   An **Update Available** banner appears on the main screen for critical updates.
    *   The **Version** section in the TopAppBar menu provides manual update options when new versions are available.

---

## 4. Secrets Management
The following secrets are stored in **GitHub Actions Secrets**:

| Secret Name | Description | Original Format |
| :--- | :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | The signing keystore file (`.jks`) | Base64 string |
| `RELEASE_STORE_PASSWORD` | The password for the keystore | Plain text |
| `RELEASE_KEY_ALIAS` | The alias for the signing key | Plain text |
| `RELEASE_KEY_PASSWORD` | The password for the specific key | Plain text |
| `GOOGLE_CLIENT_SECRETS_BASE64` | Google OAuth Client Secret JSON | Base64 string |
| `MASTER_SHEET_ID` | Google Sheets ID for Master List | Plain text |
| `EVENT_SHEET_ID` | Google Sheets ID for Event Records | Plain text |

### Synchronizing Secrets
To synchronize secrets from your `local.properties` to GitHub Repository Secrets, you can use the `attendance-sync-secrets` skill:

```bash
# In Gemini CLI
/skills activate attendance-sync-secrets
# Then ask to sync
Sync my local.properties secrets to GitHub
```

### Critical Backups
You **MUST** maintain secure, offline copies of:
1.  **`attendance-release.jks`**: Required for local builds and recovery.
2.  **Keystore/Key Passwords**: Irrecoverable if lost.
3.  **Google OAuth Client Secret JSON**: Required for Sheets API access.
4.  **Sheet IDs**: The Master and Event Spreadsheet IDs.

---

## 5. Local Release Builds
To build a signed release APK locally:

```bash
export RELEASE_STORE_FILE=path/to/attendance-release.jks
export RELEASE_STORE_PASSWORD=your_password
export RELEASE_KEY_ALIAS=release-key
export RELEASE_KEY_PASSWORD=your_password
export GOOGLE_CLIENT_SECRETS_JSON=$(cat client_secret.json | base64)
export MASTER_SHEET_ID=your_master_id
export EVENT_SHEET_ID=your_event_id

./gradlew assembleRelease
```

### Minification and ProGuard
Release builds enable minification (`isMinifyEnabled = true`). Ensure that `app/proguard-rules.pro` contains the necessary keep rules for Google API Client and Gson to avoid runtime crashes during authentication.
