/*
 * Hub Monitor Notifier - SeeedStudio D1 Indicator 'Hub Monitor' companion driver.
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

metadata {
    definition (name: "Hub Monitor Notifier", namespace: "community", author: "ultrasmart.pl") {
        capability "Switch" 
        capability "Sensor"
        capability "Notification"

        attribute "text", "string"
    
        command "setText", ["string"]
        command "sync"
    }
}

def deviceNotification(value){ setText(value) }

def sync() {
    sendEvent(name: "sync", value: "requested", isStateChange: true)
}

def setText(val) {
    sendEvent(name: "text", value: val)
    if (val == "clear" || val == "") {
        sendEvent(name: "switch", value: "off")
    } else {
        sendEvent(name: "switch", value: "on")
    }
}

def off() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "text", value: "clear") // This makes the tile invisible!
}

def on() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "text", value: "Alert Active")
}