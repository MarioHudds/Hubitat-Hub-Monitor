/*
 * Hub Monitor - The Companion App - SeeedStudio D1 Indicator 'Hub Monitor' companion app.
 * Copyright (c) 2026 'ultrasmart Mariusz Jarczak'. All Rights Reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL
 * This software is provided as a closed, commercial system. 
 * You are permitted to use this code directly on your personal Hubitat hub 
 * via HPM installation. 
 * You may not distribute, sub-license, or reuse this code for other projects unless for your personal non commercial use.	
 * * UNAUTHORIZED MODIFICATION OF THIS SOURCE CODE IMMEDIATELY VOIDS ALL 
 * WARRANTIES AND ELIGIBILITY FOR TECHNICAL SUPPORT. 
 */

definition(
    name: "Hub Monitor",
    namespace: "community",
    author: "ultrasmart.pl",
    description: "Companion App for The Hub Monitor",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

// --- NATIVE OAUTH ENDPOINTS ---
mappings {
    path("/devices/:id/:command") { action: [GET: "deviceCommand"] }
    path("/devices/:id/:command/:value") { action: [GET: "deviceCommand"] }
    path("/hsm/:command") { action: [GET: "hsmCommand"] }
    path("/modes/:id") { action: [GET: "modeCommand"] }
    path("/telemetry") { action: [POST: "telemetryHandler"] }
    path("/device_reset") { action: [POST: "handleDeviceReset"] }
}

def deviceCommand() {
    def devId = params.id
    def cmd = params.command
    def val = params.value
    
    if (cmd == "sync") {
        log.info "ESP32 requested boot layout sync."
        if (state.isOffline) {
            log.info "✅ ESP32 connection restored via Boot Handshake."
        }
        state.isOffline = false
        state.recoveryMode = false
        runIn(1, "sendFullSync")
        return [status: "ok"]
    }
    
    def device = null
    for (int i = 0; i < getMaxSlots(); i++) {
        def d = settings["device_${i}"]
        if (d && d.id.toString() == devId.toString()) {
            device = d
            break
        }
    }
    
    if (!device) {
        log.warn "Security Block: Attempt to control unauthorized device ID: ${devId}"
        return [error: "Device not authorized"]
    }
    
    try {
        if (val) {
            if (val.isNumber()) device."$cmd"(val.toInteger())
            else device."$cmd"(val)
        } else {
            device."$cmd"()
        }
        return [status: "ok"]
    } catch (e) {
        log.error "Command failed: ${e}"
        return [error: e.message]
    }
}

def hsmCommand() {
    log.info "ESP32 requested HSM change: ${params.command}"
    sendLocationEvent(name: "hsmSetArm", value: params.command)
    return [status: "ok"]
}

def modeCommand() {
    def modeId = params.id
    log.info "ESP32 requested Mode change to ID: ${modeId}"
    def modes = location.getModes()
    def targetMode = modes.find { it.id.toString() == modeId.toString() }
    
    if (targetMode) {
        setLocationMode(targetMode.name)
        return [status: "ok"]
    }
    return [error: "Mode ID not found"]
}

def telemetryHandler() {
    try {
        def payload = request.JSON
        
        if (payload && payload.event == "boot" && payload.build != null) {
            state.espCurrentBuild = safeToInt(payload.build, 0)
            log.info "ESP32 Boot Telemetry Received. Current Firmware Build: ${state.espCurrentBuild}"
            return [status: "ok", message: "Telemetry saved"]
        } else {
            log.warn "Received malformed telemetry payload: ${payload}"
            return [status: "error", message: "Invalid payload structure"]
        }
    } catch (e) {
        log.error "Telemetry endpoint crashed: ${e.message}"
        render status: 500, contentType: "application/json", data: [error: e.message]
    }
}

def mainPage() {
    if (state.visiblePages == null) state.visiblePages = 1
    
    dynamicPage(name: "mainPage", title: "Hub Monitor Configuration", install: true, uninstall: true) {
        
        // --- PREREQUISITES ---
        section("<b>⚠️ Prerequisites</b>") {
            paragraph "Before starting the configuration, make sure you have clicked the <b>OAuth</b> button in the 'App Code' section of Hubitat and enabled this feature. Secure pairing will not work without it!"
        }

        // --- NETWORK CONFIGURATION ---
        section("<b>Target Device (Network)</b>") {
            input "espIp", "text", title: "ESP32 IP Address", description: "e.g., 192.168.1.50", required: true
            input "espPort", "number", title: "Port", defaultValue: 80, required: true
        }

        // --- SECURITY & PAIRING ---
        section("<b>Security Settings (Pairing)</b>") {
            if (!state.isPaired) {
                paragraph "Your monitor is currently <b>NOT PAIRED</b>. It will reject all commands."
                paragraph "Please enter the 4-digit PIN displayed on the monitor's screen:"
                
                input "pairingPin", "text", title: "4-Digit PIN", required: false, submitOnChange: true
                
                if (settings.pairingPin) {
                    input "btnPairDevice", "button", title: "🔐 Pair Device"
                }
            } else {
                paragraph "✅ <b>Device is Securely Paired.</b>"
                input "btnUnpairDevice", "button", title: "⚠️ Unpair Device (Reset Token)"
            }
        }

        // --- VIRTUAL SLOTS RESERVATION ---
        section("<b>System Status (Virtual Tiles)</b>") {
            paragraph "Reserve slots for system tiles before assigning physical devices."
            input "hsmSlot", "number", title: "Slot for HSM Security (e.g., 4)", required: false, submitOnChange: true
            input "modeSlot", "number", title: "Slot for Hub Mode (e.g., 5)", required: false, submitOnChange: true
        }

        // --- DASHBOARD LAYOUT ---
        section("<b>Dashboard Layout</b>") {
            int maxSlots = state.visiblePages * 4
            
            for (int i = 0; i < maxSlots; i++) {
                String slotTitle = "Slot ${i}"
                
                boolean isHsm = (settings.hsmSlot != null && settings.hsmSlot.toInteger() == i)
                boolean isMode = (settings.modeSlot != null && settings.modeSlot.toInteger() == i)
                
                if (isHsm) {
                    paragraph "<b>${slotTitle}:</b> 🛡️ <i>Reserved for HSM Security</i>"
                    input "secure_hsm", "bool", title: "Secure Lock?", defaultValue: true, submitOnChange: true
                } else if (isMode) {
                    paragraph "<b>${slotTitle}:</b> 🏠 <i>Reserved for Hub Mode</i>"
                    input "secure_mode", "bool", title: "Secure Lock?", defaultValue: false, submitOnChange: true
                } else {
                    input "device_${i}", "capability.*", title: "Device for ${slotTitle}", required: false, submitOnChange: true
                    
                    def dev = settings["device_${i}"]
                    if (dev) {
                        if (dev.hasAttribute("switch")) {
                            input "forceLight_${i}", "bool", title: "Display as Lightbulb icon?", defaultValue: false
                        }
                        input "secure_${i}", "bool", title: "Secure Lock?", defaultValue: false, submitOnChange: true
                    }
                }
            }
            
            input "btnAddPage", "button", title: "Add 4 More Slots (New Page)"
            if (state.visiblePages > 1) {
                input "btnRemPage", "button", title: "Remove Last Page (and Delete Devices)"
            }
        }
        
        // --- FIRMWARE MANAGEMENT ---
        section("<b>Firmware Management - Cloud OTA</b>") {
            input "btnCheckManifest", "button", title: "🔄 Check for Updates"
            
            input "enableRedDot", "bool", title: "Enable Red Dot Alert", defaultValue: true, submitOnChange: true
            paragraph "<i>Display a red dot in the top right corner of the monitor to indicate an update is available.</i>"

            int currentEsp = state.espCurrentBuild ?: 0
            int latestCloud = state.latestCloudBuild ?: 0

            paragraph "<b>Current ESP Build:</b> ${currentEsp} <br><b>Latest Cloud Build:</b> ${latestCloud}"

            if (latestCloud > currentEsp) {
                paragraph "✨ <i>New firmware available!</i>"
                input "btnCloudOta", "button", title: "🚀 Trigger Cloud OTA Update"
            } else if (currentEsp > 0) {
                paragraph "✅ <i>Hub Monitor is up to date.</i>"
            }
        }

        // --- EMERGENCY OVERRIDE ---
        section("<b>Firmware Management - Emergency Override</b>") {
            paragraph "Use this section to manually flash a specific build if the cloud manifest is unavailable."
            input "targetBuild", "number", title: "Target Build Number (Emergency)", defaultValue: 0, required: false, submitOnChange: true
            input "emergencyOtaUrl", "text", title: "Emergency Override OTA URL", description: "Leave blank unless explicitly provided", required: false, submitOnChange: true
            
            if (settings.emergencyOtaUrl) {
                input "btnEmergencyOta", "button", title: "🚨 Execute Emergency OTA"
            }
        }

        // --- MAINTENANCE & FALLBACKS ---
        section("<b>Maintenance</b>") {
            input "btnForceSync", "button", title: "Force Full Sync Now"
            input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: true
            
            paragraph "<b>Fallback Tools:</b>"
            if (settings.espIp) {
                input "btnPushCreds", "button", title: "Manual Push Credentials"
                paragraph "<i>Use only if automatic provisioning failed (Network Glitch).</i>"
            }
        }
    }
}

// --- UI BUTTON HANDLERS ---
def appButtonHandler(btn) {
    if (btn == "btnAddPage") {
        if (state.visiblePages < 4) state.visiblePages++
    }
    
    if (btn == "btnRemPage") {
        int highestPage = state.visiblePages ?: 1
        
        int safeHsm = safeToInt(settings.hsmSlot, -1)
        int safeMode = safeToInt(settings.modeSlot, -1)

        if (safeHsm != -1 && (safeHsm / 4).toInteger() + 1 > highestPage) {
            highestPage = (safeHsm / 4).toInteger() + 1
        }
        if (safeMode != -1 && (safeMode / 4).toInteger() + 1 > highestPage) {
            highestPage = (safeMode / 4).toInteger() + 1
        }

        if (highestPage > 1) {
            int startSlot = (highestPage - 1) * 4
            int endSlot = startSlot + 3
            
            for (int i = startSlot; i <= endSlot; i++) {
                app.removeSetting("device_${i}")
                app.removeSetting("forceLight_${i}")
                app.removeSetting("secure_${i}")
            }
            
            if (safeHsm != -1 && safeHsm >= startSlot && safeHsm <= endSlot) {
                app.removeSetting("hsmSlot")
                app.removeSetting("secure_hsm")
                log.info "Wiped orphaned HSM Virtual Tile from Slot ${safeHsm}"
            }
            if (safeMode != -1 && safeMode >= startSlot && safeMode <= endSlot) {
                app.removeSetting("modeSlot")
                app.removeSetting("secure_mode")
                log.info "Wiped orphaned Mode Virtual Tile from Slot ${safeMode}"
            }
            
            state.visiblePages = highestPage - 1
        }
    }
    
    if (btn == "btnForceSync") {
        log.info "Manual Sync Requested"
        if (state.isOffline) state.recoveryMode = true
        state.isOffline = false
        sendFullSync()
    }
    
    if (btn == "btnPushCreds") {
        pushCredentialsToMonitor()
    }
    
    if (btn == "btnPairDevice") {
        executePairing()
    }
    
    if (btn == "btnUnpairDevice") {
        executeUnpairing()
    }
 
    if (btn == "btnCheckManifest") {
        fetchCloudManifestSync()
    }
    
    if (btn == "btnCloudOta") {
        sendOtaCommand(state.latestCloudBuild, state.latestCloudUrl)
    }
    
    if (btn == "btnEmergencyOta") {
        int buildNum = safeToInt(settings.targetBuild, 0)
        sendOtaCommand(buildNum, settings.emergencyOtaUrl)
    }

    if (btn.toString().startsWith("btnClear_")) {
        String slotIdx = btn.toString().split("_")[1]
        app.removeSetting("device_${slotIdx}")
        app.removeSetting("forceLight_${slotIdx}")
        app.removeSetting("secure_${slotIdx}")
        log.info "Cleared Slot ${slotIdx}"
    }
}

def installed() {
    log.info "Installed"
    app.removeSetting("hsmSlot")
    app.removeSetting("modeSlot")
    initialize()
}

def updated() {
    log.info "Updated"
    unsubscribe()
    initialize()
    
    if (settings.enableRedDot?.toString() == "false") {
        log.info "Red Dot disabled in settings. Sending explicit hide command to ESP32..."
        sendJson([command: "hide_update_alert"])
    }
    
    runIn(2, "sendFullSync") 
}

def initialize() {
    def newMap = [:]

    int maxSlots = getMaxSlots()
    for (int i = 0; i < maxSlots; i++) {
        def dev = settings["device_${i}"]
        if (dev) {
            String devId = dev.id.toString()
            if (!newMap[devId]) {
                newMap[devId] = []
            }
            newMap[devId] << i

            subscribe(dev, "switch", deviceHandler)
            subscribe(dev, "level", deviceHandler)
            subscribe(dev, "temperature", deviceHandler)
            subscribe(dev, "humidity", deviceHandler)
            subscribe(dev, "contact", deviceHandler)
            subscribe(dev, "motion", deviceHandler)
            subscribe(dev, "power", deviceHandler)
            subscribe(dev, "thermostatMode", deviceHandler)
            subscribe(dev, "heatingSetpoint", deviceHandler)
            subscribe(dev, "text", deviceHandler) 
            subscribe(dev, "battery", deviceHandler)
            subscribe(dev, "sync", bootSyncHandler)
        } else {
            app.removeSetting("forceLight_${i}")
            app.removeSetting("secure_${i}")
        }
    }
    
    state.deviceSlotMap = newMap
    
    if (settings.hsmSlot == null) app.removeSetting("secure_hsm")
    if (settings.modeSlot == null) app.removeSetting("secure_mode")

    if (settings.hsmSlot != null) {
        subscribe(location, "hsmStatus", hsmHandler)
    }
    if (settings.modeSlot != null) {
        subscribe(location, "mode", modeHandler)    
    }

    runEvery5Minutes(sendFullSync)
    
    schedule("0 23 15 ? * *", "fetchCloudManifest")
}

// --- EVENT HANDLERS ---
def bootSyncHandler(evt) {
    log.info "Boot Sync Requested by ESP32"
    sendFullSync()
}

def deviceHandler(evt) {
    if (debugMode) log.debug "EVENT IN: ${evt.device} [${evt.name}:${evt.value}] (Event DevID: ${evt.deviceId})"
    
    String eventId = evt.deviceId.toString()
    
    def mappedSlots = state.deviceSlotMap ? state.deviceSlotMap[eventId] : null
    
    if (mappedSlots) {
        mappedSlots.each { slotIdx ->
            def dev = settings["device_${slotIdx}"]
            if (dev) {
                if (debugMode) log.debug "  -> MATCH FOUND in Lookup Map! Slot ${slotIdx} matches device ${dev}"
                sendTileUpdate(slotIdx, dev, evt.name, evt.value)
            }
        }
    } else {
        if (debugMode) log.warn "Event received for device ${eventId}, but it's not mapped to any slot."
    }
}

def hsmHandler(evt) { 
    int slot = safeToInt(settings.hsmSlot, -1)
    if (slot != -1) {
        sendJson([command: "update_tile", slot: slot, attr: "hsm", val: evt.value, is_on: (evt.value == "disarmed" ? 0 : 1)]) 
    }
}

def modeHandler(evt) { 
    int slot = safeToInt(settings.modeSlot, -1)
    
    if (slot != -1) {
        String safeModeName = stripDiacritics(evt.value)
        
        if (!safeModeName) {
            String fallbackId = location.modes.find { it.name == evt.value }?.id?.toString() ?: "X"
            safeModeName = "Mode " + fallbackId
            if (debugMode) log.warn "Mode name was entirely stripped. Using fallback: ${safeModeName}"
        }
        
        sendJson([command: "update_tile", slot: slot, attr: "mode", val: safeModeName, is_on: 1]) 
    }
}

// --- SYNC ENGINE ---

def getDiacriticsMap() {
    return [
        'ą':'a', 'ć':'c', 'ę':'e', 'ł':'l', 'ń':'n', 'ó':'o', 'ś':'s', 'ź':'z', 'ż':'z',
        'Ą':'A', 'Ć':'C', 'Ę':'E', 'Ł':'L', 'Ń':'N', 'Ó':'O', 'Ś':'S', 'Ź':'Z', 'Ż':'Z',
        'ä':'a', 'ö':'o', 'ü':'u', 'ß':'ss', 'Ä':'A', 'Ö':'O', 'Ü':'U',
        'á':'a', 'é':'e', 'í':'i', 'ó':'o', 'ú':'u', 'ñ':'n',
        'Á':'A', 'É':'E', 'Í':'I', 'Ó':'O', 'Ú':'U', 'Ñ':'N',
        'à':'a', 'è':'e', 'ì':'i', 'ò':'o', 'ù':'u', 'ç':'c', 'œ':'oe',
        'À':'A', 'È':'E', 'Ì':'I', 'Ò':'O', 'Ù':'U', 'Ç':'C', 'Œ':'OE',
        'â':'a', 'ê':'e', 'î':'i', 'ô':'o', 'û':'u',
        'Â':'A', 'Ê':'E', 'Î':'I', 'Ô':'O', 'Û':'U',
        'ě':'e', 'š':'s', 'č':'c', 'ř':'r', 'ž':'z', 'ý':'y', 'ů':'u', 'ď':'d', 'ť':'t', 'ň':'n',
        'Ě':'E', 'Š':'S', 'Č':'C', 'Ř':'R', 'Ž':'Z', 'Ý':'Y', 'Ů':'U', 'Ď':'D', 'Ť':'T', 'Ň':'N',
        'å':'a', 'æ':'ae', 'ø':'o', 'Å':'A', 'Æ':'AE', 'Ø':'O'
    ]
}

def stripDiacritics(String str) {
    if (!str) return ""
    
   def res = str
    
    if (res =~ /[^\x00-\x7F]/) {
        def map = getDiacriticsMap()
        map.each { k, v -> 
            if (res.contains(k)) { 
                res = res.replace(k, v) 
            }
        }
    }
    
    res = res.replaceAll("[^\\x00-\\x7F]", "")
    res = res.trim()
    
    if (res.length() > 23) {
        res = res.substring(0, 23) + "..."
    }
    
    return res
}

def sendFullSync() {
    if (debugMode) log.info "Starting Full Sync..."
    
    def modeMap = [:]
    location.modes.each { mode ->
        modeMap[mode.id.toString()] = stripDiacritics(mode.name) 
    }

    int highestSlot = -1
    for (int i = 0; i < getMaxSlots(); i++) {
        if (settings["device_${i}"]) highestSlot = i
    }
    
    int safeHsmSlot = safeToInt(settings.hsmSlot, -1)
    int safeModeSlot = safeToInt(settings.modeSlot, -1)

    if (safeHsmSlot != -1 && safeHsmSlot > highestSlot) {
        highestSlot = safeHsmSlot
    }
    if (safeModeSlot != -1 && safeModeSlot > highestSlot) {
        highestSlot = safeModeSlot
    }
    
    int totalPages = (highestSlot == -1) ? 1 : ((highestSlot / 4).toInteger() + 1)
    def tiles = []
    int totalSlotsToSend = totalPages * 4
    
    for (int i = 0; i < totalSlotsToSend; i++) {
        
        if (safeHsmSlot != -1 && i == safeHsmSlot) {
            String hsmStat = location.hsmStatus?.toString() ?: "disarmed"
            boolean isSecure = settings.secure_hsm ?: false
            tiles << [slot: i, id: 9998, type: "device", label: "Security", hsm: hsmStat, is_on: (hsmStat == "disarmed") ? 0 : 1, is_secure: isSecure ? 1 : 0]
            continue
        }
        if (safeModeSlot != -1 && i == safeModeSlot) {
            boolean isSecure = settings.secure_mode ?: false
            
            String rawModeName = location.mode?.toString() ?: "Unknown"
            String currentModeNameSafe = stripDiacritics(rawModeName)
            
            if (!currentModeNameSafe) {
                String fallbackId = location.modes.find { it.name == rawModeName }?.id?.toString() ?: "X"
                currentModeNameSafe = "Mode " + fallbackId
            }

            tiles << [slot: i, id: 9999, type: "device", label: "Hub Mode", mode: currentModeNameSafe, is_on: 1, is_secure: isSecure ? 1 : 0]
            continue
        }
        
        def dev = settings["device_${i}"]
        if (dev) {
            boolean forceLight = settings["forceLight_${i}"] ?: false
            String iconPref = forceLight ? "light" : "auto"
            
            boolean isSecure = settings["secure_${i}"] ?: false
            def targetTemp = null
            if (dev.hasAttribute("heatingSetpoint") && dev.currentValue("heatingSetpoint") != null) {
                targetTemp = dev.currentValue("heatingSetpoint")
            } else if (dev.hasAttribute("thermostatSetpoint") && dev.currentValue("thermostatSetpoint") != null) {
                targetTemp = dev.currentValue("thermostatSetpoint")
            }

            def rawTile = [
                slot: i,
                id: dev.id,
                type: "device",
                icon: iconPref,
                is_secure: isSecure ? 1 : 0,
                label: stripDiacritics(dev.displayName),
                switch: dev.hasAttribute("switch") ? dev.currentValue("switch") : null,
                level: dev.hasAttribute("level") ? dev.currentValue("level") : null,
                text: dev.hasAttribute("text") ? dev.currentValue("text") : null,
                motion: dev.hasAttribute("motion") ? dev.currentValue("motion") : null,
                contact: dev.hasAttribute("contact") ? dev.currentValue("contact") : null,
                temp: dev.hasAttribute("temperature") ? dev.currentValue("temperature") : null,
                power: dev.hasAttribute("power") ? dev.currentValue("power") : null,
                energy: dev.hasAttribute("energy") ? dev.currentValue("energy") : null,
                thermostatMode: dev.hasAttribute("thermostatMode") ? dev.currentValue("thermostatMode") : null,
                heatingSetpoint: targetTemp,
                humidity: dev.hasAttribute("humidity") ? (dev.currentValue("humidity") in [0, 0.0, "0", "0.0"] ? null : dev.currentValue("humidity")) : null,
                battery: dev.hasAttribute("battery") ? (dev.currentValue("battery") in [0, 0.0, "0", "0.0"] ? null : dev.currentValue("battery")) : null,
                is_on: calculateIsOn(dev)
            ]

            tiles << rawTile.findAll { it.value != null }
            
        } else {
            tiles << [slot: i, type: "empty"]
        }
    }
    
    def payload = [
        command: "init_layout",
        total_pages: totalPages,
        available_modes: modeMap,
        tiles: tiles
    ]
    
    sendJson(payload)
}

// --- PAYLOAD BUILDERS ---
def sendTileUpdate(slotId, device, attrName, attrValue) {
    String payloadAttr = attrName
    def payloadValue = attrValue

    if (payloadValue != null) {
        String valStr = payloadValue.toString()
        
        if (payloadAttr in ["temperature", "humidity", "battery", "level", "heatingSetpoint", "power", "energy"]) {
            valStr = valStr.replaceAll(",", ".")
            valStr = valStr.replaceAll("[^\\d.-]", "")
        }
        
        if (valStr.isNumber()) {
            if (valStr.contains(".")) {
                payloadValue = valStr.toFloat()
            } else {
                payloadValue = valStr.toInteger()
            }
        }
    }

    if (payloadAttr == "humidity" || payloadAttr == "battery") {
        if (payloadValue == 0 || payloadValue == 0.0) {
            if (debugMode) log.warn "Gatekeeper blocked live ghost zero for ${payloadAttr} on Slot ${slotId}"
            return
        }
    }

    def payload = [
        command: "update_tile", 
        slot: slotId,
        attr: payloadAttr,
        val: payloadValue,
        is_on: calculateIsOn(device, attrName, attrValue) 
    ]
    sendJson(payload)
}

// --- LOGIC HELPER ---
def calculateIsOn(device, evtName = null, evtValue = null) {
    if (evtName == "switch") return evtValue == "on" ? 1 : 0
    if (evtName == "contact") return evtValue == "open" ? 1 : 0
    if (evtName == "motion") return evtValue == "active" ? 1 : 0
    if (evtName == "thermostatMode") return evtValue != "off" ? 1 : 0
    
    if (device.hasAttribute("switch") && device.currentValue("switch") == "on") return 1
    if (device.hasAttribute("contact") && device.currentValue("contact") == "open") return 1
    if (device.hasAttribute("motion") && device.currentValue("motion") == "active") return 1
    if (device.hasAttribute("thermostatMode") && device.currentValue("thermostatMode") != "off") return 1
    
    return 0
}

int safeToInt(val, int defaultVal = 0) {
    if (val == null) return defaultVal
    String str = val.toString().trim()
    if (str.isNumber()) {
        return str.contains(".") ? str.toFloat().toInteger() : str.toInteger()
    }
    return defaultVal
}

int getMaxSlots() {
    return (state.visiblePages ?: 4) * 4
}

// --- HTTP SENDER ---
def sendJson(dataMap) {
    if (state.isPaired && state.authToken) {
        dataMap.auth_token = state.authToken
    } else {
        if (debugMode) log.warn "Warning: Sending payload without auth_token. ESP32 will likely reject this with a 401."
        return
    }

    if (state.isOffline) {
        return
    }
    
    def jsonString = groovy.json.JsonOutput.toJson(dataMap)

    def port = settings.espPort ?: 80 
    def params = [
        uri: "http://${settings.espIp}:${port}/api/update",
        requestContentType: "application/json",
        contentType: "text/plain",
        body: jsonString,
        timeout: 5
    ]
    
    try {
        asynchttpPost("sendJsonCallback", params)
    } catch (e) {
        log.error "Failed to send JSON payload: ${e.message}"
    }
}

// --- PROVISIONING LOGIC ---
def pushCredentialsToMonitor() {
    if (!settings.espIp) return
    
    if (!state.accessToken) {
        try {
            createAccessToken()
        } catch (Exception e) {
            log.error "OAuth is not enabled! Please go to your Hubitat 'App Code' page and enable OAuth."
            return
        }
    }

    String hubIp = location.hubs[0].localIP
    String appId = app.id.toString()
    String token = state.accessToken

    def payload = [
        command: "provision",
        hub_ip: hubIp,
        app_id: appId,
        token: token
    ]

    if (state.isPaired && state.authToken) {
        payload.auth_token = state.authToken
    }
    
    def port = settings.espPort ?: 80
    def params = [
        uri: "http://${settings.espIp}:${port}/api/config",
        requestContentType: "application/json",
        contentType: "text/plain",
        body: payload,
        timeout: 5
    ]

    log.info "Pushing Native Credentials to ESP32 at ${settings.espIp}:${port}..."
    try {
        asynchttpPost("provisionCallback", params, null)
    } catch (e) {
        log.error "Failed to send credentials: ${e.message}"
    }
}

def provisionCallback(response, data) {
    if (response.hasError()) {
        log.error "Provisioning failed..."
    } else {
        log.info "Provisioning successful! The ESP32 received the credentials."
        // We add the sync HERE, guaranteeing the ESP32 is ready!
        sendFullSync() 
    }
}

// --- OTA & MANIFEST LOGIC ---
def fetchCloudManifestSync() {
    String targetUrl = "https://f39f0173.hubitat-shop.eu/manifest.json"
    
    def params = [
        uri: targetUrl,
        contentType: "application/json",
        timeout: 5
    ]
    
    log.info "UI Button: Fetching Cloud Manifest synchronously..."
    try {
        httpGet(params) { response ->
            if (response.status == 200 && response.data) {
                def manifest = response.data
                
                if (manifest.latest_build != null) {
                    state.latestCloudBuild = safeToInt(manifest.latest_build, 0)
                    state.latestCloudUrl = manifest.url 
                    
                    log.info "Sync Manifest parsed successfully. Latest Build: ${state.latestCloudBuild}"
                    
                    int currentEsp = state.espCurrentBuild ?: 0
                    if (state.latestCloudBuild > currentEsp) {
                        if (settings.enableRedDot?.toString() != "false") {
                            log.info "Update available! Sending 'Red Dot' alert to ESP32..."
                            sendJson([command: "show_update_alert"])
                        } else {
                            log.info "Update available, but Red Dot alert is disabled in settings."
                        }
                    }
                }
            }
        }
    } catch (e) {
        log.error "Failed to fetch manifest synchronously: ${e.message}"
    }
}

def fetchCloudManifest() {
    String targetUrl = "https://f39f0173.hubitat-shop.eu/manifest.json"
    
    def params = [
        uri: targetUrl,
        contentType: "application/json",
        timeout: 10
    ]
    
    log.info "Fetching Cloud Manifest from: ${targetUrl}"
    try {
        asynchttpGet("manifestCallback", params)
    } catch (e) {
        log.error "Failed to initiate manifest fetch: ${e.message}"
    }
}

def manifestCallback(response, data) {
    if (response.hasError()) {
        log.error "Manifest fetch failed: HTTP ${response.status}"
        return
    }
    
    try {
        def manifest = response.json
        
        if (manifest && manifest.latest_build != null) {
            state.latestCloudBuild = safeToInt(manifest.latest_build, 0)
            state.latestCloudUrl = manifest.url
            
            log.info "Manifest parsed successfully. Latest Build: ${state.latestCloudBuild}"
            
            int currentEsp = state.espCurrentBuild ?: 0
            if (state.latestCloudBuild > currentEsp) {
                if (settings.enableRedDot?.toString() != "false") {
                    log.info "Update available! Sending 'Red Dot' alert to ESP32..."
                    sendJson([command: "show_update_alert"])
                } else {
                    log.info "Update available, but Red Dot alert is disabled in settings."
                }
            }
        } else {
            log.warn "Manifest downloaded but missing 'latest_build' key."
        }
    } catch (e) {
        log.error "Failed to parse manifest JSON: ${e.message}"
    }
}

// --- CAPTIVE PAIRING LOGIC ---
def executePairing() {
    if (!settings.pairingPin || !settings.espIp) {
        log.warn "Pairing Aborted: Missing PIN or ESP IP address."
        return
    }

    String newToken = java.util.UUID.randomUUID().toString()
    
    def payload = [
        pin: settings.pairingPin.toString(),
        token: newToken
    ]

    def port = settings.espPort ?: 80 
    def params = [
        uri: "http://${settings.espIp}:${port}/pair",
        requestContentType: "application/json",
        contentType: "text/plain",
        body: groovy.json.JsonOutput.toJson(payload),
        timeout: 5
    ]

    log.info "Attempting to pair with ESP32... Sending PIN and Token."
    
    boolean pairSuccess = false
    
    try {
        httpPost(params) { response ->
            if (response.status == 200) {
                pairSuccess = true
            }
        }
    } catch (e) {
        if (e.toString().contains("401")) {
            log.error "❌ Pairing failed: Incorrect PIN (401 Unauthorized). Check the ESP32 screen."
        } else {
            log.error "❌ Pairing failed: ESP32 is unreachable or returned an error (${e.message})."
        }
    }
    
    if (pairSuccess) {
        state.authToken = newToken
        state.isPaired = true
        app.removeSetting("pairingPin")
        log.info "✅ Pairing successful! Token securely saved to Hubitat database."
        pushCredentialsToMonitor()
        //runIn(2, "sendFullSync")
        
        runEvery5Minutes("sendFullSync")
        schedule("0 23 15 ? * *", "fetchCloudManifest")
    }
}

def executeUnpairing() {
    if (!state.authToken || !settings.espIp) {
        log.warn "Unpair Aborted: Not currently paired or missing ESP IP."
        return
    }

    def payload = [
        auth_token: state.authToken,
        command: "unpair_device"
    ]

    def port = settings.espPort ?: 80 
    def params = [
        uri: "http://${settings.espIp}:${port}/api/update",
        requestContentType: "application/json",
        contentType: "text/plain",
        body: groovy.json.JsonOutput.toJson(payload),
        timeout: 5
    ]

    log.info "Attempting to safely unpair with ESP32..."
    
    boolean unpairSuccess = false
    
    try {
        httpPost(params) { response ->
            if (response.status == 200) {
                unpairSuccess = true
            }
        }
    } catch (e) {
        log.error "❌ Unpair failed: ESP32 did not confirm (${e.message}). The local token was kept to prevent desync. Try again."
    }
    
    if (unpairSuccess) {
        performLocalUnpairCleanup()
        log.info "ESP32 confirmed unpairing via UI."
    }
}

def sendJsonCallback(response, data) {
    if (response.hasError()) {
        if (response.status == 401) {
            log.error "🚨 ESP32 rejected the token (401 Unauthorized). The device may have been factory reset. Revoking pairing state!"
            performLocalUnpairCleanup()
        } else if (response.status == 408 || response.status == -1) {
            if (!state.isOffline) {
                log.warn "ESP32 is unreachable (${response.status}). Pausing outbound traffic for 2 minutes."
                state.isOffline = true
                state.recoveryMode = false
                runIn(120, "resumeConnection")
            }
        } else {
            log.error "ESP32 returned HTTP error: ${response.status}"
        }
    } else {
        if (state.recoveryMode) {
            state.recoveryMode = false
            log.info "✅ ESP32 connection restored after cooldown."
        }
    }
}

def resumeConnection() {
    log.info "ESP32 cooldown expired. Forcing a full sync to check connection and heal layout..."
    state.isOffline = false
    state.recoveryMode = true
    sendFullSync()
}

def sendOtaCommand(int buildNum, String overrideUrl) {
    if (!settings.espIp) {
        log.warn "OTA Aborted: ESP IP address is not configured."
        return
    }

    if (!state.isPaired || !state.authToken) {
        log.warn "OTA Aborted: Device is not paired. ESP32 will reject the OTA request."
        return
    }

    def payload = [
        auth_token: state.authToken,
        command: "ota_update",
        build: buildNum
    ]

    if (overrideUrl) {
        payload.override_url = overrideUrl
    }

    def port = settings.espPort ?: 80 
    def params = [
        uri: "http://${settings.espIp}:${port}/api/update",
        requestContentType: "application/json",
        contentType: "text/plain", 
        body: groovy.json.JsonOutput.toJson(payload),
        timeout: 10
    ]

    log.info "Sending OTA command (Build: ${buildNum}) to ESP at ${settings.espIp}:${port}..."
    try {
        asynchttpPost("otaCallback", params)
    } catch (e) {
        log.error "Failed to send OTA command: ${e.message}"
    }
}

def otaCallback(response, data) {
    if (response.status == 200) {
        log.info "OTA Update command accepted by ESP. Monitor ESP logs for progress."
    } else {
        log.error "OTA command failed. Status: ${response.status}."
    }
}

// --- CENTRAL CLEANUP ENGINE ---
def performLocalUnpairCleanup() {
    log.info "Performing deep cleanup of local pairing state..."
    
    state.isPaired = false
    state.authToken = null
    
    state.espCurrentBuild = null
    state.latestCloudBuild = null
    state.latestCloudUrl = null
    
    unschedule("sendFullSync")
    unschedule("fetchCloudManifest")
    
    log.info "✅ Deep cleanup complete. System securely reset and resources freed."
}

// --- HARDWARE UNPAIR HANDLER ---
def handleDeviceReset() {
    log.info "Received hardware unpair notification from ESP32..."
    
    def incomingToken = request.JSON?.auth_token
    
    if (!incomingToken || incomingToken != state.authToken) {
        log.warn "Unauthorized reset attempt or token mismatch. Ignored."
        return render(status: 401, data: '{"error": "Invalid token"}', contentType: "application/json")
    }
    
    log.info "Token verified. Performing deep cleanup of device state."
    performLocalUnpairCleanup()
    
    log.info "✅ Hubitat state successfully reset. Awaiting new pairing."
    
    return render(status: 200, data: '{"status": "success"}', contentType: "application/json")
}
