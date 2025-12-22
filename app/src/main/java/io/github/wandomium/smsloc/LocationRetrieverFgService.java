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

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Used to get GPS location when SMS request comes in
 */
public class LocationRetrieverFgService extends Service implements LocationRetriever.LocCb
{
    private static final String CLASS_TAG = LocationRetrieverFgService.class.getSimpleName();

    protected ArrayList<String> mDetails;
    protected String mCallStatus;

    protected record SmsEntry(int startId, int wakeLockId, String addr){};
    private LinkedBlockingQueue<SmsEntry> mSmsQueue;
    private NotificationHandler mNotHandler;


    @Override
    public void onCreate() {
        Log.d(CLASS_TAG, "onCreate");
        super.onCreate();

        mSmsQueue = new LinkedBlockingQueue<>();
        mNotHandler = NotificationHandler.getInstance(this);

        mDetails = new ArrayList<>();
        mCallStatus = "UNKNOWN";
    }

    @Override
    public void onDestroy() {
        Log.d(CLASS_TAG, "onDestory");
        if (mSmsQueue != null) {
            mSmsQueue.clear();
        }
        mSmsQueue = null;
        mNotHandler = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(CLASS_TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);

        final SmsEntry smsEntry = new SmsEntry(
                startId,
                intent.getIntExtra(SmsLoc_Intents.EXTRA_WAKE_LOCK_ID, SmsReceiver.INVALID_WAKE_LOCK_ID),
                intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR)
        );

        try {
            // This one can throw, see _getExceptionString for details
            startForeground(startId,
                NotificationHandler.getInstance(this).createOngoigNotification(
        "Request from " + (!mSmsQueue.isEmpty() ? "[multiple]" : Utils.getDisplayName(this, smsEntry.addr)),
             "Waiting for GPS fix",
                    String.format("Timeout is: %s min", SmsLoc_Settings.GPS_TIMEOUT.getInt(this))
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        }
        catch (Exception e) {
            _finalizeResponse(
                smsEntry, "FAIL", "Could not start GPS fix (check log)"
            );
            LogFile.getInstance(this).addLogEntry(_getExceptionString(e));
            return START_NOT_STICKY;
        }

        mSmsQueue.offer(smsEntry);
        // if this is the first in queue, start GPS, otherwise we assume it is running
        if (mSmsQueue.size() == 1) {
            mDetails.clear();
            mCallStatus = "OK";
            LocationRetriever.getLocation(
                (long) SmsLoc_Settings.GPS_TIMEOUT.getInt(this) * Utils.MIN_2_MS, this, this
            );
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onLocationRcvd(Location loc, String msg)
    {
        Log.d(CLASS_TAG, "onLocationReceived");
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
            GpsData.fromLocationAndBat(loc, Utils.getBatteryPct(this));

        if (!gpsData.dataValid()) {
            mCallStatus = "INVALID";
            mDetails.add("GPS data invalid");
        }

        while(mSmsQueue != null && !mSmsQueue.isEmpty()) {
            final String smsText = SmsUtils.RESPONSE_CODE + gpsData.toSmsText();

            ArrayList<SmsEntry> entries = new ArrayList<>(mSmsQueue.size());
            mSmsQueue.drainTo(entries);

            for (SmsEntry smsEntry : entries) {
                final boolean smsSendOk = SmsUtils.sendSms(this, smsEntry.addr, smsText);
                _finalizeResponse(
                    smsEntry,
                    smsSendOk ? mCallStatus : "ERROR",
                    smsSendOk ? (mDetails.isEmpty() ? null : mDetails.toString()) : ("SMS send fail" + mDetails.toString())
                );
                // IMPORTANT: onDestroy can get called here!!!
            }
        }
        //SmsReceiver.completeWakefulIntent(mIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }


    // HELPERS
    private void _finalizeResponse(SmsEntry smsEntry, final String status, final String detail) {
        Log.d(CLASS_TAG, "_finalizeResponse");
        mNotHandler.createAndPostNotification(
"Request from " + Utils.getDisplayName(LocationRetrieverFgService.this, smsEntry.addr),
     "Response " + status, detail
        );

        SmsReceiver.releaseWakeLock(smsEntry.wakeLockId);
        stopSelf(smsEntry.startId);
    }

    /** SecurityException because of permission issues, or
     * ForegroundServiceStartNotAllowedException (android 10 and later)
     * Or due to missing/invalid fg service types
     * https://developer.android.com/develop/background-work/services/foreground-services (v12 - API31)
     */
    private static String _getExceptionString(Exception e) {
        if (Build.VERSION.SDK_INT >= 31 && e instanceof ForegroundServiceStartNotAllowedException) {
            return  "App has background restrictions: " + e.getMessage();
        }
        else if (e instanceof SecurityException) {
            return  "Missing permission: " + e.getMessage();
        }
        else {
            // These are actual bugs in the code - manifest mismatch
            // InvalidForegroundServiceType, MissingForegroundServiceTypeException and SecurityException in 34
            return  "BUG: Please report this\n" + e.getMessage();
        }
    }
}
