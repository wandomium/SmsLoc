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
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;

import java.util.Timer;
import java.util.TimerTask; //TODO: TimerTask will never become a demon. keeps alive
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SuppressLint("MissingPermission")
public class LocationRetriever implements Consumer<Location>
{
    protected Timer mToutTimer;
    protected CancellationSignal mCancelSignal;

    protected LocCb mLocCb;

    @FunctionalInterface
    public interface LocCb {
        void onLocationRcvd(Location loc, String msg);
    }

    /** Executes one call to getLocations and then terminates
     */
    public static void getLocation(long delay_ms, LocCb cb, Context ctx) {
        new LocationRetriever(cb)._getLocation(delay_ms, LocationManager.GPS_PROVIDER, ctx);
    }

    /**
     * This one should only be used when the user is running the app (same
     * wat the map gets location indoors).
     *
     * It is dicey to use it with requestLocation because it will also work
     * indoor and this is an outdoor app
     */
    @Deprecated
    public static void getLocationWithNetwork(long delay_ms, LocCb cb, Context ctx)
    {
        new LocationRetriever(cb)._getLocation(delay_ms, LocationManager.NETWORK_PROVIDER, ctx);
    }

    private LocationRetriever(LocCb cb) {
        mLocCb = cb;
    }

    private void _getLocation(final long delay_ms, String provider, Context ctx)
    {
        mToutTimer    = new Timer();
        mCancelSignal = new CancellationSignal();

        LocationManager locMngr = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        // TODO:
        // When we will target <=31 we can remove the timertask and simply set the timeout
        // trough LocationRequest
        // LocationRequest.Builder locRequest = new LocationRequest.Builder(0).setDurationMillis(delay_ms).set;

        try {
            // This method may return locations from the very recent past (on the order of several seconds),
            // but will never return older locations (for example, several minutes old or older).
            // Checked: consumer is nulled so we are safe here
            locMngr.getCurrentLocation(
                provider, mCancelSignal, Executors.newSingleThreadExecutor(), this);
            mToutTimer.schedule(new ToutTimerTask(), delay_ms);
        }
        catch (IllegalStateException e) {} //task canceled/completed, itd.
        catch (SecurityException e) {
            accept(null, e.getMessage());
        }
    }

    // Location Consumer method
    @Override
    public final void accept(Location location) {
        accept(location, "");
    }
    // Update with new location and clear all references
    protected void accept(Location location, String msg)
    {
        // prevent overriding, possible memory leak if not called
        // this also clears task queue so the ToutTimerTask we have should
        // also be eligible for garbage collections
        // will always clear the list
        mToutTimer.cancel();
        mCancelSignal = null;

        mLocCb.onLocationRcvd(location, msg);
        mLocCb = null;
    }

    //using CountdownLatch instead of timer task caused
    //app not responding reports
    private class ToutTimerTask extends TimerTask
    {
        @Override
        public void run() {
            mCancelSignal.cancel();
            accept(null, "GPS fix timeout");
        }
    }
}