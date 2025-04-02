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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;

//TODO support export

/**
 * Used to storing data to files, where keys are always address in string
 * address == phone number
 *
 */
public class DataUnitFile<T extends DataUnit<T>> extends BaseFile
{
    private final Type FILE_DATA_FORMAT;
    protected final DataUnitFactory<T> mUnitFactory;

    protected HashMap<String,T> mData;

    /** This should be called from deriving class - singelton - in a locked state
     */
    protected DataUnitFile(FileType fileType, String filename,
                           Context context, final Object lock, Type gsonTypeToken, DataUnitFactory<T> factory)
    {
        super(fileType, filename, context, lock);

        FILE_DATA_FORMAT = gsonTypeToken;
        mUnitFactory = factory;

        loadFile();
    }

    @Override
    protected void _loadCmd() throws IOException
    {
        mData = new HashMap<>();

        Gson gson = new Gson();
        T[] data = null;

        try {
//            Type empMapType = new TypeToken<HashMap<String, T>>() {}.getType();
//            gson.fromJson(new FileReader(mFilename), empMapType);

            final String text = new String(Files.readAllBytes(mFilePath));
            data = gson.fromJson(text, FILE_DATA_FORMAT);
            if (data == null) {
                //null data just means no elements, since the exception is caught
                return;
            }
        }
        catch (JsonParseException e) {
            //mData is already cleared
            throw new IOException(e.getMessage());
        }

        StringBuilder errMsg = new StringBuilder();
        for (T el : data) {
            if (el.getId() != null && el.validate()) {
                mData.put(el.getId(), el);
            } else {
                //we don't throw yet, because we want to keep the elements that are ok
                //they will be rewritten on next call to writeFile
                //in reality, this is already the _loadFile function calling this one,
                //since it tries to recover from issues
                errMsg.append(String.format("Corrupted data element: %s\n", gson.toJson(el)));
                Log.e(CLASS_TAG, errMsg.toString());
            }
        }
        if (errMsg.length() == 0) {
            return;
        }

        throw new IOException(errMsg.toString());
    }

    @Override
    protected void _writeCmd() throws IOException
    {
        //(new Gson()).toJson(mData, new FileWriter(mFilename));
        Files.write(mFilePath, Collections.singleton((new Gson()).toJson(mData.values())));
    }

    public boolean containsId(String addr)
    {
        synchronized (LOCK)
        {
            return mData.containsKey(addr);
        }
    }

    public void removeDataEntry(String addr)
    {
        synchronized (LOCK)
        {
            mData.remove(addr);
            mDiskUnsynched = true;
        }
    }

    public void createOrUpdateDataEntry(String id, T dataItem)
    {
        synchronized (LOCK)
        {
            mData.put(id, dataItem.getUnitCopy());
            mDiskUnsynched = true;
        }
    }

    public T getDataEntry(String id)
    {
        synchronized (LOCK)
        {
            return mData.containsKey(id)
                    ? mData.get(id).getUnitCopy() : null;
        }
    }

    public HashMap<String,T> getDataAll()
    {
        synchronized (LOCK)
        {
            HashMap<String, T> retval = new HashMap<>();
            for (String id : mData.keySet()) {
                retval.put(id, mData.get(id).getUnitCopy());
            }

            return retval;
        }
    }

    /**
     *
     * These can be quite unsafe.
     *      - access to LOCK object (doable)
     *      - reference to internal elements can be obtained and misused
     *
     * These methods should only be called from within a block with acquired lock!!!
     *
     * They directly access the data elements, references should not be stored
     */
    public T referenceOrCreateObject_unlocked(String id)
    {
        if (!mData.containsKey(id)) {
            mData.put(id, mUnitFactory.createUnit(id));
        }

        mDiskUnsynched = true; //we have to assume this
        return mData.get(id);
    }
}


