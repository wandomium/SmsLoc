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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import com.google.i18n.phonenumbers.NumberParseException;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.unit.PersonData;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.NotificationHandler;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.toolbox.Utils;


/**
 * This is the simplest possible broadcast receiver. It is only used to
 * check if the SMS was meant for us and to forward the request to appropriate
 * services.
 * It is kept as simple as possible since it will run on every received SMS
 * and sine it will execute in the background without explicit notification
 * to the user
 * <p>
 *
 * IMPORTANT: On API31 SYSTEM_ALERT_WINDOW permission will probably have to be used
 */
public class SmsReceiver extends BroadcastReceiver//WakefulBroadcastReceiver
{
    private static final String CLASS_TAG = SmsReceiver.class.getSimpleName();
    private static final Object INSTANCE_LOCK = new Object();

    public static final int INVALID_WAKE_LOCK_ID = 0;
    private static int sNextId = INVALID_WAKE_LOCK_ID + 1;
    private static final SparseArray<PowerManager.WakeLock> sActiveWakeLocks = new SparseArray<>();

    private int mCurrentLockId;

    @FunctionalInterface
    protected interface SmsHandler {
        String handle(final Context context, final String addr, String... params);
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(context == null || intent == null || intent.getAction() == null
                || !intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)){
            return;
        }

        _acquireWakeLock(context);

