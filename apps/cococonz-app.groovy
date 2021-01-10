/**
 * ===========================  CoCoConZ - DeCONZ Gateway Integration =========================
 *
 *  Copyright 2021 Robert Morris
 *
 *  DESCRIPTION:
 *  Community-developed DeCONZ integration app for Hubitat, including support for lights,
 *  groups, scenes, and other devices.
 
 *  TO INSTALL:
 *  See documentation on Hubitat Community forum or README.MD file in GitHub repo
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
 *  Last modified: 2021-01-03
 * 
 *  Changelog:
 *  v1.0   - Initial Public Release
 */ 

import groovy.transform.Field

@Field static final String childNamespace = "RMoRobert" // namespace of child device drivers
@Field static final Map driverMap = [
   "extended color light":     "CoCoConz RGBW Bulb",
   "color light":              "CoCoConz RGBW Bulb",  // eventually should make this one RGB
   "color temperature light":  "CoCoConz CT Bulb",
   "dimmable light":           "CoCoConz Dimmable Bulb",
   "on/off light":             "CoCoConz On/Off Plug",
   "on/off plug-in unit":      "CoCoConz On/Off Plug",
   "DEFAULT":                  "CoCoConz RGBW Bulb"
]

definition (
   name: "CoCoConz - DeCONZ Gateway Integration",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Community-created DeCONZ Gateway integration",
   category: "Convenience",
   installOnOpen: true,
   documentationLink: "https://community.hubitat.com/t/COMING-SOON",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

preferences {
   page name: "pageFirstPage"
   page name: "pageIncomplete"
   page name: "pageAddBridge"
   page name: "pageReAddBridge"
   page name: "pageLinkBridge"
   page name: "pageManageBridge"
   page name: "pageSelectLights"
   page name: "pageSelectGroups"
   page name: "pageSelectScenes"
   page name: "pageSelectMotionSensors"
   page name: "pageSelectButtons"
}

void installed() {
   log.info("installed()")
   initialize()
}

void uninstalled() {
   log.info("Uninstalling...")
   if (!(settings['deleteDevicesOnUninstall'] == false)) {
      logDebug("Deleting child devices of this CoCoConz instance...")
      List DNIs = getChildDevices().collect { it.deviceNetworkId }
      logDebug("  Preparing to delete devices with DNIs: $DNIs")
      DNIs.each {
         deleteChildDevice(it)
      }
   }
}

void updated() {
    log.info("Updated with settings: ${settings}")
    initialize()
}

void initialize() {
   log.debug("Initializing...")
   unschedule()
   Integer disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
   Integer pollInt = settings["pollInterval"]?.toInteger()
   // If change polling options in UI, may need to modify some of these cases:
   switch (pollInt ?: 0) {
      case 0:
         logDebug("Polling disabled; not scheduling")
         break
      case 1..59:
         logDebug("Scheduling polling every ${pollInt} seconds")
         schedule("${Math.round(Math.random() * pollInt)}/${pollInt} * * ? * * *", "refreshGateway")
         break
      case 60..259:
         logDebug("Scheduling polling every 1 minute")
         runEvery1Minute("refreshGateway")
         break
      case 300..1800:
         logDebug("Schedulig polling every 5 minutes")
         runEvery5Minutes("refreshGateway")
         break
      default:
         logDebug("Scheduling polling every hour")
         runEvery1Hour("refreshGateway")
   }
}

void debugOff() {
   log.warn("Disabling debug logging")
   app.updateSetting("enableDebug", [value:"false", type:"bool"])
}

def pageFirstPage() {
   state.authRefreshInterval = 5
   state.discoTryCount = 0
   state.authTryCount = 0
   if (app.getInstallationState() == "INCOMPLETE") {
      // Shouldn't happen with installOnOpen: true, but just in case...
      dynamicPage(name: "pageIncomplete", uninstall: true, install: true) {
      section() {
         paragraph("Please press \"Done\" to install CoCoConz.<br>Then, re-open to set up your gateway.")
      }
   }
   } else {
      if (state.bridgeAuthorized) {
         return pageManageBridge()
      }
      else {
         return pageAddBridge()
      }
   }
}

def pageAddBridge() {
   logDebug("pageAddBridge()...")
   Integer discoMaxTries = 60
   if (settings['boolReauthorize']) {
      state.remove('bridgeAuthorized')
      app.removeSetting('boolReauthorize')
   }
   dynamicPage(name: "pageAddBridge", uninstall: true, install: false,
               refreshInterval: ((!(settings['useSSDP']) || selectedDiscoveredBridge) ? null : state.authRefreshInterval),
               nextPage: "pageLinkBridge") {
      section("Add DeCONZ Gateway") {
         input name: "bridgeIP", type: "string", title: "DeCONZ Gateway IP address:", required: true, submitOnChange: true
         input name: "customPort", type: "number", title: "DeCONZ Gateway HTTP Port", defaultValue: 80, required: true, width: 6
         input name: "wsPort", type: "number", title: "DeCONZ Gateway websocket Port", defaultValue: 443, required: true, width: 6
         input name: "protocol", type: "enum", title: "Protocol", defaultValue: "http", options: ["http", "https"], required: true
         if (settings["bridgeIP"] && !state.bridgeLinked || !state.bridgeAuthorized) {
            paragraph "<strong>Initiate authorization on DeCONZ Gateway/Phoscon app,</strong> then press \"Next\" to continue."
         }
         // Hack-y way to hide/show Next button if still waiting:
         if (settings['bridgeIP']) {
            paragraph "<script>\$('button[name=\"_action_next\"]').show()</script>"
         }
         else {
            paragraph "<script>\$('button[name=\"_action_next\"]').hide()</script>"
         }
      }
   }
}

def pageReAddBridge() {
   logDebug("pageReAddBridge()...")
   state.authRefreshInterval = 5
   state.discoTryCount = 0
   state.authTryCount = 0
   state.bridgeLinked = false
   dynamicPage(name: "pageReAddBridge", uninstall: true, install: false, nextPage: pageAddBridge) {  
      section("Options") {
         paragraph "You have chosen to edit the Gateway IP address. The Gateway you choose " +
               "must be the same as the one with which the app and devices were originally configured. To switch to " +
               "a completely different Gateway, install a new instance of the app instead."
         paragraph "If you see \"unauthorized user\" errors, try enabling the option below. In most cases, you can " +
               "continue without this option. In all cases, an existing Gateway device will be either updated to match " +
               "your selection (on the next page) or re-created if it does not exist."
         input name: "boolReauthorize", type: "bool", title: "Request new Gateway username (re-authorize)", defaultValue: false
         paragraph "<strong>Press \"Next\" to continue.</strong>"
      }
   }
}

def pageLinkBridge() {
   logDebug("Beginning gateway link process...")
   String ipAddress = settings["bridgeIP"]
   logDebug("  IP address = ${ipAddress}")
   Integer authMaxTries = 35
   dynamicPage(name: "pageLinkBridge", refreshInterval: state.authRefreshInterval, uninstall: true, install: false,
               nextPage: state.bridgeLinked ? "pageFirstPage" : "pageLinkBridge") {  
      section("Linking DeCONZ Gateway") {
         if (!(state["bridgeAuthorized"])) {
               log.debug "Attempting DeCONZ Gateway authorization; attempt number ${state.authTryCount+1}"
               sendUsernameRequest("http", settings["customPort"] as Integer ?: 80)
               state.authTryCount += 1
               paragraph("Waiting for Gateway to authorize. This page will automatically refresh.")
               if (state.authTryCount > 5 && state.authTryCount < authMaxTries) {
                  String strParagraph = "Still waiting for authorization. Please make sure you initiated third-party API authorization in DeCONZ/Phoscon."
                  if (state.authTryCount > 10) {
                     strParagraph += "Also, verify that your gateway IP address is correct: ${settings['bridgeIP']}"
                  }
                  paragraph(strParagraph)
               }
               if (state.authTryCount >= authMaxTries) {
                  state.remove('authRefreshInterval')
                  paragraph("<b>Authorization timed out.<b> Click/tap \"Next\" to return to the beginning, " + 
                           "check your settings, and try again.")
               }
         }
         else {
               if (!state.bridgeLinked) {
                  log.debug("Gateway authorized. Requesting information from Gateway and creating Gateway device on Hubitat...")
                  paragraph("Gateway authorized. Requesting information from Gateway and creating Gateway device on Hubitat...")
                  if (settings["useSSDP"]) sendGatewayInfoRequest(true)
                  else sendGatewayInfoRequest(true)
               }
               else {
                  logDebug("Gateway already linked; skipping Gateway device creation")
                  if (state.bridgeLinked && state.bridgeAuthorized) {
                     state.remove('authRefreshInterval')
                     paragraph("<b>Your DeCONZ Gateway has been linked!</b> Press \"Next\" to begin adding devices.")
                  }
                  else {
                     paragraph("There was a problem authorizing or linking your gateway. Please start over and try again.")
                  }
               }
         }
         // Hack-y way to hide/show Next button if still waiting:
         if ((state.authTryCount >= authMaxTries) || (state.bridgeLinked && state.bridgeAuthorized)) {
            paragraph "<script>\$('button[name=\"_action_next\"]').show()</script>"
         }
         else {
            paragraph "<script>\$('button[name=\"_action_next\"]').hide()</script>"
         }
      }
   }
}
def pageManageBridge() {
   if (settings["newBulbs"]) {
      logDebug("New lights selected. Creating...")
      createNewSelectedBulbDevices()
   }
   if (settings["newGroups"]) {
      logDebug("New groups selected. Creating...")
      createNewSelectedGroupDevices()
   }
   if (settings["newScenes"]) {
      logDebug("New scenes selected. Creating...")
      createNewSelectedSceneDevices()
   }
   if (settings["newSensors"]) {
      logDebug("New sensors selected. Creating...")
      createNewSelectedSensorDevices()
   }
   if (settings["newButtons"]) {
      logDebug("New buttons selected. Creating...")
      createNewSelectedButtonDevices()
   }
   // General cleanup in case left over from discovery:
   state.remove("authTryCount")
   // More cleanup...
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge != null) {
      bridge.clearBulbsCache()
      bridge.clearGroupsCache()
      bridge.clearScenesCache()
      bridge.clearSensorsCache()
      bridge.clearButtonsCache()
   }
   else {
      log.warn "Bridge device not found!"
   }
   state.remove("sceneFullNames")
   state.remove("addedBulbs")
   state.remove("addedGroups")
   state.remove("addedScenes")
   state.remove("addedSensors")
   state.remove("addedButtons")

   dynamicPage(name: "pageManageBridge", uninstall: true, install: true) {  
      section("Manage DeCONZ Gateway Devices:") {
         href(name: "hrefSelectLights", title: "Select Lights",
               description: "", page: "pageSelectLights")
         href(name: "hrefSelectGroups", title: "Select Groups",
               description: "", page: "pageSelectGroups")
         href(name: "hrefSelectScenes", title: "Select Scenes",
               description: "", page: "pageSelectScenes")
         href(name: "hrefSelectMotionSensors", title: "Select Motion Sensors",
               description: "", page: "pageSelectMotionSensors")
         href(name: "hrefSelectButtons", title: "Select Buttons/Remotes",
               description: "", page: "pageSelectButtons")
      }
      section("Advanced Options", hideable: true, hidden: true) {
         href(name: "hrefReAddBridge", title: "Edit Bridge IP, re-authorize, or re-discover...",
               description: "", page: "pageReAddBridge")
         if (settings["useSSDP"] != false) {
            input(name: "keepSSDP", type: "bool", title: "Remain subscribed to Bridge discovery requests (recommended to keep enabled if Bridge has dynamic IP address)",
               defaultValue: true)
         }
         input(name: "showAllScenes", type: "bool", title: "Allow adding scenes not associated with rooms/zones")
         input(name: "deleteDevicesOnUninstall", type: "bool", title: "Delete devices created by app (gateway, light, group, etc.) if uninstalled", defaultValue: true)
      }        
      section("Other Options:") {
         input(name: "pollInterval", type: "enum", title: "Poll gateway every...",
            options: [0:"Disabled", 10:"10 seconds", 15:"15 seconds", 20:"20 seconds", 30:"30 seconds", 45:"45 seconds", 60:"1 minute (recommended)",
                        300:"5 minutes", 3600:"1 hour"], defaultValue:60)
         input(name: "boolCustomLabel", type: "bool", title: "Customize the name of this CoCoConz app instance", defaultValue: false, submitOnChange: true)
         if (settings["boolCustomLabel"]) label(title: "Custom name for this app", required: false)
         input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
      }
   }
}

