# Demo Mode

The application includes a Demo Mode to allow immediate exploration of the UI and workflows without requiring initial cloud synchronization.

## 1. Initial Seeding
On the first launch (or if the database is empty), the application automatically seeds the local database with **50 Disney characters** as attendees.
*   **Logic**: Handled in `AttendanceRepository` using `DemoData` utility.

## 2. UI Indicators
*   **Sync Icon**: In demo mode (when the user is **not authenticated**), the cloud icon shows **`CloudOff`**. This serves as the primary indicator that the app is in a local-only demo state.
*   **Queue**: The commit button label changes to "Confirm & Archive (Demo)" or "Mark ... (Demo)".

## 3. Functional Constraints
*   **Demo State Detection**: Demo mode is active whenever the user is **not logged in**. 
*   **Mock Processing**: While not authenticated, clicking "Mark Present/Pending" will:
    1.  Commit the record to local storage for immediate UI feedback.
    2.  Create a **`SyncJob`** in the local database (though background sync is inactive until login).
    3.  Archive the batch in the `queue_archive` table.

## 4. Transition to Real Mode
Demo mode is exited by tapping the **Cloud Icon** in the top bar and completing the **Login** process in the resulting **Cloud Status Dialog**.
*   **Replacement**: Upon successful authentication, the `DemoCloudProvider` (see [CLOUD_PROVIDERS.md](CLOUD_PROVIDERS.md)) is swapped for a real provider (e.g., `GoogleSheetsProvider`).
*   **Cleanup**: Upon receiving real data, the repository:
    1.  Clears all `attendees`.
    2.  Clears the Attendance Staging queue.
    3.  Clears all pending `sync_jobs`.
    4.  Clears all `attendance_records`.
    5.  Inserts the new master list.
*   **Result**: Standard cloud sync logic is enabled (see [CLOUD_SYNC.md](CLOUD_SYNC.md)), and the cloud icon status updates based on the new authenticated state.
