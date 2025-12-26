package io.github.wandomium.smsloc.toolbox;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.wandomium.smsloc.data.file.LogFile;

public abstract class ABaseFgService<EntryDataT> extends Service
{
    private final static String CLASS_TAG = ABaseFgService.class.getSimpleName();

    protected final String cTitlePrefix;
    protected final String cStatusPrefix;
    protected final int cServiceType;

    protected ABaseFgService(String titlePx, String statusPx, int sType) {
        super();
        this.cTitlePrefix  = titlePx;
        this.cStatusPrefix = statusPx;
        this.cServiceType  = sType;
    }

    protected NotificationHandler mNotHandler;
    protected Notification mServiceNotification;

    public record QueueEntry<EntryDataT>(int startId, String addr, EntryDataT data){};
    protected LinkedBlockingQueue<QueueEntry<EntryDataT>> mQueue;

    public record ProcessResult(String okStr, String failStr){
        String getString(boolean resultOK) { return resultOK ? okStr : failStr;}
    };
    protected abstract boolean  _processEntry(QueueEntry<EntryDataT> qEntry);

    @Override
    public void onCreate()
    {
        super.onCreate();

        mQueue = new LinkedBlockingQueue<>();
        mNotHandler = NotificationHandler.getInstance(this);
    }

    @Override
    public void onDestroy() {
        if (mQueue != null) {
            // if queue is not empty, some stuff failed to send
            // we were probably shut down by the system before action was completed
            if (!mQueue.isEmpty()) {
                ArrayList<String> unsent = new ArrayList<>(mQueue.size());
                QueueEntry<EntryDataT> entry;
                while ((entry = mQueue.poll()) != null) {
                    unsent.add(entry.addr);
                }
                mNotHandler.createAndPostNotification(
                    "Failed to send SMS", unsent.toString(), "Service stopped by system"
                );
            }
        }

        mQueue = null;
        mNotHandler = null;
        mServiceNotification = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }


    protected void _onStopCommand(QueueEntry<EntryDataT> qEntry, final String status, final String detail) {
        Log.d(CLASS_TAG, "_onStopCommand");
        mNotHandler.createAndPostNotification(
    cTitlePrefix + Utils.getDisplayName(this, qEntry.addr),
         cStatusPrefix + status,
                detail
        );

        stopSelf(qEntry.startId);
    }

    protected boolean _enqueueRequest(QueueEntry<EntryDataT> qEntry) {
        if (mServiceNotification == null) {
            // create a dummy notification
            mServiceNotification = mNotHandler.createOngoigNotification(
                    cTitlePrefix,
             "service running, type=" + cServiceType, null
            );
        }

        // call startForeground within 5s, notification is the same for all calls
        try {
            ServiceCompat.startForeground(this, qEntry.startId(),
                mServiceNotification, cServiceType);
        }
        catch (Exception e) {
            // security exceptions mostly
            _onStopCommand(qEntry, "FAIL", "Could not start service (check log)");
            LogFile.getInstance(this).addLogEntry(_getExceptionString(e));
            return false;
        }

        // we are good to go
        if (!mQueue.offer(qEntry)) {
            // should really not get here in normal operation
            // TODO: limit queue size?
            _onStopCommand(qEntry, "FAIL", "queue full");
            return false;
        }
        return true;
    }

    protected void _drainQueue(final ProcessResult status, final ProcessResult detail)
    {
        while(mQueue != null && !mQueue.isEmpty()) {
            ArrayList<QueueEntry<EntryDataT>> entries = new ArrayList<>(mQueue.size());
            mQueue.drainTo(entries);

            for (QueueEntry<EntryDataT> queueEntry : entries) {
                final boolean processOk = _processEntry(queueEntry);
                _onStopCommand(
                        queueEntry,
                        status.getString(processOk),
                        detail.getString(processOk)
                );
                // IMPORTANT: onDestroy can get called here, queue can become null!!!
            }
        }
    }

    /** SecurityException because of permission issues, or
     * ForegroundServiceStartNotAllowedException (android 10 and later)
     * Or due to missing/invalid fg service types
     * https://developer.android.com/develop/background-work/services/foreground-services (v12 - API31)
     */
    protected static String _getExceptionString(Exception e) {
        if (Build.VERSION.SDK_INT >= 31 && e instanceof ForegroundServiceStartNotAllowedException) {
            return  "App has background restrictions: " + e.getMessage();
        }
        else if (e instanceof SecurityException) {
            return  "Missing permission: " + e.getMessage();
        }
        else {
            // These are actual bugs in the code - manifest mismatch
            // InvalidForegroundServiceType, MissingForegroundServiceTypeException and SecurityException in 34
            return  "BUG: Please report this\n" + e.getMessage();
        }
    }
}
