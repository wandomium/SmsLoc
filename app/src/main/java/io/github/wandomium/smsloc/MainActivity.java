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
package io.github.wandomium.smsloc;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;

import com.google.android.material.tabs.TabLayoutMediator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import android.os.PowerManager;
import android.telephony.SubscriptionManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcv;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.toolbox.Utils;
import io.github.wandomium.smsloc.ui.dialogs.SimSelectorDialogFragment;
import io.github.wandomium.smsloc.ui.dialogs.SimpleDialogs;
import io.github.wandomium.smsloc.ui.dialogs.SmsSendFailDialog;
import io.github.wandomium.smsloc.ui.main.TabPagerAdapter;
import io.github.wandomium.smsloc.toolbox.PermissionMngr;


//TODO E/TelemetryUtils: java.lang.SecurityException: getDataNetworkTypeForSubscriber

public class MainActivity extends AppCompatActivity
{
    private static boolean mCreated = false;
    private PermissionMngr mPermissionMngr;
    private ActivityResultLauncher<Intent> mSettingsLauncher;

    private boolean mIsPaused = false;
    private BroadcastReceiver mSmsFailReceiver;

    public MainActivity() {
        super();
        Utils.Debug.enableStrictMode();
    }

    public static boolean isCreated() {
        return mCreated;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //TODO-Minor
        //Error receiver
        mCreated = true;

        /* Pre-load all files so we don't get StrictMode violations */
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            LogFile.getInstance(this);
            PeopleDataFile.getInstance(this);
            SmsDayDataFile.getInstance(this);

            executor.shutdown();
        });

        /* Setup view
         * This inflates XMLs so we cannot really avoid it,
         * nor can we move it out of the UI thread
         * We tolerate it because it is in the main activity and it only happens once at startup
         */
        final String msg = MainActivity.class.getSimpleName() + ":setContentView(R.layout.activity_main)";
        Utils.Debug.strictModeDiskIOOff(msg);
        setContentView(R.layout.activity_main);
        Utils.Debug.strictModeDiskIOOn(msg);

        ViewPager2 viewPager2 = findViewById(R.id.view_pager);
        viewPager2.setAdapter(new TabPagerAdapter(MainActivity.this));
        new TabLayoutMediator(findViewById(R.id.tabs), viewPager2,
            (tab, position) -> tab.setText(Objects.requireNonNull(TabPagerAdapter.Tabs.fromInt(position), "BUG: Invalid Tab ID").cTitle)).attach();
        //We don't want to constantly recreate the map page
        //TODO: this needs to be a const but we have some issues defining it
        viewPager2.setOffscreenPageLimit(3);

        setSupportActionBar(findViewById(R.id.my_toolbar));

        /* Permissions */
        mPermissionMngr = new PermissionMngr(MainActivity.this);
        mPermissionMngr.execute();

        /* Settings updated */
        mSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    MainActivity.this.mPermissionMngr.refreshPermissions();
                    MainActivity.this.invalidateOptionsMenu();
                });

        /* Sms Send FAILED */
        mIsPaused = false;
        mSmsFailReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mIsPaused) {
                        SmsSendFailDialog.showNotification(context, intent);
                }
                else {
                    SmsSendFailDialog.showDialog(context, intent);
                }
            }
        };
        ContextCompat.registerReceiver(this, mSmsFailReceiver,
            new IntentFilter(SmsLoc_Intents.ACTION_SMS_SEND_FAIL), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public boolean batteryOptimizationOn() {
        return !((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
    }
    public boolean isBackgroundRestricted() {
        return ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).isBackgroundRestricted();
    }
    public boolean permissionsMissing() {
        return mPermissionMngr.areMissing();
    }
    public String getMissingPermissionStr() {
        return mPermissionMngr.getMissingString();
    }
    public boolean simSelectError() {
        int subId = SmsLoc_Settings.SMS_SUB_ID.getInt(this);
        if (subId == SmsLoc_Settings.SMS_SUB_ID_DEFAULT) {
            subId = SmsUtils.getDefaultSimId();
            return subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        else {
            return false;
        }
    }

    public boolean permissionCheckActive() { return mPermissionMngr.isBusy(); }

    @Override
    protected void onPause()
    {
        Utils.closeAllFiles(this);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        NotificationHandler.getInstance(this).clearAllNotifications();
        MainActivity.this.mPermissionMngr.refreshPermissions();
        invalidateOptionsMenu(); //permission or battery settings alert
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mSmsFailReceiver);
        mCreated = false;

        super.onDestroy();
    }

/*  LEAVE NOTE IN FOR FUTURE REFERENCE - Took some time to figure this one out
    @Override
    protected void onDestroy()
    {
        if (bound) {
            // If the service is not running and gets unbound, it will be destroyed.
            // However, it's onDestroy method will not necessarily get called, so this is
            // our last chance to clean up anything we need to in track service
            unbindService(mConnection);
        }
        super.onDestroy();

        return;
    }
 */

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SimSelectorDialogFragment.SIM_SELECT_REQUEST_ID) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                new SimSelectorDialogFragment().show(getSupportFragmentManager(), SimSelectorDialogFragment.TAG);
            }
        }
        else {
            mPermissionMngr.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu, menu);

        final boolean invalidSettings =
            batteryOptimizationOn() || permissionsMissing() || isBackgroundRestricted() || simSelectError() ||
            NotificationHandler.getInstance(MainActivity.this).areNotificationsBlocked();

        MenuItem alertItem = menu.findItem(R.id.per_warning);
        alertItem.setEnabled(invalidSettings);
        alertItem.setVisible(invalidSettings);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        final int id = item.getItemId();
        if (id == R.id.about) {
            SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.About);
        }
        else if (id == R.id.per_warning) {
            SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.InvalidSettings);
        }
        else if (id == R.id.gpsFixTimeout) {
            SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.GpsTimeout);
        }
        else if (id == R.id.troubleshooting) {
            SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.Troubleshooting);
        }
        else if (id == R.id.selectSim) {
            new SimSelectorDialogFragment().show(getSupportFragmentManager(), SimSelectorDialogFragment.TAG);
        }

        return super.onOptionsItemSelected(item);
    }

    public void openSettings(Intent intent) {
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            mSettingsLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_LONG).show();
        }
    }
}
