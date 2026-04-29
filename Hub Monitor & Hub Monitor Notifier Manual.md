# **Hub Monitor \- User Manual & Setup Guide**

Welcome to the Hub Monitor integration for Hubitat Elevation. This application acts as a "Thick Server," securely bridging your Hubitat smart home data with your physical ESP32-based Hub Monitor dashboard.

This guide will walk you through the initial setup, secure pairing process, and dashboard configuration.

### **📥 Installation (Hubitat Package Manager)**

The easiest and recommended way to install the integration is using the Hubitat Package Manager (HPM). This will automatically install both the "Hub Monitor" App code and the "Hub Monitor Notifier" Driver code to your hub.

1. In your Hubitat web interface, go to **Apps** and open **Hubitat Package Manager**.  
2. Click on **Install** and choose **Search by Keywords**.  
3. Type **Hub Monitor** and click Search.  
4. Select the Hub Monitor package from the list and follow the on-screen prompts to complete the installation.

*Note: HPM downloads the code to your hub, but you must complete a security step before adding the app to your main Apps list. Please proceed to Phase 1\.*

### **⚠️ Phase 1: Prerequisites (Crucial Step)**

Before installing or configuring the Hub Monitor app, you **must** ensure OAuth is enabled. Hub Monitor relies on secure, token-based local communication (Auto-Provisioning) to send data instantly without exposing your hub.

*Note: Because you installed via HPM, OAuth might already be enabled automatically.*

1. In your Hubitat web interface, go to **App Code**.  
2. Open the Hub Monitor app code.  
3. Click the **OAuth** button at the top of the page (or click the three dots at the top right corner to reveal the OAuth button).  
4. **Verify Status:** If OAuth is already enabled, **do not** disable it or generate new keys. Simply exit the page.  
5. If it is not enabled, click **Enable OAuth in App** and then click **Update** (or Save). *(Note: You do not need to copy the Client ID or Secret, the app will handle everything automatically).*

### **🔌 Phase 2: Network & Pairing**

Once the app is installed in your "Apps" list, open it to begin configuration.

**1\. Target Device (Network)**

* **ESP32 IP Address:** Enter the IP address currently displayed on your Hub Monitor's physical screen (e.g., 192.168.1.50).  
* **Port:** Leave this as 80 unless you have explicitly configured a different port.

**2\. Security Settings (Pairing)**

To prevent unauthorized devices on your local network from controlling your home, Hub Monitor requires a secure handshake.

1. Look at your physical Hub Monitor screen. It will display a **4-Digit PIN**.  
2. Enter this PIN into the Hubitat app and click outside the box or ENTER.  
3. A new button will appear: **🔐 Pair Device**. Click it.

*What happens next?* Hubitat securely sends its OAuth credentials (Auto-Provisioning) to the monitor. Once paired, the app will say "✅ Device is Securely Paired" and the PIN screen on the monitor will disappear.

### **📱 Phase 3: Dashboard Layout**

The Hub Monitor allows you to configure 4 pages, with 4 tiles (slots) per page.

The first slot’s number is ‘0’ (zero). Please remember that the first slot of page 2 is ‘4’ and so on.

**Virtual Tiles (System Status)**

Before adding physical lights or sensors, you can reserve slots for system-level controls:

* **Slot for HSM Security:** Enter a slot number (e.g., 4\) to display the Hubitat Safety Monitor (Armed/Disarmed) tile.  
* **Slot for Hub Mode:** Enter a slot number (e.g., 5\) to display the current Hub Mode (Day, Evening, Night, etc.).

**Assigning Physical Devices**

Scroll down to the **Dashboard Layout** section. Here you will assign devices to the remaining slots.

🛑 **IMPORTANT \- SUPPORTED DEVICES ONLY:**

Due to Hubitat's interface design, the dropdown menu will show **all** devices in your hub (including virtual buttons, media players, etc.). However, you **must** only select devices that support one of the following capabilities:

* Switch / Dimmer / Lightbulb  
* Temperature Sensor  
* Humidity Sensor  
* Contact Sensor (Door/Window)  
* Motion Sensor  
* Thermostat / TRV (Thermostatic Radiator Valve)  
* Power/Energy Meter  
* Notification (See Phase 5 below)

Selecting an unsupported device (like a lux/leak sensor) will result in an empty or broken tile on the monitor.

**Device Options**

Once a valid device is selected, additional options will appear:

* **Display as Lightbulb icon?:** (Appears only for switches/dimmers). Forces the monitor to draw a lightbulb icon instead of a generic plug/switch icon.  
* **Secure Lock?:** Adds a ‘LOCKED’ text to the tile. The user will be required to **long press the tile** before the device state can be changed. Perfect for garage doors or main security modes.

### **🔄 Phase 4: Firmware Management (Cloud OTA)**

Your Hub Monitor supports Over-The-Air (OTA) updates directly from our cloud servers.

* **Red Dot Alert:** By default, if a new firmware version is detected, a small red dot will appear in the top right corner of your physical monitor.  
* **Checking for Updates:** The app checks for updates automatically every day. You can also click **🔄 Check for Updates** to manually poll the server.  
* **Trigger Cloud OTA Update:** If a new build is available (e.g., Cloud Build 12 vs ESP Build 10), this button will appear. Clicking it commands the ESP32 to download and install the new firmware. The monitor will reboot automatically.

### **🔔 Phase 5: Notification Tile (Hub Monitor Notifier)**

The installation package includes a companion driver called **Hub Monitor Notifier**. This allows you to create a virtual tile on your monitor that displays custom text alerts (e.g., "Washing Machine Done", "Front Door Open").

To set this up, you must first create a virtual device in Hubitat:

1. In your Hubitat web interface, go to **Devices** and click **Add Device**.  
2. Click **Virtual**.  
3. **Select Device Type** \- search for and select **Virtual Omni Sensor**  
4. Enter a **Device Name** (e.g., "Monitor Alert").  
5. Under **Type**, search for and select **Virtual Omni Sensor**, then click **Save Device**.  
6. Once the device is saved press **View device details**.  
7. Select **Device info** tab.  
8. Change the **Type** in drop-down box to **Hub Monitor Notifier**.  
9. Click **Save** to apply the new driver.

**How to Use the Notifier:**

* Assign this newly created device to any slot in the Hub Monitor App (Phase 3).  
* You can now use Hubitat built-in apps (like Rule Machine or the Notifications app) to send custom text to this device.  
* **Clearing the Alert:** To clear the tile simply touch it on the Hub Monitor. From the hub side, simply send the text "clear" to the device, or send a standard Off command to it. Sending a standard On command will display "Alert Active". 

### **🛠️ Maintenance & Fallbacks**

* **Force Full Sync Now:** The app automatically sends a layout sync every 5 minutes as a safety net. If you make changes to the dashboard layout and want to push them immediately, click this button.  
* **Manual Push Credentials:** If your Wi-Fi network experienced a glitch during the initial PIN pairing and the monitor is stuck waiting for configuration, use this fallback button to manually resend the secure tokens.  
* **Unpair Device:** Found in the Security section. Use this if you are selling the monitor or moving it to a new Hubitat hub. It wipes the secure tokens from both the hub and the physical monitor.