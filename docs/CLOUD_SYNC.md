# Cloud Synchronization Strategy

This document defines the synchronization logic, scheduling, and error handling for communication between the local device and cloud providers.

## 1. Principles

### Local-First Architecture
The application is designed for immediate feedback in high-traffic environments. 
1.  All user actions (e.g., "Mark Present") are committed instantly to the local `attendance_records` table.
2.  A corresponding `SyncJob` is queued in the local `sync_jobs` table for upload.

### Sequential Processing (Planned)
*   **WorkManager Pipeline**: Future implementation will process `sync_jobs` one at a time in strict chronological order to ensure data integrity.
*   **Integrity Protection**: Future implementation will disable remote pulls while `sync_jobs` are pending to prevent stale data overwrites.

### Conflict Resolution: "Last Commit Wins"
*   Conflicts (e.g., two users updating the same record) are resolved by comparing commit timestamps.
*   **Local Deduplication**: The local database uses an `upsertIfNewer` pattern, ensuring only the record with the most recent timestamp is persisted for a given Attendee/Event pair.
*   **Cloud Deduplication**: 
    *   **Pushes**: The application currently appends all changes to the cloud (e.g., Google Sheets). This preserves a full audit trail of changes.
    *   **Pulls**: When fetching data from the cloud, the application reduces the incoming batch to ensure only the **latest record per attendee** (maximum timestamp) is processed. The repository then merges this reduced state with the local database, ensuring that the final local state reflects the last known state from the cloud.
*   **NTP Synchronization**: The application is configured with `TrueTime` to ensure consistent timestamps across distributed devices.

---

## 2. Synchronization Lifecycle

### Attendance Pushes (Local -> Cloud)
*   **Trigger**: \`SyncJob\` entries are created instantly and processed sequentially by \`SyncWorker\`.
*   **Row-Based Indexing**: 
    *   The app tracks a \`lastProcessedRowIndex (M)\` for each event.
    *   After a successful push of \`K\` records, if the cloud returns a total row count \`N = M + K\`, the local index is advanced to \`N\`.
    *   **Gap Detection**: If \`N > M + K\`, it indicates rows were added by another device. In this case, \`SyncWorker\` **stops advancing the index** until a clean pull is performed.

### Attendance Pulls (Cloud -> Local)
*   **Integrity Protection**: Pulls are **automatically skipped** if any local \`SyncJobs\` (pushes) are pending to prevent stale cloud state from overwriting recent local changes.
*   **Trigger-Specific Behavior**:
    1.  **Periodic Sync (\`PullWorker\`)**: ONLY reconciles attendance for the **currently selected event** (if any). Skips master lists and metadata.
    2.  **Event Screen Opening**: ONLY fetches recent events (metadata). Skips all attendance pulls.
    3.  **Full Sync (Login/App Start/Manual)**: Performs the complete suite of pulls (Attendees, Groups, Mappings, Recent Events, and Active Event Attendance).
*   **Differential Pulls**: Uses the local \`lastProcessedRowIndex\` to fetch only new or missed rows (\`M+1\` to \`N\`), significantly reducing bandwidth.

### Master List Sync (Attendees, Groups)
*   **Conditional Pulls**: Uses a \`localMasterListVersion\` (hashed from cloud-native metadata like GDrive file version) to skip global pulls if no data has changed on the cloud.
*   **Trigger**: Only performed during **Full Sync** triggers.

---

## 3. Cloud Deletion & Integrity Strategies

### "Missing on Cloud" Flagging
Instead of immediate deletion (which could break local history), records missing from the cloud are handled via a flagging strategy:
*   **Attendee/Group/Event**: Marked with a \`notExistOnCloud = true\` flag.
*   **Placeholders**: If a cloud pull references an unknown ID (e.g., a person added by another device), a local placeholder is created and flagged.
*   **UI Resolution**: Discrepancy counts are displayed in the **Cloud Status Dialog**, linking to a **Resolution Screen** for manual cleanup (Swipe-to-Delete or Purge All).

### Error Scenarios & UX Indicators

| Scenario | UX Handling | System Action |
| :--- | :--- | :--- |
| **Auth Expired** | Cloud icon: `CloudAlert`. Dialog shows "Session Expired". | Pause queue. Provide "Login Again" repair path. **Data is preserved.** |
| **Logout** | Cloud icon: `CloudOff`. | Purge all local data tables via `clearAllData()`. Enter Demo Mode (unauthenticated state). Requires acknowledgment if pending jobs exist. |
| **Offline** | Cloud icon: `CloudAlert`. Dialog shows "No Internet". | Queue jobs locally. WorkManager retries on connectivity. Disable "Sync Now" button. |
| **Concurrent Edit** | Silent (Last Commit Wins). | Merge cloud state using synchronized NTP timestamps. |
| **API Rate Limit (429)** | Cloud icon: `CloudAlert`. | Enter backoff (up to 30s). Pause all pull operations. |
| **GSheet Workbook Denied** | Cloud icon: `CloudAlert`. | Engine pauses. User must tap icon to re-auth or check permissions. |
| **Event > 30 Days Old** | Auto-removal from list. | Purge event and associated records from local database. |

### Current Status

*   **`CloudAlert`**: Shown when there is an error, expired session, or **no internet connection**.
*   **`Sync` (Rotating)**: Indicates an active sync operation.
*   **Data Preservation**: Identity-aware login ensures that re-authenticating the same user preserves local state and pending `SyncJob` entries. Transitioning to a *different* account or Logging Out requires explicit acknowledgment of data loss.
