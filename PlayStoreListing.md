# Google Play listing draft

## App name

Worship Harvest OnCue

## Short description

Plan worship services, organize local audio, and run playback offline.

## Full description

Worship Harvest OnCue helps worship teams organize a service and run its audio from one Android device.

Create up to eight upcoming services, set each service's date and time, and arrange the service flow to fit your team. Duplicate a service to reuse its outline, or edit sections and song slots for that week's plan.

Add audio from your device with Android's file picker. If Google Drive is available as a file location on your device, you can choose audio there too. Imported audio is copied into the service so it is ready to play offline.

During the service, use the controls for:

• Service loops and individual songs  
• Crossfade, continuous playback, and individual song playback  
• 2, 4, or 6 second fade outs  
• A song player with scrubbing, play/pause, rewind, and forward controls  
• Chromatic key pads that can play with songs  
• Reorderable service sections and song slots  
• Service notes saved on the device

Worship Harvest OnCue is designed for local use. It does not require an account, and your service plans, notes, and imported audio stay in the app on your device. Internet may be needed by a file provider, such as Google Drive, when you first import a file.

## Contact and policy fields

- Support email: worshipharvestoncue@gmail.com
- Privacy policy URL: publish `PrivacyPolicy.md` as a public, accessible webpage, then paste that URL into Play Console.
- Developer name: use the independent developer name from your Google Play developer account.

## Play Console declarations to review

The app manifest currently has no internet or sensitive-data permissions, and its Gradle dependencies contain no analytics or advertising SDKs. Confirm these statements against the final signed Android App Bundle and any future dependency changes before completing Play Console's Data safety form. The current implementation stores service details and selected audio locally; it does not send them to an app server.

For a newly created personal developer account, Google currently requires a closed test with at least 12 testers opted in continuously for 14 days before production access. Google Play also currently requires new apps to target Android 16 (API 36) or higher. Check the requirements displayed in your own Play Console account before release.
