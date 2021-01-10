/**
 * =============================  CoCoConz Gateway (Driver) ===============================
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
 *  Last modified: 2021-01-09
 *
 *  Changelog:
 *  v1.0    - Initial Release
 */ 

metadata {
   definition (name: "CoCoConz Gateway", namespace: "RMoRobert", author: "Robert Morris", importUrl: "") {
      capability "Actuator"
      capability "Refresh"

      command "connectWebSocket"

      attribute "status", "string"
      attribute "webSocketStatus", "string"

   }
   
   preferences() {
      input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
      input(name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true)
   }   
}

void debugOff() {
   log.warn("Disabling debug logging")
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
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
   log.debug "Initializing"
   int disableTime = 1800
   log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
   if (enableDebug) runIn(disableTime, debugOff)    
}

void parse(String description) {
   if (enableDebug) log.debug "parse(): ${description}"
   //try {
      Map parsedMap = new groovy.json.JsonSlurper().parseText(description)
      if (parsedMap instanceof Map) {
         log.warn parsedMap["attr"]
         log.trace 'a / e'
         log.warn parsedMap["e"]
         //if (parsedMap["attr"] != null) {
            //if (enableDebug) log.debug 'ignoring "attr" message'
         //}
         //else {
            if (parsedMap["e"] == "changed") {
               switch (parsedMap["r"]) {
                  case "lights":
                     String devId = parsedMap.id
                     com.hubitat.app.DeviceWrapper dev = parent.getChildDevice("${device.deviceNetworkId}/Light/${devId}")
                     if (dev != null) {
                        if (parsedMap["attr"] != null) "!!!!!!!!!!!!!!!!!"
                        log.trace " --> ${parsedMap["state"]}"
                        dev.createEventsFromMap(parsedMap["state"])
                     }
                     break
                  case "groups":
                     break
                  case "sensors":
                     String mac = parsedMap.uniqueid?.substring(0,23)
                     com.hubitat.app.DeviceWrapper dev = parent.getChildDevice("${device.deviceNetworkId}/Sensor/${mac}")
                     if (dev != null) {
                        dev.createEventsFromMap(parsedMap["state"])
                        if (parsedMap.config != null && parsedMap.config.battery != null) {
                           dev.createEventsFromMap(["battery": parsedMap.config.battery])
                        }
                     }
                     break
                  case "scenes":

                  default:
                     if (enableDebug) log.debug "unknown resource type ${parsedMap['r']}"
               }
            }
            else {
               if (enableDebug) "ignoring event (e) type of ${parsedMap['e']}"
            }
        // }
      }
      else {
         fsdfsdf(parsedMap)
      }
   //}
   //catch (Exception ex) {
   //   log.error ex
   //}
}

void refresh() {
   if (enableDebug) log.debug "refresh()"
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/",
      contentType: 'application/json',
      timeout: 15
   ]
   try {
      asynchttpGet("parseStates", params)
   } catch (Exception ex) {
      log.error "Error in refresh: $ex"
   }
}

/** Callback method that handles full Bridge refresh. Eventually delegated to individual
 *  methods below.
 */
private void parseStates(resp, data) { 
   if (enableDebug) log.debug "parseStates: States from Bridge received. Now parsing..."
   if (checkIfValidResponse(resp)) {
      parseLightStates(resp.json.lights)
      parseGroupStates(resp.json.groups)
      parseSensorStates(resp.json.sensors)
   }
}

private void parseLightStates(Map lightsJson) { 
   if (enableDebug) log.debug "Parsing light states from Bridge..."
   try {
      lightsJson.each { id, val ->
         com.hubitat.app.DeviceWrapper device = parent.getChildDevice("${device.deviceNetworkId}/Light/${id}")
         if (device) {
            device.createEventsFromMap(val.state, true)
         }
      }
      if (device.currentValue("status") != "Online") doSendEvent("status", "Online")
   }
   catch (Exception ex) {
      log.error "Error parsing light states: ${ex}"
   }
}

