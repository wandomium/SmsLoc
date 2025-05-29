# About

SmsLoc is an application that allows one to query location of friends without the use of mobile network. It uses GPS to retrieve current position and SMS for requests/responses.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/io.github.wandomium.smsloc/)

Or download the latest APK from the [Releases Section](https://github.com/wandomium/SmsLoc/releases/latest).

# Interface
The application interface is defined as:

#### Request location
`Loc?`
#### Response
`Loc:LAT,LON,ASL,UTC_S,V_KMH,ACC_M,BAT_PCNT`, where:
- `LAT, LON` are latitude and longitude in degrees rounded up to 4 decimal places.[^1]
- `ASL` or Above Sea Level is the absolute altitude as reported by the GPS in meters
- `UTC_S` is the timestamp of the GPS fix in seconds. The timestamp corresponds to the time of the actual fix, and not when SMS was received.
- `V_KMH` is the speed in kilometers/hour
- `ACC_M` is the accuracy reported by the GPS chip in meters
- `BAT_PCNT` is the battery percentage of the device sending the response

[^1]: 4 decimal places for lat/lon degrees is accurate to 11.1 meters (+/- 5.55 m ) at the equator. The accuracy of longitude increases further away from the equator, latitude accuracy stays the same.
