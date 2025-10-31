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
package io.github.wandomium.smsloc.ui.dialogs;

import static io.github.wandomium.smsloc.defs.SmsLoc_Settings.SHOW_BG_AUTOSTART_ALERT;
import static io.github.wandomium.smsloc.defs.SmsLoc_Settings.SHOW_FORCE_STOP_ALERT;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;

public class SimpleDialogs {
    public enum Type {Troubleshooting, Alerts, GpsTimeout, InvalidSettings, BatteryOptimization,
        Notifications, Permissions, BgAutostart, About, SimSelect}

    public static void createAndShow(MainActivity activity, Type which) {
        switch (which) {
            case SimSelect:
                _showSimSelectAlert(activity);
                return;
            case BgAutostart:
                _showBgAutostartAlert(activity);
                return;
            case Troubleshooting:
                _showBgAutostartAlert(activity);
                _showAlert(activity, R.string.force_stop_alert_title, R.string.force_stop_alert_msg, SHOW_FORCE_STOP_ALERT);
                return;
            case Alerts:
                if (SHOW_BG_AUTOSTART_ALERT.getBool(activity)) {
                    _showBgAutostartAlert(activity);
                }
                if (SmsLoc_Settings.SHOW_FORCE_STOP_ALERT.getBool(activity)) {
                    _showAlert(activity, R.string.force_stop_alert_title, R.string.force_stop_alert_msg, SHOW_FORCE_STOP_ALERT);
                }
                return;
            case GpsTimeout:
                _showGpsTimeoutSelector(activity);
                return;
            case InvalidSettings:
                _showInvalidSettingsAlert(activity);
                return;
            case BatteryOptimization: {
                // This does not work on newer android versions, specifically 15 and Pixel phones.
//                Intent intent = new Intent(
//                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
//                        Uri.fromParts("package", activity.getPackageName(), "battery")
//                );
                // In this case the system does not call onActivityForResult
                _showOpenSettingsDialog(activity, "Battery Saver Exemption", activity.getString(R.string.battery_saver_alert_msg),
                        new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
                return;
            }
            case Notifications: {
                _showOpenSettingsDialog(activity, "Enable Notifications", "",
                        new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS));
                return;
            }
            case Permissions: {
                /* If we are calling from here the user already declined permissions once. The procedure
                 * now is to navigate the user to settings */
                _showOpenSettingsDialog(activity, "Missing permissions", activity.getMissingPermissionStr(),
                        new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS));
                return;
            }
            case About: {
                _showAboutDialog(activity);
            }
        }
    }

    private static void _showAlert(MainActivity activity,
           @StringRes final int titleId, @StringRes final int msgId, final SmsLoc_Settings alert)
    {
        new AlertDialog.Builder(activity)
            .setTitle(titleId).setMessage(msgId)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setNeutralButton("Show on next launch", (DialogInterface d, int which) -> alert.set(activity, true))
            .setNegativeButton("Dismiss", (DialogInterface d, int which) -> alert.set(activity, false))
            .show();
    }

    private static void _showSimSelectAlert(MainActivity activity)
    {
        new AlertDialog.Builder(activity)
            .setTitle("Invalid SIM configuration")
            .setMessage(activity.getString(R.string.invalid_sim_config_msg))
            .setPositiveButton("Ok", null)
            .show();
    }

    private static void _showBgAutostartAlert(MainActivity activity)
    {
        final Intent bgAutostartIntent = SmsLoc_Intents.generateBgAutostartIntent(activity);
        if (bgAutostartIntent != null) {
            // if it is not null it means we are on a custom rom that we probably know how to handle
            // we open the settings but set flag to ignore so the user is not prompted on every boot
            SHOW_BG_AUTOSTART_ALERT.set(activity, false);

            // means that it is one of the recognized ROMs and we might be able to point the user directly to settings
            SimpleDialogs._showOpenSettingsDialog(activity,
                    activity.getString(R.string.bg_autostart_alert_title), activity.getString(R.string.bg_autostart_alert_msg),
                    bgAutostartIntent);
        }
        else {
            SimpleDialogs._showAlert(activity, R.string.bg_autostart_alert_title, R.string.bg_autostart_alert_msg, SHOW_BG_AUTOSTART_ALERT);
        }
    }

    private static void _showGpsTimeoutSelector(MainActivity activity) {
        NumberPicker mPicker = new NumberPicker(activity);
        mPicker.setMinValue(SmsLoc_Settings.GPS_TIMEOUT_MIN);
        mPicker.setMaxValue(SmsLoc_Settings.GPS_TIMEOUT_MAX);
        mPicker.setWrapSelectorWheel(false);
        mPicker.setValue(SmsLoc_Settings.GPS_TIMEOUT.getInt(activity));

        new AlertDialog.Builder(activity)
                .setTitle("Select GPS Timeout value [min]")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Ok", (DialogInterface d, int id) -> SmsLoc_Settings.GPS_TIMEOUT.set(activity, mPicker.getValue()))
                .setView(mPicker)
                .show();
    }

    private static void _showInvalidSettingsAlert(MainActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Invalid Settings (Click to change)")
                .setPositiveButton("Close", null)
                .setIcon(android.R.drawable.ic_dialog_alert);

        // Create a list of invalid settings
        List<String> itemsStrs = new ArrayList<>();
        List<Type> itemsTypes = new ArrayList<>();
        if (activity.batteryOptimizationOn()) {
            itemsTypes.add(Type.BatteryOptimization);
            itemsStrs.add("Battery Saver Active");
        }
        if (NotificationHandler.getInstance(activity).areNotificationsBlocked()) {
            itemsTypes.add(Type.Notifications);
            itemsStrs.add("Notifications disabled");
        }
        if (activity.permissionsMissing()) {
            itemsTypes.add(Type.Permissions);
            itemsStrs.add("Missing permissions");
        }
        if (activity.simSelectError()) {
            itemsTypes.add(Type.SimSelect);
            itemsStrs.add("Invalid SIM settings");
        }

        builder.setItems(itemsStrs.toArray(new String[0]), (dialog, which) -> SimpleDialogs.createAndShow(activity, itemsTypes.get(which))).show();
    }

    private static void _showOpenSettingsDialog(MainActivity activity, String title, String message, Intent intent)
    {
        new AlertDialog.Builder(activity)
                .setTitle(title).setMessage(message)
                .setNegativeButton("Ignore", null)
                .setPositiveButton("Open Settings", (DialogInterface d, int id) -> activity.openSettings(intent))
                .show();
    }
    private static void _showAboutDialog(MainActivity activity) {
        // Create a SpannableString for the message
        String message = "Please report issues here and include Activity Log and Version";
        SpannableString spannableString = new SpannableString(message);

        // Set the clickable part of the message (e.g., "Click here")
        int start = message.indexOf("here");
        int end = start + "here".length();

        String title;
        try {
            title = "Version " + activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            title = "Report Bug";
        }

        // Create the AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(spannableString)
                .setPositiveButton("OK", null) // Optional positive button
                .setCancelable(true)
                .create();

        // Make the text clickable
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wandomium/SmsLoc/issues"));
                activity.startActivity(intent);

                dialog.dismiss();
            }
        };

        spannableString.setSpan(clickableSpan, start, end, 0);

        // Set the movement method to LinkMovementMethod to make the text clickable
        dialog.setOnShowListener(dialogInterface -> {
            TextView textView = dialog.findViewById(android.R.id.message);
            if (textView != null) {
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        });

        dialog.show();
    }
}
