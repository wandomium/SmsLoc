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
package io.github.wandomium.smsloc.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import android.telephony.SmsManager;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.data.unit.SmsLocData;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.toolbox.Utils;

public class PersonActionDialogFragment extends DialogFragment
{
    private final int ACTION_REQUEST_LOC   = 0;
    private final int ACTION_NAVIGATE_TO   = 1;
    private final int ACTION_SHOW_DETAILS  = 2;
    private final int ACTION_REMOVE_PERSON = 3;
    private final String[] ACTIONS = {"Request Location", "Navigate to", "Details", "Remove from list"};

    private final String mAddr;
    private final Location mMyLocation;

    public PersonActionDialogFragment(String addr, Location loc)
    {
        this.mAddr = addr;
        this.mMyLocation = loc;
    }

    public String getCustomTag()
    {
        return getClass().getSimpleName() + mAddr;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(getContext());
        SmsDayDataFile GPSDATA = SmsDayDataFile.getInstance(getContext());
        LogFile LOGDATA = LogFile.getInstance(getContext());

        final String displayName = PEOPLEDATA.getDataEntry(mAddr).displayName;

        return new AlertDialog.Builder(getActivity())
            .setTitle("Select Action")
            .setNegativeButton("Cancel", null)
            .setItems(ACTIONS, (DialogInterface dialog, int which) -> {
                switch(which) {
                    case ACTION_REQUEST_LOC:
                        LOGDATA.addLogEntry("Requesting location from: " + displayName);
                        try {
                            _sendSmsLocationQuery();
                        }
                        catch (SecurityException e) {
                            LOGDATA.addLogEntry("App needs SEND_SMS permission for this action");
                            _showErrorDialog("App needs SEND_SMS permission for this action");
                        }
                        break;

                    case ACTION_REMOVE_PERSON:
                        LOGDATA.addLogEntry("Removing person from whitelist: " + displayName);
                        PEOPLEDATA.removeDataEntry(mAddr); //no need to remove day data, we store unlisted people anyway
                        getContext().sendBroadcast(
                            SmsLoc_Intents.generateIntent(mAddr, SmsLoc_Intents.ACTION_PERSON_REMOVED));
                        break;

                    case ACTION_NAVIGATE_TO:
                        try {
                            _navigateTo();
                        }
                        catch (NullPointerException e) {
                            _showErrorDialog("No valid location to navigate to.");
                        }
                        break;

                    case ACTION_SHOW_DETAILS:
                        new AlertDialog.Builder(getActivity())
                            .setTitle(String.format("%s: %s", displayName, mAddr))
                            .setMessage(_generateDetailsStr(GPSDATA.getDataEntry(mAddr)))
                            .setNegativeButton("Close", null)
                            .create().show();
                        break;
                }

                return; //onClickListener
            })
            .create();
    }

    private void _showErrorDialog(String msg)
    {
        new AlertDialog.Builder(getActivity())
            .setTitle("Error").setMessage(msg).setNegativeButton("Close", null)
            .show();
        return;
    }

    private void _sendSmsLocationQuery()
    {
        //TODO-Minor
        //Here we have an option to have a pending intent that checks if SMS was sent
        SmsManager.getDefault().sendTextMessage(
            mAddr, null, SmsUtils.REQUEST_CODE, null, null);

        final SmsDayDataFile DAYDATA = SmsDayDataFile.getInstance(getContext());
        synchronized (DAYDATA.getLockObject())
        {
            DAYDATA.referenceOrCreateObject_unlocked(mAddr).requestSent();
            DAYDATA.writeFile_unlocked();
        }

        getContext().sendBroadcast(
            SmsLoc_Intents.generateIntent(mAddr, SmsLoc_Intents.ACTION_REQUEST_SENT));

        return;
    }

    @SuppressLint("DefaultLocale")
    private void _navigateTo()
    {
        final GpsData geoData =
            SmsDayDataFile.getInstance(getContext()).getDataEntry(mAddr).getLastValidLocation();

        //TODO-Minor
        //doesn't work on google maps (label is not displayed)
        final String label =
            String.format("%s at %s",
                PeopleDataFile.getInstance(getContext()).getDataEntry(mAddr).displayName,
                Utils.msToStr(geoData.utc));

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
            String.format("geo:0,0?q=%f,%f(%s)", geoData.lat, geoData.lon, Uri.encode(label)))));

        return;
    }

    @SuppressLint("DefaultLocale")
    private String _generateDetailsStr(SmsLocData locData)
    {
        if (locData == null) {
            return "No data";
        }

        StringBuilder sb = new StringBuilder();
        final GpsData lastValidLocation = locData.getLastValidLocation();

        //Location display
        sb.append("Last valid location:");
        if (lastValidLocation == null) {
            sb.append("\n\tNo valid data");
        }
        else {
            Float distance = lastValidLocation.distanceFrom(mMyLocation);

            sb.append("\n\tElapsed time: ")
                    .append(Utils.timeToNowStr(lastValidLocation.utc))
                    .append("\n\tDistance from me: ")
                    .append((distance == null) ?
                            "My Location Fix failed" : String.format("%.4f km", distance/1000.0));
        }

        //Last response details
        sb.append("\n\nLast Response:")
                .append(String.format("\n\tValid: %b", locData.lastResponseValid()))
                .append("\n\tElapsedTime: ").append(Utils.timeToNowStr(locData.lastRespTime()));

        //Request statistics
        sb.append("\n\nRequest statistics:")
                .append(String.format("\n\tRequest pending: %b", locData.requestPending()));
        if (locData.requestPending()) {
            sb.append("\n\t\tfor: ").append(Utils.timeToNowStr(locData.lastReqTime()));
        }
        sb.append(String.format(
                        "\n\tReq. sent/Resp. received: %d/%d", locData.numSentReq(), locData.numResponses()))
                .append(String.format(
                        "\n\tReq. received: %d", locData.numReceivedReq()));

        return sb.toString();
    }
}
