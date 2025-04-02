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
package io.github.wandomium.smsloc.mapdata;

import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.data.unit.PersonData;
import io.github.wandomium.smsloc.data.unit.SmsLocData;

import java.security.InvalidKeyException;
import java.util.HashMap;

public abstract class AMapTracksDisplay
{

/***** Abstractions *****/
    @FunctionalInterface
    interface IMapTrack {
        void updateData(GpsData point);
    }

    protected abstract IMapTrack _createTrack(PersonData person);


/***** Impl *****/
    protected PeopleDataFile PEOPLEDATA; //we need context for this
    protected HashMap<String, IMapTrack> mTracks;

    public AMapTracksDisplay(PeopleDataFile peopledata)
    {
        PEOPLEDATA = peopledata;
        mTracks = new HashMap<String, IMapTrack>();
    }

    public void initFromDayData(HashMap<String, SmsLocData> data)
    {
        for (String addr : data.keySet()) {
            if (!data.get(addr).hasLocationData()) {
                continue;
            }
            try {
                IMapTrack track = _getOrCreateTrack(addr);
                for (GpsData loc : data.get(addr).getLocationData()) {
                    track.updateData(loc);
                }
            } catch (InvalidKeyException e) {}
        }
    }
    public void addLocation(String addr, GpsData location) throws InvalidKeyException
    {
        _getOrCreateTrack(addr).updateData(location);
    }
    public void removeAll() {
        mTracks.clear();
    }
    public IMapTrack removeTrack(String addr) {
        return mTracks.remove(addr);
    }


/***** Internal *****/
    protected IMapTrack _getOrCreateTrack(String addr) throws InvalidKeyException
    {
        if (mTracks.containsKey(addr)) {
            return mTracks.get(addr);
        }
        final PersonData person = PEOPLEDATA.getDataEntry(addr);
        if (person == null) {
            throw new InvalidKeyException("Got loc from unlisted");
        }

        IMapTrack track = _createTrack(person);
        mTracks.put(addr, track);

        return track;
    }



//    protected abstract class AMapTrack
//    {
//        protected PersonData mPerson;
//
//        public AMapTrack(PersonData personData) {
//            mPerson = personData.getUnitCopy();
//        }
//        protected void updateData(GpsData point) { updateData(new GpsData[]{point}); }
//        protected void onRemove() {};
//
//        protected abstract void updateData(GpsData[] points);
//    }
}
