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
package io.github.wandomium.smsloc.data.unit;

import android.graphics.Color;

import io.github.wandomium.smsloc.data.base.DataUnit;
import io.github.wandomium.smsloc.data.base.DataUnitFactory;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

/** this data is fixed property of the application
 * user can add people he wants to include
 * this also defines the whitelist */
public final class PersonData implements Cloneable, DataUnit<PersonData>
{
    //this one is final because we use it as unique identifier in all the settings/files
    public final String  addr;
    //public final String phone_e164;

    public String  displayName;
    public String  initials = "??";
    public Integer color = Color.GRAY;
    public boolean whitelisted = false;

    public PersonData(String addr) {
        this.addr = addr.replaceAll(" ", "");
        this.displayName = this.addr;
    }

    public PersonData(String addr, String name, Integer color, boolean whitelisted) {
        this.addr = addr.replaceAll(" ", "");
        this.displayName = name;
        this.whitelisted = whitelisted;
        this.initials = name.replaceAll("(\\B[a-zA-Z])[a-zA-Z]* ?", "");
        this.color = color;
    }

    public void setDisplayName(String name, boolean generateInitials) {
        this.displayName = name;
        if (generateInitials) {
            this.initials = name.replaceAll("(\\B[a-zA-Z])[a-zA-Z]* ?", "");
        }
    }

    public static PersonData fromJson(String json) {
        try {
            PersonData retval = (new Gson()).fromJson(json, PersonData.class);

            return
                (retval.getId() == null) ? null : retval;
        }
        catch (JsonSyntaxException e) { return null; }
        catch (JsonParseException e)  { return null; }
    }

    public String toJson() {
        return (new Gson()).toJson(this);
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
    @Override
    public PersonData getUnitCopy() {
        try {
            return (PersonData) super.clone();
        } catch (CloneNotSupportedException e) { return null; }
    }

    public static final class UnitFactory implements DataUnitFactory<PersonData> {
        @Override
        public PersonData createUnit(String id) {
            return new PersonData(id);
        }
    }
}
