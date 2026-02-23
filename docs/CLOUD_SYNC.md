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
| **Auth Expired** | Cloud icon: `CloudAlert`. Dialog shows "Not Authenticated". | Pause queue. Attempt silent refresh; wait for user login if fail. |
| **Offline** | Cloud icon: `CloudOff` or dot. | Queue jobs locally. WorkManager retries on connectivity. |
| **Concurrent Edit** | Silent (Last Commit Wins). | Merge cloud state using synchronized NTP timestamps. |
| **API Rate Limit (429)** | Cloud icon pulses/dot. | Enter backoff (up to 30s). Pause all pull operations. |
| **GSheet Workbook Denied** | Cloud icon: `CloudAlert`. | Engine pauses. User must tap icon to re-auth or check permissions. |
| **Event > 30 Days Old** | Auto-removal from list. | Purge event and associated records from local database. |

### Current Status

*   **`CloudOff`**: In demo mode (or if not authenticated), the cloud icon shows `CloudOff`. This indicates that the app is currently in a local-only or mock-only state.
*   **Sync Jobs**: The pending `SyncJob` count is visible in the **Cloud Status Dialog**, although background processing is not yet implemented.
