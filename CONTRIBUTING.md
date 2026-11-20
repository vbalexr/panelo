# Contributing to Panelo

Panelo is an open-source project and contributions are welcome: issues, fixes and features.

## AI / LLM policy

Using AI assistants (LLMs) **as a tool** while you work is fine. Fully automated contributions are not. Specifically:

- **No automated pull requests.** PRs generated and submitted by bots or AI agents without a human author will be closed.
- **A human is responsible for every change.** You must understand, review and be able to explain all code you submit, regardless of how it was written.
- **Everything must be tested by a human** on a real device or emulator. Describe in the PR what you tested and on which Android versions.
- **Code changes need tests.** New logic comes with unit tests (`./gradlew testDebugUnitTest`), and the build, lint and existing tests must pass.
- Disclosing significant AI assistance in the PR description is appreciated.

By submitting a contribution you agree that it is licensed under the project's license (GPL-3.0-or-later).

## Coding conventions

### Control flow

- **Guard clauses first.** Validate inputs and handle errors at the top of a function and return or throw early. Do not nest the happy path inside conditionals.
- **One function per purpose.** Avoid trivial pass-through wrappers that only rename a single call without adding logic.

### Structure

- **Group related functions on a class or object named for the noun,** with short verb methods, e.g. `Snapshot.capture()` or `RemoteConfig.fetch()`, not loose top-level functions such as `captureSnapshot()`.
- **No `Utils` or `Helpers` files or classes.** A helper belongs on the class named for what it does.

### Errors

- **Kotlin:** for expected failures, return `Result<T>` or a sealed result type instead of throwing. Callers check the result.

### Logging

- Use the app's `Log` object. Each line is a structured JSON record with a timestamp, level, component, event and fields.

### Source files

- Every source file starts with the SPDX header:

  ```kotlin
  // SPDX-FileCopyrightText: 2026 Alex Vargas
  // SPDX-License-Identifier: GPL-3.0-or-later
  ```

- Contributors add their own `SPDX-FileCopyrightText` line for substantial contributions.

### Performance

- Keep the app lightweight. The WebView path comes first: performance there matters more than convenience elsewhere.
- Do not add new heavy dependencies without discussing them in an issue first.

## Before opening a PR

- [ ] The project builds: `./gradlew assembleDebug`.
- [ ] Lint and unit tests pass: `./gradlew lintDebug testDebugUnitTest`.
- [ ] You tested the change yourself on a real device or emulator, and the PR lists the Android versions you used.
- [ ] New logic has unit tests.
- [ ] A human author is behind the PR, and they can explain every change (see the AI / LLM policy above).
