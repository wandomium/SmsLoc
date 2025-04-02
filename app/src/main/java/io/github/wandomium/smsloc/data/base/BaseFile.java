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
package io.github.wandomium.smsloc.data.base;

import android.content.Context;
import android.util.Log;

import io.github.wandomium.smsloc.defs.SmsLoc_Intents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public abstract class BaseFile
{
    protected static final boolean DEBUG = true;
    protected static final String CLASS_TAG = BaseFile.class.getSimpleName();

    public enum StorageLocation {EXTERNAL, INTERNAL};
    public enum FileType {settings, data, log};

    protected final Object LOCK;

    protected final Context mAppContext;

    //protected boolean mFileError = false;
    protected boolean mDiskUnsynched = false;

    protected final Path mFilePath;
    protected final String mFilename;

    protected abstract void _writeCmd() throws IOException;
    protected abstract void _loadCmd()  throws IOException;

    /**
     * This should be called from deriving class - singelton - in a locked state
     */
    protected BaseFile(FileType fileType, String filename, Context context, final Object lock)
    {
        LOCK = lock;
        mFilename = filename;
        // Application context has to be used to avoid memory leaks.
        // Prevent user mistakes of providing the wrong one.
        mAppContext = context.getApplicationContext();

        //keep settings in internal so that other apps/user cannot manipulate whitelist
        //other files can be kept in external
        StorageLocation storeLoc =
                fileType == FileType.settings ? StorageLocation.INTERNAL : StorageLocation.EXTERNAL;

        File directory;
        switch (storeLoc)
        {
            case EXTERNAL:
                directory = new File(mAppContext.getExternalFilesDir(null), fileType.name());
                break;
            case INTERNAL:
            default:
                directory = new File(mAppContext.getFilesDir(), fileType.name());
        }
        directory.mkdirs();

        final File file = new File(directory, filename);
        mFilePath = file.toPath();

        boolean newDay = false;

        try
        {
            newDay = file.createNewFile();   //creates if it does not exist
        }
        catch (IOException e)
        {
            //TODO-low
            //this will almost certainly never be an issue in non-DEBUG mode because we
            //write to internal not external storage
            Log.e(CLASS_TAG,
                    String.format("Error creating file: %s\n\tdetails: %s", filename, e.getMessage()));
            mDiskUnsynched = true;
            return;
        }

        Log.d(CLASS_TAG, String.format("File opened: %s", mFilePath.toAbsolutePath().toString()));
    }

    public final void writeFile()
    {
        synchronized (LOCK)
        {
            writeFile_unlocked();
        }
    }

    public final Object getLockObject()
    {
        return LOCK;
    }

    protected void loadFile()
    {
        Log.i(CLASS_TAG, String.format("Loading file: %s", mFilename));

        try {
            _loadCmd();
            mDiskUnsynched = false;
        }
        catch (IOException e) {
            final String errStr =
                String.format("Error loading file: %s! Overriding with new data.\n  -Error detail: %s",
                    mFilename, e.getMessage());

            Log.e(CLASS_TAG, errStr);
            mAppContext.sendBroadcast(SmsLoc_Intents.generateErrorIntent(errStr));

            //try to fix/sync this
            mDiskUnsynched = true;
            writeFile_unlocked();
        }
    }

    /**
     * Always call from lock
     */
    public void writeFile_unlocked()
    {
        if (!mDiskUnsynched) {
            return;
        }
        try {
            _writeCmd();
            mDiskUnsynched = false;
        }
        catch (IOException e) {
            Log.e(CLASS_TAG,
                String.format("Error writing file: %s\n\tdetails: %s", mFilename, e.getMessage()));
            mDiskUnsynched = true;
        }
    }
}