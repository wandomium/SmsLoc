package io.github.wandomium.smsloc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;

public class SmsSentReceiver extends BroadcastReceiver
{
    private static final String CLASS_TAG = SmsSentReceiver.class.getSimpleName();

    public final static int NUM_RETRIES = 6;
    public final static int RETRY_TO_M = 10;

    private static int sRequestCode = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String errStr;
        if (getResultCode() == Activity.RESULT_OK) {
            errStr = "SMS Sent";
            if (MainActivity.isCreated()) {
                Toast.makeText(context.getApplicationContext(), errStr, Toast.LENGTH_LONG).show();
            }
        }
        else {
            errStr = switch (getResultCode()) {
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "ERROR: Generic failure sending SMS";
                case SmsManager.RESULT_ERROR_NO_SERVICE -> "ERROR: Cound not send SMS - No service available";
                case SmsManager.RESULT_ERROR_NULL_PDU -> "ERROR: Cound not send SMS - Null PDU error";
                case SmsManager.RESULT_ERROR_RADIO_OFF -> "ERROR: Cound not send SMS - Radio off";
                default -> "SMS send status unknown ()" + getResultCode();
            };

            final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
            final String msg = intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG);
            final int retryCnt = intent.getIntExtra(SmsLoc_Intents.EXTRA_RETRY_CNT, 0) + 1;

            final boolean isResponseSms = switch (msg.substring(0, SmsUtils.CODE_LEN)) {
                case SmsUtils.REQUEST_CODE -> false;
                case SmsUtils.RESPONSE_CODE -> true;
                default -> false;
            };
            if (!MainActivity.isCreated()) {
                String details = null;

                // If this is a response, we need to retry sending, otherwise we just notify
                if (isResponseSms) {
                    details = "Retrying " + retryCnt + "/" + NUM_RETRIES;
                    if (retryCnt > NUM_RETRIES) {
                        details += " Stopping. Max retry count achieved";
                        // TODO: give up and subscribe to run when cellular network is available
                    }
                    else {
                        SmsUtils.scheduleSmsSend(context, addr, msg, retryCnt);
                    }
                }

                NotificationHandler.getInstance(context)
                    .createAndPostNotification(addr, errStr, details);
            }
            else {
                // If this is while activity is open let the user decide
                Toast.makeText(context.getApplicationContext(), errStr, Toast.LENGTH_LONG).show();
                Log.d(CLASS_TAG, "failed to respond to sms while in foreground");
//                new AlertDialog.Builder(context.getApplicationContext())
//                    .setTitle("Error sending SMS")
//                    .setMessage("Failed to send " + (isResponseSms ? "response" : " request") + " to " + addr + "\n\nRetry?")
//                        .setNegativeButton("NO", null)
//                        .setPositiveButton("YES", (dialog, which) -> {
//                            SmsUtils.sendSms(context, addr, msg);
//                        }).create().show();
            }
        }

        LogFile.getInstance(context).addLogEntry(errStr);
    }

    public static PendingIntent getPendingIntent(Context ctx, final String addr, final String msg, final int retryCnt)
    {
        Intent intent = new Intent(ctx, SmsSentReceiver.class);
        intent.putExtra(SmsLoc_Intents.EXTRA_ADDR, addr);
        intent.putExtra(SmsLoc_Intents.EXTRA_MSG, msg);
        intent.putExtra(SmsLoc_Intents.EXTRA_RETRY_CNT, retryCnt);

        return PendingIntent.getBroadcast(ctx, sRequestCode++, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
