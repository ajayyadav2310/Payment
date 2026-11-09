# Payment Announcer — Android source project

## Build APK on your phone (no PC needed)
1. Create a new GitHub repository named `PaymentAnnouncer`.
2. Upload the contents of this ZIP (not the ZIP itself) to the repository root.
3. Open **Actions** → **Build Android APK** → **Run workflow** (or push to `main`).
4. Wait for the workflow to finish, open the successful run, then download the `Payment-Announcer-debug-apk` artifact.
5. Extract the artifact ZIP and install `app-debug.apk`. If Android asks, allow installation from that source.

## First-time setup
1. Open Payment Announcer.
2. Go to Settings → Open notification access settings.
3. Enable Payment Announcer.
4. In Voice, turn on announcements and choose Hindi or English.
5. Test with a small real UPI notification. Confirm the source app exposes the amount and transaction direction in its notification.

## Features included
- Native Android app with modern dark Compose interface and five tabs.
- Local Room database; no account, cloud, or bank login.
- Notification listener for payment received/sent alerts.
- Hindi/English voice announcement toggle, incoming/outgoing toggles, test voice.
- Searchable transaction history and direction filters.
- All-time received/sent overview and app-wise totals.
- CSV export, JSON backup/restore, clear local history.
- GitHub Actions workflow to build a debug APK.

## Important limitations (read this)
- This is **notification-based**, not connected to a bank or UPI payment network. Entries are not verified bank transactions.
- It can only detect text and amount exposed by an app's Android notification. Some apps hide/redact details, use different wording, or update notifications; therefore no parser can guarantee every app. Check each UPI app on your phone.
- Notification listener access is powerful: Android lets the service read notifications. Enable it only if you understand and accept that.
- TTS voices depend on the language voices installed on Android. Hinglish currently uses the Hindi voice.
- CSV/JSON export contains local notification-derived records. Keep backups private.
- Source has not been device-tested on your exact phone; GitHub Actions is the build check. If a build fails, share the Actions error log so the failing line can be corrected.

## Technical
- Package: `com.paymentannouncer.app`
- Min SDK 23, target SDK 35
- Kotlin + Jetpack Compose + Room
