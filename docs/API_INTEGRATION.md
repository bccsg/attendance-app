# API & Integration

## Google Workspace OAuth
*   **Authentication**: standard Google OAuth 2.0.
*   **Silent Handshake**: The Sync Worker attempts silent token refresh.
*   **User Prompt**: Only occurs if silent refresh fails. Triggered by the user tapping the "Yellow Alert" cloud icon.
*   **Storage**: Tokens are stored in **EncryptedSharedPreferences** for security.

## Google Sheets Adapter
*   **Backend Agnostic**: Built against an `AttendanceCloudProvider` interface.

### Sheet Structure
1.  **`Master_Attendees`**: (Read-Only) Columns: `id`, `full_name`, `short_name`.
2.  **`Master_Groups`**: (Read-Only) Columns: `group_id`, `attendee_id`.
3.  **`[EventTitle]`**: (Read/Write) Columns: `attendee_id`, `state` (P/A), `commit_time`.

### Adapter Operations
*   **`pushAttendance`**: Appends or updates rows in the specific event sheet.
*   **`fetchMasterList`**: Downloads the latest attendee and group mappings.
*   **`fetchAttendanceForEvent`**: Pulls remote state for reconciliation.