        //We do not support multipart msgs
        for(SmsMessage sms : Telephony.Sms.Intents.getMessagesFromIntent(intent)) try {
            if (sms == null) {
                continue;
            }
            // Address is a must
            String addr = sms.getOriginatingAddress();
            if (addr == null) {
                continue; // Not likely: This should only happen on service msgs or some malformed msg
            }
            // Get msg
            final String body = sms.getDisplayMessageBody();
            if (body == null || body.length() < SmsUtils.CODE_LEN) {
                continue;
            }
            // Is it for us?
            final SmsHandler smsHandler = switch (body.substring(0, SmsUtils.CODE_LEN)) {
                case SmsUtils.REQUEST_CODE -> mRequestHandler;
                case SmsUtils.RESPONSE_CODE -> mResponseHandler;
                default -> null;
            };
            if (smsHandler == null) {
                continue;
            }

            // We need addr with country code
            Log.i(CLASS_TAG, _getSmsCountryIso(context, intent));
            Log.i(CLASS_TAG, addr);
            addr = SmsUtils.convertToE164PhoneNumFormat(addr,
                    (addr.startsWith("+") || addr.startsWith("00")) ? null : _getSmsCountryIso(context, intent));

            // Handle message
            final String broadcastAction = smsHandler.handle(context, addr, body.substring(SmsUtils.CODE_LEN));

            //we can release the lock before this. If we are not awake, the updates on the
            //app are irrelevant because we are obviously not displaying anything
            context.sendBroadcast(SmsLoc_Intents.generateIntentWithAddr(context, addr, broadcastAction));
        }
        catch (NumberParseException e) {
            LogFile.getInstance(context).addLogEntry("ERROR - could not handle SMS: " + e.getErrorType());
            Log.e(CLASS_TAG, e.toString());
        }
        finally {
            /* Synchronously write, make sure it is stored */
            if (!MainActivity.isCreated()) {
                Utils.closeAllFiles(context);
            }
            _releaseCurrentWakeLock();
        }
    }

    protected final SmsHandler mResponseHandler = (context, addr, params) ->
    {
        final String gpsDataStr = params[0];

        final PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(context);
        final SmsDayDataFile DAYDATA = SmsDayDataFile.getInstance(context);

        final GpsData location = GpsData.fromSmsText(gpsDataStr);

        synchronized (DAYDATA.getLockObject())
        {
            DAYDATA.referenceOrCreateObject_unlocked(addr).responseReceived(location);
        }
        DAYDATA.writeFileAsync();

        String summary = "Response from ";
        String status  = "Location " + (location.dataValid() ? "VALID" : "INVALID");

        if(!PEOPLEDATA.containsId(addr)) {
            summary += Utils.unlistedDisplayName(addr);
            status  += ", unrequested!";
        }
        else {
            summary += PEOPLEDATA.getDataEntry(addr).getDisplayName();
        }

        final String details = location.dataValid() ?
            String.format("Location from %s ago", Utils.timeToNowHoursStr(location.utc))
            : gpsDataStr; //we typically try to send reason for fail in sms

        NotificationHandler.getInstance(context).createAndPostNotification(summary, status, details);

        _releaseCurrentWakeLock();
        return location.dataValid() ?
            SmsLoc_Intents.ACTION_NEW_LOCATION : SmsLoc_Intents.ACTION_RESPONSE_RCVD;
    };

    protected final SmsHandler mRequestHandler = (context, addr, params) ->
    {
        final PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(context);
        final SmsDayDataFile DAYDATA = SmsDayDataFile.getInstance(context);
        final LogFile        LOGFILE = LogFile.getInstance(context);


        if (SmsLoc_Settings.IGNORE_WHITELIST.getBool(context) || PEOPLEDATA.containsId(addr)) {

            synchronized (DAYDATA.getLockObject())
            {
                DAYDATA.referenceOrCreateObject_unlocked(addr).requestReceived();
            }
            DAYDATA.writeFileAsync();

            Intent intent = new Intent(context, LocationRetrieverFgService.class);
            intent.putExtra(SmsLoc_Intents.EXTRA_ADDR, addr);
            intent.putExtra(SmsLoc_Intents.EXTRA_WAKE_LOCK_ID, mCurrentLockId);

            LOGFILE.addLogEntry("Request from " + Utils.getDisplayName(context, addr));
            try {
                context.startForegroundService(intent);
            }
            catch (Exception e) {
                //SecurityException: No permission for FG service, FG service not found
                //android.app.ForegroundServiceStartNotAllowedException – If the caller app's targeting API
                //is Build.VERSION_CODES.S or later, and the foreground service is restricted from
                //start due to background restriction.
                LOGFILE.addLogEntry(e.getMessage());
                SmsUtils.sendSms(context, addr,
                    SmsUtils.RESPONSE_CODE + SmsLoc_Common.Consts.GPS_DATA_INVALID_ERR_STR);
                _releaseCurrentWakeLock();
            }

            return SmsLoc_Intents.ACTION_REQUEST_RCVD;
        }
        else {
            String errStr = SmsLoc_Common.Consts.NOT_WHITELISTED_ERR_STR;

            if (!SmsUtils.sendSms(
                    context, addr,
                    SmsUtils.RESPONSE_CODE + SmsLoc_Common.Consts.NOT_WHITELISTED_ERR_STR)) {
                errStr += ", Missing SEND_SMS permission";
            }
            NotificationHandler.getInstance(context).createAndPostNotification(
                    "Request from " + Utils.unlistedDisplayName(addr),
                    "Response ERROR", errStr);

            synchronized (PEOPLEDATA.getLockObject())
            {
                PersonData person = PEOPLEDATA.referenceOrCreateObject_unlocked(SmsLoc_Common.Consts.UNAUTHORIZED_ID);
                String displayName = person.getDisplayName() +
                        String.format("\n%s, %s", addr, Utils.msToStr(System.currentTimeMillis()));
                person.setDisplayName(displayName);
            }
            PEOPLEDATA.writeFileAsync();

            _releaseCurrentWakeLock();

            return SmsLoc_Intents.ACTION_NOT_WHITELISTED;
        }
    };

    public static void releaseWakeLock(int lockId)
    {
        synchronized (INSTANCE_LOCK)
        {
//            if (sActiveWakeLocks.contains(lockId)) { //not available in API29, equivalent to below call
            if(sActiveWakeLocks.indexOfKey(lockId) >= 0) {
                if (sActiveWakeLocks.get(lockId) != null) {
                    sActiveWakeLocks.get(lockId).release();
                }
                sActiveWakeLocks.remove(lockId);
            }
            Log.i(CLASS_TAG, "Wake lock released");
        }
    }

    private void _releaseCurrentWakeLock()
    {
        releaseWakeLock(mCurrentLockId);
    }

    private void _acquireWakeLock(Context context)
    {
        synchronized (INSTANCE_LOCK)
        {
            mCurrentLockId = sNextId;
            sNextId++;

            final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            LogFile.getInstance(context).addLogEntry("Acquire lock. Device idle: " + powerManager.isDeviceIdleMode());

            sActiveWakeLocks.put(
                    mCurrentLockId, powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsLoc:SmsReceiver"));
            sActiveWakeLocks.get(mCurrentLockId).setReferenceCounted(false); //only one fo each wake
            //sActiveWakeLocks.get(sNextId).acquire(SmsLoc_Settings.getGpsTimeoutInMin(context) * Utils.MIN_2_MS + 500);
            sActiveWakeLocks.get(mCurrentLockId).acquire((long) SmsLoc_Settings.GPS_TIMEOUT.getInt(context) * Utils.MIN_2_MS + 500);
        }
    }

    /**
     * SmsMessage.getOriginatingAddress() does not always include country code
     * We need to be able to handle dual SIM operation
     * Some extra gymnastics are required to get the country code if we are not the
     * default messaging app and/or do not have READ_PHONE_STATE permissions
     */
    private String _getSmsCountryIso(Context ctx, Intent intent)
    {
        TelephonyManager telService = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        String smsCountryIso;

        final int subId = intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            //fallback to default
            smsCountryIso = telService.getSimCountryIso();
            LogFile.getInstance(ctx).addLogEntry("Could not determine country code for SMS, using default " + smsCountryIso);
        }
        else {
            smsCountryIso = telService.createForSubscriptionId(subId).getSimCountryIso();
        }

        return smsCountryIso;
    }
}
