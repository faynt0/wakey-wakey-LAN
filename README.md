# Wakey Wakey LAN

Wakey Wakey LAN is an Android application designed to wake up devices on your local network using Wake-on-LAN (WOL). It is optimized for Android TV (Leanback) and provides a simple way to manage and wake your computers or servers.

(Created for private use so severly untested)

## Features

- **Network Scanning**: Automatically discover devices on your local network using ARP scanning.
- **Device Management**: Save discovered devices with custom names for easy access.
- **One-Click Wake**: Send "Magic Packets" to wake up devices instantly.
- **Shortcuts**: Create shortcuts on your Android home screen or apps like Button Mapper to wake specific devices without opening the full app.
- **Android TV Optimized**: Full support for Leanback UI and D-pad navigation, making it perfect for your TV.

## Getting Started

### Prerequisites
- Devices to be woken must support Wake-on-LAN and have it enabled in BIOS/UEFI and OS settings.
- The Android device must be on the same local network as the target devices.

### Usage
1.  **Scan**: Launch the app and scan for devices on your network.
2.  **Save**: Select a discovered device and save it to your list.
3.  **Wake**: Click on a saved device to send a Wake-on-LAN packet.
4.  **Shortcut**: Use the Android "Create Shortcut" widget or the in-app shortcut creator to add a device to your home screen.

## Permissions
- `INTERNET`: Required to send UDP packets for Wake-on-LAN.
- `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Used for network scanning and determining broadcast addresses.

## Tech Stack
- **Language**: Kotlin
- **UI**: Android Leanback (for TV) and standard RecyclerViews.
- **Storage**: JSON-based local storage for saved devices.
