# TechDesk Scanner — Native Android App for TechDesk DZ

**TechDesk Scanner** is a native Android application designed to turn any Android phone into an ultra-fast, offline-first barcode scanner for **TechDesk DZ** on Windows via a direct **USB-tethered** network connection.

---

## Key Features

- **Direct USB-Tethered Connection**: Communicates over local network created by Android USB tethering (subnet detected dynamically from the active RNDIS/USB interface). No internet, cloud relay, Bluetooth, or Wi-Fi required.
- **Strict Protocol Conformance**: Matches the TechDesk DZ Windows USB scanner specification (`/api/usb-scanner/identify`, `/pair`, `/heartbeat`, `/submit`).
- **One-Tap Desktop QR Pairing**: Automatically scans and parses `{"ref":"...","code":"...","ip":"...","port":3000}`.
- **Hardware-Backed Keystore Security**: Bearer device tokens and credentials are encrypted using the Android KeyStore (`AES/GCM/NoPadding`).
- **Live CameraX & ML Kit Barcode Recognition**: Continuous, high-performance offline decoding for EAN-13, EAN-8, UPC-A, UPC-E, Code 128, and QR codes.
- **Server Context Awareness**: Live HUD displays TechDesk DZ's active context: `POS Mode` (cart sales), `Products Mode` (registration), or `Auto Mode`.
- **Deduplication & Retry Guarantee**: Scans reuse the same unique `scanId` during retries so the Windows server deduplicates repeated transmissions.
- **Multilingual Support**: English, French, and Arabic (with complete RTL layout).

---

## Quick Start Guide

### 1. Connect Phone to PC
1. Connect the Android phone to the Windows PC using a USB data cable.
2. On Android, open **Settings → Network & Internet → Hotspot & Tethering**.
3. Toggle **USB Tethering** to **ON**.
   The phone and Windows PC may receive different addresses on a device-specific subnet; the app displays the detected phone interface IP and subnet. Do not assume a fixed IP range.

### 2. Prepare TechDesk DZ on Windows
1. Open **TechDesk DZ** on your Windows PC.
2. Navigate to **Settings → Scanner Android USB**.
3. Click **Generate Code** to display the desktop pairing QR code on screen.

### 3. Pair TechDesk Scanner
1. Open **TechDesk Scanner** on your phone.
2. Tap **Scan Pairing QR** and point the camera at the desktop screen.
3. The app verifies the installation reference and establishes pairing automatically.
4. *(Fallback)* If camera QR scanning is unavailable, select **Manual Pairing** and enter the Reference, Code, the **Windows PC USB-tether IPv4 address** (not the phone address), and Port directly. The default port is `3000`.

### 4. Start Scanning Products
1. Tap **Start Scanning**.
2. Align any product barcode (e.g. EAN-13 retail item) in the viewfinder.
3. The app beeps and vibrates upon detection, transmitting the code immediately to TechDesk DZ.

---

## Building the APK

### Local Build (Command Line)
```bash
# Build the debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions CI/CD
Every push or pull request to `main` automatically triggers `.github/workflows/build-apk.yml`, compiling the APK and publishing it as a downloadable GitHub Actions artifact named `TechDesk-Scanner-debug-apk`.

---

## Protocol Specification Summary

| Endpoint | Method | Headers | Description |
|---|---|---|---|
| `/api/usb-scanner/identify` | GET | None | Verifies server identity (`appName: "TechDesk DZ"`) and matching installation reference. |
| `/api/usb-scanner/pair` | POST | None | Exchanges `installationReference` & `pairingCode` for `deviceId` & `deviceToken`. |
| `/api/usb-scanner/heartbeat` | POST | `Authorization: Bearer <token>` | Maintains session and receives `activeContext` (`pos`, `products`, `auto`). |
| `/api/usb-scanner/submit` | POST | `Authorization: Bearer <token>` | Submits `{scanId, barcode, format, timestamp}`. Retries reuse the same `scanId`. |

---

## License
Proprietary — TechDesk DZ. All rights reserved.


## USB Discovery Diagnostics

The scanner selects one USB-tether interface and keeps its IPv4 address and prefix length attached to that same interface record. It does not overwrite the selected USB address when it enumerates Wi-Fi or cellular interfaces. The pairing screen displays both the phone-side USB IP/prefix and the calculated subnet CIDR. Automatic discovery scans the usable hosts in that subnet in bounded batches and validates the exact installation reference through `/api/usb-scanner/identify`.

If automatic discovery cannot find the PC, expand **Advanced: Manual PC IP & Port Setup** and enter the Windows PC's USB Ethernet address shown by `ipconfig` (for example, `192.168.107.113`) and the scanner server port shown by TechDesk DZ. The example is illustrative; always use the current address shown on your own PC.

## Verification

Unit tests cover deterministic interface selection when an unrelated cellular address (`10.x.x.x`) is enumerated after or before `rndis0`, and ensure that the selected candidate IP remains associated with the USB interface. The instrumented test suite generates barcode bitmaps locally for EAN-13, EAN-8, UPC-A, UPC-E and Code 128 and feeds them to ML Kit using the same scanner options as the live camera analyzer. These instrumented tests still need to complete in GitHub Actions or on a connected Android test device before they can be considered passed.
