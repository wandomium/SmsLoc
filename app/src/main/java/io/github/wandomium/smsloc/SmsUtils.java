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
import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

import androidx.core.app.ActivityCompat;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import java.util.Objects;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;

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

    /**
     *
     * @throws SecurityException if app is missing SEND_SMS permission
     * @throws IllegalArgumentException if SIM id is invalid or if default sim is not selected in setting
     */
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
    public static void sendSmsAndThrow(Context context, final String addr, final String msg)
    {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PERMISSION_GRANTED) {
            throw new SecurityException("Could not send SMS: Missing SEND_SMS permissions");
        }

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

        smsManager.sendTextMessage(addr, null, msg, null, null);
    }

    //TODO fina a place for this
    public static String convertToE164PhoneNumFormat(final String phoneNumStr)
            throws NumberParseException
    {
        PhoneNumberUtil pnumberUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber;
        try {
            /* Don't even try with SIM country ISO
             * if we pass null, then it will fail if the number does not have a
             * country code included. Which is the safer/easier option in multi-sim support
             */
            phoneNumber = pnumberUtil.parse(phoneNumStr, null);
        }
        catch (NumberParseException e) {
            if (e.getErrorType() == NumberParseException.ErrorType.INVALID_COUNTRY_CODE) {
                throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE, "Missing country code");
            }
            else {
                throw e;
            }
        }

        //check if it is a mobile number, because we need to be able to send SMS
        //relax check for US and CA territories
        final String regionCode = Objects.requireNonNullElse(
                pnumberUtil.getRegionCodeForNumber(phoneNumber), "");
        final PhoneNumberUtil.PhoneNumberType requiredType =
            switch (regionCode) {
                case "US", "CA" -> PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
                default -> PhoneNumberUtil.PhoneNumberType.MOBILE;
        };
        if (pnumberUtil.getNumberType(phoneNumber) != requiredType) {
            throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "Not a mobile number, required for SMS.");
        }

        return pnumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
    }
}
