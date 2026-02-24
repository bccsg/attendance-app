# Architecture

## Technical Stack
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Declarative UI)
*   **Database**: Room (SQLite) with FTS5 support
*   **Asynchrony**: Coroutines & Flow
*   **Dependency Injection**: Hilt
*   **Network**: Retrofit + Google Sheets API v4
*   **Background Tasks**: WorkManager (for Sequential Sync)
*   **Security**: EncryptedSharedPreferences (Jetpack Security)

## Data Layer (Room Schema)

### Tables
*   **`attendees`**: Stores master list (ID, Full Name, Short Name, etc.). Uses FTS5 virtual table for search. Includes \`notExistOnCloud\` flag.
*   **`groups`**: Master list of groups. Includes \`notExistOnCloud\` flag.
*   **`attendee_groups`**: Many-to-Many mapping between attendees and groups.
*   **`events`**: ID (UUID), Title (\`yyMMdd HHmm Name\`), Cloud Event ID, lastSyncTime, \`lastProcessedRowIndex\`, and \`notExistOnCloud\` flag.
*   **`attendance_records`**: Event ID (UUID), Attendee ID, State (\`PRESENT\` or \`ABSENT\`), Timestamp. Uses "Last Commit Wins" deduplication.
*   **`persistent_queue`**: Attendee ID, isLater flag.
*   **`sync_jobs`**: Queue for sequential cloud commits (Payload, CreatedAt).
*   **`queue_archive`**: JSON blob of previous batches, Timestamp, Event ID.

## MVVM Pattern
*   **View**: Compose Screens (MainList, Queue, Archive).
*   **ViewModel**: Manages UI state (Search query, visibility toggles, Selection state, Show Selected Only Mode, Queue, Text Scaling).
*   **Repository**: Source of truth merging Room data and Sync state.
*   **AttendanceCloudProvider**: Interface for remote synchronization. See [CLOUD_PROVIDERS.md](CLOUD_PROVIDERS.md).
*   **Sync Logic**: Managed by WorkManager. See [CLOUD_SYNC.md](CLOUD_SYNC.md).

## Demo Mode & Seeding
*   **Seeding**: `DemoData` utility provides initial data (Disney characters).
*   **Mocking**: `DemoCloudProvider` is used to simulate master list replacement.
*   **Cleanup**: The repository handles the purge of demo state upon first real sync. See [DEMO_MODE.md](DEMO_MODE.md).
*   **State Separation**: Demo mode is mutually exclusive with any authenticated identity. Token expiry does *not* trigger fallback to Demo Mode; it triggers an "Action Required" state.

## Fuzzy Search Implementation
*   **Engine**: SQLite FTS5 for high-speed indexing.
*   **Scoring**: Kotlin-based layer that applies weights:
    1.  Exact Short Name Match (Highest)
    2.  Starts-with Short Name
    3.  Contains Short Name
    4.  Full Name Matches (Lower weight)
