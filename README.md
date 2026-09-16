# Together — shared habit accountability

Together is a small Android app for two people who want to keep each other accountable. Each person signs in anonymously, chooses a name, and either creates or enters a one-time pairing code. Once paired, both phones see the same habits and today’s check-ins in real time.

## What’s included

- Shared habit list, stored per pair in Cloud Firestore
- Real-time completion status for both partners
- Six-character pairing codes that expire after 24 hours
- No password screen: Firebase Anonymous Auth keeps the first-run experience light

## Run it

1. Create a Firebase project and add an Android app whose package name is `com.together.habits`.
2. Enable **Anonymous** and **Google** in Firebase Authentication. For Google, choose a support email when prompted.
3. In Firebase Project settings, add the SHA-1 and SHA-256 fingerprints for every signing key you use. The debug key is needed for local builds; the release key is needed before publishing.
4. Create a Cloud Firestore database, then paste the contents of `firestore.rules` into its Rules tab and publish.
5. Download Firebase’s `google-services.json` and put it at `app/google-services.json` (it is deliberately ignored by Git).
6. Install the Firebase CLI, run `firebase login`, then `firebase use --add` to select your Firebase project. From this folder run `firebase deploy --only firestore:rules,functions`. Cloud Functions requires billing to be enabled on the Firebase project.
7. In Android Studio, open this folder, allow Gradle to sync, then run on two devices or emulators.

## Cloud builds

This project includes a GitHub Actions workflow at `.github/workflows/android.yml`. Add the full contents of your Firebase `google-services.json` as a repository secret named `GOOGLE_SERVICES_JSON`, then push to GitHub. Each push builds a debug APK; download it from the workflow run’s **Artifacts** section.

To publish a permanent phone-downloadable release, create and push a version tag such as `v0.1.0`. The workflow will create a GitHub Release and attach `Trackability-v0.1.0.apk` automatically. In your repository settings, ensure **Actions → General → Workflow permissions** allows workflows to read and write repository contents.

Pairing is handled by the included callable Cloud Function, so it atomically validates and consumes an invite before connecting the two accounts. Before publishing an app, add App Check, a deletion/unpair flow, and a privacy policy.
