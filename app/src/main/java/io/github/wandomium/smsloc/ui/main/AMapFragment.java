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
package io.github.wandomium.smsloc.ui.main;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.LayoutRes;

import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.mapdata.AMapTracksDisplay;
import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcvr;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.security.InvalidKeyException;

public abstract class AMapFragment extends ABaseFragment
{
    protected AMapTracksDisplay mTracksDisplay;
    protected GpsData mLastUpdateLoc = null;
    protected String mLastUpdateAddr = null;

    public AMapFragment(@LayoutRes int layout)
    {
        super(layout);
    }

    protected abstract void _clearPopups();
    protected abstract void _zoomToLastPoint();

//    Should be done in child if necessary @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//    public void onDestroyView()
//    public boolean onMapClick(@NonNull LatLng point)

    @Override
    protected void _createBroadcastReceivers() {
        /** HANDLE PEOPLE DATA UPDATES */
        mReceiverList.add(new ABaseBrdcstRcvr<AMapFragment>(AMapFragment.this,
                new String[]{SmsLoc_Intents.ACTION_PERSON_UPDATE, SmsLoc_Intents.ACTION_PERSON_REMOVED}) {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (mParent.get() == null) {
                    return;
                }
                final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
                switch (intent.getAction()) {
                    case SmsLoc_Intents.ACTION_PERSON_REMOVED:
                        mParent.get().mTracksDisplay.removeTrack(addr);
//                        mParent.get()._applyBounds();
                        break;
                    case SmsLoc_Intents.ACTION_PERSON_UPDATE:
                        /** TODO: placeholder - support for initials or color change, not implemented anywhere */
                        break;
                }
            }
        });

        /** HANDLE LOCATION DATA UPDATES */
        mReceiverList.add(new ABaseBrdcstRcvr<AMapFragment>(AMapFragment.this,
                new String[]{SmsLoc_Intents.ACTION_NEW_LOCATION, SmsLoc_Intents.ACTION_DAY_DATA_CLR}) {
            // this always runs on the main thread
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (mParent.get() == null) {
                    return;
                }
                switch (intent.getAction()) {
                    case SmsLoc_Intents.ACTION_NEW_LOCATION:
                        final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
                        //responses from people not on the list are still stored. This will not be null
                        final GpsData location = SmsDayDataFile.getInstance(context).getDataEntry(addr).getLastValidLocation();
                        try {
                            mParent.get().mTracksDisplay.addLocation(addr, location);
                            mParent.get().mLastUpdateLoc  = location;
                            mParent.get().mLastUpdateAddr = addr;
                            mParent.get()._zoomToLastPoint();
                            //should be last so we don't remove if not needed
                            mParent.get()._clearPopups();
                        } catch (InvalidKeyException e) {
                        } //responses that are not in the list
                        break;
                    case SmsLoc_Intents.ACTION_DAY_DATA_CLR:
                        mParent.get().mTracksDisplay.removeAll();
//                        mParent.get()._applyBounds();
                        //mParent.get().mBoundsHandler.clear();
                        mParent.get()._clearPopups();
                        break;
                }
            }
        });
    }

    public static String markerDataString(GpsData gpsData)
    {
        return String.format("%s\nSpeed: %d kmh\nAltitude: %d m\nBattery: %d%%",
                Utils.msToStr(gpsData.utc), gpsData.v_kmh, gpsData.alt_m, gpsData.bat_prct);
    }
    public static String markerDataString(GpsData gpsData, Integer gnd_m)
    {
        return String.format("%s\nSpeed: %d kmh\nAltitude: %d m\nBattery: %d%%\nAGL: ~%s m",
                Utils.msToStr(gpsData.utc), gpsData.v_kmh, gpsData.alt_m, gpsData.bat_prct,
                (gnd_m == null) ? "NA" : Integer.toString(gpsData.alt_m - gnd_m));
    }
}