private void parseGroupStates(Map groupsJson) {
   if (enableDebug) log.debug "Parsing group states from Bridge..."
   try {
      groupsJson.each { id, val ->
         com.hubitat.app.DeviceWrapper dev = parent.getChildDevice("${device.deviceNetworkId}/Group/${id}")
         if (dev) {
            dev.createEventsFromMap(val.action, true)
            dev.createEventsFromMap(val.state, true)
            dev.setMemberBulbIDs(val.lights)
         }
      }
      Boolean anyOn = groupsJson.any { it.value?.state?.any_on == false }
      com.hubitat.app.DeviceWrapper allLightsDev = parent.getChildDevice("${device.deviceNetworkId}/Group/0")
      if (allLightsDev) {
         allLightsDev.createEventsFromMap(['any_on': anyOn], true)
      }
      
   }
   catch (Exception ex) {
      log.error "Error parsing group states: ${ex}"   
   }
}

private void parseSensorStates(Map sensorsJson) {
   if (enableDebug) log.debug "Parsing sensor states from Bridge..."
   try {
      Map allSensors = [:]
      sensorsJson.each { key, val ->
         if (val.type == "ZLLPresence" || val.type == "ZLLLightLevel" || val.type == "ZLLTemperature" ||
             val.type == "ZHAPresence" || val.type == "ZHALightLevel" || val.type == "ZHATemperature") {
            String mac = val?.uniqueid?.substring(0,23)
            if (mac != null) {
               com.hubitat.app.DeviceWrapper dev = parent.getChildDevice("${device.deviceNetworkId}/Sensor/${mac}")
               if (dev != null) {
                  dev.createEventsFromMap(val.state)
                  // All entries have config.battery, so just picking one to parse here to avoid redundancy:
                  if (val.type == "ZLLPresence" || val.type == "ZHAPresence") dev.createEventsFromMap(["battery": val.config.battery])
               }
            }
         }
      }
   }
   catch (Exception ex) {
      log.error "Error parsing sensor states: ${ex}"   
   }
}

/** Performs basic check on data returned from HTTP response to determine if should be
  * parsed as likely Hue Bridge data or not; returns true (if OK) or logs errors/warnings and
  * returns false if not
  * @param resp The async HTTP response object to examine
  */
private Boolean checkIfValidResponse(resp) {
   if (enableDebug) log.debug "Checking if valid HTTP response/data from Bridge..."
   Boolean isOK = true
   if (resp?.json == null) {
      isOK = false
      if (resp?.headers == null) log.error "Error: HTTP ${resp?.status} when attempting to communicate with Bridge"
      else log.error "No JSON data found in response. ${resp.headers.'Content-Type'} (HTTP ${resp.status})"
      parent.setBridgeStatus(false)
   }
   else if (resp.status < 400 && resp.json) {
      if (resp.json[0]?.error) {
         // Bridge (not HTTP) error (bad username, bad command formatting, etc.):
         isOK = false
         log.warn "Error from gateway: ${resp.json[0].error}"
         // Not setting Bridge to offline when light/scene/group devices end up here because could
         // be old/bad ID and don't want to consider Bridge offline just for that (but also won't set
         // to online because wasn't successful attempt)
      }
      // Otherwise: probably OK (not changing anything because isOK = true already)
   }
   else {
      isOK = false
      log.warn("HTTP status code ${resp.status} from Bridge") 
      parent.setBridgeStatus(false)
   }
   if (isOK) parent.setBridgeStatus(true)
   return isOK
}


// ------------ BULBS ------------

/** Requests list of all bulbs/lights from Hue Bridge; updates
 *  allBulbs in state when finished. Intended to be called
 *  during bulb discovery in app.
 */
void getAllBulbs() {
   if (enableDebug) log.debug "Getting bulb list from Bridge..."
   //clearBulbsCache()
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/lights",
      contentType: "application/json",
      timeout: 15
      ]
   asynchttpGet("parseGetAllBulbsResponse", params)
}

private void parseGetAllBulbsResponse(resp, data) {
   if (enableDebug) log.debug "Parsing in parseGetAllBulbsResponse"
   if (checkIfValidResponse(resp)) {
      try {
         Map bulbs = [:]
         resp.json.each { key, val ->
            bulbs[key] = [name: val.name, type: val.type]
         }
         state.allBulbs = bulbs
         if (enableDebug) log.debug "  All bulbs received from Bridge: $bulbs"
      }
      catch (Exception ex) {
         log.error "Error parsing all bulbs response: $ex"
      }
   }
}

/** Intended to be called from parent app to retrive previously
 *  requested list of bulbs
 */
