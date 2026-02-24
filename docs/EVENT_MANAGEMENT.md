# Event Management

The Event Management system allows ushers to manage the lifecycle of attendance events, including context switching and creation.

## 1. Event Selection & Window
*   **Contextual Hub**: The `MainListScreen` always operates within the context of an "Active Event".
*   **30-Day Window**: Users can only switch to manage attendance for events occurring within the last 30 days.
*   **Auto-Selection Logic**: 
    *   The system automatically selects the **earliest event** that started **within the last hour** or is scheduled for the **future**.
    *   If no such events exist (e.g., all events occurred more than an hour ago), the system defaults to the **latest available event**.
    *   Auto-selection only triggers if there is no current selection or if the current selection is older than 30 days.
*   **Active Indicator**: The currently selected event is clearly highlighted. Selecting a different event updates the main attendance list and summary counts.

## 2. Event Creation
*   **Smart Defaults**: When creating a new event, the system suggests:
    *   **Date**: Today (if Sunday), otherwise the upcoming Sunday.
    *   **Time**: 10:30 (stored as `1030`).
    *   **Name**: "sunday service".
*   **Immediate Use**: New events created on the device can be used immediately for local attendance tracking.
*   **Selection**: Upon creation, the new event is automatically selected as the current active event.
*   **No Edit/Delete**: Events can only be created or switched to. Editing or deleting past events is not supported via the app.
*   **Unique Titles**: The system prevents creating multiple events with the same date, time, and name (case-insensitive) to ensure each worksheet mapping remains unique.

## 3. Cloud Synchronization & Cloud Event Mapping
*   **UUID Tracking**: Local events use a unique identifier (UUID) to track attendance locally, ensuring stability even if the event name or cloud event ID changes.
*   **Cloud Event Association**: Each event corresponds to a specific cloud event (e.g., a Google Sheets worksheet).
*   **Sync Matching**: 
    *   The sync job matches an event to an existing cloud event by name or creates a new one if it doesn't exist.
    *   The cloud event ID is cached locally once associated.
*   **Auto-Recovery**: If a cached ID is missing on the cloud, the system attempts to re-map by finding a cloud event with the same name.
*   **Name Normalization**: During scheduled master data syncs, if a cloud event's name has changed on the cloud, the local event name is updated to match.

## 4. Lifecycle
*   **Fetch Past Events**: Past events can be fetched from the cloud to populate the local management list.
*   **Local Persistence**: Events and their associated cloud event metadata are stored in the `events` table.
*   **Automatic Purge**: To keep the local database lean, events older than 30 days (based on their event date) and all their associated `attendance_records` are automatically purged from the device.

## 5. UI/UX Flow
*   **Access**: Triggered by tapping the **active event title component** in the `TopAppBar` of the `MainListScreen`.
*   **Events List**:
    *   **Header**: Titled "**Events**" with a `PlaylistAdd` icon in the `TopAppBar` (right side) to create new events.
    *   **Item Display**:
        *   **DateIcon**: A box on the left showing abbreviated Month over Day number.
        *   **Content**: Event Name (Headline) and Time in **12h AM/PM format** (Supporting).
        *   **ID**: Displays the **cloud event ID** (if associated) or the first 8 characters of the **local UUID** to the right of the time.
        *   **Status**: Displays an active checkmark and a cloud sync icon (`CloudDone` or `CloudOff`) on the right.
*   **Empty State**: If no events exist within the manageable window, the user is prompted to create their first event.

