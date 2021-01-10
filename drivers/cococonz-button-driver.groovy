/*
 * =============================  CoCoConz Button (Driver) ===============================
 *
 *  Copyright 2020-2021 Robert Morris
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  Last modified: 2021-01-10
 * 
 *  Changelog:
 *  v1.0    - Initial release
 */
 
metadata {
   definition (name: "CoCoConz Button", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-button-driver.groovy") {
      capability "Sensor"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "Battery"

      command "push", [[name:"NUMBER", type: "NUMBER", description: "Button number" ]]
      command "hold", [[name:"NUMBER", type: "NUMBER", description: "Button number" ]]
      command "release", [[name:"NUMBER", type: "NUMBER", description: "Button number" ]]
   }

   preferences {
      input name: "numButtons", type: "enum", title: "Number of buttons", options: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void installed() {
   log.debug "Installed..."
   runIn(2, "setDefaultValues")
   initialize()
}

void updated() {
   log.debug "Updated..."
   if (settings["numButtons"]) doSendEvent("numberOfButtons", settings["numButtons"])
   initialize()
}

void initialize() {
   log.debug "Initializing..."
   int disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
}

void setDefaultValues() {
   log.trace "*** ${device.getDataValue('modelid')}"
   if (device.getDataValue("modelid")) {
      Integer numberOfButtons = 1
      switch (device.getDataValue("modelid")) {
         case "TRADFRI remote control":
            numberOfButtons = 5
            break
         // TODO: Add more as needed (can also check manufacturername if need to distinguish)
         default:
            break
      }
   device.updateSetting("numButtons", [value: numberOfButtons, type:"enum"])
   doSendEvent("numberOfButtons", numberOfButtons)
   }
}

void refresh() {
   log.warn "Refresh CoCoConz Gateway device instead of individual device to update (all) bulbs/groups/sensors if needed, but in most cases websocket will update in real time regardless."
}

void debugOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

// Probably won't happen but...
void parse(String description) {
   log.warn "Running unimplemented parse for: '${description}'"
}

void push(Number buttonNumber) {
   doSendEvent("pushed", buttonNumber)
}

void hold(Number buttonNumber) {
   doSendEvent("held", buttonNumber)
}

void release(Number buttonNumber) {
   doSendEvent("released", buttonNumber)
}

/**
 * Iterates over Hue sensor state commands/states in Hue format (e.g., ["lightlevel": 25000]) and does
 * a sendEvent for each relevant attribute; for sensors, intended to be called
 * to parse/update sensor state on Hubitat based on data received from Bridge
 * @param bridgeCmd Map of sensor states from Bridge (for lights, this could be either a command to or response from)
 */
void createEventsFromMap(Map bridgeCmd) {
   if (!bridgeCmd) {
      if (enableDebug) log.debug "createEventsFromMap called but map empty; exiting"
      return
   }
   if (enableDebug) log.debug "Preparing to create events from map: ${bridgeCmd}"
   String eventName, eventUnit, descriptionText
   Integer eventValue
   bridgeCmd.each {
      switch (it.key) {
         case "buttonevent":
            // Seems to be pretty consistent (e.g, 3002 = "button 3 pushed"), but may need adjustments for some devices:
            eventValue = (it.value as Integer)/1000
            Integer btnAct = ((it.value as Integer) - (eventValue * 1000))
            eventName = "pushed"
            if (btnAct == 1) eventName = "held"
            else if (btnAct == 3) eventName = "released"
            doSendEvent(eventName, eventValue)
            break
         case "battery":
            eventName = "battery"
            eventValue = (it.value != null) ? (it.value as Integer) : 0
            eventUnit = "%"
            if (device.currentValue(eventName) != eventValue) doSendEvent(eventName, eventValue, eventUnit)
            break
         default:
            break
            //log.warn "Unhandled key/value discarded: $it"
      }
   }
}

void doSendEvent(String eventName, eventValue, String eventUnit=null) {
   if (enableDebug) log.debug "Creating event for $eventName..."
   String descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"
   if (settings.enableDesc) log.info(descriptionText)
   if (eventUnit) {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
   } else {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
   }
}