Map getAllBulbsCache() {
   return state.allBulbs 
}

/** Clears cache of bulb IDs/names/types; useful for parent app to call if trying to ensure
 * not working with old data
 */
void clearBulbsCache() {
   if (enableDebug) log.debug "Running clearBulbsCache..."
   state.remove('allBulbs')
}

// ------------ GROUPS ------------

/** Requests list of all bulbs/lights from Hue Bridge; updates
 *  allBulbs in state when finished. Intended to be called
 *  during bulb discovery in app.
 */
void getAllGroups() {
   if (enableDebug) log.debug "Getting group list from Bridge..."
   //clearGroupsCache()
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/groups",
      contentType: "application/json",
      timeout: 15
      ]
   asynchttpGet("parseGetAllGroupsResponse", params)
}

private void parseGetAllGroupsResponse(resp, data) {
   if (enableDebug) log.debug "parseGetAllGroupsResponse..."
   if (checkIfValidResponse(resp)) {
      try {
         Map groups = [:]
         resp.json.each { key, val ->
            groups[key] = [name: val.name, type: val.type]
         }
         groups[0] = [name: "All DeCONZ Lights", type:  "LightGroup"] // add "all lights" group, ID 0
         state.allGroups = groups
         if (enableDebug) log.debug "  All groups received from Bridge: $groups"
      }
      catch (Exception ex) {
         log.error "Error parsing all groups response: $ex"
      }
   }
}

/** Intended to be called from parent app to retrive previously
 *  requested list of groups
 */
Map getAllGroupsCache() {
   return state.allGroups
}

/** Clears cache of group IDs/names; useful for parent app to call if trying to ensure
 * not working with old data
 */
void clearGroupsCache() {
    if (enableDebug) log.debug "clearGroupsCache()"
    state.remove('allGroups')
}

// ------------ SCENES ------------

/** Requests list of all scenes from Hue Bridge; updates
 *  allScenes in state when finished. Intended to be called
 *  during bulb discovery in app.
 */
void getAllScenes() {
   if (enableDebug) log.debug "getAllScenes()"
   getAllGroups() // so can get room names, etc.
   //clearScenesCache()
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/groups",
      contentType: "application/json",
      timeout: 15
      ]
   asynchttpGet("parseGetAllScenesResponse", params)
}

private void parseGetAllScenesResponse(resp, data) {
   if (enableDebug) log.debug "Parsing in parseGetAllGroupsResponse"
   if (checkIfValidResponse(resp)) {
      try {
         Map scenes = [:]
         resp.json.each { key, val ->
            val.scenes?.each { sc ->
               scenes["${key}/${sc.id}"] = [name: "${val.name} - ${sc.name}", sceneName: "${sc.name}"]
            }
         }
         state.allScenes = scenes
         if (enableDebug) log.debug "  All scenes received from Gateway: $scenes"
      }
      catch (Exception ex) {
         log.error "Error parsing all scenes response: $ex"
      }
   }
}

/** Intended to be called from parent app to retrive previously
 *  requested list of scenes
 */
Map getAllScenesCache() {
   return state.allScenes
}

/** Clears cache of scene IDs/names; useful for parent app to call if trying to ensure
 * not working with old data
 */
void clearScenesCache() {
   if (enableDebug) log.debug "clearScenesCache()"
   state.remove('allScenes')
}

// ------------ SENSORS (Motion/etc.) ------------

/** Requests list of all sensors from Hue Bridge; updates
 *  allSensors in state when finished. Filters down to only Hue
 *  Motion sensors. Intended to be called during sensor discovery in app.
 */
void getAllSensors() {
   if (enableDebug) log.debug "getAllSensors()"
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/sensors",
      contentType: "application/json",
      timeout: 15
      ]
   asynchttpGet("parseGetAllSensorsResponse", params)
}

