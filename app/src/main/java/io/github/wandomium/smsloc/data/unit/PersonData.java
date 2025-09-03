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

import android.graphics.Color;

import androidx.annotation.NonNull;

import io.github.wandomium.smsloc.data.base.DataUnit;
import io.github.wandomium.smsloc.data.base.DataUnitFactory;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/** this data is fixed property of the application
 * user can add people he wants to include
 * this also defines the whitelist */
public final class PersonData implements Cloneable, DataUnit<PersonData>
{
    //this one is final because we use it as unique identifier in all the settings/files
    private final String mAddr;

    // TODO In future versions we might support selecting color or display name, so these are not final
    private String  mDisplayName;
    private String  mInitials;
    private int     mColor = Color.GRAY;

    public PersonData(@NonNull String addr, String name) {
        this.mAddr = addr.replaceAll(" ", "");
        setDisplayName(name);
    }

    public String getAddr() { return mAddr;}

    public void setDisplayName(String name) {
        if (name == null) {
            mDisplayName = this.mAddr;
            mInitials = "??";
        }
        else {
            this.mDisplayName = name;
            this.mInitials = name.replaceAll("(\\B[a-zA-Z])[a-zA-Z]* ?", "");
        }
    }
    public String getDisplayName() { return mDisplayName;}
    public String getInitials() { return mInitials;}

    public void setColor(int color) { mColor = color;}
    public int getColor() { return mColor;}

    public static PersonData fromJson(String json) {
        try {
            PersonData retval = (new Gson()).fromJson(json, PersonData.class);

            return
                (retval.getId() == null) ? null : retval;
        }
        catch (JsonParseException e)  { return null; }
    }

    public String toJson() {
        return (new Gson()).toJson(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof PersonData) {
            return ((PersonData)other).mAddr.equals(this.mAddr);
        }
        return false;
    }

//////////
//interfaces used for data file
//////////
    @Override
    public boolean validate() {
        return mAddr != null;
    }
    @Override
    public String getId() {
        return mAddr;
    }
    @NonNull
    @Override
    public PersonData getUnitCopy() {
        try {
            return (PersonData) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // we have strings and primitives, this should never happen
        }
    }

    public static final class UnitFactory implements DataUnitFactory<PersonData> {
        @Override
        public PersonData createUnit(String id) {
            return new PersonData(id, null);
        }
    }
}
