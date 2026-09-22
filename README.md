# PassPorta

PassPorta is a local-first Android app for organizing boarding passes, event tickets, coupons, and loyalty cards. It works with standard `.pkpass` files and Google Wallet links without relying on cloud services.

All data stays on your device inside a local Room database. No account required.

## Key Features

- **`.pkpass` Import:** Open files directly from email attachments, local storage, or web links.
- **Auto-Extraction:** Extracts barcodes and text directly from imported PDFs, documents, or screenshots.
- **Google Wallet Link Support:** Save passes shared from Google Wallet *(Live Update passes are currently unsupported)*.
- **Camera Scanner:** Scan physical cards and paper tickets straight into the app.
- **Custom Passes & Editing:** Create passes manually or modify existing fields whenever needed.
- **Document Attachments:** Keep original tickets, PDFs, and images paired with their respective passes.
- **Local Backups:** Export and import your full pass database as a ZIP file. Also automatically possible.
- **Multi-Barcode:** Support multiple barcodes in a single pass.

## Installation

### Download Pre-built APK

1. Go to [Releases](https://github.com/mdhia/PassPorta/releases/latest).
2. Download the latest `PassPorta-<date>.apk`.
3. Enable "Install unknown apps" for your file manager or browser if prompted (Android Settings → Apps → Special app access).
4. Open the APK to install.

*Requires Android 7.0 (API 24) or higher.*

### Build from Source

Prerequisites: JDK 17 and the Android SDK (or Android Studio).

```powershell
git clone https://github.com/mdhia/PassPorta.git
cd PassPorta
./gradlew assembleDebug
```
