# Sync Strategy

## Local-First Principle
The app provides immediate visual feedback. All commits are written to the local `attendance_records` table and a `sync_jobs` queue.

## Sequential Synchronization
*   **Process**: WorkManager processes `sync_jobs` one at a time in strict chronological order.
*   **Indicator**: A cloud icon in the header tracks state:
    *   **Checkmark**: All caught up.
    *   **Spinning**: Actively uploading.
    *   **Yellow Alert**: Action Required (Auth/Error).
    *   **Red Dot**: Pending commits.
*   **Integrity**: While `sync_jobs` are pending, the app **disables pulling** from the cloud to prevent stale data overwrites.

## Conflict Resolution
*   **Strategy**: "Last Commit Wins" based on timestamps.
*   **Time Source**: Uses an **NTP library (e.g., TrueTime)** to ensure all devices share a consistent time, mitigating local clock drift or desync.

## Lifecycle
*   **Auto-Pull**: Every **15 minutes**, provided there are no pending local commits.
*   **Manual-Pull**: Tapping the cloud icon (when authenticated) requests an off-cycle pull.

## Error Handling (Google Sheets)
*   **Sheet Deletion/Limits**: If an event sheet is deleted or hits cell limits, the app will:
    1.  Alert the user via a persistent Snack-bar.
    2.  Recreate the sheet using the original title (`yyMMdd HHmm Name`) suffixed with a space and a **UUID** to ensure a fresh, unique write target.
