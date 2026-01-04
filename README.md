# Bank SMS Tracker

[![Build Status](https://github.com/user/BankSMSTracker/workflows/Android%20CI/badge.svg)](https://github.com/user/BankSMSTracker/actions)
[![Code Coverage](https://img.shields.io/badge/coverage-WIP-yellow.svg)](./app/build/reports/jacoco/jacocoTestReport/html/index.html)
[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](LICENSE)

**An Android app that automatically tracks bank transactions from SMS messages and categorizes your spending.**

> **Note:** This project is fully AI-developed using Claude Code and is maintained by a QA Engineer to demonstrate expertise in test automation, quality assurance, and modern Android development practices.

## Features

- **Automatic SMS Parsing** - Extracts transaction details from bank SMS messages using configurable regex patterns
- **Category Management** - Organize merchants into categories (Food, Transport, Shopping, etc.)
- **Sender Configuration** - Configure multiple bank senders with custom parsing rules
- **Transaction History** - View, filter, and export your payment history
- **Regex Builder** - Visual tool to create and test regex patterns with real SMS messages
- **Import/Export** - Backup and restore your configuration as JSON
- **Category Cascade** - Automatically re-categorize payments when rules change
- **Bug Reporting** - Built-in bug report feature with device info collection

## Screenshots

<!-- TODO: Add screenshots -->

## Tech Stack

- **Language:** Kotlin 2.0
- **Architecture:** MVVM with Repository pattern
- **Database:** Room (SQLite)
- **Testing:**
  - JUnit 5 for unit tests
  - AndroidJUnit5 for instrumented tests
  - Appium for E2E UI automation
  - JaCoCo for code coverage
- **CI/CD:** GitHub Actions
- **Code Quality:** ktlint

## Project Structure

```
app/src/
├── main/
│   ├── java/com/example/banksmstracker/
│   │   ├── data/           # Data models (Payment, Category, Sender, etc.)
│   │   ├── database/       # Room entities, DAOs, and database
│   │   ├── parser/         # SMS receiver and parsing
│   │   ├── processor/      # Payment processing logic
│   │   ├── repository/     # Data repositories
│   │   ├── serializer/     # Config loading/validation
│   │   ├── ui/             # Activities and UI components
│   │   └── util/           # Utility classes
│   └── res/                # Android resources
├── test/                   # Unit tests
└── androidTest/            # Instrumented tests
```

## Building and Installing

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34 (API level 34)
- Android device or emulator (API 26+)

### Build from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/user/BankSMSTracker.git
   cd BankSMSTracker
   ```

2. **Build the debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

   The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build the release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

### Installing on a Real Device

#### Option 1: Using ADB (Recommended)

1. **Enable Developer Options** on your device:
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times
   - Go back and open "Developer Options"
   - Enable "USB Debugging"

2. **Connect your device** via USB cable

3. **Verify connection:**
   ```bash
   adb devices
   ```
   Your device should appear in the list.

4. **Install the APK:**
   ```bash
   # Debug build
   ./gradlew installDebug

   # Or manually install the APK
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

#### Option 2: Using Android Studio

1. Open the project in Android Studio
2. Connect your device via USB
3. Select your device from the device dropdown
4. Click "Run" (green play button) or press `Shift+F10`

#### Option 3: Manual APK Transfer

1. Build the APK: `./gradlew assembleDebug`
2. Transfer `app/build/outputs/apk/debug/app-debug.apk` to your device
3. On your device, open the file manager and locate the APK
4. Tap to install (you may need to enable "Install from unknown sources")

### Granting Permissions

After installation, the app requires the following permissions:

1. **SMS Permission** - Required to read incoming bank SMS messages
   - Go to Settings > Apps > Bank SMS Tracker > Permissions
   - Enable "SMS" permission

2. **Storage Permission** (optional) - For config import/export
   - Enable when prompted during export/import

## Running Tests

### Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Appium E2E Tests

1. **Start Appium server:**
   ```bash
   make appium-docker-start
   ```

2. **Run Appium tests:**
   ```bash
   make test-appium
   ```

### Code Coverage Report
```bash
./gradlew jacocoTestReport
```

Report will be available at: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

## Configuration

### Default Rules

The app comes with a default configuration file (`default_rules.json`) that includes:
- Common bank sender addresses
- Regex patterns for transaction parsing
- Default spending categories

### Custom Configuration

You can customize the app by:
1. Adding new senders in the "Senders" screen
2. Creating categories in the "Categories" screen
3. Building regex patterns with the "Regex Builder"
4. Importing/exporting configurations via JSON

## Development

### Code Style

This project uses ktlint for Kotlin code formatting:

```bash
# Check code style
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

### Makefile Commands

```bash
make help          # Show all available commands
make build         # Build the project
make test          # Run all tests
make coverage      # Generate coverage report
make lint          # Run linter
make install       # Install debug APK on connected device
make clean         # Clean build artifacts
```

## Quality Assurance Highlights

This project demonstrates QA engineering best practices:

- **Comprehensive Test Coverage:** Unit, integration, and E2E tests with JaCoCo coverage reports
- **Test Automation:** Automated UI testing with Appium
- **CI/CD Pipeline:** GitHub Actions for automated testing and linting
- **Code Quality:** ktlint enforcement and code review guidelines
- **Bug Tracking:** Structured bug reporting with device info collection
- **Documentation:** Detailed technical documentation and test plans

## Contributing

This is a demonstration project. Contributions are welcome for educational purposes.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `./gradlew test`
5. Submit a pull request

## License

This project is licensed under the Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0).

**You are free to:**
- Share - copy and redistribute the material
- Adapt - remix, transform, and build upon the material

**Under the following terms:**
- **Attribution** - You must give appropriate credit
- **NonCommercial** - You may not use the material for commercial purposes

See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Developed with [Claude Code](https://claude.com/claude-code) by Anthropic
- Maintained by a QA Engineer for portfolio demonstration

---

**Contact:** For questions or feedback, please open an issue on GitHub.
