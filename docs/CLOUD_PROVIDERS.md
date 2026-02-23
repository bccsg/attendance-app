# Cloud Providers

This document defines the `AttendanceCloudProvider` interface and its implementations.

## 1. The Interface (`AttendanceCloudProvider`)

The application interacts with remote backends through a unified interface, ensuring the core logic is decoupled from specific implementations.

### Core Operations
*   **`fetchMasterAttendees` / `fetchMasterGroups` / `fetchAttendeeGroupMappings`**: Retrieves the global master list data.
*   **`fetchRecentEvents(days: Int)`**: Retrieves events within a manageable window (default: 30 days).
*   **`pushAttendance(event: Event, records: List<AttendanceRecord>)`**: Uploads local attendance commits.
*   **`fetchAttendanceForEvent(event: Event)`**: Pulls remote state for a specific event for reconciliation.

---

## 2. Implementation: Demo Mode (`DemoCloudProvider`)

Enabled by default upon first launch to allow immediate exploration without authentication.

*   **Seeding**: Seeds the database with 50 "Disney" characters.
*   **Behavior**: 
    *   Simulates "Cloud" responses using in-memory session storage.
    *   Generates deterministic events for the last 30 days if no local events exist.
*   **Sync Jobs**: Creates real `SyncJob` entries in the local database, which the `DemoCloudProvider` processes as successful pushes during a demo session.

---

## 3. Implementation: Google Sheets (Planned)

The production backend will utilize the Google Sheets API v4. 

### Planned Features
*   **OAuth 2.0**: Silent background authentication and token storage in `EncryptedSharedPreferences`.
*   **Sheet Mapping**: Mapping local event UUIDs to specific worksheets by title (`yyMMdd HHmm Name`).
*   **Recovery**: Automatic worksheet creation and recovery if mapped resources are deleted or hit limits.
