package io.github.wandomium.smsloc.ui.dialogs;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.toolbox.Utils;

// currently we only deal with failed requests. Responses are retries automatically
public class SmsSendFailDialog
{
    private static AlertDialog mInstance;

    public static void showNotification(Context ctx, Intent intent) {
        NotificationHandler.getInstance(ctx).createAndPostNotification(
            _getTitle(ctx, intent), "SMS send fail", _getDetails(intent)
        );
    }

    public static void showDialog(Context ctx, Intent intent) {
        if (mInstance != null) {
            mInstance.dismiss();
        }
        mInstance = new AlertDialog.Builder(ctx)
                .setTitle(_getTitle(ctx, intent))
                .setMessage("SMS send fail - " + _getDetails(intent))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Resend", (dialog, id) -> {
                    dialog.dismiss();
                    SmsUtils.sendSms(ctx,
                        intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR),
                        intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG));
                })
                .setOnDismissListener(dialog -> mInstance = null)
                .create();
        mInstance.show();
    }

    private static String _getTitle(Context ctx, Intent intent) {
        final String msg  = intent.getStringExtra(SmsLoc_Intents.EXTRA_MSG);
        final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);

        final String displayName = Utils.getDisplayName(ctx, addr);
        final Boolean isResponse = SmsUtils.isResponseSms(msg);

        return (isResponse ? "Response to " : "Rrequest to ") + displayName;
    }

    private static String _getDetails(Intent intent) {
        return intent.getStringExtra(SmsLoc_Intents.EXTRA_DEFOPT);
    }
}
