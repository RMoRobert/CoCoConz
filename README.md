# CoCoConz
CoCoConz: <b>Co</b>mmunity <b>Co</b>llection of <b>DeCONZ</b> Apps and Drivers for Hubitat

(DeCONZ Gateway Integration App for Hubitat)

This is a DeCONZ Gateway integration designed to allow importing of devices from DeCONZ into Hubitat.
It works similarly to the CoCoHue (communinuty Hue Bridge) integration, but it uses DeCONZ websocket
for device status updates instead of relying on polling. Thus, all status updates should be instant.
(Significant portions of the codebase were based on CoCoHue, and you may occasionally see references
to Hue or Hue Bridge instead of DeCONZ or a DeCONZ Gateway throughout the app and code.)

NOTE: This integration is considered pre-release software. Future versions may introduce breaking changes.
Please understand that use is at your own risk.

For discussion and more information, visit the <a href="https://community.hubitat.com/t/COMING SOON">Hubitat Community forum thread</a>.

## To Install (Manual Method)

1. Back up your hub and save a local copy before proceeding.

2. Install the app  from the "apps" folder in this repository into the "Apps Code" section of Hubitat: https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/apps/cococonz-app.groovy

3. Install all necessary drivers from the "drivers" folder in this repository into the "Drivers Code" section of Hubitat. (I'd recommend
just installing them all, but technically all you need is the Gateway driver plus the driver for any device types you plan to use.)
    * Install the Gatway driver code: https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-gateway-driver.groovy
    * Install the bulb, group, scene, plug, and button drivers:
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-rgbw-bulb-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-ct-bulb-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-dimmable-bulb-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-plug-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-group-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-scene-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-motion-temp-lux-driver.groovy
      * https://raw.githubusercontent.com/RMoRobert/CoCoConz/main/drivers/cococonz-button-driver.groovy
      
4. Install an instance of app: go to **Apps > Add User App**, choose **CoCoConz**, and follow the prompts.

## To Install (Hubitat Package Manager/Automatic Method)

CoCoConz will soon also be available on <a href="https://community.hubitat.com/t/beta-hubitat-package-manager/38016">Hubitat
Package Manager</a>, a community app designed to make installing and updating community apps and drivers easier.