def pageSelectLights() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   bridge.getAllBulbs()
   List arrNewBulbs = []
   Map bulbCache = bridge.getAllBulbsCache()
   List<com.hubitat.app.ChildDeviceWrapper> unclaimedBulbs = getChildDevices().findAll { it.deviceNetworkId.startsWith("Cz/${app.id}/Light/") }
   dynamicPage(name: "pageSelectLights", refreshInterval: bulbCache ? 0 : 6, uninstall: true, install: false, nextPage: "pageManageBridge") {
      Map addedBulbs = [:]  // To be populated with lights user has added, matched by DeCONZ ID
      if (!bridge) {
         log.error "No Gateway device found"
         return
      }
      if (bulbCache) {
         bulbCache.each { cachedBulb ->
            com.hubitat.app.ChildDeviceWrapper bulbChild = unclaimedBulbs.find { b -> b.deviceNetworkId == "Cz/${app.id}/Light/${cachedBulb.key}" }
            if (bulbChild) {
               addedBulbs.put(cachedBulb.key, [hubitatName: bulbChild.name, hubitatId: bulbChild.id, hueName: cachedBulb.value?.name])
               unclaimedBulbs.removeElement(bulbChild)
            } else {
               Map newBulb = [:]
               newBulb << [(cachedBulb.key): (cachedBulb.value.name)]
               arrNewBulbs << newBulb
            }
         }
         arrNewBulbs = arrNewBulbs.sort { a, b ->
            // Sort by bulb name (default would be DeCONZ ID)
            a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
         }
         addedBulbs = addedBulbs?.sort { it.value.hubitatName }
      }
      if (!bulbCache) {
         section("Discovering lights. Please wait...") {            
            paragraph("Press \"Refresh\" if you see this message for an extended period of time")
            input(name: "btnBulbRefresh", type: "button", title: "Refresh", submitOnChange: true)
         }
      }
      else {
         section("Manage Lights") {
            input(name: "newBulbs", type: "enum", title: "Select DeCONZ lights to add:",
                  multiple: true, options: arrNewBulbs)
            input(name: "boolAppendBulb", type: "bool", title: "Append \"(DeCONZ Light)\" to Hubitat device name")
            paragraph ""
            paragraph("Previously added lights${addedBulbs ? ' <span style=\"font-style: italic\">(DeCONZ device name in parentheses)</span>' : ''}:")
            if (addedBulbs) {
               StringBuilder bulbText = new StringBuilder()
               bulbText << "<ul>"
               addedBulbs.each {
                  bulbText << "<li><a href=\"/device/edit/${it.value.hubitatId}\" target=\"_blank\">${it.value.hubitatName}</a>"
                  bulbText << " <span style=\"font-style: italic\">(${it.value.hueName ?: 'not found on DeCONZ'})</span></li>"
                  //input(name: "btnRemove_Light_ID", type: "button", title: "Remove", width: 3)
               }
               bulbText << "</ul>"
               paragraph(bulbText.toString())
            }
            else {
               paragraph "<span style=\"font-style: italic\">No added lights found</span>"
            }
            if (unclaimedBulbs) {                  
               paragraph "Hubitat light devices not found on DeCONZ:"
               StringBuilder bulbText = new StringBuilder()
               bulbText << "<ul>"
               unclaimedBulbs.each {                  
                  bulbText << "<li><a href=\"/device/edit/${it.id}\" target=\"_blank\">${it.displayName}</a></li>"
               }
               bulbText << "</ul>"
               paragraph(bulbText.toString())
            }
         }
         section("Rediscover Bulbs") {
               paragraph("If you added new lights to the DeCONZ Gateway and do not see them above, click/tap the button " +
                        "below to retrieve new information from the Bridge.")
               input(name: "btnBulbRefresh", type: "button", title: "Refresh Bulb List", submitOnChange: true)
         }
      }
   }
}

