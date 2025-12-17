package io.github.wandomium.smsloc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.toolbox.Utils;

public class SmsSentReceiver extends BroadcastReceiver
{
    private static final String CLASS_TAG = SmsSentReceiver.class.getSimpleName();

    public final static int NUM_RETRIES = 6;
    public final static int RETRY_TO_M = 10;

    private static int sRequestCode = 0;

    // this will always be to
    @Override
    public void onReceive(Context context, Intent intent)
    {
        final LogFile LOGFILE = LogFile.getInstance(context);
        final Context APPCTX = context.getApplicationContext();

        if (getResultCode() == Activity.RESULT_OK) {
            LOGFILE.addLogEntry("SMS sent");
            Toast.makeText(APPCTX, "SMS sent", Toast.LENGTH_LONG).show();
            return;
        }

        // Send failed
        String status = "SMS send FAIL";
        String detail = switch (getResultCode()) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure";
            case SmsManager.RESULT_ERROR_NO_SERVICE -> "no service";
            case SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU error";
            case SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off";
            default -> "reason unknown (" + getResultCode() + ")";
        };

        // Responses need to be resent ALWAYS!
        final String msg = intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG);
        final Boolean isResponseSms = SmsUtils.isResponseSms(msg);
        if (isResponseSms) {
            LOGFILE.addLogEntry(status + " - " + detail);

            // Retries when network comes back. No need to panic. If all resend attempts fail,
            // user will be notified in foreground service
            intent.setClass(APPCTX, SmsResendFgService.class);
            context.startForegroundService(intent); //TODO - try/catch permission issues
            return;
        }

        // Notify the user
        final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
        final String displayName = Utils.getDisplayName(context, addr);

        NotificationHandler.getInstance(context)
            .createAndPostNotification("Request to " + displayName, status, detail);

        // Show toast if user is currently in the app (which will be almost always if he just requested location
        if (MainActivity.isCreated()) {
            Toast.makeText(APPCTX, status, Toast.LENGTH_LONG).show();

            Intent outIntent = SmsLoc_Intents.generateIntentWithAddr(context, addr, SmsLoc_Intents.ACTION_SMS_SEND_FAIL);
            outIntent.putExtra(SmsLoc_Intents.EXTRA_MSG, msg);
            outIntent.putExtra(SmsLoc_Intents.EXTRA_DEFOPT, detail);
            context.sendBroadcast(outIntent);
        }
    }

    public static PendingIntent getPendingIntent(Context ctx, final String addr, final String msg, final int retryCnt)
    {
        Intent intent = new Intent(ctx, SmsSentReceiver.class);
        intent.putExtra(SmsLoc_Intents.EXTRA_ADDR, addr);
        intent.putExtra(SmsLoc_Intents.EXTRA_MSG, msg);
        intent.putExtra(SmsLoc_Intents.EXTRA_RETRY, retryCnt);

        return PendingIntent.getBroadcast(ctx, sRequestCode++, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
