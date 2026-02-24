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
    *   **Pulls**: When fetching data from the cloud, the application processes records in chronological order (by row index or timestamp), ensuring that the final local state reflects the last known state from the cloud.
*   **NTP Synchronization (Planned)**: The application is configured with `TrueTime` to ensure consistent timestamps across distributed devices. In the current implementation, it falls back to `System.currentTimeMillis()`.

---

## 2. Synchronization Lifecycle

### Attendance Pushes (Local -> Cloud)
*   **Trigger**: Currently, `SyncJob` entries are created and stored in the local database. Sequential upload to the cloud provider is a planned feature for a background worker.
*   **Partial Failures**: The provider interface is designed to track successfully processed records and resume from points of failure.

### Master & Event Pulls (Cloud -> Local)
*   **Master Lists (Attendees, Groups)**: Pulled manually on start (if not in demo mode).
*   **Event List**: Pulled on application start and whenever the **Events** screen is opened.
*   **Purge**: Local events and associated attendance records older than 30 days are automatically purged from the device on application start.

---

## 3. Error Scenarios & UX Indicators (Planned Implementation)

While background processing is currently mocked, the application is designed to support the following error and status states:

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