def pageSelectGroups() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   bridge.getAllGroups()
   List arrNewGroups = []
   Map groupCache = bridge.getAllGroupsCache()
   List<com.hubitat.app.ChildDeviceWrapper> unclaimedGroups = getChildDevices().findAll { it.deviceNetworkId.startsWith("Cz/${app.id}/Group/") }
   dynamicPage(name: "pageSelectGroups", refreshInterval: groupCache ? 0 : 6, uninstall: true, install: false, nextPage: "pageManageBridge") {
      Map addedGroups = [:]  // To be populated with groups user has added, matched by DeCONZ ID
      if (!bridge) {
         log.error "No Gateway device found"
         return
      }
      if (groupCache) {
         groupCache.each { cachedGroup ->
            com.hubitat.app.ChildDeviceWrapper groupChild = unclaimedGroups.find { grp -> grp.deviceNetworkId == "Cz/${app.id}/Group/${cachedGroup.key}" }
            if (groupChild) {
               addedGroups.put(cachedGroup.key, [hubitatName: groupChild.name, hubitatId: groupChild.id, hueName: cachedGroup.value?.name])
               unclaimedGroups.removeElement(groupChild)
            }
            else {
               Map newGroup = [:]
               newGroup << [(cachedGroup.key): (cachedGroup.value.name)]
               arrNewGroups << newGroup
            }
         }
         arrNewGroups = arrNewGroups.sort {a, b ->
               // Sort by group name (default would be DeCONZ ID)
               a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
               }
         addedGroups = addedGroups?.sort { it.value.hubitatName }
      }
      if (!groupCache) { 
         section("Discovering groups. Please wait...") {            
               paragraph("Press \"Refresh\" if you see this message for an extended period of time")
               input(name: "btnGroupRefresh", type: "button", title: "Refresh", submitOnChange: true)
         }
      }
      else {
         section("Manage Groups") {
            input(name: "newGroups", type: "enum", title: "Select DeCONZ groups to add:",
                  multiple: true, options: arrNewGroups)
            input(name: "boolAppendGroup", type: "bool", title: "Append \"(DeCONZ Group)\" to Hubitat device name")         
            paragraph ""
            paragraph("Previously added groups${addedGroups ? ' <span style=\"font-style: italic\">(DeCONZ group name in parentheses)</span>' : ''}:")
               if (addedGroups) {
                  StringBuilder grpText = new StringBuilder()
                  grpText << "<ul>"
                  addedGroups.each {
                     grpText << "<li><a href=\"/device/edit/${it.value.hubitatId}\" target=\"_blank\">${it.value.hubitatName}</a>"
                     grpText << " <span style=\"font-style: italic\">(${it.value.hueName ?: 'not found on DeCONZ'})</span></li>"
                     //input(name: "btnRemove_Group_ID", type: "button", title: "Remove", width: 3)
                  }
                  grpText << "</ul>"
                  paragraph(grpText.toString())
               }
               else {
                  paragraph "<span style=\"font-style: italic\">No added groups found</span>"
               }
               if (unclaimedGroups) {                  
                  paragraph "Hubitat group devices not found on DeCONZ:"
                  StringBuilder grpText = new StringBuilder()
                  grpText << "<ul>"
                  unclaimedGroups.each {                  
                     grpText << "<li><a href=\"/device/edit/${it.id}\" target=\"_blank\">${it.displayName}</a></li>"
                  }
                  grpText << "</ul>"
                  paragraph(grpText.toString())
               }
         }
         section("Rediscover Groups") {
            paragraph("If you added new groups to the DeCONZ Gateway and do not see them above, click/tap the button " +
                     "below to retrieve new information from the Bridge.")
            input(name: "btnGroupRefresh", type: "button", title: "Refresh Group List", submitOnChange: true)
         }
      }
   }    
}

