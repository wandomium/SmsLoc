/**
 * This file is part of SmsLoc.
 * <p>
 * SmsLoc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * <p>
 * SmsLoc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SmsLoc. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.wandomium.smsloc.defs;

import java.util.Locale;

import io.github.wandomium.smsloc.BuildConfig;

/**** HOW TO HANDLE REQUEST AND GPS RESPONSE TIME MISMATCH *****
 * Might be better to rely on open requests
 * <p>
 * Getting the GPS time can be rather confusing! To extend discussions in
 * accepted answer, getTime() in onLocationChanged() callback gives different
 * answers depending on how the location (not necessarily GPS) information is
 * retrieved, (based on Nexus 5 testing):
 * <p>
 * (a) If using Google FusedLocationProviderApi (Google Location Services API)
 * then getProvider() will return 'fused' and getTime() will return devices
 * time (System.currentTimeMillis())
 * <p>
 * (b) If using Android LocationManager (Android Location API), then, depending
 * on the phone's 'location' settings and requestLocationUpdates settings
 * (LocationManager.NETWORK_PROVIDER and/or LocationManager.GPS_PROVIDER), getProvider() will return:
 * <p>
 * Either 'network', in which case getTime() will return the devices time (System.currentTimeMillis()).
 * Or, 'gps', in which case getTime will return the GPS (satellite) time.
 * Essentially: 'fused' uses GPS & Wi-Fi/Network, 'network' uses Wi-Fi/Network, 'gps' uses GPS.
 * <p>
 * Thus, to obtain GPS time, use the Android LocationManager with requestLocationUpdates
 * set to LocationManager.GPS_PROVIDER. (Note in this case the getTime()
 * milliseconds part is always 000)
 *
 *
 */

public class SmsLoc_Common
{
    // This locale needs to be used to properly format SMS string (. for decimal separator)
    public static final Locale LOCALE = Locale.US;
    public static class Consts
    {
        public static final String PEOPLE_DATA_FILENAME = BuildConfig.APPLICATION_ID + ".people";
        public static final String DAY_DATA_FILENAME = BuildConfig.APPLICATION_ID + ".data";
        public static final String LOG_FILENAME = BuildConfig.APPLICATION_ID + ".log";

        public static final String GPS_DATA_INVALID_ERR_STR = "GPS Data invalid";
        public static final String NOT_WHITELISTED_ERR_STR = "Not whitelisted";

        public static final String UNAUTHORIZED_ID = "unauthorized";
    }
}
