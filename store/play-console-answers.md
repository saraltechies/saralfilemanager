# Play Console answers for SaralFileManager

Answers for every declaration in **Policy → App content**, plus the release setup.
They match the code as of v1.0.0: no ads, no analytics, no crash reporting, and **no INTERNET permission**
(the manifest removes it and the release workflow fails if it appears).
**Re-check them whenever you add an SDK or a permission.**

## Create app
- Developer account: **Saral Techies Official** (organization)
- App name: `SaralFileManager: Files & Zip`
- Default language: English (India) – en-IN
- App or game: **App**
- Free or paid: **Free**

## App access
- **All functionality is available without special access** (no login). Reviewers need to allow "All files access" on first launch; mention it in the notes:
  "On first launch, tap Allow and turn on All files access for SaralFileManager. The app is a file manager and needs it to list and manage files."

## Ads
- **No, my app does not contain ads.**

## Content rating (IARC questionnaire)
- Category: **All other app types**
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Users interact or exchange content: **No**. Shares location: **No**. Digital purchases: **No**
- Unrestricted internet access / web browser: **No**
- Expected result: Everyone / PEGI 3 / IARC 3+

## Target audience and content
- Target age groups: **13–15, 16–17, 18 and over** (do NOT select under 13)
- Could the app unintentionally appeal to children? **No**

## News app
- **No**

## Data safety
- Does your app collect or share any of the required user data types? **No**
  - Reason: the app can't send anything off the device (no internet permission). Files are only read and changed on the phone when the user asks.
- Is all user data encrypted in transit? Not applicable. If the form forces an answer, choose **Yes**.
- Deletion request: not applicable; uninstalling deletes the app's settings and bookmarks.
- Result shown on the store: "No data collected · No data shared with third parties"

## Government apps / Financial features / Health
- **No** / **None** / **No**

## Permissions declaration: All files access (MANAGE_EXTERNAL_STORAGE)
Play Console asks for this under **App content → Permissions declaration** (or when the first AAB is uploaded).
- Core functionality: **File manager** ("File management" in the list of permitted uses).
- Description to paste:
  ```
  SaralFileManager is a file manager. Its core function is to let the user browse, open, copy, move, rename, delete, zip, extract and search the files and folders in the device's shared storage, SD cards and USB drives. These actions need broad access to all files, which the Storage Access Framework and MediaStore cannot provide (for example copying whole folder trees between volumes, showing every file type, and managing files created by other apps). The permission is requested on first launch with an explanation, and the user grants it in system settings. The app has no internet permission, so file data never leaves the device.
  ```
- Video: record a 30-second screen capture showing: first launch → Allow → All files access switch → home → open a folder → copy a file → paste in another folder. Upload it to YouTube as **Unlisted** and paste the link.

## Store settings
- Category: **Tools**
- Contact email: support@saraltechies.com
- Website: https://saraltechies.com/

## Release
- Countries: **all countries** (or start with India)
- Track: **Internal testing** first, then **Production**
- Play App Signing: **use Google-generated app signing key**. Upload `app-release.aab`, signed with our upload key `saralfilemanager-upload`.
- Upload key SHA-256 (for reference):
  `C1:38:DE:8B:78:5A:2E:32:28:06:6F:B0:9F:96:D3:A7:C6:80:7D:1C:2B:E1:DF:2C:72:63:9E:D0:F9:90:27:05`
- Deobfuscation file: upload `mapping.txt` from the same build.
- Release name: `1.0.0 (1)`
- Release notes (en-IN):
  ```
  <en-IN>
  First release of SaralFileManager.
  • Browse internal storage, SD cards and USB drives
  • Copy, move, rename, delete, and zip or unzip files
  • Categories, bookmarks, recent files and search
  • List or grid view with photo and video previews
  • Private and open source: no ads, no internet access
  </en-IN>
  ```
