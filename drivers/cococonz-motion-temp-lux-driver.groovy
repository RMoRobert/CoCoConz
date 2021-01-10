/*
 * =============================  CoCoConz Motion/Temperature/Lux Sensor (Driver) ===============================
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
   definition (name: "CoCoConz Motion/Temperature/Lux Sensor", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-motion-temp-lux-driver.groovy") {
      capability "Sensor"
      capability "Refresh"
      capability "MotionSensor"
      capability "IlluminanceMeasurement"
      capability "TemperatureMeasurement"
      capability "Battery"
   }

   preferences {
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void installed() {
   log.debug "Installed..."
   initialize()
}

void updated() {
   log.debug "Updated..."
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
   def eventValue // could be numeric (lux, temp) or boolean (motion)
   bridgeCmd.each {
      switch (it.key) {
         case "presence":
            eventName = "motion"
            eventValue = it.value ? "active" : "inactive"
            eventUnit = null
            if (device.currentValue(eventName) != eventValue) doSendEvent(eventName, eventValue, eventUnit)
            break
         case "lightlevel":
            eventName = "illuminance"
            eventValue = Math.round(10 ** (((it.value as Integer)-1)/10000))
            eventUnit = "lux"
            if (device.currentValue(eventName) != eventValue) doSendEvent(eventName, eventValue, eventUnit)
            break
         case "temperature":
            eventName = "temperature"
            if (location.temperatureScale == "C") eventValue = ((it.value as BigDecimal)/100.0).setScale(1, java.math.RoundingMode.HALF_UP)
            else eventValue = celsiusToFahrenheit((it.value as BigDecimal)/100.0).setScale(1, java.math.RoundingMode.HALF_UP)
            eventUnit = "°${location.temperatureScale}"
            if (device.currentValue(eventName) != eventValue) doSendEvent(eventName, eventValue, eventUnit)
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
