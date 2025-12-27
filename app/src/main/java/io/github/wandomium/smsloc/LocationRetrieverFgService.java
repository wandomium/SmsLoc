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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.toolbox.ABaseFgService;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Used to get GPS location when SMS request comes in
 *
 * EntryDataT = Integer and represents wakeLockId
 * it can be retrieved with call to QueueEntry.data()
 */
public class LocationRetrieverFgService extends ABaseFgService<Integer> implements LocationRetriever.LocCb
{
    private static final String CLASS_TAG = LocationRetrieverFgService.class.getSimpleName();

    public static final int NOT_ID = SmsResendFgService.NOT_ID - 1;

    private static final String TITLE_PREFIX = "Request from ";
    private static final String STATUS_PREFIX = "Response ";

    private ArrayList<String> mDetails;
    private String mCallStatus;
    private String mSmsText;

    private Integer mGpsTimeout;

    public LocationRetrieverFgService() {
        super(TITLE_PREFIX, STATUS_PREFIX, NOT_ID, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mDetails = new ArrayList<>();
        mCallStatus = "UNKNOWN";

        // it is ok to get it here. subsequent calls to start will not start a new GPS fix
        // when location cb returns, all calls will be stopped (queue will be drained)
        // and service onDestroy called
        mGpsTimeout = SmsLoc_Settings.GPS_TIMEOUT.getInt(this);

        // create custom service notification
        mServiceNotification = mNotHandler.createOngoigNotification(
    "Location request",
                String.format("Waiting for GPS fix. Timeout is %s min", mGpsTimeout),
                null
        );
    }

    @Override
    public void onDestroy() {
        if (mDetails != null) {
            mDetails.clear();
        }

        mDetails = null;
        mCallStatus = null;
        mSmsText = null;
        mGpsTimeout = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        // Create a new entry for the queue
        final QueueEntry<Integer> qEntry = new QueueEntry<>(
                startId,
                intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR),
                intent.getIntExtra(SmsLoc_Intents.EXTRA_WAKE_LOCK_ID, SmsReceiver.INVALID_WAKE_LOCK_ID)
        );

        // Start foreground service within 5s after call to onStartCommand
        if (enqueueEntry(qEntry)) {
            if (mQueue.size() == 1) {
                // if this is the first in queue, start GPS, otherwise we assume it is running
                mDetails.clear();
                mCallStatus = "OK";
                mSmsText = SmsUtils.RESPONSE_CODE + SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR;
                LocationRetriever.getLocationWithGPS(
                        (long) mGpsTimeout * Utils.MIN_2_MS, this, this
                );
            }
        }
        return START_NOT_STICKY;
    }

    // IMPL
    @Override
    protected boolean processEntry(QueueEntry<Integer> qEntry) {
        return SmsUtils.sendSms(this, qEntry.addr(), mSmsText);
    }
    // OVERRIDES
    @Override
    protected void onProcessEntryDone(QueueEntry<Integer> queueEntry) {
        super.onProcessEntryDone(queueEntry);
        SmsReceiver.releaseWakeLock(queueEntry.data());
    }

    // LOCATION RECEIVER
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

        mSmsText = SmsUtils.RESPONSE_CODE + gpsData.toSmsText();
        // In some bizarre situation where we get crazy amounts of location requests,
        // this could loop forever but it is not a realistic scenario
        getMainExecutor().execute(() -> {
                drainQueue(
                    new ProcessResult(mCallStatus, "Sms send ERROR"),
                    mDetails.isEmpty() ? null : mDetails.toString(), getMainExecutor());
                });
        // TODO-low my location update intent
    }
}
