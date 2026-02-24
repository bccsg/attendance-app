# UI/UX Specification

## Visual Hierarchy
*   **Primary Display**: Attendee **Short Name**.
*   **Fallback**: Full Name (truncated with ellipsis if too long).
*   **Group Indicator**: A group icon appears to the right of the primary name if the attendee belongs to any groups.
*   **Queue Status**: Attendees in the queue show a **`BookmarkAdded`** icon on the far right of the list item.
*   **Presence Status**: Attendees already marked present show a **`PersonCheck`** icon in the main list and in details.
*   **Context**: TopAppBar displays the active event with a **DateIcon** (Month/Day), event name, time (**12h format**), and **event ID** (Cloud ID or partial Local UUID). Tapping this component navigates to the **Events** screen.

## Feedback Mechanisms
*   **Haptic**: Short, sharp vibration on:
    *   Commit button activation.
    *   Crossing the swipe-to-remove threshold (both entering and leaving).
*   **Animations**:
    *   **Search**: Matched text is **bold** and **Primary Colored**.
    *   **Removal**: 100ms flash (Pastel Green for Present, Secondary for Absent) -> 400ms fade-out + height collapse.
    *   **Scaling**: Transition between "Normal" and "Large" text sizes.
*   **Cloud Icon**: Dynamic indicator of sync health and connectivity.
    *   **`CloudOff`**: Demo mode active, or user is not authenticated.
    *   **`CloudAlert`**: Error with cloud operations or authentication session has expired.
    *   **`Cloud`**: Slow pulsing alpha animation (100% to 30%) indicates a sync operation is currently in progress.
    *   **`CloudDone`**: All commits synced, no active operations, and user is logged in.

### 5. Cloud Status Dialog
*   **Trigger**: Tapping the Cloud Icon in the TopAppBar.
*   **States**:
    *   **Authenticated**: Shows Google profile (email, name) and a **Logout** button.
    *   **Unauthenticated**: Shows a "Not Authenticated" warning and a **Login** button. If in Demo Mode, includes a notice that logging in will clear demo data.
*   **Content**:
    *   **Sync Jobs**: Displays the count of pending `sync_jobs`.
    *   **Pull Schedule**: Shows the next scheduled time for a cloud master list pull.
    *   **Sync History**: Displays the last pull status and a list of recent sync errors (if any).

### 6. Events Screen
*   **Trigger**: Tapping the event title component in the TopAppBar.
*   **List**: Displays local events within a 30-day manageable window.
*   **Item Layout**:
    *   **DateIcon**: Stylized box on the left (Month over Day).
    *   **Text**: Event name and 12h formatted time.
    *   **Status**: Active checkmark and sync status icon (`CloudDone` for synced, `CloudOff` for demo).
*   **Actions**:
    *   **Select**: Single-tap to switch the active event context.
    *   **Create**: `PlaylistAdd` icon in the TopAppBar launches the creation dialog.

### 7. Attendee Detail Sheet
*   **Trigger**: Tapping an attendee in the main list.
*   **Format**: Modal Bottom Sheet.
*   **Header**: Compact profile info (Name, Short Name, ID) with presence and queue indicators.
*   **Navigation**:
    *   **Group Peer Discovery**: Lists other members of the attendee's groups.
    *   **Breadcrumb Back Action**: A "Return to [Name]" line at the top for navigating between group members.
*   **Action**: A screen-local FAB labeled "Add to Queue" (icon: `PlaylistAdd`) appears if the attendee isn't staged.

## Interaction Patterns

### 1. Bottom Bar Navigation
*   **PersonSearch**: (Left) Expands an animated search pill.
*   **Filter Chips**: (Center) Toggles for category visibility. 
    *   If both are on: Clicking one isolates that category (the other becomes invisible).
    *   If one is off: Clicking it adds it back.
    *   Auto-enables the other category if one hits count zero.
    *   Activating chips deactivates **"Show Selected Only"** mode.
    *   **Branding (Non-Selection Mode)**: Uses **Primary** color theme if only one category is visible in the current list pool.
    *   **Branding (Selection Mode)**: 
        *   Primary branding for isolated chips is **disabled**.
        *   Badges use **Secondary** theme colors.
        *   Counts reflect the number of **currently selected** attendees in each state.
        *   Chips reset to visible when **"Show Selected Only"** mode is activated.
    *   **Badges**:
        *   **Present (Ready)**: Red (`error`) in non-selection mode.
        *   **Pending (Later)**: Theme Grey (`secondary`) in non-selection mode.
        *   Both use **Secondary** in selection mode.
*   **Queue Launcher**: (Right) Uses dynamic **`FilterNone`** to **`Filter9Plus`** icons.
*   **Adaptive Height**: Snaps instantly between 80dp (keyboard open) and 80dp + navBarPadding (keyboard closed) to maintain a clean aesthetic.

### 2. Screen-Local Floating Action Button
*   **Main List**: 
    *   **Default**: QR Scanner icon (`QrCodeScanner`) in `PrimaryContainer`. Positioned 112dp from bottom (above bottom bar).
    *   **Selection Mode**: Replaced by an "Add to Queue" icon (`PlaylistAdd`) in `Primary`. Replaces the current queue with the selected subset and opens the queue sheet.
*   **Attendee Sheet**: "Add to Queue" icon (`PlaylistAdd`) in `Primary`. Positioned 16dp from bottom (above navigation handle).
*   **Behavior**:
    *   **Visibility**: Automatically hides during sheet transitions to prevent "sliding" artifacts.
    *   **Handle Awareness**: Always adds `navigationBarsPadding` to the base offset when the keyboard is hidden.

### 3. Hold-to-Activate (Queue)
*   **Target**: "Present" (Pill, fills width) and "Mark Pending" (Circle, 64dp).
*   **Icons**: `PersonCheck` (Present) and **`PersonCancel`** (Pending).
*   **Duration**: 1.0 second with clockwise progress border.
*   **Safety**: Displays a "Note: You are editing a past event" bar if the event date is in the past.

### 4. Selection Mode
*   **Entry**: Tap an attendee's **Attendee Avatar** or long-tap an item.
*   **Toggling**: Single-tap to add/remove attendees.
*   **Auditing**: A **"Show Selected Only"** checklist icon in the TopAppBar filters the list. 
*   **Confirmation**: The screen-local FAB (`PlaylistAdd`) replaces the Queue with the selection and opens the sheet.

### 5. Queue Management
*   **Tap**: Toggles "Ready/Later" status.
*   **Footer Status Chips**: Both "Ready" and "Later" chips use a theme grey (`secondaryContainer`) background. The "Ready" badge uses `primary` color, while the "Later" badge uses `secondary`.
*   **Swipe-to-Remove**:
    *   Threshold at 25% width.
    *   Icon: **`PlaylistRemove`**.
    *   **"Remove on Lift"**: Removal only activates when the user releases their finger while in the armed state.
*   **Clear Logic**:
    *   Immediate clear if only "Ready" items exist.
    *   "Clear All / Keep those set aside for later" dialog if any "Later" items exist.

## Accessibility
*   **Text Size**: Configurable via a radio-button selection in the overflow menu (Normal/Large) or by using a **pinch gesture** (pinch-out for Large, pinch-in for Normal) on any list view. Scales UI elements (fonts, icons, avatars) by 50%.
