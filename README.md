# Hiking Attendance

Android app for RHUL Hiking Club committee members to take attendance at the start of a hike.
Pick the hike, hold the phone out, and members tap their RHUL student card on the back. The app
reads the student number from the card, finds the student's row in that hike's tab of the club
Google Sheet, and changes the status column from `Signed Up` to `HERE`.

## How it works

- **Reading cards:** student cards are MIFARE Classic. The app authenticates sector 6 with the known
  keys and reads the ASCII student number from block 24, using NFC reader mode.
- **The sheet:** each hike has its own tab, filled by the sign-up form. The app reads only three
  columns: student ID (default `B`), name (`C`) and status (`M`). It writes only the status column.
  The other columns hold contact, emergency and medical details. They are never requested and
  never stored on the phone.
- **Matching:** IDs are compared after trimming, stripping non-digits and dropping leading
  zeros. Numeric sheet values such as `1.01096712E8` are handled. Row 1 is treated as a header
  only if its ID cell isn't a number.
- **Before every write**, the ID and status columns are read again, because the form can add rows
  mid-session.

| Tap result | Screen | Sheet change |
| --- | --- | --- |
| Signed up | Green, name (or ID if blank) | Status set to `HERE` |
| Already `HERE` | Amber "Already registered" | None |
| Signed up more than once | Green, with a note | Every matching row set to `HERE` |
| Not on this hike's tab | Red "Not signed up for this hike", optional "Add as walk-in" | Walk-in adds a row with only timestamp, ID and `HERE` |
| Card unreadable / moved / not MIFARE | Red with the reason, "tap again" | None |
| No signal | Blue "Queued: will sync" | Written automatically once back online |

- **Repeat taps:** the same card is ignored for 3 seconds after a successful read.
- **Offline taps:** these are stored in a small on-device queue holding only the hike tab, student
  ID and time. A background job syncs them when there is a connection, and entries are deleted as
  soon as they sync. A queued ID that turns out not to be on the tab is kept for a committee member
  to add as a walk-in or dismiss. The app never adds walk-ins on its own.

## Phone requirements

- Android 8.0 (API 26) or newer, with NFC.
- **The NFC chip must support MIFARE Classic.** Many phones have NFC but can't read MIFARE Classic,
  including most Google Pixels. Most Samsung phones and others with NXP NFC chips work. The app
  checks this on launch and explains if the phone is unsupported. Use **Settings > NFC card read
  test** to confirm a phone reads cards before relying on it at a hike.

## One-time setup

Do this once for the club. It takes about 15 minutes. Use the club Google account for all of it
where possible.

### 1. Create a Google Cloud project

1. Go to <https://console.cloud.google.com/> and sign in with the club Google account.
2. Click the project picker at the top, then **New project**.
3. Name it `Hiking Attendance` and click **Create**. Make sure it's selected in the project picker.

### 2. Enable the Google Sheets API

1. Go to **APIs & Services > Library**.
2. Search for **Google Sheets API**, open it, and click **Enable**.

### 3. Configure the OAuth consent screen and add committee accounts

1. Go to **APIs & Services > OAuth consent screen**. In newer consoles this is **Google Auth Platform**.
2. Click **Get started** and fill in:
   - App name: `Hiking Attendance`
   - User support email: the club account
   - Audience: **External**
   - Contact email: the club account
3. Under **Data access**, click **Add or remove scopes** and add
   `https://www.googleapis.com/auth/spreadsheets`. Save.
4. Under **Audience**, leave the publishing status as **Testing**. Under **Test users**, click
   **Add users** and add the Google account of every committee member who will run the app.
   Up to 100 test users are allowed. Only these accounts can sign in.

While the app is in Testing, Google may ask a user to approve access again about once a week.
The app shows the consent screen when that happens.

### 4. Create the OAuth clients

You need one **Android** client per signing key: one for debug builds and one for release builds.
You can also create one optional **Web** client. Clients don't use a secret, so nothing secret goes
in the app.

**Get the SHA-1 fingerprints.**

From the project directory, this prints the SHA-1 for every variant:

```sh
./gradlew signingReport
```

Or use `keytool` directly. For the debug key, which Android Studio and Gradle create
automatically:

```sh
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

For a release key, create one if you don't have one yet. **Keep this file and its passwords safe,
and never commit them.**

```sh
keytool -genkeypair -v -keystore hiking-release.jks -alias hiking -keyalg RSA -keysize 2048 -validity 10000
keytool -list -v -keystore hiking-release.jks -alias hiking
```

Copy the `SHA1:` line from the output.

**Create the clients.**

1. Go to **APIs & Services > Credentials** (or **Google Auth Platform > Clients**) and choose
   **Create credentials > OAuth client ID**.
2. Application type **Android**:
   - Name: `Hiking Attendance debug`
   - Package name: `com.rhulhiking.attendance`
   - SHA-1: the debug fingerprint
3. Repeat with name `Hiking Attendance release` and the release SHA-1.
4. Optional but recommended: create one more client with type **Web application**, named
   `Hiking Attendance sign-in`. No redirect URIs are needed. Copy its **Client ID**. It enables the
   standard "Sign in with Google" sheet. Without it, the app still works, and the account is chosen
   on the consent screen instead.

Put the Web client ID in `local.properties` in the project root. This file is git-ignored.

```properties
sdk.dir=/path/to/Android/Sdk
hiking.webClientId=1234567890-abc123.apps.googleusercontent.com
```

For CI, use the `HIKING_WEB_CLIENT_ID` environment variable or `-Phiking.webClientId=...` instead.

### 5. Share the spreadsheet

1. Open the attendance spreadsheet in Google Sheets.
2. Click **Share**, then add the club Google account, and any committee accounts that will sign in
   to the app, as **Editor**.

The app needs edit access to write the status column. A "doesn't have edit access" message means
the signed-in account isn't an Editor.

### 6. Build and sideload the APK

You need JDK 17 or newer and the Android SDK with API 37 installed. Android Studio includes both.

**Debug build**, quickest for trying it out:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Release build**, for the committee phones. Create `keystore.properties` in the project root.
This file is git-ignored.

```properties
storeFile=hiking-release.jks
storePassword=...
keyAlias=hiking
keyPassword=...
```

Then:

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `adb`, copy the APK to the phone (USB, Drive, email) and open it. Android will ask you to
allow installing apps from that source.

Release and debug builds use different signing keys. Install builds signed with a key whose SHA-1
you registered in step 4, or Google sign-in fails with a developer error.

### 7. First run on each phone

1. Open the app. If the phone can't read student cards, it will say so.
2. Tap **Sign in with Google** and pick a committee account that is a test user and a sheet Editor.
   Approve access to Google Sheets.
3. Paste the spreadsheet link (the whole URL is fine) and tap **Continue**.
4. On the hike screen, open **Hide non-hike tabs** to hide `Hike Calendar`, `Members`, etc. These
   are hidden by default. Keep **Remember hidden tabs** on so the choice sticks.

## Using it at a hike

1. Open the app, choose the hike from the dropdown, and tap **Start taking attendance**.
2. Hold the phone out with the screen facing you. Members tap their card flat against the back of
   the phone, near the camera on most models.
3. The counter shows `X / Y here`. The list below shows who has and hasn't arrived. Pull down to
   refresh it. The screen stays on while this screen is open.
4. A blue **N to sync** badge means taps are queued for when signal returns. They sync on their own.
   You don't need to keep the app open.
5. Tap **End session** to go back to the hike picker.

**Settings** lets you change the ID, name and status columns, the value written (default `HERE`)
and whether walk-ins are allowed. The status column can't be set to the same column as the ID,
name or timestamp.

## Development

```sh
./gradlew build          # compile, lint and run all unit tests
./gradlew testDebugUnitTest
```

Stack: Kotlin, Jetpack Compose (Material 3), Hilt, coroutines and Flow, DataStore, Room,
WorkManager, OkHttp with kotlinx.serialization against the Sheets REST API v4, and Google Identity
Services (Credential Manager and AuthorizationClient) for auth. Versions are in
`gradle/libs.versions.toml`.

| Package | Contents |
| --- | --- |
| `nfc` | Reader mode, `SmartCardManager` (card read), debounce, capability checks |
| `sheets` | A1 range building, URL parsing, REST repository, error mapping |
| `auth` | Google sign-in and access tokens (held in memory only) |
| `attendance` | ID normalisation, header detection, row matching, `CheckInService` |
| `queue` | Room offline queue, `QueueFlusher`, `SyncWorker` |
| `ui` | Compose screens and view models |

Unit tests cover ID normalisation, header-row detection, row matching, A1 range escaping,
spreadsheet URL parsing, HTTP error mapping and retries, the card read loop, and the queue flush
logic against a fake Sheets repository.

### Privacy

- No student data is written to logs in release builds. Logging goes through `DebugLog` (debug
  builds only), and R8 also strips `android.util.Log` from release builds.
- The only student data stored on the phone is a queued check-in (hike tab, student ID, time), and
  it is deleted once synced.
- Backups and device transfer are disabled for all app data.
- Never commit `local.properties`, `keystore.properties`, keystores, `google-services.json` or
  client secret files. All are in `.gitignore`.
