package io.github.wandomium.smsloc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.widget.Toast;

import io.github.wandomium.smsloc.data.file.LogFile;

public class SmsSentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String errStr = switch (getResultCode()) {
            case Activity.RESULT_OK -> "SMS Sent";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "ERROR: Generic failure sending SMS";
            case SmsManager.RESULT_ERROR_NO_SERVICE -> "ERROR: Cound not send SMS - No service available";
            case SmsManager.RESULT_ERROR_NULL_PDU -> "ERROR: Cound not send SMS - Null PDU error";
            case SmsManager.RESULT_ERROR_RADIO_OFF -> "ERROR: Cound not send SMS - Radio off";
            default -> "SMS send status unknown";
        };

        LogFile.getInstance(context).addLogEntry(errStr);
        if (MainActivity.isCreated()) {
            Toast.makeText(context.getApplicationContext(), errStr, Toast.LENGTH_LONG).show();
        }
    }

    public static PendingIntent getPendingIntent(Context ctx)
    {
        return PendingIntent.getBroadcast(ctx, 0,
                    new Intent(ctx, SmsSentReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
