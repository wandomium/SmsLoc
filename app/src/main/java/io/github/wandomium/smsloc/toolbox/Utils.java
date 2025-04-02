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
package io.github.wandomium.smsloc.toolbox;

import android.content.Context;
import android.os.BatteryManager;

import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Context.BATTERY_SERVICE;

public class Utils
{
    public static final int MIN_2_MS = 60 * 1000;

    public static final String getDateForFilename()
    {
        //TODO-low always use utc format here
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis()));
    }

    public static final String unlistedDisplayName(String addr)
    {
        return String.format("%s (unlisted)", addr);
    }

    public static final String msToStr(Long ms)
    {
        if (ms == null) {
            return "";
        }
        return
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
    }

    public static final String timeToNowHoursStr(long start)
    {
        long dt_s = System.currentTimeMillis() - start;
        dt_s /= 1000;

        int seconds = (int) (dt_s % 60);
        int minutes = (int) ((dt_s / 60) % 60);
        int hours = (int) ((dt_s / (60 * 60)) % 24);
        int days = (int) (dt_s / (60 * 60 * 24));

        if (days > 0) {
            return "> 1 day";
        }
        return String.format("%d h %d min", hours, minutes);
    }

    public static final String timeToNowStr(long start)
    {
        long dt_s = System.currentTimeMillis() - start;
        dt_s /= 1000;

        int seconds = (int) (dt_s % 60);
        int minutes = (int) ((dt_s / 60) % 60);
        int hours = (int) ((dt_s / (60 * 60)) % 24);
        int days = (int) (dt_s / (60 * 60 * 24));

        return String.format("%d d %d h %d min %d sec", days, hours, minutes, seconds);
    }

    public static int getBatteryPrcnt(Context context)
    {
        return
                ((BatteryManager) context.getApplicationContext().getSystemService(BATTERY_SERVICE))
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
