/**
 * This file is part of SmsLoc.
 * <p>
 * SmsLoc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * <p>
 * SmsLoc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SmsLoc. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.wandomium.smsloc;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;

public class SmsUtils
{
    public static final String REQUEST_CODE = "Loc?";
    public static final String RESPONSE_CODE = "Loc:";
    public static final int CODE_LEN = 4;

    /**
     * It is getting progressively harder to select default for SMS,
     * but default for Voice is easily accessible on all devices.
     */
    public static final boolean mUseVoiceDefopt = true;

    public static int getDefaultSimId()
    {
        return mUseVoiceDefopt ?
            SubscriptionManager.getDefaultVoiceSubscriptionId() : SubscriptionManager.getDefaultSmsSubscriptionId();
    }

    public static boolean sendSms(Context context, final String addr, final String msg)
    {
        try {
            sendSmsAndThrow(context, addr, msg);
        } catch (Exception e) {
            LogFile.getInstance(context).addLogEntry("ERROR: " + e.getMessage());
            return false;
        }
        return true;
    }
    public static void sendSmsAndThrow(Context context, final String addr, final String msg) {
        sendSmsAndThrow(context, addr, msg, 0);
    }
    /**
     * @throws SecurityException if app is missing SEND_SMS permission
     * @throws IllegalArgumentException if SIM id is invalid or if default sim is not selected in setting
     */
    public static void sendSmsAndThrow(Context context, final String addr, final String msg, final int retryCnt) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PERMISSION_GRANTED) {
            throw new SecurityException("Could not send SMS: Missing SEND_SMS permissions");
        }

        _getSmsManager(context).sendTextMessage(addr, null, msg,
                SmsSentReceiver.getPendingIntent(context, addr, msg, retryCnt), null);
    }

    public static void scheduleSmsSend(Context context, final String addr, final String msg, final int retryCnt) {
        Data inputData = new Data.Builder()
                .putString(SmsLoc_Intents.EXTRA_ADDR, addr)
                .putString(SmsLoc_Intents.EXTRA_MSG, msg)
                .putInt(SmsLoc_Intents.EXTRA_RETRY_CNT, retryCnt)
                .build();

        OneTimeWorkRequest smsWorkRequest = new OneTimeWorkRequest.Builder(SmsSendWorker.class)
                .setInputData(inputData)
                .setInitialDelay(SmsSentReceiver.RETRY_TO_M, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueue(smsWorkRequest);
    }

    public static class SmsSendWorker extends Worker {
        public SmsSendWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            final String addr = getInputData().getString(SmsLoc_Intents.EXTRA_ADDR);
            final String msg = getInputData().getString(SmsLoc_Intents.EXTRA_MSG);
            final int retryCnt = getInputData().getInt(SmsLoc_Intents.EXTRA_RETRY_CNT, 0);

            try {
                SmsUtils.sendSmsAndThrow(getApplicationContext(), addr, msg, retryCnt);
            }
            catch (IllegalArgumentException e) {
                // Technically if we throw here we would also throw on first try
                NotificationHandler.getInstance(getApplicationContext())
                        .createAndPostNotification(addr, "Resend FAIL", e.getMessage());
                return Result.failure();
            }
            return Result.success();
        }
    }

    /**
     * WHEN ADDING NEW PERSON: Don't even try with SIM country ISO
     * if we pass null, then it will fail if the number does not have a
     * country code included. Which is the safer/easier option in multi-sim support
     * *
     * WHEN CALLING FROM SmsReceiver: Needs default sim country iso because SMS addr can have none
     * and it will fail
     */
    public static String convertToE164PhoneNumFormat(String phoneNumStr, String defaultRegion)
            throws NumberParseException
    {
        PhoneNumberUtil pNumberUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber;

        // If country code starts with 00 and not + replace with +
        if (phoneNumStr.startsWith("00")) {
            phoneNumStr = "+" + phoneNumStr.substring(2);
        }

        try {
            if (defaultRegion != null) {
                defaultRegion = defaultRegion.toUpperCase(Locale.ROOT);
            }
            phoneNumber = pNumberUtil.parse(phoneNumStr, defaultRegion);
        }
        catch (NumberParseException e) {
            if (e.getErrorType() == NumberParseException.ErrorType.INVALID_COUNTRY_CODE) {
                throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE, "Missing country code.");
            }
            else {
                throw e;
            }
        }

        // Check if it is a mobile number, because we need to be able to send SMS
        // Relax this check. A lot of issues reported with rejected mobile numbers. Use sentIntent to monitor success

        return pNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
    }


    private static SmsManager _getSmsManager(Context context) throws IllegalArgumentException
    {
        int subId = SmsLoc_Settings.SMS_SUB_ID.getInt(context);
        if (subId == SmsLoc_Settings.SMS_SUB_ID_DEFAULT) {
            subId = getDefaultSimId();
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                throw new IllegalArgumentException("Could not send SMS: Use default selected, but default SIM not selected in system settings");
            }
        }

        SmsManager smsManager;
        if (Build.VERSION.SDK_INT < 31) {
            // this one is depreciated in 31+
            smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        }
        else {
            smsManager = context.getSystemService(SmsManager.class).createForSubscriptionId(subId);
        }

        if (smsManager == null) {
            throw new IllegalArgumentException("Could not send sms: Invalid SIM Id");
        }

        return smsManager;
    }
}
