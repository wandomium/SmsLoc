package io.github.wandomium.smsloc.ui.dialogs;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.Utils;

// currently we only deal with failed requests. Responses are retries automatically
public class SmsSendFailDialog
{
    public static void showDialog(Context ctx, Intent intent) {

        final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
        final String msg  = intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG);
        final String details = intent.getStringExtra(SmsLoc_Intents.EXTRA_DEFOPT);

        final String displayName = Utils.getDisplayName(ctx, addr);
        final Boolean isResponse = SmsUtils.isResponseSms(msg);

        // we don't really care about this because activity is in te foreground
        final int retryCount = intent.getIntExtra(SmsLoc_Intents.EXTRA_RETRY, 0) + 1;

        new AlertDialog.Builder(ctx)
                .setTitle((isResponse ? "Response to " : "Rrequest to ") + displayName)
                .setMessage("SMS send fail - " + details)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Resend", (dialog, id) -> {
                    SmsUtils.sendSms(ctx, addr, msg);
                }).create().show();
    }
}