def pageSelectScenes() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   bridge.getAllScenes()
   List arrNewScenes = []
   Map sceneCache = bridge.getAllScenesCache()
   List<com.hubitat.app.ChildDeviceWrapper> unclaimedScenes = getChildDevices().findAll { it.deviceNetworkId.startsWith("Cz/${app.id}/Scene/") }
   dynamicPage(name: "pageSelectScenes", refreshInterval: sceneCache ? 0 : 7, uninstall: true, install: false, nextPage: "pageManageBridge") {  
      Map addedScenes = [:]  // To be populated with scenes user has added, matched by DeCONZ ID
      if (!bridge) {
         log.error "No Gateway device found"
         return
      }
      if (sceneCache) {
         state.sceneFullNames = [:]
         sceneCache.each { sc ->
            com.hubitat.app.ChildDeviceWrapper sceneChild = unclaimedScenes.find { scn -> scn.deviceNetworkId == "Cz/${app.id}/Scene/${sc.key}" }
            if (sceneChild) {
               addedScenes.put(sc.key, [hubitatName: sceneChild.name, hubitatId: sceneChild.id, hueName: sc.value?.name])
               unclaimedScenes.removeElement(sceneChild)
            }
            else {
               Map newScene = [:]
               newScene << [(sc.key): (sc.value.name)]
               arrNewScenes << newScene
            }
         }
         arrNewScenes = arrNewScenes.sort {a, b ->
            // Sort by group name (default would be DeCONZ ID)
            a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
         }
         addedScenes = addedScenes.sort { it.value.hubitatName }
      }

      if (!sceneCache) {
         section("Discovering scenes. Please wait...") {            
            paragraph("Press \"Refresh\" if you see this message for an extended period of time")
            input(name: "btnSceneRefresh", type: "button", title: "Refresh", submitOnChange: true)
         }
      }
      else {
         section("Manage Scenes") {
            input(name: "newScenes", type: "enum", title: "Select Hue scenes to add:",
                  multiple: true, options: arrNewScenes)
            paragraph ""
            paragraph("Previously added scenes${addedScenes ? ' <span style=\"font-style: italic\">(DeCONZ scene name [without room/zone] in parentheses)</span>' : ''}:")
            if (addedScenes) {
               StringBuilder scenesText = new StringBuilder()
               scenesText << "<ul>"
               addedScenes.each {
                  scenesText << "<li><a href=\"/device/edit/${it.value.hubitatId}\" target=\"_blank\">${it.value.hubitatName}</a>"
                  scenesText << " <span style=\"font-style: italic\">(${it.value.hueName ?: 'not found on DeCONZ'})</span></li>"
                  //input(name: "btnRemove_Group_ID", type: "button", title: "Remove", width: 3)
               }
               scenesText << "</ul>"
               paragraph(scenesText.toString())
            }
            else {
               paragraph "<span style=\"font-style: italic\">No added scenes found</span>"
            }
            if (unclaimedScenes) {                  
               paragraph "Hubitat scene devices not found on DeCONZ:"
               StringBuilder scenesText = new StringBuilder()
               scenesText << "<ul>"
               unclaimedScenes.each {                  
                  scenesText << "<li><a href=\"/device/edit/${it.id}\" target=\"_blank\">${it.displayName}</a></li>"
               }
               scenesText << "</ul>"
               paragraph(scenesText.toString())
            }
         }
         section("Rediscover Scenes") {
            paragraph("If you added new scenes to the DeCONZ Gateway and do not see them above, if room/zone names are " +
                     "missing from scenes (if assigned to one), or if you changed the \"Allow adding scenes not associated with rooms/zones...\" setting, " +
                     "click/tap the button below to retrieve new information from the Bridge.")
            input(name: "btnSceneRefresh", type: "button", title: "Refresh Scene List", submitOnChange: true)
         }
      }
   }
}

