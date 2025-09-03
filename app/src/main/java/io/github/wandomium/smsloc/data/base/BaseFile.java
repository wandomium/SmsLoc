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
package io.github.wandomium.smsloc.data.base;

import android.content.Context;
import android.util.Log;

import io.github.wandomium.smsloc.defs.SmsLoc_Intents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseFile implements AutoCloseable
{
    protected static final String CLASS_TAG = BaseFile.class.getSimpleName();

    public enum StorageLocation {EXTERNAL, INTERNAL}
    public enum FileType {settings, data, log}

    protected final Object LOCK;

    protected final Context mAppContext;

    //protected boolean mFileError = false;
    protected boolean mDiskUnsynced = false;

    protected final Path mFilePath;
    protected final String mFilename;
    protected ExecutorService mExecutor;

    protected abstract void _writeCmd() throws IOException;
    protected abstract void _loadCmd()  throws IOException;

    /**
     * This should be called from deriving class - singleton - in a locked state
     */
    protected BaseFile(FileType fileType, String filename, Context context, final Object lock)
    {
        LOCK = lock;
        mFilename = filename;
        //Application context has to be used to avoid memory leaks.
        //prevent user mistakes of providing the wrong one.
        mAppContext = context.getApplicationContext();

        //keep settings in internal so that other apps/user cannot manipulate whitelist
        //other files can be kept in external
        StorageLocation storeLoc =
                fileType == FileType.settings ? StorageLocation.INTERNAL : StorageLocation.EXTERNAL;

        File directory =
            storeLoc == StorageLocation.EXTERNAL ?
                    new File(mAppContext.getExternalFilesDir(null), fileType.name()) :
                    new File(mAppContext.getFilesDir(), fileType.name()); // INTERNAL is DEFAULT

        if (!directory.exists() && !directory.mkdirs()) {
            // hello, we have  problem
            Log.wtf(CLASS_TAG, "Cannot create directory for file store. Exiting");
        }

        final File file = new File(directory, filename);
        mFilePath = file.toPath();

        try {
            final boolean newDay = file.createNewFile();   //creates if it does not exist
            // TODO: clean old files
        }
        catch (IOException e) {
            //TODO-low
            //this will almost certainly never be an issue in non-DEBUG mode because we
            //write to internal not external storage
            Log.wtf(CLASS_TAG, String.format("Error creating file: %s\n\tdetails: %s",
                    filename, e.getMessage()));
            mDiskUnsynced = true;
            return;
        }

        Log.d(CLASS_TAG, String.format("File opened: %s", mFilePath.toAbsolutePath().toString()));
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
            mDiskUnsynced = false;
        }
        catch (IOException e) {
            _handleIOException("load", e);
        }
    }

    public void writeFileBlocking()
    {
        synchronized (LOCK) {
            if (!mDiskUnsynced) {
                return;
            }
            try {
                _writeCmd();
                mDiskUnsynced = false;
            } catch (IOException e) {
                _handleIOException("write", e);
            }
        }
    }

    public void writeFileAsync() {
        _getExecutor().execute(this::writeFileBlocking);
    }

    private void _handleIOException(final String operation, final IOException e)
    {
        final String errStr =
                String.format("Error %s file: %s!\n  -Error detail: %s",
                        operation, mFilename, e.getMessage());

        Log.e(CLASS_TAG, errStr);
        mAppContext.sendBroadcast(SmsLoc_Intents.generateErrorIntent(mAppContext, errStr));

        //try to fix/sync this
        mDiskUnsynced = true;
    }

    protected ExecutorService _getExecutor() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadExecutor();
        }
        return mExecutor;
    }

    @Override
    public void close() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        mExecutor = null;
    }
}