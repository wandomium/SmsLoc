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
package io.github.wandomium.smsloc.toolbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.wandomium.smsloc.R;

import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.data.file.LogFile;

public class NotificationHandler
{
    private static final String GROUP_ID = "SmsLoc";
    private static final String CHANNEL_ID = "SmsLoc";
    private static final String CHANNEL_NAME = CHANNEL_ID;
    private static final String GROUP_NAME = GROUP_ID;

    //private static final int FOREGROUND_ID = 1;
    private static final int SUMMARY_ID = 0;
    private static final int INIT_ID = 2;

    private static int mNotId = INIT_ID;
    private static boolean mNotRestart = true;

    private final Context mAppContext;
    private final NotificationManagerCompat mNotificationMngr;

    private static NotificationHandler mInstance;

    private final PendingIntent mLauncherIntent;
    private Notification  mGroupingNotification;

    public static synchronized NotificationHandler getInstance(Context context)
    {
        if (mInstance == null) {
            mInstance = new NotificationHandler(context);
        }
        return mInstance;
    }
    private NotificationHandler(Context context)
    {
        mNotId = INIT_ID;
        mNotRestart = true;
        // this one is a singleton so it's ok
        // app context will be alive as long as the app is alive, no weak ref needed
        mAppContext = context.getApplicationContext();
        mNotificationMngr = NotificationManagerCompat.from(mAppContext);

        if (mNotificationMngr.getNotificationChannel(CHANNEL_ID) == null) {
            mNotificationMngr.createNotificationChannel(
                new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH));
        }
        if (mNotificationMngr.getNotificationChannelGroup(GROUP_ID) == null) {
            mNotificationMngr.createNotificationChannelGroup(
                new NotificationChannelGroup(GROUP_ID, GROUP_NAME)
            );
        }

        // notification launcher intent is reused for all notifications
        {
            Intent intent = new Intent(mAppContext, MainActivity.class);
            mLauncherIntent = PendingIntent.getActivity(mAppContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        }
    }

    public boolean areNotificationsBlocked()
    {
        if (!mNotificationMngr.areNotificationsEnabled()) {
            return true;
        }
        //noinspection DataFlowIssue - This can only return null on API < 26. Our min is 29
        if (mNotificationMngr.getNotificationChannel(CHANNEL_ID).getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return true;
        }
        //noinspection DataFlowIssue - This can only return null on API < 28. Our min is 29
        return mNotificationMngr.getNotificationChannelGroup(GROUP_ID).isBlocked();
    }

    /** Create a notification with sane defaults for this app
     *  Notifications are bundled in a group */
    public final Notification createNotification(
            @NonNull String displayName, @NonNull String status, String details)
    {
        return createNotification(
                displayName, status, details, false, false, true, true);
    }

    /** Post notification to a group */
    public final synchronized int postNotification(Notification notification)
    {
        if (mGroupingNotification == null) {
            mGroupingNotification = createGroupingNotification();
        }
        try {
            if (mNotRestart) {
                mNotificationMngr.notify(SUMMARY_ID, mGroupingNotification);
                mNotRestart = false;
            }

            mNotificationMngr.notify(mNotId++, notification);
            return mNotId - 1;  //if we want to update the notification later on
        }
        catch (SecurityException e) {
            LogFile.getInstance(mAppContext).addLogEntry("ERROR: Missing POST_NOTIFICATION permission");
            return mNotId;
        }
    }

    /** @noinspection UnusedReturnValue*/
    public final int createAndPostNotification(String displayName, String status, String detail)
    {
        return postNotification(createNotification(displayName, status, detail));
    }

    /**
     * Create a foreground service notification
     * Not attached to a group and are cancelled by app not user action */
    public final Notification createOngoigNotification(
            @NonNull String displayName, @NonNull String status, String details)
    {
        return createNotification(
            displayName, status, details, false, true, true, false);
    }

    public final void clearAllNotifications()
    {
        mNotificationMngr.cancelAll();
        restartNotifications();
    }

/***** INTERNAL
 * @noinspection SameParameterValue*****/
    protected final Notification createNotification(
            @NonNull String title, @NonNull String content, String extra,
            boolean persist, boolean isOngoing, boolean enAppLaunch, boolean useGroup)
    {
        StringBuilder logText = new StringBuilder(title).append(", ").append(content);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(mAppContext, CHANNEL_ID)
                .setContentTitle(title)   //person
                .setContentText(content)  //status
                .setAutoCancel(!persist)
                .setOngoing(isOngoing)
                .setSmallIcon(R.drawable.ic_logo_24);
        if (useGroup)
        {
            builder.setGroup(GROUP_ID);
        }
        if (enAppLaunch)
        {
            builder.setContentIntent(mLauncherIntent); //createLauncherIntent());
        }
        if (extra != null && !extra.isEmpty())
        {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(
                    String.format("%s\n%s", content,extra)));
            logText.append(", details: ").append(extra);
        }

        LogFile.getInstance(mAppContext).addLogEntry(logText.toString());
        return builder.build();
    }

    protected final Notification createGroupingNotification()
    {
        final Intent intent = new Intent(mAppContext, NotGroupClearedRcv.class).setAction("notification_cancelled");
        final PendingIntent deleteIntent = PendingIntent.getBroadcast(mAppContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(mAppContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_logo_24)
                        .setGroup(GROUP_ID).setGroupSummary(true)
                        .setAutoCancel(true)
                        .setDeleteIntent(deleteIntent);    //createDeleteIntent());

        return builder.build();
    }

    public final synchronized void restartNotifications() { mNotRestart = true; }
    public static class NotGroupClearedRcv extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            NotificationHandler.getInstance(context).restartNotifications();
        }
    }
}
