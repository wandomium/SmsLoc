<center>
  
**Download latest**<br>
&nbsp;&nbsp;[![GitHub Stable](
   https://img.shields.io/github/release/wandomium/SmsLoc?label=Stable)](
   https://github.com/wandomium/SmsLoc/releases/latest)
<br>**or**<br>
[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="50">](https://f-droid.org/packages/io.github.wandomium.smsloc)

<br><br>

[![GPL v3](https://img.shields.io/badge/License-GPLv3-blue?style=flat&logo=gnu&logoColor=white)](./LICENSE)
![Downloads](https://img.shields.io/github/downloads/wandomium/SmsLoc/total)

---
</center>

# About

SmsLoc is an application that allows one to query location of friends without the use of mobile data. It uses GPS to retrieve current position and SMS for requests/responses.

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
