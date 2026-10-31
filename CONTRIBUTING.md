# Contributing to SaralFileManager

Thanks for helping. A few things keep the app simple and trustworthy:

- **No internet access, no ads, no analytics.** Pull requests that add the `INTERNET` permission, an ads SDK, analytics
  or crash reporting won't be merged. CI fails the release build if `INTERNET` shows up.
- **Keep it small.** Prefer the Android SDK and Jetpack over new libraries. Explain any new dependency in the PR.
- **Never lose data.** Anything that writes or deletes files needs a test in `app/src/test` (see `FileJobTest`) and has to
  handle cancellation and failures without leaving half-written files.

## Workflow

1. Open an issue first for anything bigger than a small fix, so we can agree on the approach.
2. Branch from `main` (`fix/<topic>` or `feature/<topic>`), keep commits focused, and describe *why* in the message.
3. Run `./gradlew testDebugUnitTest assembleDebug` before pushing. CI runs the same.
4. Add new user-facing text to `app/src/main/res/values/strings.xml`. Don't hard-code strings in Kotlin.

## Translations

Translations are welcome as `app/src/main/res/values-<language>/strings.xml`. Keep the brand name `SaralFileManager` in Latin script.

## License

By contributing, you agree that your contribution is licensed under the GPL-3.0-or-later, like the rest of the project.
