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

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;

import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.ui.dialogs.SimpleDialogs;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class handles permissions and other restrictions that system can impose on the app that might
 * prevent the app from running correctly.
 * <p>
 * Classic part includes ManifestPermissions that require explicit confirmation from the user
 * Other sections are:
 *      - BatteryOptimization (It is depreciated to directly open the settings dialog for this, but on newer Android version
 *        Pixel phones for example, this setting can no longer be access via existing method of opening app or system settings
 *        and prompting the user to disable battery optimizations. The REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission is
 *        currently the only was that works on all tested systems
 *      - backgroundRestrictions
 *      - MIUI background autostart block
 */
public class PermissionMngr
{
    private final List<RequiredExplicit> mMissing = new ArrayList<>();
    private final WeakReference<Activity> mParentActivity;
    private boolean mBusy = false;

    public PermissionMngr(@NonNull Activity activity) {
        this.mParentActivity = new WeakReference<>(activity);
    }

    enum RequiredExplicit {
        @SuppressLint("InlinedApi") // check is done in execute
        NOTIFICATION(0,  new String[]{Manifest.permission.POST_NOTIFICATIONS},-1), //implicitly granted on API<33 but we only call this once at app startup so it does not matter
        SMS(1,           new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, R.string.per_sms_rationale),
        FINE_LOCATION(2, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, R.string.per_loc_rationale),
        BG_LOCATION(3,   new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, -1),
        PHONE_STATE(4,   new String[]{Manifest.permission.READ_PHONE_STATE}, R.string.per_phone_rationale);

        final int code;
        final String[] list;
        @StringRes
        final int rationale;

        RequiredExplicit(final int code, final String[] list, @StringRes final int rationale) {
            this.code = code; this.list = list; this.rationale = rationale;
        }

        static RequiredExplicit fromInt(final int code) {
            return (code >= values().length) ? null : (code < 0 ? null : values()[code]);
        }
    }

    public boolean isBusy() { return mBusy;}

    public void execute() {
        if (mBusy) {
            return;
        }
        mBusy = true;

        mMissing.clear();
        // skip POST_NOTIFICATIONS on lower APIs
        _requestNext((Build.VERSION.SDK_INT < 33) ? 0 : -1);
    }

    public void onDone()
    {
        MainActivity mainActivity = (MainActivity) mParentActivity.get();

        // BG autostart and force stop
        // TODO until we move to datastore from shared preferences, this will always trigger
        // disk read violation
        SimpleDialogs.createAndShow(mainActivity, SimpleDialogs.Type.Alerts);

        if (mainActivity.batteryOptimizationOn()) {
            SimpleDialogs.createAndShow(mainActivity, SimpleDialogs.Type.BatteryOptimization);
        }

        mBusy = false;
    }

    public boolean areMissing() {
        return !mMissing.isEmpty();
    }

    public void refreshPermissions() {
        mMissing.clear();
        for (RequiredExplicit req : RequiredExplicit.values()) {
            if (req == RequiredExplicit.NOTIFICATION && Build.VERSION.SDK_INT < 33) {
                continue;
            }
            for (String perStr : req.list) {
                if (ActivityCompat.checkSelfPermission(mParentActivity.get(), perStr) != PERMISSION_GRANTED) {
                    mMissing.add(req);
                    break; // already added for this group
                }
            }
        }
    }

    public String getMissingString()
    {
        if (mMissing.isEmpty()) {
            return "";
        }
        StringBuilder msg = new StringBuilder();
        for (RequiredExplicit per : mMissing) {
            msg.append(per.toString()).append(" ");
        }
        msg.append("\n");
        for (RequiredExplicit per : mMissing) {
            if (per.rationale != -1) {
                msg.append(mParentActivity.get().getString(per.rationale));
            }
        }
        return msg.toString();
    }

    /** @noinspection unused*/
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        final RequiredExplicit per = RequiredExplicit.fromInt(requestCode);
        if (per == null) {
            // one of non-essential permissions was denied, so we don't need to keep track
            return;
        }

        for (int result : grantResults) {
            if (result != PERMISSION_GRANTED) {
                mMissing.add(per);
            }
        }

        if (per == RequiredExplicit.FINE_LOCATION) {
            if (grantResults[0] != PERMISSION_GRANTED) {
                // we cannot request BG Location if we do not have access to FG location
                mMissing.add(RequiredExplicit.BG_LOCATION);
            }
            else {
                // we don't need to show explanation for BG location because we showed it with FINE location
                ActivityCompat.requestPermissions(mParentActivity.get(), new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, RequiredExplicit.BG_LOCATION.code);
                return;
            }
        }

        _requestNext(requestCode);
    }


    private void _requestNext(final int perCode)
    {
        final RequiredExplicit nextPer = RequiredExplicit.fromInt(perCode + 1);
        if (nextPer == null) {
            _finish();
            return;
        }

        for (String perStr : nextPer.list) {
            if (ActivityCompat.checkSelfPermission(mParentActivity.get(), perStr) != PERMISSION_GRANTED) {
                if (nextPer.rationale == -1) {
                    ActivityCompat.requestPermissions(mParentActivity.get(), nextPer.list, nextPer.code);
                }
                else {
                    // request with rationale
                    new AlertDialog.Builder(mParentActivity.get()).setTitle("Permission required")
                            .setMessage(nextPer.rationale)
                            .setNegativeButton("Use without", ((dialog, which) -> {
                                int[] grantResults = new int[nextPer.list.length];
                                Arrays.fill(grantResults, PERMISSION_DENIED);
                                PermissionMngr.this.onRequestPermissionsResult(nextPer.code, nextPer.list, grantResults);
                                dialog.dismiss();
                            }))
                            .setPositiveButton("Request", ((dialog, which) -> {
                                ActivityCompat.requestPermissions(mParentActivity.get(), nextPer.list, nextPer.code);
                                dialog.dismiss();
                            }))
                            .show();
                }
                return;
            }
        }

        // If current one is granted move on to next
        _requestNext(nextPer.code);
    }

    private void _finish()
    {
        onDone();
        mBusy = false;
    }
}





