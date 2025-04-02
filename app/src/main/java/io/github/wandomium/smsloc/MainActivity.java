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
package io.github.wandomium.smsloc;

import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;

import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.PersonData;
import com.google.android.material.tabs.TabLayout;
import com.google.i18n.phonenumbers.NumberParseException;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import android.os.PowerManager;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.ui.dialogs.SimpleDialogs;
import io.github.wandomium.smsloc.ui.main.TabPagerAdapter;
import io.github.wandomium.smsloc.toolbox.PermissionChecker;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;

import java.util.Random;


//TODO E/TelemetryUtils: java.lang.SecurityException: getDataNetworkTypeForSubscriber

public class MainActivity extends AppCompatActivity
{
    public PermissionChecker mPermissionChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //TODO-Minor
        //Error receiver

        /** VIEW SETUP **/
        setContentView(R.layout.activity_main);

        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(new TabPagerAdapter(getSupportFragmentManager()));

        ((TabLayout) findViewById(R.id.tabs)).setupWithViewPager(viewPager);
        setSupportActionBar(findViewById(R.id.my_toolbar));

/*
        startActivity(
                new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)
                )
        );
*/
        mPermissionChecker = new PermissionChecker(MainActivity.this);

        /** PERMISSION CHECK */
        mPermissionChecker.execute();

        //invalidateOptionsMenu();

        return;
    }

    public boolean batteryOptimizationOn() {
        return !((PowerManager) getSystemService(POWER_SERVICE))
                .isIgnoringBatteryOptimizations(getPackageName());
    }

    public boolean permissionsMissing() {
        return !mPermissionChecker.mMissingList.isEmpty();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        SmsDayDataFile.getInstance(this).writeFile();
        PeopleDataFile.getInstance(this).writeFile();
        return;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        NotificationHandler.getInstance(this).clearAllNotifications();
        invalidateOptionsMenu(); //permission or battery settings alert
        return;
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
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
        return;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu, menu);

        final boolean invalidSettings =
            batteryOptimizationOn() || permissionsMissing() ||
            NotificationHandler.getInstance(MainActivity.this).areNotificationsBlocked();

        MenuItem alertItem = menu.findItem(R.id.per_warning);
        alertItem.setEnabled(invalidSettings);
        alertItem.setVisible(invalidSettings);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.per_warning:
                SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.InvalidSettings);
                break;
            case R.id.gpsFixTimeout:
                SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.GpsTimeout);
                break;
            case R.id.troubleshooting:
                SimpleDialogs.createAndShow(MainActivity.this, SimpleDialogs.Type.Troubleshooting);
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /** ADD PERSON related gymnastics*/
    public void onAddPersonClick(View view) {

        Intent intent = new Intent(
                Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);

        startActivityForResult(intent, SmsLoc_Intents.RESULT_CONTACT_SELECTED);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case SmsLoc_Intents.RESULT_BATT_SETTINGS:
                invalidateOptionsMenu();
                return;

            case SmsLoc_Intents.RESULT_CONTACT_SELECTED:
                if (resultCode == RESULT_OK) try {
                    Cursor cursor =
                            getContentResolver()
                                    .query(data.getData(), null, null, null, null);
                    cursor.moveToFirst();

                    addPerson(
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                    return;
                }
                catch (IllegalArgumentException e) {}
                Toast.makeText(getApplicationContext(), "Failed to add contact", Toast.LENGTH_LONG).show();
                break;
        }
    }

    public void addPerson(String addr, String name)
    {
        final PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(getApplicationContext());

        try {
            addr = SmsUtils.convertToE164PhoneNumFormat(addr, getApplicationContext());
        }
        catch (NumberParseException e) {
           Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
           return;
        }

        if (PEOPLEDATA.containsId(addr)) {
            Toast.makeText(getApplicationContext(), "Contact exists", Toast.LENGTH_LONG).show();
            return;
        }

        final TypedArray colors = getResources().obtainTypedArray(R.array.letter_tile_colors);
        final int color = colors.getColor((new Random()).nextInt(colors.length()), Color.DKGRAY);

        synchronized (PEOPLEDATA.getLockObject())
        {
            PersonData personData = PEOPLEDATA.referenceOrCreateObject_unlocked(addr);
            personData.setDisplayName(name, true);
            personData.color = color;
            personData.whitelisted = true;
            PEOPLEDATA.writeFile_unlocked();
        }

        sendBroadcast(SmsLoc_Intents.generateIntent(addr, SmsLoc_Intents.ACTION_NEW_PERSON));
        return;
    }
}