private void parseGetAllSensorsResponse(resp, data) {
   if (enableDebug) log.debug "Parsing all sensors response..."
   // log.debug "response data = ${resp.data}"
   if (checkIfValidResponse(resp)) {
      try {
         Map allSensors = [:]
         resp.json.each { key, val ->
            if (val.type == "ZLLPresence" || val.type == "ZHAPresence" || val.type == "ZLLLightLevel" || val.type == "ZHALightLevel" ||
               val.type == "ZLLTemperature" || val.type == "ZHATemperature") {
               String mac = val?.uniqueid?.substring(0,23)
               if (mac != null) {
                  if (!(allSensors[mac])) allSensors[mac] = [:]
                  if (allSensors[mac]?.ids) allSensors[mac].ids.add(key)
                  else allSensors[mac].ids = [key]
               }
               if (allSensors[mac].name) {
                  // On Hue, the ZLLPresence endpoint appears to be the one carrying the user-defined name...maybe same here?
                  if (val.type == "ZLLPresence" || val.type == "ZHAPresence") allSensors[mac].name = val.name
               }
               else {
                  //...but get the other names if none has been set, just in case
                  allSensors[mac].name = val.name
               }
            }
         }
         Map hueMotionSensors = [:]
         allSensors.each { key, value ->
            // Hue  Motion sensors should have all three types but let's just use two here for lux/temp-only ones too:
            if (value.ids?.size >= 2) hueMotionSensors << [(key): value]
         }
         state.allSensors = hueMotionSensors
         if (enableDebug) log.debug "  Filtered sensors from Bridge: $hueMotionSensors"
      }
      catch (Exception ex) {
         log.error "Error parsing all sensors response: ${ex}"   
      }
   }
}

/** Intended to be called from parent app to retrive previously
 *  requested list of sensors
 */
Map getAllSensorsCache() {
   return state.allSensors
}

/** Clears cache of sensor IDs/names; useful for parent app to call if trying to ensure
 * not working with old data
 */
void clearSensorsCache() {
   if (enableDebug) log.debug "Running clearSensorsCache..."
   state.remove('allSensors')
}

// ------------ BUTTON/REMOTE "SENSORS" ------------

/** Requests list of all sensors from Hue Bridge; updates
 *  allSensors in state when finished. Filters down to only Hue
 *  remote/button/switch-type sensors. Intended to be called during button/remote discovery in app.
 */
void getAllButtons() {
   if (enableDebug) log.debug "getAllSensors()"
   Map<String,String> data = parent.getBridgeData()
   Map params = [
      uri: data.fullHost,
      path: "/api/${data.username}/sensors",
      contentType: "application/json",
      timeout: 15
      ]
   asynchttpGet("parseGetAllButtonsResponse", params)
}

private void parseGetAllButtonsResponse(resp, data) {
   if (enableDebug) log.debug "Parsing all remote/button sensors response..."
   // log.debug "response data = ${resp.data}"
   if (checkIfValidResponse(resp)) {
      try {
         Map buttonSensors = [:]
         resp.json.each { key, val ->
            if (val.type == "ZHASwitch" || val.type == "ZLLSwitch") {
               String mac = val?.uniqueid?.substring(0,23)
               if (mac != null) {
                  if (!(buttonSensors[mac])) buttonSensors[mac] = [:]
               }
               if (val.name) buttonSensors[mac].name = val.name
               if (val.modelid) buttonSensors[mac].modelid = val.modelid
               if (val.manufacturername) buttonSensors[mac].manufacturername = val.manufacturername
            }
         }
         state.buttonSensors = buttonSensors
         if (enableDebug) log.debug "  Filtered button/remote sensors from Gateway: $buttonSensors"
      }
      catch (Exception ex) {
         log.error "Error parsing all remote/button sensors response: ${ex}"   
      }
   }
}

/** Intended to be called from parent app to retrive previously
 *  requested list of sensors
 */
Map getAllButtonsCache() {
   return state.buttonSensors
}

/** Clears cache of sensor IDs/names; useful for parent app to call if trying to ensure
 * not working with old data
 */
void clearButtonsCache() {
   if (enableDebug) log.debug "Running clearButtonsCache..."
   state.remove('buttonSensors')
}

///////

private void doSendEvent(String eventName, eventValue) {
   //if (enableDebug) log.debug "doSendEvent(eventName: $eventName, eventValue: $eventValue)"
   String descriptionText = "${device.displayName} ${eventName} is ${eventValue}"
   if (enableDesc) log.info descriptionText
   sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText)
}

void connectWebSocket() {
   if (enableDebug) log.debug "connectWebSocket()"
   Map<String,String> data = parent.getBridgeData()
   interfaces.webSocket.connect(data.wsHost)
}

private void webSocketStatus(String msg) {
   doSendEvent("webSocketStatus", msg)
}