def pageSelectMotionSensors() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   bridge.getAllSensors()
   List arrNewSensors = []
   Map sensorCache = bridge.getAllSensorsCache()
   List<com.hubitat.app.ChildDeviceWrapper> unclaimedSensors = getChildDevices().findAll { it.deviceNetworkId.startsWith("Cz/${app.id}/Sensor/") }
   dynamicPage(name: "pageSelectMotionSensors", refreshInterval: sensorCache ? 0 : 6, uninstall: true, install: false, nextPage: "pageManageBridge") {
      Map addedSensors = [:]  // To be populated with lights user has added, matched by DeCONZ ID
      if (!bridge) {
         log.error "No Gateway device found"
         return
      }
      if (sensorCache) {
         sensorCache.each { cachedSensor ->
            //log.warn "* cached sensor = $cachedSensor"
            com.hubitat.app.ChildDeviceWrapper sensorChild = unclaimedSensors.find { s -> s.deviceNetworkId == "Cz/${app.id}/Sensor/${cachedSensor.key}" }
            if (sensorChild) {
               addedSensors.put(cachedSensor.key, [hubitatName: sensorChild.name, hubitatId: sensorChild.id, hueName: cachedSensor.value?.name])
               unclaimedSensors.removeElement(sensorChild)
            } else {
               Map newSensor = [:]
               newSensor << [(cachedSensor.key): (cachedSensor.value.name)]
               arrNewSensors << newSensor
            }
         }
         arrNewSensors = arrNewSensors.sort { a, b ->
            // Sort by sensor name (default would be DeCONZ ID)
            a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
         }
         addedSensors = addedSensors.sort { it.value.hubitatName }
      }
      if (!sensorCache) {
         section("Discovering sensors. Please wait...") {            
            paragraph("Press \"Refresh\" if you see this message for an extended period of time")
            input(name: "btnSensorRefresh", type: "button", title: "Refresh", submitOnChange: true)
         }
      }
      else {
         section("Manage Sensors") {
            input(name: "newSensors", type: "enum", title: "Select deCONZ sensors to add:",
                  multiple: true, options: arrNewSensors)
            paragraph ""
            paragraph("Previously added sensors${addedSensors ? ' <span style=\"font-style: italic\">(DeCONZ device name in parentheses)</span>' : ''}:")
            if (addedSensors) {
               StringBuilder sensorText = new StringBuilder()
               sensorText << "<ul>"
               addedSensors.each {
                  sensorText << "<li><a href=\"/device/edit/${it.value.hubitatId}\" target=\"_blank\">${it.value.hubitatName}</a>"
                  sensorText << " <span style=\"font-style: italic\">(${it.value.hueName ?: 'not found on DeCONZ'})</span></li>"
                  //input(name: "btnRemove_Sensor_ID", type: "button", title: "Remove", width: 3)
               }
               sensorText << "</ul>"
               paragraph(sensorText.toString())
            }
            else {
               paragraph "<span style=\"font-style: italic\">No added sensors found</span>"
            }
            if (unclaimedSensors) {
               paragraph "Hubitat sensor devices not found on DeCONZ:"
               StringBuilder sensorText = new StringBuilder()
               sensorText << "<ul>"
               unclaimedSensors.each {
                  if (it.getDataValue("type") != "switch") { // exclude buttons
                     sensorText << "<li><a href=\"/device/edit/${it.id}\" target=\"_blank\">${it.displayName}</a></li>"
                  }
               }
               sensorText << "</ul>"
               paragraph(sensorText.toString())
            }
         }
         section("Rediscover Sensors") {
               paragraph("If you added new sensors to the DeCONZ Gateway and do not see them above, click/tap the button " +
                        "below to retrieve new information from the Gateway. Some sensor types may also not be currently " +
                        "supported. Please ask the developer in the forums if you are wondering about specific devices.")
               input(name: "btnSensorRefresh", type: "button", title: "Refresh Sensor List", submitOnChange: true)
         }
      }
   }
}

