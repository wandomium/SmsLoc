/**
 * This file is part of SmsLoc.
 *
 * SmsLoc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * SmsLoc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SmsLoc. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.wandomium.smsloc.data.unit;

import android.location.Location;

import io.github.wandomium.smsloc.defs.SmsLoc_Common;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.util.Comparator;

/** Core class for sending sms messages
 */
public final class GpsData
{
    public static class Fields {
        public static final String LAT      = "lat";
        public static final String LON      = "lon";
        public static final String ALT_M    = "alt_m";
        public static final String UTC      = "utc";
        public static final String V_KMH    = "v_kmh";
        public static final String ACC_M    = "acc_m";
        public static final String BAT_PRCT = "bat_prct";
    }
    //data elements
    public final Double  lat;   //   = -Double.MAX_VALUE;
    public final Double  lon;   //   = -Double.MAX_VALUE;
    public final Integer alt_m;    //    = -Integer.MAX_VALUE;
    public final Long    utc;     // = -Long.MAX_VALUE;
    public final Integer v_kmh;    //   = -Integer.MAX_VALUE;
    public final Integer acc_m;    //   = -Integer.MAX_VALUE;
    public final Integer bat_prct; // = -Integer.MAX_VALUE;

    //getters
/*
    public double getLat()   { return lat; }
    public double getLon()   { return lon; }
    public int getAlt_m()    { return alt_m; }
    public long getUtc()     { return utc; }
    public int getV_kmh()    { return v_kmh; }
    public int getAcc_m()    { return acc_m; }
    public int getBat_prct() { return bat_prct; }
*/
    private GpsData () {
        lat = lon = null;
        alt_m = null; utc = null;
        v_kmh = acc_m = bat_prct = null;

        // Used by jason in GpsData.class call
        // Log.e("GpsData",  "invalid data unit");
    }
    public GpsData (
            Double lat, Double lon, Integer alt_m, Long utc, Integer v_kmh, Integer acc_m, Integer bat_prct
    ) {
        this.lat = lat;
        this.lon = lon;
        this.alt_m = alt_m;
        this.utc = utc;
        this.v_kmh = v_kmh;
        this.acc_m = acc_m;
        this.bat_prct = bat_prct;
    }

    public Float distanceFrom(Location loc)
    {
        try {
            float retval[] = {0.0f};
            Location.distanceBetween(this.lat, this.lon, loc.getLatitude(), loc.getLongitude(), retval);

            return retval[0];
        } catch (NullPointerException e) { return null; }

    }

    public static GpsData fromLocation(final Location loc) {
        return fromLocationAndBat(loc, null);
    }

    public static GpsData fromLocationAndBat(final Location loc, final Integer bat_prct) {
        //todo recheck units in location
        if (loc == null) {
            return new GpsData(); //invalid all the way!!
        }
        return new GpsData(
                loc.getLatitude(),
                loc.getLongitude(),
                (int) loc.getAltitude(),
                loc.getTime(),
                (int) loc.getSpeed(),
                (int) loc.getAccuracy(),
                bat_prct
        );
    }

    public static GpsData fromSmsText(String str) {
        String[] params = str.split(",", 7);

        //TODO maybe having lat lon and utc is good enough for us
        try {
            return new GpsData(
            /*retval.lat      = */Double.valueOf(params[0]),
            /*retval.lon      = */Double.valueOf(params[1]),
            /*retval.alt_m    = */Integer.valueOf(params[2]),
            /*retval.utc_s    = */Long.valueOf(params[3]) * 1000,
            /*retval.v_kmh    = */Integer.valueOf(params[4]),
            /*retval.acc_m    = */Integer.valueOf(params[5]),
            /*retval.bat_prct = */Integer.valueOf(params[6]));
        }
        catch (NumberFormatException e) { return new GpsData(); }
        catch (ArrayIndexOutOfBoundsException e) { return new GpsData(); }
    }

    /* This needs to be kept below 160 characters so it fits
       in one sms */
    public String toSmsText() {

        if (!dataValid()) {
            return SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR;
        }

        Double tmp;
        /* A value in decimal degrees to an accuracy of
           4 decimal places is accurate to 11.1 meters (+/- 5.55 m) at the equator.

           the accuracy of the longitude increases the further from the equator you get.
           The accuracy of the latitude part does not increase.

           range: lat +-90, lon +-180
         */
        tmp = lat * 10000;
        double printLat = tmp.intValue() / 10000.0;

        tmp = lon * 10000;
        double printLon = tmp.intValue() / 10000.0;

        //seconds is good enough
        long printUtc = utc / 1000;

        try {
            StringBuilder sb = new StringBuilder();
                sb.append(printLat).append(",")
                  .append(printLon).append(",")
                  .append(alt_m).append(",")
                  .append(printUtc).append(",")
                  .append(v_kmh).append(",")
                  .append(acc_m).append(",")
                  .append(bat_prct);

            return sb.toString();
        }
        catch (NullPointerException e) {return SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR;}
    }

    //todo we probably want all of this for the location to be valid
    public boolean dataValid() {
        if (lat == null || lon == null || utc == null) {
            return false;
        }
        if (lat > 90.0 || lat < -90.0) {
            return false;
        }
        if (lon > 180.0 || lon < -180.0) {
            return false;
        }
        if (bat_prct != null && (bat_prct < 0 || bat_prct > 100)) {
            return false;
        }
        return true;
    }

    public static GpsData fromJson(String json) {
        try {
            return  (new Gson()).fromJson(json, GpsData.class);
        }
        catch (JsonSyntaxException e)  { return new GpsData(); }
        catch (JsonParseException e)   { return new GpsData(); }
    }

    public String toJson() {
        return (new Gson()).toJson(this);
    }

    public static class SortByGpsUtc implements Comparator<GpsData> {
        protected final int mOrder;
        public SortByGpsUtc(int order) {
            mOrder = (order < 0) ? -1 : 1;
        }

        @Override
        public int compare(GpsData o1, GpsData o2) {
            //if (o1.utc_s == null || o2.utc_s == null) {return 0;}
            if (o1.utc > o2.utc)  { return mOrder;}  //{ return 1;  }
            if (o1.utc == o2.utc) { return 0;  }
            if (o1.utc < o2.utc)  { return -mOrder;} //{ return -1; }
            return 0;
        }
    }
}
