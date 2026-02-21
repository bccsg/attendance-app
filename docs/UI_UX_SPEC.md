# UI/UX Specification

## Visual Hierarchy
*   **Primary Display**: Attendee **Short Name**.
*   **Fallback**: Full Name (truncated with ellipsis if too long).
*   **Queue Status**: Attendees in the queue show a **`BookmarkAdded`** icon on the far right of the list item.
*   **Presence Status**: Attendees already marked present show a **`HowToReg`** icon in the Queue sheet.
*   **Context**: TopAppBar shows "Attendance" as title and full Event ID (`yyMMdd HHmm Name`) as subtitle.

## Feedback Mechanisms
*   **Haptic**: Short, sharp vibration on:
    *   Commit button activation.
    *   Crossing the swipe-to-remove threshold (both entering and leaving).
*   **Animations**:
    *   **Search**: Matched text is **bold** and **Primary Colored**.
    *   **Removal**: 100ms flash (Pastel Green for Present, Secondary for Absent) -> 400ms fade-out + height collapse.
    *   **Scaling**: Transition between "Normal" and "Large" text scales fonts, photos, and paddings by 50%.
*   **Cloud Icon**: Dynamic indicator of sync health. Shows `CloudOff` in Demo Mode.

## Interaction Patterns

### 1. Bottom Bar Navigation
*   **PersonSearch**: (Left) Expands an animated search pill.
*   **Filter Chips**: (Center) Toggles for category visibility. 
    *   If both are on: Clicking one isolates that category.
    *   If one is off: Clicking it adds it back.
    *   Auto-enables the other category if one hits count zero.
*   **Queue Launcher**: (Right) Uses dynamic **`FilterNone`** to **`Filter9Plus`** icons. Swiping up from the bottom bar also opens the Queue (if not in selection mode).

### 2. Hold-to-Activate (Queue)
*   **Target**: "Present" (66% width/Pill) and "Mark Pending" (34% width/Pill).
*   **Icons**: `CheckCircle` (Present) and `PersonOff` (Pending).
*   **Duration**: 1.5 seconds with clockwise progress border.
*   **3D Effect**: Buttons "sink" from 2dp to 0dp elevation on press.

### 3. Selection Mode
*   **Entry**: Tap an attendee's **contact photo**.
*   **Toggling**: Single-tap to add/remove attendees.
*   **Confirmation**: `GroupAdd` icon replaces the Queue with the selection and opens the sheet.
*   **Persistence**: "Set Aside" states in the queue are remembered when adding new items.

### 4. Queue Management
*   **Tap**: Toggles "Ready/Set Aside" status.
*   **Swipe-to-Remove**:
    *   Maximum swipe distance restricted to 30% of item width.
    *   Threshold at 25% width.
    *   **"Remove on Lift"**: Removal only activates when the user releases their finger while in the armed state.
*   **Clear Logic**:
    *   Immediate clear if only "Set Aside" items exist.
    *   "Clear All / Keep Set Aside" dialog if "Ready" items exist. The background sheet mutes (alpha 0.38) while the dialog is open.

## Accessibility
*   **Large Text Mode**: Scales UI elements (fonts, icons, photos, vertical spacing) by 50% for improved readability. The menu itself remains at a standard scale for consistency.
