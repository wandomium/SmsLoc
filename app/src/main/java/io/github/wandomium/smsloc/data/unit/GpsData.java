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
package io.github.wandomium.smsloc.data.unit;

import android.location.Location;

import androidx.annotation.NonNull;

import io.github.wandomium.smsloc.defs.SmsLoc_Common;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.Comparator;

/** Core class for sending sms messages
 */
public final class GpsData
{
    //data elements
    public final double  lat;   //   = -Double.MAX_VALUE;
    public final double  lon;   //   = -Double.MAX_VALUE;
    public final int     alt_m;    //    = -Integer.MAX_VALUE;
    public final long    utc;     // = -Long.MAX_VALUE;
    public final int     v_kmh;    //   = -Integer.MAX_VALUE;
    public final int     acc_m;    //   = -Integer.MAX_VALUE;
    public final int     bat_pct; // = -Integer.MAX_VALUE;

    private GpsData () {
        /* Used by jason in GpsData.class call */
        lat = lon = Integer.MIN_VALUE;
        alt_m = 0; utc = Integer.MIN_VALUE;
        v_kmh = acc_m = bat_pct = Integer.MIN_VALUE;
    }
    public GpsData (
            double lat, double lon, int alt_m, long utc, int v_kmh, int acc_m, int bat_pct
    ) {
        this.lat = lat;
        this.lon = lon;
        this.alt_m = alt_m;
        this.utc = utc;
        this.v_kmh = v_kmh;
        this.acc_m = acc_m;
        this.bat_pct = bat_pct;
    }

    public Float distanceFrom(@NonNull Location loc)
    {
        float[] retval = {0.0f};
        Location.distanceBetween(this.lat, this.lon, loc.getLatitude(), loc.getLongitude(), retval);
        return retval[0];

    }

    public static GpsData fromLocationAndBat(final Location loc, final int bat_pct)
    {
        return loc == null ? new GpsData() : new GpsData(
                loc.getLatitude(),
                loc.getLongitude(),
                (int) loc.getAltitude(),
                loc.getTime(),
                (int) loc.getSpeed(),
                (int) loc.getAccuracy(),
                bat_pct
        );
    }

    public static GpsData fromSmsText(String str) {
        String[] params = str.split(",", 7);
        try {
            return new GpsData(
            /*retval.lat      = */Double.parseDouble(params[0]),
            /*retval.lon      = */Double.parseDouble(params[1]),
            /*retval.alt_m    = */Integer.parseInt(params[2]),
            /*retval.utc_s    = */Long.parseLong(params[3]) * 1000,
            /*retval.v_kmh    = */Integer.parseInt(params[4]),
            /*retval.acc_m    = */Integer.parseInt(params[5]),
            /*retval.bat_pct  = */Integer.parseInt(params[6]));
        }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { return new GpsData(); }
    }

    /* This needs to be kept below 160 characters so it fits
       in one sms */
    public String toSmsText() {

        if (!dataValid()) {
            return SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR;
        }
        try {
            /* A value in decimal degrees to an accuracy of
               4 decimal places is accurate to 11.1 meters (+/- 5.55 m) at the equator.

               the accuracy of the longitude increases the further from the equator you get.
               The accuracy of the latitude part does not increase.

               range: lat +-90, lon +-180

               For UTC we round it to seconds
             */
            return String.format(SmsLoc_Common.LOCALE, "%.4f,%.4f,%d,%d,%d,%d,%d",
                    lat, lon, alt_m, utc / 1000, v_kmh, acc_m, bat_pct);
        }
        catch (NullPointerException e) {return SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR;}
    }

    //todo we probably want all of this for the location to be valid
    public boolean dataValid() {
        if (utc < 0 || acc_m < 0 || alt_m < 0 ) {
            return false;
        }
        if (lat > 90.0 || lat < -90.0) {
            return false;
        }
        if (lon > 180.0 || lon < -180.0) {
            return false;
        }
        return (bat_pct >= 0 && bat_pct <= 100);
    }

    public static GpsData fromJson(String json) {
        try {
            return  (new Gson()).fromJson(json, GpsData.class);
        }
        catch (JsonParseException e)  { return new GpsData(); }
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
        public int compare(GpsData o1, GpsData o2) throws NullPointerException {
            if (o1.utc > o2.utc)  { return mOrder;}
            if (o1.utc < o2.utc)  { return -mOrder;}
            //if (o1.utc == o2.utc) { return 0;  }
            return 0;
        }
    }
}
