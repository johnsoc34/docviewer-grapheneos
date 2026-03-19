# DocViewer for GrapheneOS

A lightweight, privacy-respecting document viewer for Android (GrapheneOS-focused).
Opens Word, Excel, and PowerPoint files **locally** with **zero network access**.

## Why This Exists

There is no good, lightweight, open-source Office document viewer on Android.
The existing options are either:
- **Bloated & slow** (Collabora, OnlyOffice)
- **Tracker-laden** (Microsoft Office, random Play Store apps)
- **Incomplete** (OpenDocument Reader, Librera — missing Excel support)

DocViewer fills this gap: a fast, minimal viewer that handles all major Office formats
with no internet permission, no trackers, no accounts, no ads.

## Features

- **Formats supported:** `.docx` `.xlsx` `.pptx` `.doc` `.xls` `.ppt` `.odt` `.ods` `.odp`
- **Zero network access** — no INTERNET permission, network explicitly blocked in config
- **No accounts required**
- **No trackers, no ads, no telemetry**
- **Dark mode support** (follows system theme)
- **Opens files from any file manager** (registered as handler for Office MIME types)
- **Handles embedded images** (in DOCX and PPTX)
- **Formula evaluation** (in XLSX)
- **Merged cell support** (in XLSX)
- **Speaker notes** (in PPTX)
- **Multi-sheet display** (in XLSX/ODS)
- **Storage Access Framework** — no storage permissions needed

## Architecture

```
File Manager → Intent (ACTION_VIEW) → MainActivity
                                          │
                                    ┌─────┴──────┐
                                    │ Detect Type │
                                    └─────┬──────┘
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    │                     │                     │
              DocxConverter         XlsxConverter         PptxConverter
              DocConverter          XlsConverter          PptConverter
                    │                     │                OdfConverter
                    │                     │                     │
                    └─────────────────────┼─────────────────────┘
                                          │
                                    ┌─────┴──────┐
                                    │  HTML/CSS   │
                                    └─────┬──────┘
                                          │
                                    ┌─────┴──────┐
                                    │   WebView   │  (JS disabled, no network)
                                    └────────────┘
```

Each converter parses the Office format (using Apache POI for OOXML/legacy formats,
or raw XML parsing for ODF) and outputs clean HTML+CSS, which is rendered in a
sandboxed WebView with JavaScript disabled.

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build from command line
```bash
# Clone the repo
git clone https://github.com/youruser/docviewer-grapheneos.git
cd docviewer-grapheneos

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Build release APK
```bash
# Generate a signing key (first time only)
keytool -genkey -v -keystore release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias docviewer

# Build release
./gradlew assembleRelease

# Sign the APK
apksigner sign --ks release-key.jks \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install on device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── AndroidManifest.xml          # No INTERNET permission, MIME type handlers
├── java/org/grapheneos/docviewer/
│   ├── MainActivity.kt          # File picking, intent handling, WebView rendering
│   ├── HtmlWrapper.kt           # Shared HTML/CSS template with dark mode
│   ├── DocxConverter.kt         # .docx → HTML (Apache POI XWPF)
│   ├── XlsxConverter.kt        # .xlsx → HTML (Apache POI XSSF)
│   ├── PptxConverter.kt        # .pptx → HTML (Apache POI XSLF)
│   ├── LegacyConverters.kt     # .doc/.xls/.ppt → HTML (Apache POI HWPF/HSSF/HSLF)
│   └── OdfConverter.kt         # .odt/.ods/.odp → HTML (ZIP + XML parsing)
└── res/
    ├── layout/activity_main.xml
    ├── values/strings.xml
    ├── values/themes.xml
    ├── drawable/                 # Vector icons
    └── xml/network_security_config.xml  # Blocks all network
```

## Dependencies

| Library | Purpose | License |
|---------|---------|---------|
| Apache POI 5.2.5 | Parse OOXML & legacy Office formats | Apache 2.0 |
| Apache XMLBeans | XML schema support for POI | Apache 2.0 |
| AndroidX AppCompat | Material Design components | Apache 2.0 |
| Material Components | UI widgets | Apache 2.0 |

All dependencies are open source under Apache 2.0.

## Known Limitations

- **Viewer only** — no editing capability (by design)
- **Complex layouts** — heavily formatted documents may not render pixel-perfect
- **Charts** — Excel/PowerPoint charts are not rendered (data in cells is shown)
- **Macros** — VBA macros are ignored (security feature)
- **Encrypted files** — password-protected documents are not supported yet
- **APK size** — Apache POI adds ~15-20MB; could be reduced with ProGuard

## Roadmap

- [ ] PDF support (via Android's built-in PdfRenderer)
- [ ] CSV/TSV viewer
- [ ] Pinch-to-zoom improvements
- [ ] Recent files list
- [ ] Search within document
- [ ] F-Droid submission
- [ ] Reduce APK size (custom minimal POI build)

## Contributing

PRs welcome. Priority areas:
1. Better DOCX rendering (margins, page breaks, columns)
2. Chart rendering for XLSX
3. Image extraction from ODF files
4. APK size reduction

## License

Apache License 2.0
