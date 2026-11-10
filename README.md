# Worship Harvest OnCue for Android

Worship Harvest OnCue is a native Android app for organizing and running a worship service. Service plans, notes, and imported audio are stored on the device. Playback works offline after audio has been imported.

## What it includes

- Up to eight services, sorted by date and then service time. Service names, dates, and times can be edited; services can be duplicated or deleted.
- Reorderable service sections, with controls to add, rename, resize, or delete sections. Song sections support one to four songs; response/ministry sections support one to four songs plus a loop. Song slots use a two-column layout on phones and tablets.
- Local audio import through Android's document picker, with an edit dialog for labels, replacing audio, and removing audio. If Google Drive is installed and available as a document provider on the device, it appears as a location in that picker. Imported files are copied into the service and remain available offline.
- Loop and song playback, crossfade/continuous/individual modes, 2/4/6-second fades, a song scrubber, chromatic key pads, and up to 600 characters of service notes.

Android's built-in media decoder support varies by device. MP3, AAC/M4A, and WAV are common formats; use another format if a particular phone cannot decode a file.

## Open and run in Android Studio

1. Install Android Studio Narwhal 3 Feature Drop or newer and unzip this folder.
2. In Android Studio, choose **Open** and select `WorshipHarvestOnCue-Android`.
3. Let Android Studio sync the Gradle project. The first setup requires internet to download Gradle and Android build tools.
4. Connect an Android phone with USB debugging enabled, or select an emulator, and press **Run**.

The app supports Android 8.0 (API 26) and later. The Play build targets API 36. The Play version is configured as version 1.0, build 1. The current application ID is `com.worshipflow.app`; keep it unchanged when uploading updates to the same Play listing.

## Prepare a Google Play release

1. In Google Play Console, create the app using application ID `com.worshipflow.app` if that ID is available to your developer account. Package IDs are unique; if it is already registered, choose an available ID before the first release and update the project before building.
2. In Android Studio, select **Build → Generate Signed Bundle / APK… → Android App Bundle**.
3. Create or select an upload key. Keep the keystore file and its passwords in a safe place; they are needed for future updates. Do not put them in this project or upload them to GitHub.
4. Upload the resulting `.aab` to a Play Console test track first, then complete the store listing, content rating, app access, and Data safety forms.

The app itself has no account, backend, analytics, advertising, or internet permission. It stores service plans and copied audio in app-private storage. Review each Play Console declaration against the final build and your use of Android's system document picker. New personal Play developer accounts may have a required closed-testing step before production access.

The project includes an in-app privacy policy and a copy at `PrivacyPolicy.md` for publishing as a public webpage. Google Play requires the policy to be linked in Play Console; the draft listing and suggested declarations are in `PlayStoreListing.md`. Replace the policy's contact details only if your support contact changes.

## Project identity and compatibility

The Android package/application ID remains `com.worshipflow.app` to preserve compatibility with the Android project that has already been tested. The user-facing app name is **Worship Harvest OnCue**. The iOS app has a separate bundle identifier and does not share data with this Android app.
