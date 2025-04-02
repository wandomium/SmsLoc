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
package io.github.wandomium.smsloc.defs;

public class SmsLoc_Intents
{
    public static final String EXTRA_GEODATA      = "GeoData";
    public static final String EXTRA_GEOJSON_PT   = "GeoJsonPt";
    public static final String EXTRA_ADDR         = "Addr";
    public static final String EXTRA_DEFOPT       = "Extra";
    public static final String EXTRA_WAKE_LOCK_ID = "WakeLockId";

    public static final String ACTION_NEW_LOCATION       = SmsLoc_Common.Consts.APP_NAME + ".intent.new_location";
    public static final String ACTION_REQUEST_RCVD       = SmsLoc_Common.Consts.APP_NAME + ".intent.req_rcvd";
    public static final String ACTION_REQUEST_SENT       = SmsLoc_Common.Consts.APP_NAME + ".intent.req.sent";
    public static final String ACTION_RESPONSE_RCVD      = SmsLoc_Common.Consts.APP_NAME + ".intent.resp_rcvd";
    public static final String ACTION_DAY_DATA_CLR       = SmsLoc_Common.Consts.APP_NAME + ".intent.day_data_clr";
    public static final String ACTION_NEW_PERSON         = SmsLoc_Common.Consts.APP_NAME + ".intent.new_person";
    public static final String ACTION_PERSON_REMOVED     = SmsLoc_Common.Consts.APP_NAME + ".intent.person_removed";
    public static final String ACTION_PERSON_UPDATE      = SmsLoc_Common.Consts.APP_NAME + ".intent.person_update";
    public static final String ACTION_MY_LOCATION_UPDATE = SmsLoc_Common.Consts.APP_NAME + ".intent.my_location_update";
    public static final String ACTION_NOT_WHITELISTED    = SmsLoc_Common.Consts.APP_NAME + ".intent.not_whitelisted";
    public static final String ACTION_LOG_UPDATED        = SmsLoc_Common.Consts.APP_NAME + ".intent.log_updated";
    public static final String ACTION_ERROR              = SmsLoc_Common.Consts.APP_NAME + ".intent.error";

    public static final int RESULT_CONTACT_SELECTED = 3; //(Consts.APP_NAME + ".result.contact_selected").hashCode();
    public static final int RESULT_BATT_SETTINGS = 4;

    //TODO update this
    public static android.content.Intent generateIntent(final String addr, final String action)
    {
        android.content.Intent intent = new android.content.Intent(action);
        intent.putExtra(EXTRA_ADDR, addr);

        return intent;
    }
    public static android.content.Intent generateErrorIntent(final String msg)
    {
        android.content.Intent intent = new android.content.Intent(ACTION_ERROR);
        intent.putExtra(EXTRA_DEFOPT, msg);

        return intent;
    }
}
