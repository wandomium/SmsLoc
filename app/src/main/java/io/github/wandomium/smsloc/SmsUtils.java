/**
 * This file is part of SmsLoc.
 *
 * SmsLoc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * SmsLoc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SmsLoc. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.wandomium.smsloc;

import android.Manifest;
import android.content.Context;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class SmsUtils
{
    public static final String REQUEST_CODE = "Loc?";
    public static final String RESPONSE_CODE = "Loc:";
    public static final int CODE_LEN = 4;

    public static final boolean sendSms(Context context, final String addr, final String msg)
    {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PERMISSION_GRANTED)
        {
            return false;
        }

        SmsManager.getDefault().sendTextMessage(addr, null, msg, null, null);

        return true;
    }

    //TODO fina a place for this
    public static String convertToE164PhoneNumFormat(final String phoneNumStr, Context context)
            throws NumberParseException
    {
        TelephonyManager telephonyService
                = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        PhoneNumberUtil PhoneNumUtilInstance
                = PhoneNumberUtil.getInstance();

        Phonenumber.PhoneNumber phoneNumber
                = PhoneNumUtilInstance.parse(phoneNumStr, telephonyService.getSimCountryIso().toUpperCase());

        //check if it is a mobile number, because we need to be able to send SMS
        if (PhoneNumUtilInstance.getNumberType(phoneNumber) != PhoneNumberUtil.PhoneNumberType.MOBILE)
        {
            throw new NumberParseException(
                NumberParseException.ErrorType.NOT_A_NUMBER, "Not a mobile number, required for SMS");
        }

        return
                PhoneNumUtilInstance.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
    }
}
