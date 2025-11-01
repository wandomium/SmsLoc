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
package io.github.wandomium.smsloc.data.unit;

import androidx.annotation.NonNull;

import io.github.wandomium.smsloc.data.base.DataUnit;
import io.github.wandomium.smsloc.data.base.DataUnitFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Arrays;

/**
 *  stored per address */
public final class SmsLocData implements Cloneable, DataUnit<SmsLocData>
{
    private final String addr;

    private long lastReqTime, lastRespTime = 0;
    private boolean lastRespValid = false;
    private int numSentReq, numResponses, numReceivedReq = 0;
    private GpsData[] gpsDataPoints = new GpsData[0]; //private, keep sorted chronologically

    public SmsLocData(String addr) {
        this.addr = addr;
    }

    public static SmsLocData fromJson(String json) {
        try {
            SmsLocData retval = (new Gson()).fromJson(json, SmsLocData.class);
            if (retval.getId() == null) {
                return null;
            }
            // Others are primitives and initialized to 0/false if missing which is ok
            if (retval.gpsDataPoints == null) { retval.gpsDataPoints = new GpsData[0]; }
            return retval;
        }
        catch (JsonParseException e)  { return null; }
    }
    public String toJson() {
        return (new Gson()).toJson(this);
    }

    public long lastReqTime()           { return lastReqTime; }
    public long lastRespTime()          { return lastRespTime; }
    public boolean lastResponseValid()  { return lastRespValid; }
    public int numSentReq()             { return numSentReq; }
    public int numReceivedReq()         { return numReceivedReq; }
    public int numResponses()           { return numResponses; }

    public void requestSent()     { numSentReq++; lastReqTime = System.currentTimeMillis(); }
    public void requestReceived() { numReceivedReq++; }
    public void responseReceived(GpsData location) {
        numResponses++;
        lastRespTime = System.currentTimeMillis();

        if (location == null || !location.dataValid()) {
            lastRespValid = false;
        }
        else {
            _addLocation(location);
            lastRespValid = true;
        }
    }

    /* TODO find a better name, there might be requests pending even if the
       one was just received
     */
    public boolean requestPending() {
        //in reality we only care if there was at least one response that is late enough
        //missed sms-es in between are not dramatic
        return lastRespTime < lastReqTime;
    }

    public boolean locationUpToDate() {
        /* TODO check again if this is true
            - one thing that comes to mind is an old gps fix, maybe add a tolerance window
           if last location is valid and last response ts is > request ts
           than this should be valid.
           Also, in this case we rely on timestamps from the same system
         */
        return
            !requestPending() && lastRespValid;
    }

    public boolean hasLocationData() {
        return gpsDataPoints.length != 0;
    }
    @NonNull
    public GpsData[] getLocationData()  {
        //array contents themselves are immutable which is all we care about really
        return gpsDataPoints.clone();
    }
    public GpsData getLastValidLocation() {
        return (gpsDataPoints.length == 0) ?
             null : gpsDataPoints[gpsDataPoints.length - 1];
    }

    private void _addLocation(@NonNull GpsData gpsData) {

        if (gpsDataPoints.length == 0) {
            gpsDataPoints = new GpsData[]{gpsData};
        }
        else {
            //This is being extra careful, normally locations from a
            //specific address will have ascending timestamps
            //I guess the only problem could be if the sender is without
            //a network for some time. And even texts might go out in the
            //correct order - probably depends on the app
            //TODO check
            ArrayList<GpsData>
                    tmpList = new ArrayList<>(Arrays.asList(gpsDataPoints));
            tmpList.add(gpsData);
            tmpList.sort(new GpsData.SortByGpsUtc(1));
            gpsDataPoints = tmpList.toArray(new GpsData[0]);
        }
    }

//////////
//interfaces used for data file
//////////
    @Override
    public boolean validate() {
        return addr != null;
    }
    @Override
    public String getId() {
        return addr;
    }

    @NonNull
    @Override
    public SmsLocData getUnitCopy()
    {
        SmsLocData copy;
        try {
            copy = (SmsLocData) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // we should never get here
        }

        //array of immutable objects
        copy.gpsDataPoints = gpsDataPoints.clone();
        return copy;
    }

    public final static class UnitFactory implements DataUnitFactory<SmsLocData> {
        @Override
        public SmsLocData createUnit(String id) {
            return new SmsLocData(id);
        }
    }
}
