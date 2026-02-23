# Demo Mode

The application includes a Demo Mode to allow immediate exploration of the UI and workflows without requiring initial cloud synchronization.

## 1. Initial Seeding
On the first launch (or if the database is empty), the application automatically seeds the local database with **50 Disney characters** as attendees.
*   **Logic**: Handled in `AttendanceRepository` using `DemoData` utility.

## 2. UI Indicators
*   **Sync Icon**: In demo mode (when the user is **not authenticated**), the cloud icon shows **`CloudOff`**. This serves as the primary indicator that the app is in a local-only demo state.
*   **Queue**: The commit button label changes to "Confirm & Archive (Demo)" or "Mark ... (Demo)".

## 3. Functional Constraints
*   **Demo State Detection**: Demo mode is active whenever the user is **not logged in**. This state is determined by the absence of a valid authentication session in `AuthManager`.
*   **Mock Processing**: While not authenticated, clicking "Mark Present/Pending" will:
    1.  Commit the record to local storage for immediate UI feedback.
    2.  Create a **`SyncJob`** in the local database (though background sync is inactive until login).
    3.  Archive the batch in the `queue_archive` table.

## 4. Transition to Real Mode
Demo mode is exited by completing the **Login** process in the **Cloud Status Dialog**.
*   **Replacement**: Upon successful authentication, the `DemoCloudProvider` is swapped for a real provider (e.g., `GoogleSheetsProvider`).
*   **Cleanup**: Upon receiving real data, the repository clears all local tables and inserts the new master list.
*   **Logout**: Logging out via the Cloud Status Dialog will **purge all local data** (attendees, events, attendance records, sync jobs) to ensure security and a clean state.
*   **Token Expiry**: If an authentication token expires or becomes invalid, the application enters an "Action Required" state (`CloudAlert`). This is **not** a logout; local data is preserved, but synchronization is paused until the user re-authenticates.
