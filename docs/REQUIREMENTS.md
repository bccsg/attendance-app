# Requirements

## Core Concept
A local-first Android attendance tracking application for ushers. It allows for fast, reliable tracking of attendees for specific events, with a focus on ease of use in high-traffic environments and robust cloud synchronization.

## Entities

### Attendee
*   **ID**: Unique identifier (UUID or Remote ID).
*   **Full Name**: Official name.
*   **Short Name**: (Optional) Preferred or common name. Used as the primary display if available.
*   **Groups**: Predefined global categories an attendee can belong to (e.g., "Volunteers", "Youth").

### Event
*   **Title**: Unique identifier formatted as `<yymmdd> <hhmm> <event-name>`.
*   **Date/Time**: yymmdd and hhmm (24h), no timezone.
*   **Name**: Short descriptive string (e.g., "sunday service").

### Attendance
*   **Link**: Connects an Attendee to an Event.
*   **State**: 
    *   `PRESENT`: Attendee is marked as having attended.
    *   `ABSENT`: Used as an "undo" or correction mechanism to mark an attendee as not present (e.g., if they were marked `PRESENT` by mistake).
*   **Timestamp**: Precise record of the commit time (synchronized via NTP).

## Features

### 1. Event Suggester & Management
*   Automatically defaults the context to **today** if it is Sunday, otherwise the **coming Sunday**.
*   Default time: **10:30**.
*   Default name: "**Sunday Service**".
*   Users can switch between existing events or create new ones via the **Events** interface (manageable window: last 30 days). See [EVENT_MANAGEMENT.md](EVENT_MANAGEMENT.md) for full details.
*   **Purge**: Local event data and attendance records older than 30 days are automatically purged from the device.

### 2. Main Listing
*   **Title**: Displays "Attendance" with the full Event ID as a subtitle.
*   **Fuzzy Search**: Prioritizes short names over full names.
    *   **Highlighting**: Matched portions of the name are bolded and use the primary theme color.
*   **Visibility Toggles**: Centered in the bottom bar. 
    *   **Present Chip**: Toggles visibility of attendees marked as `PRESENT`.
    *   **Pending Chip**: Toggles visibility of attendees not yet marked.
    *   **Isolation Behavior**: When both chips are active and visible, tapping on one will make that one visible only and the other invisible (isolating the category). If only one is active, tapping the other adds it back.
    *   **Safety**: If one category's count drops to zero, the other category is automatically forced visible to prevent an empty list.
    *   **Badge Counts**: 
        *   Non-Selection Mode: Displays total present or pending in the current event (not affected by search filters). "Ready" badge (Present) uses red (error), "Later" badge (Pending) uses theme grey (secondary).
        *   Selection Mode: Displays total present or pending among the **currently selected** attendees. Both badges use secondary theme colors.
    *   **Selection Mode Integration**:
        *   Chips reset to visible when "Show Selected Only" mode is active.
        *   Activating chips manually deactivates "Show Selected Only" mode.
        *   Chips are disabled only when the total category count is zero (regardless of selection).
        *   "Branded" primary coloring (when only one chip is active) is disabled in selection mode.
*   **Queue Launcher**: Located on the right of the bottom bar. Uses dynamic icons (`FilterNone` to `Filter9Plus`) to show the current queue count.
*   **Selection Mode**:
    *   Activated by tapping an attendee's **Attendee Avatar** (circle).
    *   Initial state: Merges the current Queue into the selection.
    *   **Show Selected Only**: A checklist icon in the TopAppBar filters the list to only currently selected items. Activating this closes search and ensures category chips are visible.
    *   Action: **`PlaylistAdd`** icon replaces the Queue with the selection and automatically opens the Queue sheet.
*   **Attendee Detail Sheet Action**:
    *   **`PlaylistAdd`** FAB adds the individual to the queue with a "Queued" success animation.
    *   **Logic**: Does not automatically open the Queue sheet (preserves context).
*   **Dynamic Scaling**: Fonts and Attendee Avatars can be scaled by 50% using a **pinch-to-scale gesture** on any list view (pinch-out for Large, pinch-in for Normal) or via the "Text Size" option in the menu.

### 3. Queue
*   **UI**: Card-style Modal Bottom Sheet with a standard 56dp top margin.
*   **Footer Status Chips**: Displays "Ready" and "Later" count chips. Both use a theme grey (`secondaryContainer`) background. "Ready" badge uses `primary` color; "Later" badge uses `secondary`.
*   **Status Indicators**: Attendees already marked present show a `PersonCheck` icon on the right.
*   **Interactions**:
    *   **Tap**: Toggles "Ready/Later" status.
    *   **Swipe-to-Remove**:
        *   Visual distance limited to 30% width.
        *   **Haptic Pulse** when crossing the 25% threshold.
        *   **Remove on Lift**: Action only triggers upon finger release while beyond the threshold.
        *   Icon: **`PlaylistRemove`**.
*   **Commit Actions**:
    *   **Mark Present**: (Primary/Pill) Uses `PersonCheck` icon and "Present" text. Fills remaining width.
    *   **Mark Pending**: (Secondary/Circle) Uses **`PersonCancel`** icon. Fixed 64dp size.
    *   **Animation**: 100ms color flash (Pastel Green for Present) followed by a 400ms fade-out and height collapse.
    *   **Preservation**: Only "Ready" items are cleared on commit; "Later" items remain in the queue.
*   **Clear Action**: **`PlaylistRemove`** icon in the app bar.
    *   If only "Ready" items exist: Clears immediately.
    *   If "Later" items exist: Prompts with a "Clear All / Keep those set aside for later" dialog. Background dims during the prompt.

### 4. Archive System
*   **Capacity**: Default limit of **25 slots** (configurable, minimum 25).
*   **Logic**: FIFO removal of oldest batches.
*   **Recall**: Allows restoring a previously cleared queue (Append mode).
