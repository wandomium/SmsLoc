package io.github.wandomium.smsloc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.ABaseFgService;

/**
 * Used for resending response SMS when network is unavailable.
 * It waits until the network comes back up and resends the SMS.
 * It retries this max NUM_TRIES
 * <p>
 * (Could also be used for sending requests but those are currently handled
 * via SmsSendFailDialog class.
 */
public class SmsResendFgService extends ABaseFgService<SmsResendFgService.SmsData>
{
    private static final String CLASS_TAG = SmsResendFgService.class.getSimpleName();

    public static final int NOT_ID = Integer.MAX_VALUE;
    // TODO make this configurable
    public static final int NUM_RETRIES = 3;


    public record SmsData(String msg, int retryCnt){}

    private final static String TITLE_PREFIX = "Resend SMS to ";
    private final static String STATUS_PREFIX = "";

    private ServiceStateListener mServiceStateListener;

    public SmsResendFgService() {
//        final boolean isResponse = SmsUtils.isResponseSms(qEntry.data().msg);
//        return "Resend " + (isResponse ? "response" : "request") + " to ";
        super(TITLE_PREFIX, STATUS_PREFIX, NOT_ID,
                (Build.VERSION.SDK_INT < 34) ?
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE :
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                );
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        mServiceStateListener = new ServiceStateListener();
        mServiceStateListener.register();

        // create custom service notification
        mServiceNotification = mNotHandler.createOngoigNotification(
                "Resending SMS", "Waiting for network", null
        );
    }

    @Override
    public void onDestroy()
    {
        if (mServiceStateListener != null) {
            mServiceStateListener.unregister();
        }
        mServiceStateListener = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        // create new queue entry
        final QueueEntry<SmsData> qEntry = new QueueEntry<>(
                startId,
                intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR),
                new SmsData(
                    intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG),
                    intent.getIntExtra(SmsLoc_Intents.EXTRA_RETRY, 0) + 1
                ));


        // do not retry indefinitely - since we wait for network, this should not
        // be an issue unless sms in malformed
        if (qEntry.data().retryCnt > NUM_RETRIES) {
            onStartFailed(qEntry, "Max retries reached (" + NUM_RETRIES + ")");
            return START_NOT_STICKY;
        }

        if (!enqueueEntry(qEntry)) {
            Log.e(CLASS_TAG, "FAILED to start");
        }
        return START_NOT_STICKY;
    }

    // IMPL
    protected boolean processEntry(QueueEntry<SmsData> qEntry) {
        return SmsUtils.sendSms(this, qEntry.addr(), qEntry.data().msg, qEntry.data().retryCnt);
    }

    // SERVICE STATE MONITORING
    protected void _onServiceStateChanged(ServiceState serviceState) {
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            drainQueue(
                new ProcessResult("SUCCESS", "FAIL"),
                null);
        }
    }
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
