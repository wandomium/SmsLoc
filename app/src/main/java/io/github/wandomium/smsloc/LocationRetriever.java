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

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Timer;
import java.util.TimerTask; //TODO: TimerTask will never become a demon. keeps alive
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.wandomium.smsloc.data.file.LogFile;

@SuppressLint("MissingPermission")
public class LocationRetriever implements Consumer<Location>, LocationListener
{
    private Timer mToutTimer;
    private LocCb mLocCb;

    private CancellationSignal mCancelSignal;
    private Context mCtx; //we need this stored for API29 to unregister calls

    private boolean mCallFinished = false;

    private LocationRetriever(LocCb cb, Context ctx) {
        mLocCb = cb;
        mCtx = ctx;
    }

    @FunctionalInterface
    public interface LocCb {
        void onLocationRcvd(Location loc, String msg);
    }

    /** Executes one call to getLocations and then terminates
     */
    public static void getLocation(long delay_ms, @NonNull LocCb cb, @NonNull Context ctx) {
        new LocationRetriever(cb, ctx)._getLocation(delay_ms, LocationManager.GPS_PROVIDER);
    }

    /**
     * This one should only be used when the user is running the app (same
     * wat the map gets location indoors).
     * <p>
     * It is dicey to use it with requestLocation because it will also work
     * indoor and this is an outdoor app
     */
    @Deprecated
    public static void getLocationWithNetwork(long delay_ms, @NonNull LocCb cb, @NonNull Context ctx) {
        new LocationRetriever(cb, ctx)._getLocation(delay_ms, LocationManager.NETWORK_PROVIDER);
    }

    /** Consumer<Location> method (for API >= 30)
     * Can have null if call failed
     */
    @Override
    public final void accept(Location location) {
        _finishCall(location, location == null ? "GPS fix OK" : "GPS fix FAIL");
    }
    /** LocationListener method (API 29 version of accept method)
     * only called on success
     */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        _finishCall(location, "GPS fix OK");
    }

    private void _getLocation(final long delay_ms, String provider)
    {
        mToutTimer    = new Timer();

        LocationManager locMngr = (LocationManager) mCtx.getSystemService(Context.LOCATION_SERVICE);

        // TODO: (This only works with play services enabled no-go for F-droid)
        // When we will target <=31 we can remove the timertask and simply set the timeout
        // trough LocationRequest
        // LocationRequest.Builder locRequest = new LocationRequest.Builder(0).setDurationMillis(delay_ms).set;

        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                // this one waits for MAX_SINGLE_LOCATION_TIMEOUT_MS = 30 * 1000. We want control over wait time
                // locMngr.requestSingleUpdate(provider, this, null);
                locMngr.requestLocationUpdates(provider, 1000, 500, this);
            }
            else {
                mCancelSignal = new CancellationSignal();
                // This method may return locations from the very recent past (on the order of several seconds),
                // but will never return older locations (for example, several minutes old or older).
                // Checked: consumer is null-ed so we are safe here
                locMngr.getCurrentLocation(
                        provider, mCancelSignal, Executors.newSingleThreadExecutor(), this);
            }
            mToutTimer.schedule(new ToutTimerTask(), delay_ms);
        }
        catch (IllegalStateException e) { //task canceled/completed, itd.
            _finishCall(null, e.getMessage());
        }
        catch (SecurityException e) {
            LogFile.getInstance(mCtx).addLogEntry("ERROR: Could not get GPS fix: Missing permissions");
            _finishCall(null, e.getMessage());
        }
    }

    // Update with new location and clear all references
    private void _finishCall(Location location, String msg)
    {
        // For API29: Handles a race condition when timer expires but we get a
        // location update before we manage to call removeUpdates
        if (mCallFinished) {
            Log.d("LocationRetriever", "double call of _callFinished");
            return;
        }
        mCallFinished = true;

        mToutTimer.cancel();
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ((LocationManager) mCtx.getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this);
        }
        else {
            mCancelSignal.cancel();
        }

        mLocCb.onLocationRcvd(location, msg);

        // null all references to avoid dangling
        mCancelSignal = null;
        mCtx   = null;
        mLocCb = null;
    }

    //using CountdownLatch instead of timer task caused
    //app not responding reports
    private class ToutTimerTask extends TimerTask
    {
        @Override
        public void run() {
            _finishCall(null, "GPS fix timeout");
        }
    }
}