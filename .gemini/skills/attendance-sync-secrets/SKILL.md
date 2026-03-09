---
name: attendance-sync-secrets
description: Synchronize attendance project secrets from local.properties to GitHub Repository Secrets. Use when sheet IDs or client secrets change locally and need to be updated for GitHub Actions release builds.
---

# Attendance Sync Secrets

This skill synchronizes configuration values from `local.properties` to GitHub Repository Secrets using the `gh` CLI.

## Prerequisites

- **GitHub CLI (`gh`)**: Must be installed and authenticated (`gh auth status`).
- **`local.properties`**: Must exist in the project root with the following keys:
  - `MASTER_SHEET_ID`
  - `EVENT_SHEET_ID`
  - `GOOGLE_CLIENT_SECRETS_JSON` (optional)

## Workflow

1.  **Verify Environment**: Ensure you are in the project root and `gh` is authenticated.
2.  **Extract & Sync**: Run the `sync_secrets.cjs` script to read values and update GitHub.

## Manual Execution

If you prefer to run the commands manually:

```bash
# Get values from local.properties
MASTER_ID=$(grep 'MASTER_SHEET_ID=' local.properties | cut -d'=' -f2)
EVENT_ID=$(grep 'EVENT_SHEET_ID=' local.properties | cut -d'=' -f2)

# Set GitHub Secrets
gh secret set MASTER_SHEET_ID --body "$MASTER_ID"
gh secret set EVENT_SHEET_ID --body "$EVENT_ID"
```
