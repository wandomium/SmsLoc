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

import android.Manifest;
import android.content.Context;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class SmsLoc_Permissions
{
    public static final int REQUEST_ID = 1;

    public static final String[] LIST = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
    };

    public static String stripPrefix(String str)
    {
        return str.replaceAll("android.permission.", "");
    }

    public static String getPermissionRationale(final String per)
    {
        switch (per) {
            case Manifest.permission.SEND_SMS:
                return "Needed to send location request and to respond to location requests";
            case Manifest.permission.RECEIVE_SMS:
                return "Needed to receive location requests trough sms";
            case Manifest.permission.ACCESS_BACKGROUND_LOCATION:
                return "Needed to respond to sms location requests when the app is not running";
            case Manifest.permission.ACCESS_COARSE_LOCATION:
            case Manifest.permission.ACCESS_FINE_LOCATION:
                return "Needed to respond to sms location requests";
            case Manifest.permission.INTERNET:
                return "Needed to load and display the map";
            case Manifest.permission.WAKE_LOCK:
                return "Needed to react to incoming SMS when phone is asleep";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return "Needed to display the map";
        }

        return "";
    }

    public static String[] getMissingPermissions(Context context, String[] permissionList)
    {
        ArrayList<String> retval = new ArrayList<>();

        for (String p : permissionList) {
            if (ActivityCompat.checkSelfPermission(context, p) != PERMISSION_GRANTED) {
                retval.add(p);
            }
        }

        return retval.toArray(new String[retval.size()]);
    }
}
