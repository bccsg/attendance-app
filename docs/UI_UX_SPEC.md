# UI/UX Specification

## Visual Hierarchy
*   **Primary Display**: Attendee **Short Name**.
*   **Fallback**: Full Name (truncated with ellipsis if too long).

## Feedback Mechanisms
*   **Snack-bars**: Used for full name display and hints. Set to a long duration to ensure the usher can read them in busy environments.
*   **Cloud Icon**: Dynamic indicator of sync health and auth status.
*   **Presence Badge**: A numeric counter on the "Hide Present" button showing how many matched attendees are currently hidden.

## Interaction Patterns

### 1. Hold-to-Activate
*   **Target**: "Mark Present" (Primary/Green) and "Mark Absent" (Secondary/Red/Outlined).
*   **Duration**: **1.5 seconds** (Global Constant).
*   **Animation**: A progress border draws clockwise around the button until activation completes.

### 2. Selection Mode
*   **Entry**: Long-tap an attendee.
*   **Toggling**: Single-tap to add/remove attendees from the selection.
*   **Merging**: Automatically includes anyone already in the Persistent Queue.
*   **Cancel**: Discards the selection; the original Queue remains untouched.

### 3. Queue Management
*   **Tap**: Toast/Snack-bar of full name + hint.
*   **Swipe-to-Dequeue**: Removes the item from the staging area immediately with an Undo option.
*   **Long-Tap-to-Exclude**: Greys out the item. It remains in the list for reference but is skipped during the "Mark" action.

## Search Behavior
*   Fuzzy search results do not change when entering Selection Mode.
*   Results respect the "Hide Present" toggle at all times.
