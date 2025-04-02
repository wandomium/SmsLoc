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

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;

import androidx.annotation.Nullable;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.util.ArrayList;

/**
 * Used to get GPS location when SMS request comes in
 */


/** TODO
 * ACQUIRE_CAUSES_WAKEUP
 * WakefulBrRec is depreciated
 * Some changes about where can services be started in the next release
 */
public class LocationRetrieverFgService extends Service implements LocationRetriever.LocCb
{
    private static final String CLASS_TAG = LocationRetrieverFgService.class.getSimpleName();

    private int mWakeLockId;

    protected String mTitle;
    protected ArrayList<String> mDetails;
    protected String mCallStatus;

    protected String mAddr;

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        final int retval = super.onStartCommand(intent, flags, startId);

        final PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(getApplicationContext());

        mWakeLockId = intent.getIntExtra(SmsLoc_Intents.EXTRA_WAKE_LOCK_ID, SmsReceiver.INVALID_WAKE_LOCK_ID);
        mAddr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);

        mTitle = String.format("Request from %s",
            PEOPLEDATA.containsId(mAddr) ?
                PEOPLEDATA.getDataEntry(mAddr).displayName : Utils.unlistedDisplayName(mAddr));
        mDetails = new ArrayList<>();
        mCallStatus = "OK";

        try {
            // This one can throw
            //* SecurityException because of permission issues, or
            //* ForegroundServiceStartNotAllowedException (android 10 and later)
            //* Or due to missing/invalid fg service types
            // https://developer.android.com/develop/background-work/services/foreground-services (v12 - API31)
            startForeground(startId,
                NotificationHandler.getInstance(this).createOngoigNotification(
                    mTitle, "Waiting for GPS fix", String.format("Timeout is: %s min", SmsLoc_Settings.GPS_TIMEOUT.getInt(this))),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        }
        catch (IllegalStateException e) {
            LogFile.getInstance(getApplication()).addLogEntry(e.getMessage());
            onLocationRcvd(null, e.getMessage());
        }

        LocationRetriever.getLocation(
            SmsLoc_Settings.GPS_TIMEOUT.getInt(this) * Utils.MIN_2_MS, this, this
        );

        return retval;
    }


    @Override
    public void onLocationRcvd(Location loc, String msg)
    {
        mDetails.add(msg);
        // Try to get last known location
        if (loc == null) try {
            //try to check network location?? NO - this is an outdoor app, should not invade more than necessary
            loc = ((LocationManager) getSystemService(Context.LOCATION_SERVICE))
                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
            mCallStatus = "STALE";
            mDetails.add(String.format("Trying last known location: %s", loc != null ? "OK" : "FAIL"));
        }
        catch (SecurityException e) { //shouldn't happen here, but just in case
            mDetails.add(String.format("Trying last known location: FAIL - %s", e.getMessage()));
        }

        GpsData gpsData =
            GpsData.fromLocationAndBat(loc, Utils.getBatteryPrcnt(this));

        if (!gpsData.dataValid()) {
            mCallStatus = "INVALID";
            mDetails.add("GPS data invalid");
        }
        if (!SmsUtils.sendSms(
            this, mAddr, SmsUtils.RESPONSE_CODE + gpsData.toSmsText())) {

            mCallStatus = "ERROR"; //this is more pressing than stale location
            mDetails.add("Missing SEND_SMS permission");
        }

        this.stopForeground(true);

        // This automatically logs
        NotificationHandler.getInstance(this).createAndPostNotification(
            mTitle, "Response " + mCallStatus, mDetails.isEmpty() ? null : mDetails.toString());

        this.stopSelf();
        //SmsReceiver.completeWakefulIntent(mIntent);
        SmsReceiver.releaseWakeLock(mWakeLockId);

        return;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
