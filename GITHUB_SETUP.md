# Publishing this project on GitHub

This guide uses Windows PowerShell. The same Git commands also work in Git Bash and most Linux terminals.

## 1. Prepare the project folder

1. Download and extract the `DualSubPlayer-GitHub-ready.zip` archive.
2. Open the extracted `DualSubPlayer` folder in File Explorer.
3. Open a terminal in that folder. Confirm that `README.md`, `settings.gradle.kts`, `gradlew.bat`, and the `app` folder are visible.
4. Open the folder in Android Studio and let Gradle sync finish.

## 2. Test the project before publishing

Use JDK 17 and make sure Android SDK Platform 35 is installed. In PowerShell, run:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

Install and test the generated debug APK on a device before making a public release. If the build fails, fix the error before publishing. See `AUDIT.md` for checks that have not yet been verified in the preparation environment.

## 3. Create an empty GitHub repository

1. Sign in at [GitHub](https://github.com/).
2. Select **New repository**.
3. Use `DualSubPlayer` as the repository name.
4. Choose **Public** or **Private**.
5. Do not initialize the repository with a README, `.gitignore`, or license. This project already contains its README and `.gitignore`.
6. Create the repository and copy its HTTPS URL. It will look like `https://github.com/YOUR-USERNAME/DualSubPlayer.git`.

## 4. Push the project

In PowerShell, with the terminal still open in the `DualSubPlayer` project folder, run:

```powershell
git init -b main
git add .
git status --short
```

Review the staged files. Make sure that no signing keystore, passwords, API credentials, `local.properties`, or other private files are staged. Then commit and push:

```powershell
git commit -m "Initial release of DualSub Player"
git remote add origin https://github.com/YOUR-USERNAME/DualSubPlayer.git
git push -u origin main
```

Replace the repository URL with the one copied from GitHub. GitHub may ask you to authenticate using a browser or Git Credential Manager.

## 5. Check the automated build

After the push, open the repository on GitHub and select **Actions**. The `Android CI` workflow should run unit tests and build a debug APK. When the run succeeds, open it and download the `DualSubPlayer-debug-apk` artifact. This artifact is for testing only; it is not the signed public release APK.

For signing and publishing the public APK, follow `RELEASING.md`.

## Important notes

- Never commit your Android signing keystore or its passwords. Keep a secure backup of the keystore and use the same key for future updates distributed outside Google Play.
- The repository does not include a `LICENSE` file. Choose and add a license before granting others permission to reuse, modify, or redistribute the source code. Making a repository public does not by itself grant those permissions.
- Review the security and Android permission notes in `README.md` and `RELEASING.md` before distributing the APK widely.
