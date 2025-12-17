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
package io.github.wandomium.smsloc.toolbox;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.content.Context.BATTERY_SERVICE;

//import org.osmdroid.library.BuildConfig;
import io.github.wandomium.smsloc.BuildConfig;
import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;

public class Utils
{
    public static final int MIN_2_MS = 60 * 1000;

    public static String getDateForFilename()
    {
        //TODO-low always use utc format here
        return new SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(new Date(System.currentTimeMillis()));
    }

    public static String getDisplayName(Context context, final String addr) {
        try {
            return PeopleDataFile.getInstance(context).getDataEntry(addr).getDisplayName();
        } catch (Exception ignored) {
            return unlistedDisplayName(addr);
        }
    }
    public static String unlistedDisplayName(String addr)
    {
        return String.format("%s (unlisted)", addr);
    }

    public static String msToStr(Long ms)
    {
        if (ms == null) {
            return "";
        }
        return
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(new Date(ms));
    }

    public static String timeToNowHoursStr(long start)
    {
        long dt_s = System.currentTimeMillis() - start;
        dt_s /= 1000;

        // int seconds = (int) (dt_s % 60);
        int minutes = (int) ((dt_s / 60) % 60);
        int hours = (int) ((dt_s / (60 * 60)) % 24);
        int days = (int) (dt_s / (60 * 60 * 24));

        if (days > 0) {
            return "> 1 day";
        }
        return String.format(SmsLoc_Common.LOCALE, "%d h %d min", hours, minutes);
    }

    public static String timeToNowStr(long start)
    {
        long dt_s = System.currentTimeMillis() - start;
        dt_s /= 1000;

        int seconds = (int) (dt_s % 60);
        int minutes = (int) ((dt_s / 60) % 60);
        int hours = (int) ((dt_s / (60 * 60)) % 24);
        int days = (int) (dt_s / (60 * 60 * 24));

        return String.format(SmsLoc_Common.LOCALE, "%d d %d h %d min %d sec", days, hours, minutes, seconds);
    }

    public static int getBatteryPct(Context context)
    {
        return
                ((BatteryManager) context.getApplicationContext().getSystemService(BATTERY_SERVICE))
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /** Synchronously write, make sure it is stored
     * close all executor threads */
    public static void closeAllFiles(Context ctx) {
        SmsDayDataFile.getInstance(ctx).writeFileBlocking();
        SmsDayDataFile.getInstance(ctx).close();
        PeopleDataFile.getInstance(ctx).writeFileBlocking();
        PeopleDataFile.getInstance(ctx).close();
        LogFile.getInstance(ctx).writeFileBlocking();
        LogFile.getInstance(ctx).close();
    }

    public static class Debug
    {
        private static final String CLASS_TAG = Debug.class.getSimpleName();

        public static void enableStrictMode() {
            if (!BuildConfig.STRICT_MODE_ENABLE) {
                return;
            }
            Log.w(CLASS_TAG, "Enabling StrictMode reporting");
            if (Build.VERSION.SDK_INT >= 31) {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectUnsafeIntentLaunch().build());
            }
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().build());
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().build());
        }

        public static void strictModeDiskIOOff(final String msg) {
            if (!BuildConfig.STRICT_MODE_ENABLE) {
                return;
            }
            Log.w(CLASS_TAG, "Disabling StrictMode detection of disk I/O " + msg);
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().build());
        }
        public static void strictModeDiskIOOn(final String msg) {
            if (!BuildConfig.STRICT_MODE_ENABLE) {
                return;
            }
            Log.w(CLASS_TAG, "Enabling StrictMode detection of disk I/O " + msg);
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().build());
        }
    }
}
