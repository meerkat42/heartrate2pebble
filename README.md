# Heartbeat2Pebble (1.0.0-beta)

A lightweight, open-source Android bridge that streams real-time heart rate data from Bluetooth LE wearables directly to your Pebble smartwatch.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Version](https://img.shields.io/badge/version-1.0.0--beta-orange)

## 📋 Overview
This app is designed for users who want to monitor their heart rate during workouts without constantly checking their phone. It functions as a foreground service that:
1. Connects to a Bluetooth Low Energy (BLE) Heart Rate Strap.
2. Displays live data on your Android device.
3. Forwards heart rate packets to your Pebble watch via the Pebble app.

## 📸 Screenshots

|   Android Interface | Pebble Watchface |
| :---: | :---: |
   
| ![Android Main Screen](./screenshots/android_main.png) | ![Pebble Display](./screenshots/pebble_watch_hr.jpg) |

| *Heartrate measurement.* | *BPM forwarded to Pebble watch.* |

## 🔒 Privacy & Safety
* **Strictly Local:** This app does not collect, store, or transmit your health data to any servers. It operates entirely on your device.
* **Open Source:** The code is transparent and open for audit to ensure your privacy.
* **Disclaimer:** This is a recreational tool, NOT a medical device. See [DISCLAIMER.md](./DISCLAIMER.md) for full details.

## 🛠 Features
- **Foreground Service:** Reliable data streaming even when the app is in the background or the screen is off.
- **Auto-Scan:** Once the wearable is paired, automatically looks for your heart rate strap on startup.
- **Disconnect Alerts:** Configurable watch alerts trigger when connection is interrupted.
- **Pebble Integration:** Optimized for low-latency updates to your wrist.
- **Battery Efficient:** Built to minimize radio wake-ups.


## 🚀 Getting Started

### Prerequisites
- **Android Device:** (Android 12+ recommended).
- **Wearable:** Any standard BLE Heart Rate Monitor (Polar, Garmin, Wahoo, etc.).
- **Watch:** A Pebble Smartwatch connected via the Pebble App.

### Installation
1. Download the latest APK from the [Releases](link-to-your-github-releases) page.
2. Grant **Bluetooth** and **Nearby Devices** permissions.
3. Grant **Notification** permissions (required for the background service).
4. Pair your Heart Rate strap within the app.

## 📂 Project Structure
- `/android`: The Android Studio project (Kotlin, Jetpack Compose).
- `/watch`: The C Pebble watchapp source code.

## ⚖️ License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](./LICENSE) file for details.

## 🤝 Contributing
Since this is a beta release, feedback is welcome! 
1. Fork the repo.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---
*Created by meerkat*