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
*   **Title**: Unique identifier formatted as `<yymmdd><hhmm>:<event-name>`.
*   **Date/Time**: yymmdd and hhmm (24h), no timezone.
*   **Name**: Short descriptive string (e.g., "sunday service").

### Attendance
*   **Link**: Connects an Attendee to an Event.
*   **State**: `PRESENT` or `ABSENT`.
*   **Timestamp**: Precise record of the commit time (synchronized via NTP).

## Features

### 1. Event Suggester
*   Automatically defaults the creation form to **today** if it is Sunday, otherwise the **coming Sunday**.
*   Default time: **10:30**.
*   Default name: "**sunday service**".

### 2. Main Listing
*   **Fuzzy Search**: Prioritizes short names over full names.
*   **Hide Present Toggle**: Filters out attendees marked as `PRESENT`.
*   **Presence Badge**: Displays the count of hidden attendees who match the current search query.
*   **Selection Mode**:
    *   Activated by **Long-Tap** on an attendee.
    *   While active: **Single-Tap** toggles selection.
    *   Initial state: Merges the current Persistent Queue into the selection.
    *   Action: FAB replaces the Persistent Queue with the selection (archiving the old queue).

### 3. Persistent Queue
*   **Persistence**: Survives app restarts and navigation.
*   **Interactions**:
    *   **Tap**: Shows a Snack-bar with the full name and hint to long-tap to exclude.
    *   **Swipe**: Dequeues (removes) the attendee from the staging area.
    *   **Long-Tap**: Toggles "Excluded" state (greyed out, ignored by commit).
*   **Commit Actions**:
    *   **Mark Present**: (Primary) Hold for 1.5s to activate.
    *   **Mark Absent**: (Secondary) Hold for 1.5s to activate.

### 4. Archive System
*   **Capacity**: Default limit of **25 slots** (configurable, minimum 25).
*   **Logic**: FIFO (First-In, First-Out) removal of oldest batches.
*   **Recall**: Allows restoring a previously cleared queue (Append mode).