def pageSelectButtons() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   bridge.getAllButtons()
   List arrNewButtons = []
   Map buttonCache = bridge.getAllButtonsCache()
   List<com.hubitat.app.ChildDeviceWrapper> unclaimedButtons = getChildDevices().findAll { it.deviceNetworkId.startsWith("Cz/${app.id}/Sensor/") }
   dynamicPage(name: "pageSelectButtons", refreshInterval: buttonCache ? 0 : 6, uninstall: true, install: false, nextPage: "pageManageBridge") {
      Map addedButtons = [:]  // To be populated with lights user has added, matched by DeCONZ ID
      if (!bridge) {
         log.error "No Gateway device found"
         return
      }
      if (buttonCache) {
         buttonCache.each { cachedBtn ->
            log.warn "* cached button = $cachedBtn"
            com.hubitat.app.ChildDeviceWrapper btnChild = unclaimedButtons.find { s -> s.deviceNetworkId == "Cz/${app.id}/Sensor/${cachedBtn.key}" }
            if (btnChild) {
               addedButtons.put(cachedBtn.key, [hubitatName: btnChild.name, hubitatId: btnChild.id, hueName: cachedBtn.value?.name])
               unclaimedButtons.removeElement(btnChild)
            } else {
               Map newSensor = [:]
               newSensor << [(cachedBtn.key): (cachedBtn.value.name)]
               arrNewButtons << newSensor
            }
         }
         arrNewButtons = arrNewButtons.sort { a, b ->
            // Sort by sensor name (default would be DeCONZ ID)
            a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
         }
         addedButtons = addedButtons?.sort { it.value.hubitatName }
      }
      if (!buttonCache) {
         section("Discovering button devices. Please wait...") {            
            paragraph("Press \"Refresh\" if you see this message for an extended period of time")
            input(name: "btnSensorRefresh", type: "button", title: "Refresh", submitOnChange: true)
         }
      }
      else {
         section("Manage Buttons") {
            input(name: "newButtons", type: "enum", title: "Select deCONZ button devices to add:",
                  multiple: true, options: arrNewButtons)
            paragraph ""
            paragraph("Previously added buttons${addedButtons ? ' <span style=\"font-style: italic\">(DeCONZ device name in parentheses)</span>' : ''}:")
            if (addedButtons) {
               StringBuilder sensorText = new StringBuilder()
               sensorText << "<ul>"
               addedButtons.each {
                  sensorText << "<li><a href=\"/device/edit/${it.value.hubitatId}\" target=\"_blank\">${it.value.hubitatName}</a>"
                  sensorText << " <span style=\"font-style: italic\">(${it.value.hueName ?: 'not found on DeCONZ'})</span></li>"
                  //input(name: "btnRemove_Sensor_ID", type: "button", title: "Remove", width: 3)
               }
               sensorText << "</ul>"
               paragraph(sensorText.toString())
            }
            else {
               paragraph "<span style=\"font-style: italic\">No added buttons found</span>"
            }
            if (unclaimedButtons) {
               paragraph "Hubitat button devices not found on DeCONZ:"
               StringBuilder sensorText = new StringBuilder()
               sensorText << "<ul>"
               unclaimedButtons.each {
                  if (it.getDataValue("type") == "switch") { // only include button (set for ZHASwitch and ZLLSwitch)
                     sensorText << "<li><a href=\"/device/edit/${it.id}\" target=\"_blank\">${it.displayName}</a></li>"
                  }
               }
               sensorText << "</ul>"
               paragraph(sensorText.toString())
            }
         }
         section("Rediscover Buttons") {
               paragraph("If you added new buttons/remotes/switches to the DeCONZ Gateway and do not see them above, click/tap the button " +
                        "below to retrieve new information from the Gateway. Some sensor types may also not be currently " +
                        "supported. Please ask the developer in the forums if you are wondering about specific devices.")
               input(name: "btnSensorRefresh", type: "button", title: "Refresh Button List", submitOnChange: true)
         }
      }
   }
}

/** Creates new Hubitat devices for new user-selected bulbs on lights-selection
 * page (intended to be called after navigating away/using "Done" from that page)
 */
void createNewSelectedBulbDevices() {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge == null) log.error("Unable to find Bridge device")
   Map bulbCache = bridge?.getAllBulbsCache()
   settings["newBulbs"].each {
      Map b = bulbCache.get(it)
      if (b) {
         try {
            logDebug("Creating new device for DeCONZ light ${it} (${b.name})")
            String devDriver = driverMap[b.type.toLowerCase()] ?: driverMap["DEFAULT"]
            String devDNI = "Cz/${app.id}/Light/${it}"
            Map devProps = [name: (settings["boolAppendBulb"] ? b.name + " (DeCONZ Light)" : b.name)]
            addChildDevice(childNamespace, devDriver, devDNI, devProps)

         } catch (Exception ex) {
            log.error("Unable to create new device for $it: $ex")
         }
      } else {
         log.error("Unable to create new device for bulb $it: ID not found on DeCONZ Gateway")
      }
   }
   bridge.clearBulbsCache()
   bridge.getAllBulbs()
   app.removeSetting("newBulbs")
}

/** Creates new Hubitat devices for new user-selected groups on groups-selection
 * page (intended to be called after navigating away/using "Done" from that page)
 */
void createNewSelectedGroupDevices() {
   String driverName = "CoCoConz Group"
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge == null) log.error("Unable to find Bridge device")
   Map groupCache = bridge?.getAllGroupsCache()
   settings["newGroups"].each {
      def g = groupCache.get(it)
      if (g) {
         try {
            logDebug("Creating new device for CoCoConz group ${it} (${g.name})")
            String devDNI = "Cz/${app.id}/Group/${it}"
            Map devProps = [name: (settings["boolAppendGroup"] ? g.name + " (DeCONZ Group)" : g.name)]
            addChildDevice(childNamespace, driverName, devDNI, devProps)

         }
         catch (Exception ex) {
            log.error("Unable to create new group device for $it: $ex")
         }
      } else {
         log.error("Unable to create new device for group $it: ID not found on DeCONZ Gateway")
      }
   }    
   bridge.clearGroupsCache()
   bridge.getAllGroups()
   bridge.refresh()
   app.removeSetting("newGroups")
}

/** Creates new Hubitat devices for new user-selected scenes on scene-selection
 * page (intended to be called after navigating away/using "Done" from that page)
 */
void createNewSelectedSceneDevices() {
   String driverName = "CoCoConz Scene"
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (!bridge) log.error("Unable to find Gateway device")
   Map sceneCache = bridge?.getAllScenesCache()
   settings["newScenes"].each {
      Map sc = sceneCache.get(it)
      if (sc) {
         try {
               logDebug("Creating new device for DeCONZ group ${it}" +
                        " (state.sceneFullNames?.get(it) ?: sc.name)")
               String devDNI = "Cz/${app.id}/Scene/${it}"
               Map devProps = [name: (state.sceneFullNames?.get(it) ?: sc.name)]
               addChildDevice(childNamespace, driverName, devDNI, devProps)
         } catch (Exception ex) {
               log.error("Unable to create new scene device for $it: $ex")
         }
      } else {
         log.error("Unable to create new scene for scene $it: ID not found on DeCONZ Gateway")
      }
   }
   bridge.clearScenesCache()
   //bridge.getAllScenes()
   app.removeSetting("newScenes")
   state.remove("sceneFullNames")
}

