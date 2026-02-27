# BCC Attendance

An offline-first Android attendance tracking application for BCC ushers. Designed for high-speed tracking in busy environments with robust, sequential synchronization to Google Sheets.

## Key Features
- **Offline-First Workflow**: Capture attendance instantly regardless of connectivity; syncs automatically when online.
- **Queue-Based Tracking**: Batch attendees into a queue for rapid confirmation or set them aside for later.
- **QR Code Support**: Scan individual or group QR codes for lightning-fast check-ins; share QR codes directly from the app.
- **Flexible Groups**: Support for multi-group membership (Pastors, Cells, Families).
- **Intelligent Search**: High-performance fuzzy search (SQLite FTS5) prioritizing short names.
- **Event-Driven**: Context-aware tracking within a 30-day manageable event window.
- **NTP Synchronization**: Consistent timestamps across all ushers' devices via `TrueTime`.

## Technical Stack
- **Language**: Kotlin 2.3.10
- **UI Framework**: Jetpack Compose (Declarative)
- **Local Storage**: Room 2.8.4 (with FTS5 and SQLite virtual tables)
- **Dependency Injection**: Hilt 2.59.1
- **Synchronization**: WorkManager (Sequential Sync jobs)
- **Cloud Backend**: Google Sheets API v4
- **Time Management**: `kotlinx-datetime` & TrueTime (NTP)

## Documentation

Comprehensive documentation is available in the `docs/` directory:

| Document | Description |
| :--- | :--- |
| **[User Guide](docs/USER_GUIDE.md)** | Step-by-step instructions for ushers on using the app. |
| **[Requirements](docs/REQUIREMENTS.md)** | High-level product requirements and core entity definitions. |
| **[Architecture](docs/ARCHITECTURE.md)** | Technical design, data layer schema, and pattern overviews. |
| **[UI/UX Specification](docs/UI_UX_SPEC.md)** | Design mandates, visual hierarchy, and interaction patterns. |
| **[Cloud Sync Strategy](docs/CLOUD_SYNC.md)** | Principles of "Last Commit Wins" and the sequential sync pipeline. |
| **[Event Management](docs/EVENT_MANAGEMENT.md)** | Details on event lifecycles, selection logic, and cloud mapping. |
| **[Cloud Providers](docs/CLOUD_PROVIDERS.md)** | Definitions for backend adapters (Demo vs. Google Sheets). |
| **[Demo Mode](docs/DEMO_MODE.md)** | Behavior and seeding logic for the local exploration state. |

## Getting Started

Refer to **[GEMINI.md](GEMINI.md)** for absolute foundational mandates on build configuration, environment setup, and development standards.

### Quick Commands
```bash
# Install and run on emulator/device
./gradlew installDebug
adb shell am start -n sg.org.bcc.attendance/.MainActivity
```
