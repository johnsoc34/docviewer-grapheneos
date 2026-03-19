# DocViewer for GrapheneOS

A lightweight, privacy-respecting document viewer for Android (GrapheneOS-focused).
Opens documents **locally** with **zero network access**.

## Why This Exists

There is no good, lightweight, open-source Office document viewer on Android.
The existing options are either bloated and slow (Collabora, OnlyOffice),
tracker-laden (Microsoft Office, random Play Store apps), or incomplete
(OpenDocument Reader, Librera — missing Excel support).

DocViewer fills this gap: a fast, minimal viewer that handles all major
document formats with no internet permission, no trackers, no accounts, no ads.

## Supported Formats

| Format | Extension | Engine |
|--------|-----------|--------|
| Word (modern) | .docx | Apache POI XWPF |
| Excel (modern) | .xlsx | Apache POI XSSF |
| PowerPoint (modern) | .pptx | Apache POI XSLF |
| Word (legacy) | .doc | Apache POI HWPF |
| Excel (legacy) | .xls | Apache POI HSSF |
| PowerPoint (legacy) | .ppt | Apache POI HSLF |
| OpenDocument Text | .odt | XML parser |
| OpenDocument Spreadsheet | .ods | XML parser |
| OpenDocument Presentation | .odp | XML parser |
| PDF | .pdf | Android PdfRenderer |
| Plain text | .txt, .log, .md, .json, .xml, .yaml | Text reader |
| CSV | .csv | Delimited parser |
| TSV | .tsv | Delimited parser |

## Features

- **Zero network access** — no INTERNET permission, network explicitly blocked
- **No accounts required**
- **No trackers, no ads, no telemetry**
- **Dark mode support** (follows system theme)
- **Search within documents** — live highlighting with match count and navigation
- **Opens files from any file manager** (registered as handler for all supported MIME types)
- **Handles embedded images** (in DOCX and PPTX)
- **Formula evaluation** (in XLSX)
- **Merged cell support** (in XLSX)
- **Speaker notes** (in PPTX)
- **Multi-sheet display** (in XLSX/XLS/ODS)
- **CSV/TSV rendering as tables** with header detection and numeric alignment
- **PDF page rendering** at 2x resolution for readability
- **Storage Access Framework** — no storage permissions needed

## Architecture

```
File Manager / App Drawer
        |
        v
   MainActivity
        |
   Detect Type (MIME + extension)
        |
   +-----------+-----------+-----------+-----------+-----------+
   |           |           |           |           |           |
  Docx       Xlsx       Pptx        PDF        Text        ODF
 Converter  Converter  Converter  Converter  Converter  Converter
   |           |           |           |           |           |
   +-----------+-----------+-----------+-----------+-----------+
        |
     HTML/CSS
        |
     WebView (JS disabled, no network)
```

Each converter parses the document format and outputs clean HTML+CSS,
rendered in a sandboxed WebView with JavaScript disabled.

PDF uses Android's built-in PdfRenderer to render pages as images.

## Building

### Prerequisites
- JDK 17 (Eclipse Temurin recommended)
- Android SDK 34 (command line tools sufficient, Android Studio not required)
- Gradle 8.5

### Build from command line

```bash
git clone https://github.com/johnsoc34/docviewer-grapheneos.git
cd docviewer-grapheneos

# Set SDK location
echo "sdk.dir=C:\\android-sdk" > local.properties  # Windows
# echo "sdk.dir=/path/to/android-sdk" > local.properties  # Linux/Mac

# Generate wrapper if needed
gradle wrapper --gradle-version 8.5

# Build
./gradlew assembleDebug
```

APK output: app/build/outputs/apk/debug/app-debug.apk

### Install on device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── AndroidManifest.xml          # No INTERNET permission, MIME type handlers
├── java/org/grapheneos/docviewer/
│   ├── MainActivity.kt          # File picking, intent handling, search, WebView
│   ├── HtmlWrapper.kt           # Shared HTML/CSS template with dark mode
│   ├── DocxConverter.kt         # .docx → HTML (Apache POI XWPF)
│   ├── XlsxConverter.kt        # .xlsx → HTML (Apache POI XSSF)
│   ├── PptxConverter.kt        # .pptx → HTML (Apache POI XSLF)
│   ├── LegacyConverters.kt     # .doc/.xls/.ppt → HTML (Apache POI)
│   ├── OdfConverter.kt         # .odt/.ods/.odp → HTML (ZIP + XML parsing)
│   ├── PdfConverter.kt         # .pdf → HTML (Android PdfRenderer)
│   └── TextConverter.kt        # .txt/.csv/.tsv → HTML
├── java/org/apache/logging/log4j/  # No-op log4j shims for Android compatibility
└── res/
    ├── layout/activity_main.xml
    ├── menu/main_menu.xml
    ├── values/strings.xml
    ├── values/themes.xml
    ├── drawable/                 # Vector icons
    └── xml/network_security_config.xml
```

## Dependencies

| Library | Purpose | License |
|---------|---------|---------|
| Apache POI 5.2.5 | Parse OOXML and legacy Office formats | Apache 2.0 |
| Apache POI Scratchpad 5.2.5 | Legacy .doc/.xls/.ppt support | Apache 2.0 |
| Apache XMLBeans | XML schema support for POI | Apache 2.0 |
| AndroidX AppCompat | Material Design components | Apache 2.0 |
| Material Components | UI widgets | Apache 2.0 |

All dependencies are open source under Apache 2.0.

log4j is excluded from all POI dependencies and replaced with no-op shim
classes to avoid Android runtime compatibility issues (AccessController.doPrivileged
is blocked on Android).

## Known Limitations

- **Viewer only** — no editing capability (by design)
- **Complex layouts** — heavily formatted documents may not render pixel-perfect
- **Charts** — Excel/PowerPoint charts are not rendered (cell data is shown)
- **Macros** — VBA macros are ignored (security feature)
- **Encrypted files** — password-protected documents are not supported yet
- **PDF search** — search does not work on PDF pages (rendered as images)
- **APK size** — Apache POI adds ~15-20MB; can be reduced with ProGuard

## License

Apache License 2.0
