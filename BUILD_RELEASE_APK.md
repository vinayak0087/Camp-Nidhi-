# CampNidhi — Android Release APK Build & Distribution Guide

## Architecture Overview
CampNidhi is built as a **native Android application (Kotlin + Jetpack Compose)** connected directly to **Firebase Authentication (Google Sign-In via Jetpack Credential Manager)**, **Cloud Firestore (Enterprise Edition with zero-trust server-side rules)**, and **Firebase AI Logic (Gemini + App Check)**.

- **Application Name:** `CampNidhi`
- **Android Package ID (`applicationId`):** `com.mcc.campnidhi`
- **Minimum SDK:** API 24 (Android 7.0) | **Target / Compile SDK:** API 36
- **Firebase Project ID:** `gen-lang-client-0697017042`

> Note: Because CampNidhi runs natively on Android (Kotlin + Jetpack Compose) rather than a Capacitor/WebView wrapper, there is no `webDir` or local API server; all backend connectivity goes directly over HTTPS to Firebase Auth, Firestore, and Firebase AI Logic.

---

## Security & Release Hardening Included
1. **No Embedded Secrets:** No private API keys, database admin credentials, or Gemini API keys are stored in APK code or `.env`. AI OCR uses Firebase AI Logic authenticated via **Firebase App Check**.
2. **Server-Side Access Control:** All Primary Admin (`2501721049055@mcc.edu.in`), Co-Admin (`2501721049029@mcc.edu.in`), Faculty In-Charge, Invited Viewer, and Committee permissions are enforced in `firestore.rules`.
3. **Immutable Original Bills:** Uploaded camera photos, gallery images, and PDFs are hashed (SHA-256) and preserved immutably (`bill_originals`, update forbidden in Firestore rules). Local working copies are kept strictly inside app-private storage (`filesDir/protected_bills`) — never public external storage.
4. **Backup & Data Extraction Disabled:** `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` explicitly exclude private files, databases, and preferences from cloud backups and device transfers.
5. **Runtime Permissions:** Declares `CAMERA` with runtime permission prompt for direct bill photography; gallery/image selection uses Android Photo Picker (`PickVisualMedia`) and PDF selection uses Storage Access Framework (`OpenDocument`) with zero broad storage permissions.

---

## Step-by-Step: Generate a Signed Release APK in Android Studio

### 1. Export & Open the Project
1. Export the source ZIP from the workspace menu (or clone from your linked GitHub repository).
2. Extract the ZIP and open the root folder in **Android Studio** (Meerkat / Ladybug or newer with JDK 17+ / AGP 9.x).
3. Wait for Gradle Sync to complete.

### 2. Create or Select Your Release Upload Keystore
1. In Android Studio, open **Build > Generate Signed App Bundle / APK...**
2. Select **APK** and click **Next**.
3. Under **Key store path**, click **Create new...** (or choose an existing `.jks` keystore):
   - Set **Key store path** (store it safely outside the repository).
   - Set **Key store password**, **Alias** (recommended: `upload`), and **Key password**.
   - Fill in certificate details (e.g., Organizational Unit: MCC, Country: IN) and click **OK**.
4. Select the **`release`** build variant and click **Create** to generate `app/release/app-release.apk`.

### 3. Register the Release Keystore SHA-1 & SHA-256 in Firebase (Critical for Google Sign-In)
Google Sign-In (Credential Manager) and App Check verify the APK's signing certificate. Before distributing the release APK:
1. Extract your release keystore's fingerprints:
   ```bash
   keytool -list -v -keystore /path/to/your-release-key.jks -alias upload
   ```
2. Open **Firebase Console** -> Project `gen-lang-client-0697017042` -> **Project Settings** -> **Your apps** -> Android app **`com.mcc.campnidhi`**.
3. Click **Add fingerprint** and add **both** the **SHA-1** and **SHA-256** from your release keystore.
4. Download the updated `google-services.json`, replace `app/google-services.json`, and rebuild the release APK.
5. In **Firebase Console > App Check**, ensure **Play Integrity** (for Google Play) or **reCAPTCHA** is registered for `com.mcc.campnidhi` alongside the bundled `recaptcha_site_key`.

### 4. Command-Line Alternative (Gradle)
With environment variables configured (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` for alias `upload`):
```bash
gradle :app:assembleRelease
```
Output APK path: `app/build/outputs/apk/release/app-release.apk`