/** Creates new Hubitat devices for new user-selected sensors on sensor-selection
 * page (intended to be called after navigating away/using "Done" from that page)
 */
void createNewSelectedSensorDevices() {
   String driverName = "CoCoConz Motion/Temperature/Lux Sensor"
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge == null) log.error("Unable to find Bridge device")
   Map sensorCache = bridge?.getAllSensorsCache()
   //log.warn "* sensorCache = $sensorCache"
   settings["newSensors"].each {
      def s = sensorCache.get(it)
      if (s) {
         try {
            logDebug("Creating new device for Hue sensor ${it} (${s.name})")
            String devDNI = "Cz/${app.id}/Sensor/${it}"
            Map devProps = [name: s.name]
            com.hubitat.app.ChildDeviceWrapper dev = addChildDevice(childNamespace, driverName, devDNI, devProps)
            dev?.updateDataValue("type", "presence")

         }
         catch (Exception ex) {
            log.error("Unable to create new sensor device for $it: $ex")
         }
      } else {
         log.error("Unable to create new device for sensor $it: MAC not found in DeCONZ Gateway cache")
      }
   }    
   bridge.clearSensorsCache()
   bridge.getAllSensors()
   bridge.refresh()
   app.removeSetting("newSensors")
}

/** Creates new Hubitat devices for new user-selected buttons/remotes on device selection
 * page (intended to be called after navigating away/using "Done" from that page)
 */
void createNewSelectedButtonDevices() {
   String driverName = "CoCoConz Button"
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge == null) log.error("Unable to find Gateway device")
   Map btnCache = bridge?.getAllButtonsCache()
   settings["newButtons"].each {
      def s = btnCache.get(it)
      if (s) {
         try {
            logDebug("Creating new device for Hue button ${it} (${s.name})")
            String devDNI = "Cz/${app.id}/Sensor/${it}"
            Map devProps = [name: s.name]
            com.hubitat.app.ChildDeviceWrapper dev = addChildDevice(childNamespace, driverName, devDNI, devProps)
            dev?.updateDataValue("type", "switch")
            dev?.updateDataValue("modelid", s.modelid)
            dev?.updateDataValue("manufacturername", s.manufacturername)
         }
         catch (Exception ex) {
            log.error("Unable to create new button device for $it: $ex")
         }
      } else {
         log.error("Unable to create new device for button $it: MAC not found in DeCONZ Gateway cache")
      }
   }    
   bridge.clearButtonsCache()
   bridge.getAllButtons()
   bridge.refresh()
   app.removeSetting("newButtons")
}


/** Sends request for username creation to DeCONZ API. Intended to be called after user
 *  presses activation button in Phoscon web UI.
 */
void sendUsernameRequest(String protocol=settings["protocol"] ?: "http", Integer port=settings["customPort"] as Integer ?: 80) {
   logDebug("sendUsernameRequest()...")
   String locationNameNormalized = location.name?.replaceAll("\\P{InBasic_Latin}", "_")
   String userDesc = locationNameNormalized ? "Hubitat CoCoConz#${locationNameNormalized}" : "Hubitat CoCoConz"
   String ip = settings["bridgeIP"]
   Map params = [
      uri:  ip ? """${protocol}://${ip}${port ? ":$port" : ''}""" : getBridgeData().fullHost,
      requestContentType: "application/json",
      contentType: "application/json",
      path: "/api",
      body: [devicetype: userDesc],
      contentType: 'text/xml',
      timeout: 15
   ]
   log.warn params = params
   asynchttpPost("parseUsernameResponse", params, null)
}


/** Callback for sendUsernameRequest. Saves username in app state if Bridge is
 * successfully authorized, or logs error if unable to do so.
 */
void parseUsernameResponse(resp, data) {
   def body = resp.json
   logDebug("Attempting to request DeCONZ Gateway username; result = ${body}")    
   if (body.success != null) {
      if (body.success[0] != null) {
         if (body.success[0].username) {
               state.username = body.success[0].username
               state.bridgeAuthorized = true
         }
      }
   }
   else {
      if (body.error != null) {
         log.warn("  Error from Bridge: ${body.error}")
      }
      else {
         log.error("  Unknown error attempting to authorize DeCONZ Gateway username")
      }
   }
}

/** Requests Bridge info (/description.xml by default) to verify that device is a
 *  DeCONZ Gateway and to retrieve information necessary to either create the Bridge device
 *  (when parsed in parseGatewayInfoResponse if createBridge == true) or to add to the list
 *  of discovered Bridge devices (when createBridge == false). protocol, ip, and port are optional
 *  and will default to getBridgeData() values if not specified
 */
void sendGatewayInfoRequest(Boolean createBridge=true, String protocol=settings["protocol"] ?: "http",
                           String ip = settings["BridgeIP"], Integer port=settings["customPort"] ?: 80,
                           String ssdpPath="/description.xml") {
   log.debug("Sending request for Gateway information")
   String fullHost = ip ? """${protocol ?: "http"}://${ip}${port ? ":$port" : ''}""" : getBridgeData().fullHost
   Map params = [
      uri: fullHost,
      path: ssdpPath,
      contentType: 'text/xml',
      timeout: 15
   ]
   asynchttpGet("parseGatewayInfoResponse", params, [createBridge: createBridge, protocol: protocol ?: "http",
                                                    port: port, ip: (ip ?: state.ipAddress)])
}

