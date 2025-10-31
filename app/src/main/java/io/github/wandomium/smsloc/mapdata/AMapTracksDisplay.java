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
    public interface IMapTrack {
        void updateData(GpsData point);
        boolean equals(Object o);
    }

    protected abstract IMapTrack _createTrack(PersonData person);


/***** Impl *****/
    protected final PeopleDataFile PEOPLEDATA; //we need context for this
    protected final HashMap<String, IMapTrack> mTracks;

    public AMapTracksDisplay(PeopleDataFile peopledata)
    {
        PEOPLEDATA = peopledata;
        mTracks = new HashMap<>();
    }

    public void initFromDayData(HashMap<String, SmsLocData> data)
    {
        for (String addr : data.keySet()) {
            //noinspection DataFlowIssue - this cannot return null
            if (!data.get(addr).hasLocationData()) {
                continue;
            }
            IMapTrack track = _getOrCreateTrack(addr);
            //noinspection DataFlowIssue - this cannot return null
            for (GpsData loc : data.get(addr).getLocationData()) {
                track.updateData(loc);
            }
        }
    }
    public void addLocation(String addr, GpsData location)
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
    protected IMapTrack _getOrCreateTrack(String addr)
    {
        if (mTracks.containsKey(addr)) {
            return mTracks.get(addr);
        }
        PersonData person = PEOPLEDATA.getDataEntry(addr);
        if (person == null) {
            // Create a default person if it is not in the list
            person = new PersonData(addr, null);
        }

        IMapTrack track = _createTrack(person);
        mTracks.put(addr, track);

        return track;
    }
}
