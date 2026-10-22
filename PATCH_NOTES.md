# TechDesk Scanner — source corrections

## Changes included

- Fixed the USB interface selection bug: the selected RNDIS interface's address and prefix are now kept together and cannot be overwritten by a later Wi-Fi/cellular adapter during enumeration.
- Added deterministic interface ranking and a pure selection function used by production discovery and regression tests.
- Corrected subnet calculation and IPv4 validation; discovery enumerates the selected subnet in bounded batches and stops when it finds a server matching the exact installation reference.
- Pairing discovery reuses the same diagnostics snapshot, reports the selected interface/subnet, and shows more actionable failure messages.
- Pairing and subnet-probe loading flags are cleared on failures, exceptions and completion to prevent an endless spinner.
- Manual IP setup validates the Windows PC address and port, and the UI now distinguishes the phone-side USB IP/prefix from the network subnet.
- Added regression tests for `rndis0` at `192.168.107.218/24` with an unrelated cellular interface `10.161.62.97`, testing both interface enumeration orders and verifying that `192.168.107.113` is included in scan candidates.
- Centralized ML Kit barcode-format options so production camera scanning and instrumented bitmap tests use the same EAN-13, EAN-8, UPC-A, UPC-E and Code 128 configuration.
- Updated manual-pairing hints so they no longer assume the PC always uses `192.168.42.x`.

## Verification status

- `SubnetDiscoveryManager.kt` compiled with `kotlinc` against small temporary stubs for Android logging and the API model/client.
- A Kotlin regression smoke test passed for both interface enumeration orders, selected address `192.168.107.218`, derived subnet `192.168.107.0/24`, and candidate discovery of `192.168.107.113`.
- The full Gradle test/build was **not run successfully** in this environment: the Gradle wrapper could not download Gradle 9.3.1 because `services.gradle.org` did not resolve (`UnknownHostException`).
- No physical USB-tether hardware test was performed. The full test suite and APK build still need to run in GitHub Actions or an Android SDK environment.
