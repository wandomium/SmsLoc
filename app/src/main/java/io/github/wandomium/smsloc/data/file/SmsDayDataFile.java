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
package io.github.wandomium.smsloc.data.file;

import android.content.Context;

import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.base.DataUnitFile;
import io.github.wandomium.smsloc.data.unit.SmsLocData;
import io.github.wandomium.smsloc.toolbox.Utils;

import com.google.gson.reflect.TypeToken;

//TODO handle going over midnight and day change

public class SmsDayDataFile extends DataUnitFile<SmsLocData>
{
    private static SmsDayDataFile mInstance = null;
    private static final Object GET_INSTANCE_LOCK = new Object();

    private SmsDayDataFile(Context context)
    {
        super(
                FileType.data,
                String.format("%s-%s", SmsLoc_Common.Consts.DAY_DATA_FILENAME, Utils.getDateForFilename()),
                context, GET_INSTANCE_LOCK,
                new TypeToken<SmsLocData[]>() {}.getType(),
                new SmsLocData.UnitFactory()
        );
    }

    public static SmsDayDataFile getInstance(Context context)
    {
        synchronized (GET_INSTANCE_LOCK)
        {
            if (mInstance == null) {
                mInstance = new SmsDayDataFile(context);
            }
            return mInstance;
        }
    }
}
