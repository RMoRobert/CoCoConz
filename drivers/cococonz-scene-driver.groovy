/*
 * =============================  CoCoConz Scene (Driver) ===============================
 *
 *  Copyright 2021 Robert Morris
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
   definition (name: "CoCoConz Scene", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-scene-driver.groovy") {
      capability "Actuator"
      capability "PushableButton"

      command "push", [[name:"NUMBER", type: "NUMBER", description: "Button number (must be 1; will activate scene)" ]]
   }

   preferences {
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void installed() {
   log.debug "Installed..."
   setDefaultAttributeValues()
   initialize()
}

void updated() {
   log.debug "Updated..."
   initialize()
}

void initialize() {
   log.debug "Initializing"
   sendEvent(name: "numberOfButtons", value: 1)				
   int disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
}

void debugOff() {
   log.warn("Disabling debug logging")
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

// Probably won't happen but...
void parse(String description) {
   log.warn("Running unimplemented parse for: '${description}'")
}

/**
 * Parses DeCONZ group ID number out of Hubitat DNI for use with Hue API calls
 * Hubitat DNI is created in format "Cz/AppID/Scenes/GroupId/SceneId", so just
 * looks for number after third "/" character
 */
String getDeconzGroupNumber() {
   return device.deviceNetworkId.split("/")[3]
}

/**
 * Parses DeCONZ scene ID number out of Hubitat DNI for use with Hue API calls
 * Hubitat DNI is created in format "Cz/AppID/Scenes/GroupId/SceneId", so just
 * looks for number after fourth "/" character
 */
String getDeconzSceneNumber() {
   return device.deviceNetworkId.split("/")[4]
}

/** 
  * Parses response from Gateway (or not) after sendBridgeCommand. Pretty generic.
  * @param resp Async HTTP response object
  * @param data Map of commands sent to Gateway if specified to create events from map
  */
void parseSendCommandResponse(resp, data) {
   if (enableDebug) log.debug "parseSendCommandResponse()"
   if (checkIfValidResponse(resp) && data) {
      if (enableDebug) log.debug "  Gateway response valid: ${resp.data}"
      if (data?.attribute) doSendEvent(data.attribute, data.value) // create event if worked ("pushed")
   }
   else {
      if (enableDebug) log.debug "  Invalid Gateway response: ${resp.data}"
   }
}

/** Performs basic check on data returned from HTTP response to determine if should be
  * parsed as likely Hue Bridge data or not; returns true (if OK) or logs errors/warnings and
  * returns false if not
  * @param resp The async HTTP response object to examine
  */
private Boolean checkIfValidResponse(resp) {
   logDebug("Checking if valid HTTP response/data from Bridge... ${resp.status}")
   Boolean isOK = true
   if (resp?.json == null) {
      isOK = false
      if (resp?.headers == null) log.error "Error: HTTP ${resp?.status} when attempting to communicate with Bridge"
      else log.error "No JSON data found in response. ${resp.headers.'Content-Type'} (HTTP ${resp.status})"
      parent.sendBridgeDiscoveryCommandIfSSDPEnabled(true) // maybe IP changed, so attempt rediscovery 
      parent.setBridgeStatus(false)
   }
   else if (resp.status < 400 && resp.json) {
      if (resp.json[0]?.error) {
         // Bridge (not HTTP) error (bad username, bad command formatting, etc.):
         isOK = false
         log.warn "Error from Hue Bridge: ${resp.json[0].error}"
         // Not setting Bridge to offline when light/scene/group devices end up here because could
         // be old/bad ID and don't want to consider Bridge offline just for that (but also won't set
         // to online because wasn't successful attempt)
      }
      // Otherwise: probably OK (not changing anything because isOK = true already)
   }
   else {
      isOK = false
      log.warn("HTTP status code ${resp.status} from Bridge")
      if (resp?.status >= 400) parent.sendBridgeDiscoveryCommandIfSSDPEnabled(true) // maybe IP changed, so attempt rediscovery 
      parent.setBridgeStatus(false)
   }
   if (isOK) parent.setBridgeStatus(true)
   return isOK
}

void push(btnNum) {
   logDebug("Turning on scene...")
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/groups/${getDeconzGroupNumber()}/scenes/${getDeconzSceneNumber()}/recall",
      contentType: 'application/json',
      timeout: 15
      ]
   asynchttpPut("parseSendCommandResponse", params, [attribute: 'pushed', value: '1'])
   logDebug("Parameters sent to Gateway: $params")
}

void doSendEvent(String eventName, eventValue, String eventUnit=null, forceStateChange=false) {
   logDebug("Creating event for $eventName...")
   String descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"
   logDesc(descriptionText)
   // TODO: Map-ify these parameters to make cleaner and less verbose?
   if (eventUnit) {
      if (forceStateChange) {
         sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit, isStateChange: true) 
      }
      else {
         sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit)          
      }
   } else {
      if (forceStateChange) {
         sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, isStateChange: true)  
      }
      else {
         sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText)  
      }
   }
}

/**
 * Sets all group attribute values to something, intended to be called when device initially created to avoid
 * missing attribute values (may cause problems with GH integration, etc. otherwise). Default values are
 * approximately warm white and off.
 */
private void setDefaultAttributeValues() {
   logDebug("Setting scene device states to sensibile default values...")
   event = sendEvent(name: "switch", value: "off", isStateChange: false)
   event = sendEvent(name: "pushed", value: 1, isStateChange: false)
}

/**
 * Returns Hue group ID (as String, since it is likely to be used in DNI check or API call).
 * May return null (if is not GroupScene)
 */
String getGroupID() {
   return state.group
}

void logDebug(str) {
   if (settings.enableDebug) log.debug(str)
}

void logDesc(str) {
   if (settings.enableDesc) log.info(str)
}