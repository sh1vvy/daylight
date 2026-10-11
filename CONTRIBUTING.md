<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-08 through 2026-10-11.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Contributing to Daylight

Thank you for contributing to Daylight. Follow these guidelines to ensure an efficient development and review process.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
Base Daylight changes on `main`. Keep upstream imports and Daylight changes in separate commits.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `docs/translation-guide`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup
The project requires the following tools:
- **JDK**: Java Development Kit 17 (Eclipse Temurin 17 recommended).
- **Android SDK**: `compileSdkVersion("android-37.0")`, `targetSdk = 36`, `minSdk = 26`.
- **C/C++ NDK & CMake**: CMake 3.22.1+ and Android NDK (for native audio DSP components configured under `app/src/main/cpp`).
- **Cloudflare Jam (Optional)**: Node.js 22+ if developing or testing the party service (`cloudflare-jam/`).

## Build and Test Commands
Run Gradle commands from the repository root.

Build:
```bash
./gradlew assembleDevDebug
```

Windows:
```powershell
.\gradlew.bat assembleDevDebug
```

Tests:
```bash
./gradlew testDevDebugUnitTest
```

Windows:
```powershell
.\gradlew.bat testDevDebugUnitTest
```

Run a specific test:
```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised device capability, and external physical endpoint behavior.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
Daylight is licensed under the **GNU General Public License v3.0 (GPLv3)**.
Identified Daylight additions carry the reasonable attribution and origin terms
in [DAYLIGHT-NOTICE.md](DAYLIGHT-NOTICE.md), under GPLv3 sections 7(b) and 7(c).
Original BitChord and third-party licenses and notices must remain intact.

Keep existing authorship notices when editing source. Add your own dated notice
for your copyrightable contributions; contributing does not assign your rights
to sh1vvy. Update the contribution inventory with
`python3 tools/daylight_attribution.py --update --author YOUR_NAME`, using the
actual author's name for new files, and run
`python3 tools/daylight_attribution.py --check` before submission. Existing
authors are not overwritten by this command. For an existing file, also add
your own notice alongside its retained author notices where appropriate.

These checks protect provenance and package the legal documents; they do not
add a permission requirement for downstream reuse or claim ownership of ideas.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