/** Parses response from GET of description.xml on the Bridge;
 *  verifies that device is a DeCONZ Gateway (modelName contains "Philips DeCONZ Gateway")
 * and obtains MAC address for use in creating Bridge DNI and device name
 */
private void parseGatewayInfoResponse(resp, data) {
   logDebug("Parsing response from Gateway information request (resp = $resp, data = $data)")
   groovy.util.slurpersupport.GPathResult body
   try {
      body = resp.xml
   }
   catch (Exception ex) {
      logDebug("  Responding device likely not a DeCONZ Gateway: $ex")
      body = null
   }
   if (body?.device?.modelName?.text()?.toLowerCase().contains("philips hue bridge")) {
      String friendlyBridgeName
      String serial = body?.device?.serialNumber?.text().toUpperCase()
      if (serial) {
         logDebug("  Gateway serial parsed as ${serial}; getting additional device info...")
         friendlyBridgeName = body?.device?.friendlyName
         if (friendlyBridgeName) friendlyBridgeName = friendlyBridgeName.substring(0,friendlyBridgeName.lastIndexOf(' ('-1)) // strip out parenthetical IP address
         com.hubitat.app.ChildDeviceWrapper bridgeDevice
         if (data?.createBridge) {
            log.debug("    Creating Gateway device for device with MAC: $serial")
            state.bridgeID = serial.drop(6) // last (12-6=) 6 of MAC
            state.bridgeMAC = serial // full MAC
            try {
               bridgeDevice = getChildDevice("Cz/${app.id}")
               if (!bridgeDevice) bridgeDevice = addChildDevice(childNamespace, "CoCoConz Gateway", "Cz/${app.id}", null,
                                    [label: """CoCoConz Gateway ${state.bridgeID}${friendlyBridgeName ? " ($friendlyBridgeName)" : ""}""", name: "CoCoConz Gateway"])
               if (!bridgeDevice) {
                  log.error "    Gateway device unable to be created or found. Check that driver is installed or for any other errors." 
               }
               if (bridgeDevice) state.bridgeLinked = true
               if (!(settings['boolCustomLabel'])) {
                  app.updateLabel("""CoCoConz DeCONZ Integration (${state.bridgeID}${friendlyBridgeName ? " - $friendlyBridgeName)" : ")"}""")
               }
            }
            catch (IllegalArgumentException e) { // could be bad DNI if already exists
               bridgeDevice = getChildDevice("Cz/${app.id}")
               if (bridgeDevice) {
                  log.error("   Error creating Gateway device: $e")                  
               }
               else {
                  log.error("    Error creating Gateway device. Ensure another device does not already exist for this Gateway. Error: $e")
               }
            }
            catch (Exception e) {
               log.error("    Error creating Gateway device: $e")
            }
            if (!state.bridgeLinked) log.error("    Unable to create Gateway device. Ensure driver is installed, and look for any other errors.")
         }
      } else {
         log.error("Unexpected response received from Gateway (no serial)")
      }
   } else {
      if (data?.createBridge) log.error("No Gateway found at IP address")
      else logDebug("No Gateway found at IP address")
   }
}

private String convertHexToIP(hex) {
	[hubitat.helper.HexUtils.hexStringToInt(hex[0..1]),
    hubitat.helper.HexUtils.hexStringToInt(hex[2..3]),
    hubitat.helper.HexUtils.hexStringToInt(hex[4..5]),
    hubitat.helper.HexUtils.hexStringToInt(hex[6..7])].join(".")
}

/**
 * Returns map containing Gateway username, IP, and full HTTP post/port, intended to be
 * called by child devices so they can send commands to the HTTP Gateway API using info
 */
Map<String,String> getBridgeData() {
   logDebug("Running getBridgeData()...")
   if (!state["username"] || !(settings["bridgeIP"])) log.error "Missing username or IP address for Gateway"
   Integer port = settings["customPort"] ? settings["customPort"] as Integer : 80
   Integer wsPort = settings["wsPort"] ? settings["wsPort"] as Integer : 443
   String protocol = settings["protocol"] ?: "http"
   Map map = [username: state.username, ip: settings["bridgeIP"], fullHost: "${protocol}://${settings['bridgeIP']}:${port}",
      wsHost: "${protocol}://${settings['bridgeIP']}:${wsPort}"]
   return map
}

/**
 * Calls refresh() method on Gateway child, intended to be called at user-specified
 * polling interval
 */
private void refreshGateway() {
   com.hubitat.app.ChildDeviceWrapper gateway = getChildDevice("Cz/${app.id}")
   if (!gateway) {
      log.error "No Gateway device found; could not refresh/poll"
      return
   }
   logDebug("Polling Gateway...")
   gateway.refresh()
}

/**
 * Sets "status" attribute on Bridge child device (intended to be called from child light/group scene devices with
 * successful or unsuccessful commands to Bridge as needed
 * @param setToOnline Sets status to "Online" if true, else to "Offline"
 */
void setBridgeStatus(setToOnline=true) {
   com.hubitat.app.ChildDeviceWrapper bridge = getChildDevice("Cz/${app.id}")
   if (bridge == null) {
      log.error "No Gateway device found; could not set Bridge status"
      return
   }
   String value = setToOnline ? 'Online' : 'Offline'
   logDebug("  Setting Gateway status to ${value}...")
   if (bridge.currentValue("status") != value) bridge.doSendEvent("status", value)
}

void appButtonHandler(btn) {
   switch(btn) {
      case "btnBulbRefresh":
      case "btnGroupRefresh":
      case "btnSceneRefresh":
      case "btnSesnsorRefresh":
         // Just want to resubmit page, so nothing
         break
      default:
         log.warn "Unhandled app button press: $btn"
   }
}

private void logDebug(str) {
   if (!(settings["enableDebug"] == false)) log.debug(str)
}