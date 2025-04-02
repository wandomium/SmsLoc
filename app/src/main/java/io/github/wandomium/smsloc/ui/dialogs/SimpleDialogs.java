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
package io.github.wandomium.smsloc.ui.dialogs;

import static io.github.wandomium.smsloc.defs.SmsLoc_Settings.SHOW_FORCE_STOP_ALERT;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.NumberPicker;

import androidx.annotation.StringRes;

import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.defs.SmsLoc_Permissions;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;

public class SimpleDialogs
{
    public enum Type {Troubleshooting, Alerts, GpsTimeout, InvalidSettings, BatteryOptimization, Notifications};

    public static void createAndShow(MainActivity activity, Type which)
    {
        switch (which) {
            case Troubleshooting:
                _showAlert(activity, SHOW_FORCE_STOP_ALERT);
                _showAlert(activity, SmsLoc_Settings.SHOW_BG_AUTOSTART_ALERT);
                return;
            case Alerts:
                if (SmsLoc_Settings.SHOW_FORCE_STOP_ALERT.getBool(activity)) {
                    _showAlert(activity, SmsLoc_Settings.SHOW_FORCE_STOP_ALERT);
                }
                if (SmsLoc_Settings.SHOW_BG_AUTOSTART_ALERT.getBool(activity)) {
                    _showAlert(activity, SmsLoc_Settings.SHOW_BG_AUTOSTART_ALERT);
                }
                return;
            case GpsTimeout:
                _showGpsTimeoutSelector(activity); return;
            case InvalidSettings:
                _showInvalidSettingsAlert(activity); return;
            case BatteryOptimization:
            {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.getPackageName(), "battery")
                );
                _showOpenSettingsDialog(activity,
                        R.string.battery_saver_request_title, R.string.battery_saver_request_msg, intent);
                return;
            }
            case Notifications:
            {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                _showOpenSettingsDialog(activity,
                        R.string.notifications_request_title, R.string.notifications_request_msg, intent);
                return;
            }
        }

        return;
    }

    private static void _showAlert(MainActivity activity, final SmsLoc_Settings alert)
    {
        int titleId = -1;
        int msgId = -1;
        switch(alert)
        {
            case SHOW_FORCE_STOP_ALERT:
                titleId = R.string.force_stop_alert_title;
                msgId = R.string.force_stop_alert_msg;
                break;
            case SHOW_BG_AUTOSTART_ALERT:
                titleId = R.string.bg_autostart_alert_title;
                msgId = R.string.bg_autostart_alert_msg;
                break;
        }

        new AlertDialog.Builder(activity)
            .setTitle(titleId)
            .setMessage(msgId)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setNeutralButton("Show on next launch", (DialogInterface d, int which) -> {
                alert.set(activity, true);
            })
            .setNegativeButton("Dismiss", (DialogInterface d, int which) -> {
                alert.set(activity, false);
            })
            .show();
        return;
    }

    private static void _showGpsTimeoutSelector(MainActivity activity)
    {
        NumberPicker mPicker = new NumberPicker(activity);
        mPicker.setMinValue(SmsLoc_Settings.GPS_TIMEOUT_MIN);
        mPicker.setMaxValue(SmsLoc_Settings.GPS_TIMEOUT_MAX);
        mPicker.setWrapSelectorWheel(false);
        mPicker.setValue(SmsLoc_Settings.GPS_TIMEOUT.getInt(activity));

        new AlertDialog.Builder(activity)
            .setTitle("Select GPS Timeout value [min]")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ok", (DialogInterface d, int id) -> {
                SmsLoc_Settings.GPS_TIMEOUT.set(activity, mPicker.getValue());
            })
            .setView(mPicker)
            .show();
        return;
    }

    private static void _showInvalidSettingsAlert(MainActivity activity)
    {
        StringBuilder msg = new StringBuilder("");
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
        boolean addSettingsBtn = false;

        dialog.setTitle("Invalid Settings Detected")
              .setNeutralButton("Close", null)
              .setIcon(android.R.drawable.ic_dialog_alert);

        if (activity.batteryOptimizationOn()) {
            msg.append("\nBATTERY SAVER ACTIVE\n")
                    .append(activity.getString(R.string.battery_saver_alert_msg))
                    .append("\n");
            addSettingsBtn = true;
        }
        if (NotificationHandler.getInstance(activity).areNotificationsBlocked())
        {
            msg.append("\nNOTIFICATIONS DISABLED\n");
            addSettingsBtn = true;
        }

        if (activity.permissionsMissing()) {
            //sb.append("MISSING PERMISSIONS\nApp is missing the following permissions for full functionality: \n");
            msg.append("\nMISSING PERMISSIONS\n");
            for (String p : activity.mPermissionChecker.mMissingList.keySet()) {
                msg.append(SmsLoc_Permissions.stripPrefix(p))
                        .append(": ").append(SmsLoc_Permissions.getPermissionRationale(p)).append("\n");
            }
            dialog.setNegativeButton("Request Permissions", (DialogInterface d, int id) -> {
                activity.mPermissionChecker.execute();
            });
        }

        if (addSettingsBtn) {
            dialog.setPositiveButton("Open Settings", (DialogInterface d, int id) -> {
                try {
                    activity.startActivityForResult(new Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.getPackageName(), "")
                    ), 6564);
                } catch (ActivityNotFoundException e) {}
            });
        }

        dialog.setMessage(msg.toString()).show();
        return;
    }

    private static void _showOpenSettingsDialog(MainActivity activity, @StringRes int title, @StringRes int message, Intent intent)
    {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Use Without", null)
                .setPositiveButton("Open Settings", (DialogInterface d, int id) -> {
                    try {
                        activity.startActivityForResult(intent, 6564);
                    } catch (ActivityNotFoundException e) {}
                })
                .show();
    }
}
