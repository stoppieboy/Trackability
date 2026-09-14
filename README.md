# Together — shared habit accountability

Together is a small Android app for two people who want to keep each other accountable. Each person signs in anonymously, chooses a name, and either creates or enters a one-time pairing code. Once paired, both phones see the same habits and today’s check-ins in real time.

## What’s included

- Shared habit list, stored per pair in Cloud Firestore
- Real-time completion status for both partners
- Six-character pairing codes that expire after 24 hours
- No password screen: Firebase Anonymous Auth keeps the first-run experience light

## Run it

1. Create a Firebase project and add an Android app whose package name is `com.together.habits`.
2. Enable **Anonymous** in Firebase Authentication.
3. Create a Cloud Firestore database, then paste the contents of `firestore.rules` into its Rules tab and publish.
4. Download Firebase’s `google-services.json` and put it at `app/google-services.json` (it is deliberately ignored by Git).
5. Install the Firebase CLI, run `firebase login`, then `firebase use --add` to select your Firebase project. From this folder run `firebase deploy --only firestore:rules,functions`. Cloud Functions requires billing to be enabled on the Firebase project.
6. In Android Studio, open this folder, allow Gradle to sync, then run on two devices or emulators.

## Cloud builds

This project includes a GitHub Actions workflow at `.github/workflows/android.yml`. Add the full contents of your Firebase `google-services.json` as a repository secret named `GOOGLE_SERVICES_JSON`, then push to GitHub. Each push builds a debug APK; download it from the workflow run’s **Artifacts** section.

Pairing is handled by the included callable Cloud Function, so it atomically validates and consumes an invite before connecting the two accounts. Before publishing an app, add App Check, a deletion/unpair flow, and a privacy policy.
