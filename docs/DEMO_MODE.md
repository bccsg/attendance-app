# Demo Mode

The application includes a Demo Mode to allow immediate exploration of the UI and workflows without requiring initial cloud synchronization.

## 1. Initial Seeding
On the first launch (or if the database is empty), the application automatically seeds the local database with **50 Disney characters** as attendees.
*   **IDs**: Prefix `D` (e.g., `D01`, `D02`).
*   **Logic**: Handled in `MainActivity` using `DemoData` utility.

## 2. UI Indicators
*   **Main List**: A banner appears at the top: "DEMO DATA - Sync master list to replace" using the `tertiaryContainer` color scheme.
*   **Queue**: The commit button label changes to "Confirm & Archive (Demo)" or "Mark ... (Demo)".
*   **Sync Icon**: In demo mode, the cloud icon typically shows `CloudDone` (primary) unless manual actions are taken.

## 3. Functional Constraints
*   **No Cloud Sync**: While in demo mode (detected by attendees having `D` prefix IDs), clicking "Mark Present/Pending" will **Archive** the batch but will **NOT** create a `SyncJob`.
*   **Local-Only**: Changes reflect in the local `attendance_records` table immediately for UI feedback but are not queued for upload.

## 4. Transition to Real Mode
Demo mode is exited by tapping the **Cloud Icon** (Sync Master List) in the top bar.
*   **Replacement**: The `FakeCloudProvider` (or real provider) returns a set of "Real Users".
*   **Cleanup**: Upon receiving real data, the repository:
    1.  Clears all `attendees`.
    2.  Clears the Attendance Staging queue.
    3.  Clears all pending `sync_jobs`.
    4.  Inserts the new master list.
*   **Result**: The "DEMO DATA" banner disappears, and standard cloud sync logic is enabled.
