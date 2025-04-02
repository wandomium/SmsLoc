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
package io.github.wandomium.smsloc.defs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * All context params used in this class always call
 * context.getApplicationContext so it is irrelevant what context
 * is passed
 */
public enum SmsLoc_Settings
{
    IGNORE_WHITELIST("ignore_whitelist"),
    SHOW_FORCE_STOP_ALERT("show_force_stop_alert"),
    SHOW_BG_AUTOSTART_ALERT("show_bg_autostart_alert"),
    GPS_TIMEOUT("gps_timeout");

    public static final boolean IGNORE_WHITELIST_DEFAULT = false;
    public static final int GPS_TIMEOUT_MIN = 1;
    public static final int GPS_TIMEOUT_MAX = 5;
    public static final int GPS_TIMEOUT_DEFAULT = GPS_TIMEOUT_MIN;

    private final String name;
    private SmsLoc_Settings(final String name) { this.name = name;}
    private static final String SETTINGS_FILE = SmsLoc_Common.Consts.APP_NAME + ".settings";

    public boolean getBool(@NonNull Context context)
    {
        boolean defopt = true;
        switch(this)
        {
            case IGNORE_WHITELIST:
                defopt = IGNORE_WHITELIST_DEFAULT;
            case SHOW_FORCE_STOP_ALERT:
            case SHOW_BG_AUTOSTART_ALERT:
                return context.getApplicationContext()
                        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
                        .getBoolean(name, defopt);
            default:
                // ERR
                throw new ClassCastException();
        }
    }
    public int getInt(@NonNull Context context)
    {
        if (this == GPS_TIMEOUT) {
            return
                context.getApplicationContext()
                    .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
                        .getInt(name, GPS_TIMEOUT_DEFAULT);
        }
        throw new ClassCastException();
    }
    public void set(@NonNull Context context, final boolean val)
    {
        SharedPreferences.Editor editor =
                context.getApplicationContext()
                        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit();
        editor.putBoolean(name, val);
        editor.commit();
    }
    public void set(@NonNull Context context, final int val)
    {
        SharedPreferences.Editor editor =
                context.getApplicationContext()
                        .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit();

        editor.putInt(name, val);
        editor.commit();
    }
}
