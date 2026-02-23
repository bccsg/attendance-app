# Attendance - Foundational Mandates

This document is the absolute source of truth for build configuration, architectural patterns, and engineering standards. All development MUST strictly adhere to these mandates.

## 1. Technical Stack (February 2026 Stable)
*   **Build**: AGP 9.0.1, Gradle 9.3.1, Kotlin 2.3.10
*   **Targeting**: compileSdk 36, targetSdk 36, minSdk 26
*   **UI**: Jetpack Compose (Declarative)
*   **Database**: Room 2.8.4 (with FTS4 virtual tables for search)
*   **DI**: Hilt 2.59.1 (Core) + AndroidX Hilt 1.3.0 (Extensions)
*   **Testing**: JUnit 4 (for stable Robolectric), Kotest Assertions, MockK 1.14.9
*   **Time**: `kotlinx-datetime` 0.7.1, TrueTime 3.5 (NTP Synchronization)
*   **Package**: `sg.org.bcc.attendance`

## 2. Architectural Principles
*   **Offline-First**: UI state must be a reactive stream (Flow/StateFlow) observing the local Room database.
*   **Sequential Cloud Sync**: No direct cloud writes. Changes must be queued in `SyncJob` and processed one-at-a-time by WorkManager.
*   **Last Commit Wins**: Conflict resolution uses NTP-synchronized timestamps to determine the current state.
*   **Adapter Pattern**: All remote operations are defined by the `AttendanceCloudProvider` interface to remain backend-agnostic.

## 3. Engineering & UX Standards
*   **Test-Driven**: Every feature or fix MUST have corresponding tests (Unit or Robolectric).
*   **Name Priority**: UI always displays `shortName` as primary; fallback to `fullName`.
*   **Safety Interaction**: "Mark Present/Absent" actions require a 1.0s hold with a clockwise drawing-border animation.
*   **Icons**: Use **Material Symbols Rounded**. 
    *   Find icons at: [fonts.google.com/icons](https://fonts.google.com/icons?icon.style=Rounded)
    *   To install: Download the **Android (XML)** version or use direct URLs like `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/[icon_name]/default/24px.xml`.
    *   Location: Save to `app/src/main/res/drawable/` with `ic_` prefix.
    *   Mapping: Add the resource to `sg.org.bcc.attendance.ui.components.AppIcons`.
*   **Folder Structure**:
    *   `data/local`: Room schema and DAOs.
    *   `data/remote`: AttendanceCloudProvider implementations.
    *   `data/repository`: The single source of truth for ViewModels.
    *   `ui/components`: Reusable Compose widgets.
    *   `util`: Standalone logic (e.g., EventSuggester).

## 4. Development Environment
*   **Emulator**: Use the **Pixel 6** profile (`pixel_6_demo`).
    ```bash
    $ANDROID_HOME/emulator/emulator -avd pixel_6_demo -no-audio -no-snapshot
    ```
*   **Build \u0026 Run**:
    ```bash
    ./gradlew installDebug
    adb shell am start -n sg.org.bcc.attendance/.MainActivity
    ```
