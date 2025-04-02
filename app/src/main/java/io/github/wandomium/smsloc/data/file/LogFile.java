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
import android.content.Intent;

import io.github.wandomium.smsloc.data.base.BaseFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

public class LogFile extends BaseFile
{
    private static LogFile mInstance = null;
    private static final Object GET_INSTANCE_LOCK = new Object();

    private ArrayList<String> mLogEntries;

    private LogFile(Context context, final Object lock)
    {
        super(
                FileType.data,
                String.format("%s-%s", SmsLoc_Common.Consts.LOG_FILENAME, Utils.getDateForFilename()),
                context, lock
        );

        loadFile();
    }

    public static LogFile getInstance(Context context)
    {
        synchronized (GET_INSTANCE_LOCK)
        {
            if (mInstance == null) {
                mInstance = new LogFile(context, GET_INSTANCE_LOCK);
            }
            return mInstance;
        }
    }

    @Override
    protected void _loadCmd() throws IOException
    {
        if (mLogEntries != null)
        {
            mLogEntries.clear();
        }
        mLogEntries = new ArrayList<String>(Files.readAllLines(mFilePath));
    }

    @Override
    protected void _writeCmd() throws IOException
    {
        Files.write(mFilePath, mLogEntries);
    }

    public void addLogEntry(String msg)
    {
        synchronized (LOCK)
        {
            mLogEntries.add(0,msg);
            mLogEntries.add(0, Utils.msToStr(System.currentTimeMillis()));
            mDiskUnsynched = true;
            writeFile_unlocked();
        }

        mAppContext.sendBroadcast(new Intent(SmsLoc_Intents.ACTION_LOG_UPDATED));
        return;
    }

    public final ArrayList<String> readLog()
    {
        //we count on other entities that they will not mess with this log
        return mLogEntries;
    }

    public void clearLog()
    {
        synchronized (LOCK)
        {
            mLogEntries.clear();
            mDiskUnsynched = true;
            writeFile_unlocked();
        }
    }
}
