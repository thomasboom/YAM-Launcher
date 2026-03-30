# Privacy Policy

## Summary
No data leaves the device by default. Enabling the weather option sends coordinates to Open-Meteo and Open-Meteo may also store your IP address in this case. Additionally, manually searching for locations uses Open-Meteo's geocoding api, which sends the search string to them. 

## Collected data
The application stores data about the state of its preferences on your device's local storage. This is purely to store your launcher settings and is not sent outside of your device.

For example, if you choose to align the clock to the center, this preference is saved so that the clock does not get reset to the left when the application is launched the next time.

By default, no more data is collected, but certain options may result in further data being collected.

### Weather
If you enable weather, the application stores the coordinates that you set for the location. If you have set a location manually, those are the coordinates of that location. If you use GPS location, your GPS coordinates are stored. 

### Permissions

The application uses the following permissions:

- `REQUEST_DELETE_PACKAGES` - Allows uninstallation of apps through the launcher's activity menu.
- `EXPAND_STATUS_BAR` - Allows pulling down the status bar from the app.
- `INTERNET` - Required for API calls to Open-Meteo. Only used if weather integration is enabled.
- `ACCESS_COARSE_LOCATION` - Requested when enabling GPS location. Needs to be allowed to use GPS location.
- `READ_CONTACTS` - Used to find contacts. Only necessary if the contacts menu is enabled. 
- `SET_ALARM` - Used for the clock opening gesture on clicking the time. 
- `QUERY_ALL_PACKAGES` - Used to ensure that the app properly detects all installed apps. 

### Accessibility service
If you enable the Double Tap to Lock Screen preference, the application needs its accessibility service to be enabled. This means giving it "full control of your device" as described by the notification by Android. This permission is exclusively used to lock your phone's screen. The application does not collect any data using the service. 

## Data sent to the developer
No data is sent to the developer.

## Data sent to third parties
By default, the application does not connect to the internet or send any data anywhere. 

However, enabling the weather option requires certain data to be sent to Open-Meteo for purposes of retrieving locations and weather. This data includes the URL used for the request. Open-Meteo may also store your IP address.

The URL includes the coordinates of your weather location. If the location is manually set, the coordinates are of the selected location. If GPS location is used, the coordinates are the set GPS coordinates.

Additionally, searching for a location for the weather manually sends the search query to Open-Meteo as it uses their geocoding api to obtain matching locations.

For information on how this data is handled, refer to [Open-Meteo](https://open-meteo.com/en/terms).
