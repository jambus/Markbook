# Android Google Drive OAuth Setup

This setup is for the Mate 60 debug APK. It uses Google Play services for the
Android OAuth flow and does not use a client secret.

## Create the Test Client

1. In Google Cloud Console, create a project named `Markbook` and enable the
   Google Drive API.
2. In Google Auth Platform, complete Branding and choose an External audience.
   Keep the app in Testing and add the Google account used on the Mate 60 as a
   test user.
3. Under Data Access, declare `https://www.googleapis.com/auth/drive`. Markbook
   needs this scope in development because it must synchronize an existing
   desktop Obsidian Vault rather than only files that it created itself.
4. Create an OAuth client of type Android with:

   ```text
   Package name: com.markbook.android
   Debug SHA-1:  6E:32:05:4F:82:2D:2F:59:84:23:C7:DF:26:18:ED:32:CC:43:D8:E6
   ```

5. Provide only the generated client ID to the local Android build
   configuration. Do not add a client secret, Google password, refresh token,
   or credential JSON file to this repository.

## Release Note

The debug SHA-1 is only for local Mate 60 testing. A future signed release APK
needs a separate Android OAuth client that uses its release signing SHA-1. A
public app using broad Drive access requires Google's verification process
before store release.
