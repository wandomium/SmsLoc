package io.github.wandomium.smsloc;

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

import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.toolbox.Utils;

public class SmsResendFgService extends Service
{
    // TODO make this configurable
    public static final int NUM_RETRIES = 3;

    private String mTitle;
    private String mAddr;
    private String mMsg;
    private int mRetryCnt;

    private ServiceStateListener mServiceStateListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        mAddr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
        mMsg  = intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG);
        mRetryCnt = intent.getIntExtra(SmsLoc_Intents.EXTRA_RETRY, 0) + 1;

        mTitle = String.format("Resend %s to %s",
            SmsUtils.isResponseSms(mMsg) ? "response" : "request",
            Utils.getDisplayName(this, mAddr));

        NotificationHandler notHandler = NotificationHandler.getInstance(this);

        // limited umber of retries
        if (mRetryCnt > NUM_RETRIES) {
            stopSelf();
            notHandler.createAndPostNotification(mTitle, "FAIL", "Max retries reached (" + NUM_RETRIES + ")");
            return START_NOT_STICKY;
        }

        // security exceptions mostly
        try {
            ServiceCompat.startForeground(
        this, startId,
                notHandler.createOngoigNotification(mTitle, "Waiting for network", null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        }
        catch (Exception e) {
            stopSelf(startId);
            notHandler.createAndPostNotification(mTitle, "FAIL", e.getMessage());
            return START_NOT_STICKY;
        }

        // ok, we are good to go
        mServiceStateListener = new ServiceStateListener();
        mServiceStateListener.register();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // just in case
        if (mServiceStateListener != null) {
            mServiceStateListener.unregister();
            mServiceStateListener = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void _onServiceStateChanged(ServiceState serviceState) {
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            mServiceStateListener.unregister();
            mServiceStateListener = null;

            try {
                SmsUtils.sendSmsAndThrow(this, mAddr, mMsg, mRetryCnt);//TODO: retries -> message id
            }
            catch (Exception e) {
                NotificationHandler.getInstance(this)
                    .createAndPostNotification(mTitle, "FAIL", e.getMessage());
            }

            stopForeground(STOP_FOREGROUND_REMOVE);
            this.stopSelf();
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
