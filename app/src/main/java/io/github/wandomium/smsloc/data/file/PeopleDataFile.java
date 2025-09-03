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
package io.github.wandomium.smsloc.data.file;

import android.content.Context;

import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.base.DataUnitFile;
import io.github.wandomium.smsloc.data.unit.PersonData;

import com.google.gson.reflect.TypeToken;

public class PeopleDataFile extends DataUnitFile<PersonData> {

    private static PeopleDataFile mInstance = null;
    private static final Object GET_INSTANCE_LOCK = new Object();

    private PeopleDataFile(Context context)
    {
        super(
                FileType.settings, SmsLoc_Common.Consts.PEOPLE_DATA_FILENAME,
                context, GET_INSTANCE_LOCK,
                new TypeToken<PersonData[]>() {}.getType(),
                new PersonData.UnitFactory()
        );
    }
    /*
    It doesn’t matter where our Context came from, because the reference we are holding is safe.
    The application context is itself a singleton, so we aren’t leaking anything by creating
    another static reference to it.
    */
    public static PeopleDataFile getInstance(Context context)
    {
        synchronized (GET_INSTANCE_LOCK)
        {
            if (mInstance == null) {
                mInstance = new PeopleDataFile(context);
            }
            return mInstance;
        }
    }
}
