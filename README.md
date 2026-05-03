# Hub Monitor

**SenseCap D1 Indicator integration for the Hubitat Hub**

Hub Monitor is a dedicated SmartApp and Device Driver package for Hubitat Elevation. It provides a secure, event-driven, full-sync bridge between your Hubitat hub and your physical SenseCap D1 Indicator display which must be purchased from hubitat-shop.eu site. Any other hardware (SenseCap D1 Indicator) purchased elsewhere WILL NOT WORK with this software.

---

## ⚠️ Important Prerequisites

Before installing this app, please ensure you have the following ready:
1. **SenseCap D1 Indicator Monitor purchased from hubitat-shop.eu** You must have the official physical hardware powered on and connected to your local Wi-Fi network.
2. **Hubitat Package Manager (HPM):** It is highly recommended to install this via HPM for automatic updates.
3. **OAuth Must Be Enabled:** This app requires secure token communication. **After installation, you must go to the 'App Code' section in your Hubitat dashboard, open the Hub Monitor app code, and make sure `OAuth` is anabled.** If it is, do not changge anything. The pairing process will fail without this.

---

## ⚙️ Installation Instructions

**Method 1: Hubitat Package Manager (Recommended)**
1. Open the Hubitat Package Manager on your hub.
2. Click **Install** and select **Search by Keyword**.
3. Search for **"Hub Monitor"**.
4. Follow the prompts to install. Both the SmartApp and the required Driver will be installed automatically.

**Method 2: Manual Installation**
If you choose not to use HPM, you must manually install both `companion_app_prod.groovy` (as a SmartApp) and the associated driver code (as a Device Driver) via the Hubitat developer tools.

---

## 🚀 Initial Setup & Secure Pairing

This system uses a secure captive pairing process to link your Hubitat hub to your specific display.

1. Once installed, go to your **Apps** tab in Hubitat and add the **Hub Monitor** User App.
2. Enter the **IP Address** of your ESP32 monitor in the Network Configuration section.
3. Look at your physical ESP32 screen. It will display a **4-Digit PIN**.
4. Enter this PIN into the Hub Monitor SmartApp and click **Pair Device**.
5. Once paired securely, you can begin assigning your physical devices to the virtual slots to display on your monitor!

---

## 🛡️ License & Support Policy

**Copyright (c) 2026 ultrasmart.pl. All Rights Reserved.**

This software is provided as a closed, proprietary system strictly for use with official ultrasmart.pl hardware. You are permitted to download and install this code via HPM for personal use. 

**Any unauthorized modification of this source code immediately voids all warranties and eligibility for technical support.** You may not distribute, sub-license, extract, or reuse this code for other projects. 

For the complete legal terms, please read the `LICENSE` file included in this repository.
