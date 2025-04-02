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

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.defs.SmsLoc_Permissions;
import io.github.wandomium.smsloc.ui.dialogs.SimpleDialogs;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class PermissionChecker
{
    public HashMap<String, Boolean> mMissingList;
    protected final WeakReference<Activity> mParentActivity;

    private int mRequestCode;
    private int MY_PERMISSIONS_REQUEST = 1;

    private boolean mBusy = false;

    public PermissionChecker(@NonNull Activity activity)
    {
       this.mParentActivity = new WeakReference<>(activity);
    }

    public boolean execute()
    {
        if (mBusy) {
            return false;
        }
        mBusy = true;

        //determine missing permissions
        mMissingList = new HashMap<>();
        for (String p : SmsLoc_Permissions.LIST) {
            if (ActivityCompat.checkSelfPermission(mParentActivity.get(), p) != PERMISSION_GRANTED) {
                mMissingList.put(p, false);
            }
        }

        _checkPermissions(MY_PERMISSIONS_REQUEST);
        return true;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mRequestCode != requestCode) {
            //whoops
            return;
        }
        //we ask for permissions one by one
        switch (grantResults[0]) {
            case PERMISSION_GRANTED:
                mMissingList.remove(permissions[0]);
                break;
            case PERMISSION_DENIED:
                mMissingList.put(permissions[0], true); //mark as handled
                break;
        }

        _checkPermissions(requestCode++);
        return;
    }

    private void _checkPermissions(int req) {
        mRequestCode = req;
        for (String per : mMissingList.keySet()) {
            if (!mMissingList.get(per)) {
                //recheck, because some permissions are granted in bundles (sms send/receive for example)
                if (ActivityCompat.checkSelfPermission(mParentActivity.get(), per) == PERMISSION_GRANTED) {
                    mMissingList.remove(per);
                    _checkPermissions(req);
                } else {
                    _makeRequest(per, req);
                }
                return;
            }
        }

        _finish();
        return;
    }

    private void _makeRequest(final String per, final int req) {
        new AlertDialog.Builder(mParentActivity.get())
                .setTitle(per.replaceAll("android.permission.", ""))
                .setMessage(SmsLoc_Permissions.getPermissionRationale(per))
                .setNegativeButton("Use without", (DialogInterface dialog, int id) -> {
                        mMissingList.put(per, true);  //mark as handled
                        _checkPermissions(req); //move to next
                        dialog.dismiss();
                })
                //TODO-Low
                //Deny and don't ask again would be more polite
                .setPositiveButton("Proceed", (DialogInterface dialog, int id) -> {
                        ActivityCompat.requestPermissions(mParentActivity.get(), new String[]{per}, req);
                        dialog.dismiss();
                })
                .show();

        return;
    }

    private void _finish()
    {
        onDone();
        mBusy = false;
        return;
    }


    public void onDone()
    {
        MainActivity mainActivity = (MainActivity) mParentActivity.get();

        /** BATTERY OPTIMIZATION and autostart
         * Exemptions from background start restrictions on android 12 (API level 31)
         * The user turns off battery optimizations for your app. You can help users find this option
         * by sending them to your app's App info page in system settings. To do so, invoke an intent
         * that contains the ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS intent action.
         *
         * https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions

         *
         * PS: not done implicitly because PlayStore blocks the app if so.
         */
        if (mainActivity.batteryOptimizationOn()) {
            SimpleDialogs.createAndShow(mainActivity, SimpleDialogs.Type.BatteryOptimization);
        }

//        if (NotificationHandler.getInstance(mainActivity).areNotificationsBlocked()) {
//            SimpleDialogs.createAndShow(mainActivity, SimpleDialogs.Type.Notifications);
//        }

        /** ALERTS */
        //call first so that permission request draw over these alerts
        SimpleDialogs.createAndShow(mainActivity, SimpleDialogs.Type.Alerts);
    }
}
