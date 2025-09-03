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
package io.github.wandomium.smsloc.defs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import io.github.wandomium.smsloc.BuildConfig;

public class SmsLoc_Intents
{
    public static final String EXTRA_ADDR         = "Addr";
    public static final String EXTRA_DEFOPT       = "Extra";
    public static final String EXTRA_WAKE_LOCK_ID = "WakeLockId";

    public static final String ACTION_REQUEST_RCVD       = BuildConfig.APPLICATION_ID + ".intent.req_rcvd";
    public static final String ACTION_NEW_LOCATION       = BuildConfig.APPLICATION_ID + ".intent.new_location";
    public static final String ACTION_REQUEST_SENT       = BuildConfig.APPLICATION_ID + ".intent.req.sent";
    public static final String ACTION_RESPONSE_RCVD      = BuildConfig.APPLICATION_ID + ".intent.resp_rcvd";
    public static final String ACTION_DAY_DATA_CLR       = BuildConfig.APPLICATION_ID + ".intent.day_data_clr";
    public static final String ACTION_NEW_PERSON         = BuildConfig.APPLICATION_ID + ".intent.new_person";
    public static final String ACTION_PERSON_REMOVED     = BuildConfig.APPLICATION_ID + ".intent.person_removed";
    public static final String ACTION_PERSON_UPDATE      = BuildConfig.APPLICATION_ID + ".intent.person_update";
    public static final String ACTION_MY_LOCATION_UPDATE = BuildConfig.APPLICATION_ID + ".intent.my_location_update";
    public static final String ACTION_NOT_WHITELISTED    = BuildConfig.APPLICATION_ID + ".intent.not_whitelisted";
    public static final String ACTION_LOG_UPDATED        = BuildConfig.APPLICATION_ID + ".intent.log_updated";
    public static final String ACTION_ERROR              = BuildConfig.APPLICATION_ID + ".intent.error";

    //TODO update this
    public static android.content.Intent generateIntent(Context ctx, final String addr, final String action)
    {
        android.content.Intent intent = new android.content.Intent(action);
        intent.setPackage(ctx.getPackageName());
        intent.putExtra(EXTRA_ADDR, addr);

        return intent;
    }
    public static android.content.Intent generateErrorIntent(Context ctx, final String msg)
    {
        android.content.Intent intent = new android.content.Intent(ACTION_ERROR);
        intent.setPackage(ctx.getPackageName());
        intent.putExtra(EXTRA_DEFOPT, msg);

        return intent;
    }
    /** @noinspection SpellCheckingInspection*/
    public static Intent generateBgAutostartIntent()
    {
        Intent intent = new Intent();
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        switch (manufacturer) {
            case "xiaomi":
                intent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                break;
            case "oppo":
                intent.setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                break;
            case "vivo":
                intent.setComponent(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
                break;
            case "letv":
                intent.setComponent(new ComponentName("com.letv.android.letvsafe",
                        "com.letv.android.letvsafe.AutobootManageActivity"));
                break;
            case "asus":
                intent.setComponent(new ComponentName("com.asus.mobilemanager",
                        "com.asus.mobilemanager.entry.FunctionActivity"));
                break;
            case "huawei":
                intent.setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                break;
            default:
                intent = null;
                break;
        }
        return intent;
    }
}
