package io.github.wandomium.smsloc;

import android.app.Activity;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.ui.dialogs.SmsSendFailDialog;

public class SmsSentStatusReceiver extends BroadcastReceiver
{
    private static final String CLASS_TAG = SmsSentStatusReceiver.class.getSimpleName();

    private static int sRequestCode = 0;

    // this will always be to
    @Override
    public void onReceive(Context context, Intent intent)
    {
        final LogFile LOGFILE = LogFile.getInstance(context);
        final Context APPCTX = context.getApplicationContext();

        if (getResultCode() == Activity.RESULT_OK) {
            final String status = "SMS sent";
            LOGFILE.addLogEntry(status);
            Toast.makeText(APPCTX, status, Toast.LENGTH_LONG).show();
            return;
        }

        // Send failed
        final String status = "SMS send FAIL";
        final String detail = switch (getResultCode()) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure";
            case SmsManager.RESULT_ERROR_NO_SERVICE -> "no service";
            case SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU error";
            case SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off";
            default -> "reason unknown (" + getResultCode() + ")";
        };
        LOGFILE.addLogEntry(status + " - " + detail);
        intent.putExtra(SmsLoc_Intents.EXTRA_DEFOPT, detail);

        /// ////////
        // RESPONSE: need to be resent ALWAYS!
        final Boolean isResponseSms = SmsUtils.isResponseSms(intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG));
        if (isResponseSms) try {
            // Retries when network comes back. No need to panic. If all resend attempts fail,
            // user will be notified in foreground service
            intent.setComponent(new ComponentName(APPCTX, SmsResendFgService.class));
            context.startForegroundService(intent);
            return;
        } catch (Exception e) {
            intent.setComponent(null);
            SmsSendFailDialog.showNotification(context, intent);
        }

        ///////////
        // REQUEST: notify the user and let him decide
        intent.setComponent(null);
        if (MainActivity.isCreated()) {
            // Show alert if user is currently in the app (which will be almost always if he just requested location
            intent.setAction(SmsLoc_Intents.ACTION_SMS_SEND_FAIL);
            context.sendBroadcast(intent);
        }
        else {
            // Show notification if this was somehow triggered in he background
            SmsSendFailDialog.showNotification(context, intent);
        }
    }

    public static PendingIntent getPendingIntent(Context ctx, final String addr, final String msg, final int retryCnt)
    {
        Intent intent = SmsLoc_Intents.generateIntentWithAddr(ctx, addr, null);
        intent.setComponent(new ComponentName(ctx, SmsSentStatusReceiver.class));
        intent.putExtra(SmsLoc_Intents.EXTRA_MSG, msg);
        intent.putExtra(SmsLoc_Intents.EXTRA_RETRY, retryCnt);

        return PendingIntent.getBroadcast(ctx, sRequestCode++, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
