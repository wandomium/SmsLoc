package io.github.wandomium.smsloc;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.toolbox.Utils;

/**
 * Used for resending response SMS when network is unavailable.
 * It waits until the network comes back up and resends the SMS.
 * It retries this max NUM_TRIES
 *
 * (Could also be used for sending requests but those are currently handled
 * via SmsSendFailDialog class.
 */
public class SmsResendFgService extends Service
{
    private static final String CLASS_TAG = SmsResendFgService.class.getSimpleName();

    // TODO make this configurable
    public static final int NUM_RETRIES = 3;

    protected record SmsEntry(int startId, Intent intent){};
    private static final int MAX_QUEUE_SIZE = 10;
    private LinkedBlockingQueue<SmsEntry> mResendQueue;

    private ServiceStateListener mServiceStateListener;
    private NotificationHandler mNotHandler;
    private Notification mNotification;

    @Override
    public void onCreate()
    {
        super.onCreate();

        mResendQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        mNotHandler = NotificationHandler.getInstance(this);
        mNotification = mNotHandler.createOngoigNotification("Resending SMS", "Waiting for network", null);

        mServiceStateListener = new ServiceStateListener();
        mServiceStateListener.register();
    }

    @Override
    public void onDestroy() {
        if (mServiceStateListener != null) {
            mServiceStateListener.unregister();
            mServiceStateListener = null;
        }
        mResendQueue.clear();
        mResendQueue = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        final int retryCnt = intent.getIntExtra(SmsLoc_Intents.EXTRA_RETRY, 0) + 1;
        intent.putExtra(SmsLoc_Intents.EXTRA_RETRY, retryCnt);
        final SmsEntry smsEntry = new SmsEntry(startId, intent);

        if (retryCnt > NUM_RETRIES) {
            _finalizeResend(smsEntry, "FAIL", "Max retries reached (" + NUM_RETRIES + ")");
            return START_NOT_STICKY;
        }
        try {
            ServiceCompat.startForeground(
        this, smsEntry.startId, mNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        }
        catch (Exception e) {
            // security exceptions mostly
            _finalizeResend(smsEntry, "FAIL", e.getMessage());
            return START_NOT_STICKY;
        }

        // we are good to go
        mResendQueue.offer(smsEntry);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }


    // HELPER METHODS
    private void _finalizeResend(final SmsEntry smsEntry, final String status, final String errorDetail) {
        final String title = String.format(
                SmsLoc_Common.LOCALE, "Resend %s to %s",
                SmsUtils.isResponseSms(smsEntry.intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG)) ? "response" : "request",
                Utils.getDisplayName(this, smsEntry.intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR)));

        mNotHandler.createAndPostNotification(title, status, errorDetail);

        stopSelf(smsEntry.startId);
    }

    protected void _onServiceStateChanged(ServiceState serviceState) {
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE)
        {
            while (mResendQueue != null && !mResendQueue.isEmpty()) {
                ArrayList<SmsEntry> entries = new ArrayList<>(mResendQueue.size());
                mResendQueue.drainTo(entries);
                for (SmsEntry smsEntry : entries) {
                    final boolean smsSendOk = SmsUtils.sendSms(this, smsEntry.intent);
                    _finalizeResend(
                        smsEntry, (smsSendOk ? "OK" : "FAIL (check log)"), null
                    );
                    // IMPORTANT: onDestroy can get called here!!
                }
            }
        }
    }

    // SERVICE STATE MONITORING
    private class ServiceStateListener
    {
        private final TelephonyManager cTelMngr;
        private final Legacy cLegacy;
        private final Modern cModern;

        public ServiceStateListener() {
            cTelMngr = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

            if (Build.VERSION.SDK_INT < 31) {
                cLegacy = new Legacy();
                cModern = null;
            }
            else {
                cLegacy = null;
                cModern = new Modern();
            }
        }

        public void register() {
            if (Build.VERSION.SDK_INT < 31) {
                cTelMngr.listen(cLegacy, PhoneStateListener.LISTEN_SERVICE_STATE);
            } else {
                cTelMngr.registerTelephonyCallback(getMainExecutor(), cModern);
            }
        }

        public void unregister() {
            if (Build.VERSION.SDK_INT < 31) {
                cTelMngr.listen(cLegacy, PhoneStateListener.LISTEN_NONE);
            }
            else {
                cTelMngr.unregisterTelephonyCallback(cModern);
            }
        }

        private class Legacy extends PhoneStateListener {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                SmsResendFgService.this._onServiceStateChanged(serviceState);
            }
        }

        @RequiresApi(api = 31)
        private class Modern extends TelephonyCallback implements TelephonyCallback.ServiceStateListener {
            @Override
            public void onServiceStateChanged(@NonNull ServiceState serviceState) {
                SmsResendFgService.this._onServiceStateChanged(serviceState);
            }
        }
    }
}
