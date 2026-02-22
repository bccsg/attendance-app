# Demo Mode

The application includes a Demo Mode to allow immediate exploration of the UI and workflows without requiring initial cloud synchronization.

## 1. Initial Seeding
On the first launch (or if the database is empty), the application automatically seeds the local database with **50 Disney characters** as attendees.
*   **IDs**: Prefix `D` (e.g., `D01`, `D02`).
*   **Logic**: Handled in `MainActivity` using `DemoData` utility.

## 2. UI Indicators
*   **Sync Icon**: In demo mode (or if not authenticated), the cloud icon shows **`CloudOff`**. This serves as the primary indicator that the app is in a local-only demo state.
*   **Queue**: The commit button label changes to "Confirm & Archive (Demo)" or "Mark ... (Demo)".

## 3. Functional Constraints
*   **No Cloud Sync**: While in demo mode (detected by attendees having `D` prefix IDs), clicking "Mark Present/Pending" will **Archive** the batch but will **NOT** create a `SyncJob`.
*   **Local-Only**: Changes reflect in the local `attendance_records` table immediately for UI feedback but are not queued for upload.

## 4. Transition to Real Mode
Demo mode is exited by tapping the **Cloud Icon** in the top bar and completing the **Login** process in the resulting **Cloud Status Dialog**.
*   **Replacement**: Upon successful authentication, the `FakeCloudProvider` (or real provider) returns a set of "Real Users".
*   **Cleanup**: Upon receiving real data, the repository:
    1.  Clears all `attendees`.
    2.  Clears the Attendance Staging queue.
    3.  Clears all pending `sync_jobs`.
    4.  Inserts the new master list.
*   **Result**: Standard cloud sync logic is enabled, and the cloud icon status updates based on the new authenticated state.
