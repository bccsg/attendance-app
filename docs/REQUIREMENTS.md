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
*   **State**: `PRESENT` or `ABSENT`.
*   **Timestamp**: Precise record of the commit time (synchronized via NTP).

## Features

### 1. Event Suggester
*   Automatically defaults the context to **today** if it is Sunday, otherwise the **coming Sunday**.
*   Default time: **10:30**.
*   Default name: "**sunday service**".
*   Users can switch between existing events or create new ones via a context menu in the TopAppBar.

### 2. Main Listing
*   **Title**: Displays "Attendance" with the full Event ID as a subtitle.
*   **Fuzzy Search**: Prioritizes short names over full names.
    *   **Highlighting**: Matched portions of the name are bolded and use the primary theme color.
*   **Visibility Toggles**: Centered in the bottom bar. 
    *   **Present Chip**: Toggles visibility of attendees marked as `PRESENT`.
    *   **Pending Chip**: Toggles visibility of attendees not yet marked.
    *   **Safety**: If one category's count drops to zero, the other category is automatically forced visible to prevent an empty list.
*   **Queue Launcher**: Located on the right of the bottom bar. Uses dynamic icons (`FilterNone` to `Filter9Plus`) to show the current queue count.
*   **Selection Mode**:
    *   Activated by tapping an attendee's **contact photo** (circle).
    *   Initial state: Merges the current Queue into the selection.
    *   Action: `GroupAdd` icon replaces the Queue with the selection and automatically opens the Queue sheet.
*   **Dynamic Scaling**: A "Large Text" option in the menu scales fonts, contact photos, and item spacing by 50%.

### 3. Queue
*   **UI**: Card-style Modal Bottom Sheet with a standard 56dp top margin.
*   **Status Indicators**: Attendees already marked present show a `HowToReg` icon on the right.
*   **Interactions**:
    *   **Tap**: Toggles "Ready/Set Aside" status.
    *   **Swipe-to-Remove**:
        *   Visual distance limited to 30% width.
        *   **Haptic Pulse** when crossing the 25% threshold.
        *   **Remove on Lift**: Action only triggers upon finger release while beyond the threshold.
*   **Commit Actions**:
    *   **Mark Present**: (Primary/Pill) Uses `CheckCircle` icon and "Present" text.
    *   **Mark Pending**: (Secondary/Pill) Uses `PersonOff` icon and secondary theme color.
    *   **Animation**: 100ms color flash (Pastel Green for Present) followed by a 400ms fade-out and height collapse.
    *   **Preservation**: Only "Ready" items are cleared on commit; "Set Aside" items remain in the queue.
*   **Clear Action**: `DeleteSweep` icon in the app bar.
    *   If only "Set Aside" items exist: Clears immediately.
    *   If "Ready" items exist: Prompts with a "Clear All / Keep Set Aside" dialog. Background dims during the prompt.

### 4. Archive System
*   **Capacity**: Default limit of **25 slots** (configurable, minimum 25).
*   **Logic**: FIFO removal of oldest batches.
*   **Recall**: Allows restoring a previously cleared queue (Append mode).
