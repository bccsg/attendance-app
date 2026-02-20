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
*   **`attendees`**: Stores master list (ID, Full Name, Short Name, etc.). Uses FTS5 virtual table for search.
*   **`groups`**: Master list of groups.
*   **`attendee_groups`**: Many-to-Many mapping between attendees and groups.
*   **`events`**: Metadata for tracked events.
*   **`attendance_records`**: Event ID, Attendee ID, State, Timestamp.
*   **`persistent_queue`**: Attendee ID, isExcluded flag.
*   **`sync_jobs`**: Queue for sequential cloud commits (Payload, CreatedAt).
*   **`queue_archive`**: JSON blob of previous batches, Timestamp, Event ID.

## MVVM Pattern
*   **View**: Compose Screens (MainList, Queue, Archive).
*   **ViewModel**: Manages UI state (Search query, HidePresent toggle, Selection state).
*   **Repository**: Source of truth merging Room data and Sync state.
*   **CloudAdapter**: Interface for Google Sheets integration.

## Fuzzy Search Implementation
*   **Engine**: SQLite FTS5 for high-speed indexing.
*   **Scoring**: Kotlin-based layer that applies weights:
    1.  Exact Short Name Match (Highest)
    2.  Starts-with Short Name
    3.  Contains Short Name
    4.  Full Name Matches (Lower weight)
