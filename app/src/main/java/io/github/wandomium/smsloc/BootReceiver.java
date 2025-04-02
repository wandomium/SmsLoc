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
package io.github.wandomium.smsloc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.wandomium.smsloc.toolbox.NotificationHandler;

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        final String action = intent.getAction();

        if(action != null) {
            if (action.equals(Intent.ACTION_BOOT_COMPLETED) ||
                action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)) {
                NotificationHandler.getInstance(context)
                    .createAndPostNotification("Click here to enable SmsLoc", "", null);
            }
        }
    }
}
