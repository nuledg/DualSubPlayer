# Building and publishing a release

This guide describes how to build a signed APK and publish it as a GitHub Release.

## 1. Check the version

The Android version is defined in `app/build.gradle.kts`:

- `versionCode`: an integer that must increase for every Android app update.
- `versionName`: the human-readable version shown to users.

The first prepared release is `versionCode = 1` and `versionName = "0.1.0"`. Its Git tag should be `v0.1.0`.

For the next release, increase `versionCode` and update `versionName` before building. Do not reuse a version code for a later update.

## 2. Verify the project

From the project directory, run:

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

On Windows PowerShell, run:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

Install and test the debug APK on a real device before publishing. The GitHub Actions workflow also runs the unit tests and creates a downloadable **debug** APK for each successful run. That debug APK is for testing, not the public release file.

## 3. Create a signed release APK in Android Studio

1. Open the project in Android Studio and wait for Gradle sync to finish.
2. Select **Build → Generate Signed Bundle / APK**.
3. Choose **APK**, then select the `app` module.
4. Select **Create new** to create a signing key, or choose an existing key if this app has already been released.
5. Store the keystore and its passwords somewhere secure and backed up. Do not put them in the repository, a commit, or a public release asset.
6. Choose the **release** build variant and finish the wizard.
7. Locate the generated signed APK. Android Studio shows the output location when the build finishes; it is usually under `app/build/outputs/apk/release/`.
8. Install that APK on a device and test it before publishing.

**Important:** Keep the same keystore for every future update of this app. If you lose it, you may not be able to publish an update that Android recognizes as coming from the same developer. The `.gitignore` file excludes common signing-key files, but still check what you stage before committing.

## 4. Publish the APK on GitHub

1. Push the source code to your GitHub repository by following [GITHUB_SETUP.md](GITHUB_SETUP.md).
2. Open the repository on GitHub and select **Releases → Draft a new release**.
3. Create the tag `v0.1.0` and target the commit you tested.
4. Set the release title to `DualSub Player v0.1.0`.
5. Add a short summary of the changes, using `CHANGELOG.md` as the source.
6. Attach the **signed release APK**. Give it a clear filename such as `DualSubPlayer-v0.1.0.apk`.
7. Publish the release.

GitHub Releases are tied to Git tags and can contain release notes and binary downloads. See the [GitHub release guide](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## 5. Optional: publish an APK checksum

A checksum lets users check that their downloaded APK matches the file you published.

Windows PowerShell:

```powershell
Get-FileHash .\DualSubPlayer-v0.1.0.apk -Algorithm SHA256
```

Linux:

```bash
sha256sum DualSubPlayer-v0.1.0.apk
```

Paste the resulting SHA-256 value into the GitHub release notes.

## Before distributing widely

- This source archive did not include a `LICENSE` file. Choose a license deliberately before describing the repository as open source. Without a license, other people generally do not receive permission to reuse, modify, or redistribute the code.
- `README.md` documents that provider credentials are stored in app-private preferences without app-level encryption. Consider that before recommending users save sensitive credentials in the app.
- The manifest requests `MANAGE_EXTERNAL_STORAGE` for optional same-folder subtitle discovery. Review Google's current [All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955) before attempting a Google Play release. A GitHub APK release and a Google Play release have different requirements.
- The current `AUDIT.md` records that the latest project source has not yet been verified by a complete Android Gradle build or a physical-device test in the preparation environment. Do not claim that those checks passed until you run them.
