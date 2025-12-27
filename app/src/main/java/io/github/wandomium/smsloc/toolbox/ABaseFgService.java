package io.github.wandomium.smsloc.toolbox;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;

public abstract class ABaseFgService<EntryDataT> extends Service
{
    @SuppressWarnings("unused")
    private final static String CLASS_TAG = ABaseFgService.class.getSimpleName();

    protected final String cTitlePrefix;
    protected final String cStatusPrefix;
    protected final int cServiceType;
    protected final int cNotId;

    protected ABaseFgService(String titlePx, String statusPx, int notifId, int sType) {
        super();
        this.cTitlePrefix  = titlePx;
        this.cStatusPrefix = statusPx;
        this.cNotId = notifId;
        this.cServiceType  = sType;
    }

    protected NotificationHandler mNotHandler;
    protected Notification mServiceNotification;

    public record QueueEntry<EntryDataT>(int startId, String addr, EntryDataT data){}

    protected LinkedBlockingQueue<QueueEntry<EntryDataT>> mQueue;

    public record ProcessResult(String okStr, String failStr){
        String getString(boolean resultOK) { return resultOK ? okStr : failStr;}
    }

    protected abstract boolean processEntry(QueueEntry<EntryDataT> qEntry);

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
//        mNotHandler = null;
        mServiceNotification = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    protected void onStartFailed(QueueEntry<EntryDataT> qEntry, final String reason) {
        mNotHandler.createAndPostNotification(
    cTitlePrefix + Utils.getDisplayName(this, qEntry.addr),
         cStatusPrefix + "start FAILED", reason
        );

        onProcessEntryDone(qEntry);
    }
    protected void onProcessEntryDone(QueueEntry<EntryDataT> qEntry) {
        stopSelf(qEntry.startId);
    }

    protected boolean enqueueEntry(QueueEntry<EntryDataT> qEntry) {
        if (mServiceNotification == null) {
            // create a dummy notification
            mServiceNotification = mNotHandler.createOngoigNotification(
                    cTitlePrefix,
             "service running, type=" + cServiceType, null
            );
        }

        // call startForeground within 5s, notification is the same for all calls
        try {
            ServiceCompat.startForeground(this, cNotId, mServiceNotification, cServiceType);
        }
        catch (Exception e) {
            // security exceptions mostly
            onStartFailed(qEntry, "Could not start service (check log)");
            LogFile.getInstance(this).addLogEntry(_getExceptionString(e));
            return false;
        }

        // we are good to go
        if (!mQueue.offer(qEntry)) {
            // should really not get here in normal operation
            // TODO: limit queue size?
            onStartFailed(qEntry, "queue full");
            return false;
        }
        return true;
    }

    protected void drainQueue(final ProcessResult processResult, final String detail)
    {
        while(mQueue != null && !mQueue.isEmpty()) {
            // drain the queue
            final int numEntries = mQueue.size();
            ArrayList<QueueEntry<EntryDataT>> entries = new ArrayList<>(numEntries);
            mQueue.drainTo(entries);

            final String title = cTitlePrefix +
               (numEntries == 1 ? Utils.getDisplayName(this, entries.get(0).addr) : "multiple (" + numEntries + ")");

            if (numEntries == 1) {
                final QueueEntry<EntryDataT> qEntry = entries.get(0);
                final boolean processOk = processEntry(qEntry);
                getMainExecutor().execute(() -> {
                    _postFinalNotification(title, processResult, detail, processOk);
                    onProcessEntryDone(qEntry);
                });
            }
            else {
                // log details
                final LogFile LOGFILE = LogFile.getInstance(this);

                final ProcessResult procResultCombined = new ProcessResult(
                        processResult.okStr, "ERROR (check log)"
                );
                boolean procOkCombined = true;

                for (int i = 0; i < numEntries; i++) {
                    final QueueEntry<EntryDataT> qEntry = entries.get(i);
                    final boolean processOk = processEntry(qEntry); //this one already logs
                    if (!processOk) {
                        procOkCombined = false;
                    }

                    // log for every item
                    LOGFILE.addLogEntry(
                        String.format(
                            SmsLoc_Common.LOCALE, "[multiple %d/%d] - %s: %s ",
                         i+1, numEntries,
                                Utils.getDisplayName(this, qEntry.addr),
                                cStatusPrefix + processResult.getString(processOk))
                    );

                    // notify only for last item
                    if (i == numEntries - 1) {
                        final boolean procOkCombFinal = procOkCombined;
                        getMainExecutor().execute(() ->
                            _postFinalNotification(title, procResultCombined, detail, procOkCombFinal)
                        );
                    }
                    getMainExecutor().execute(() -> onProcessEntryDone(qEntry));
                    // IMPORTANT: onDestroy can get called here,
                    // queue & nothandler can become null!!!
                }
            }
        }
    }

    private void _postFinalNotification(final String title,
                final ProcessResult procResult, final String detail, final boolean procOk) {
        final Notification not = mNotHandler.createNotification(
                title,
         cStatusPrefix + procResult.getString(procOk),
                procOk ? detail : null); //do not post detail on fail, can be confusing

        // final notification
        startForeground(cNotId, not);
        stopForeground(STOP_FOREGROUND_DETACH);

        // if detail was not posted log it
        if (!procOk && detail != null) {
            LogFile.getInstance(this).addLogEntry(title + ": " + detail);
        }
    }

    /** SecurityException because of permission issues, or
     * ForegroundServiceStartNotAllowedException (android 10 and later)
     * Or due to missing/invalid fg service types
     * <a href="https://developer.android.com/develop/background-work/services/foreground-services">...</a> (v12 - API31)
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
