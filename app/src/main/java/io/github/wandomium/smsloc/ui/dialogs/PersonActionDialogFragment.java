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
package io.github.wandomium.smsloc.ui.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.data.unit.SmsLocData;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.toolbox.Utils;


public class PersonActionDialogFragment extends DialogFragment
{
    private final String mAddr;
    private final Location mMyLocation;

    private enum Actions {
        REQUEST, NAVIGATE, DETAILS, REMOVE;

        final static String[] LIST = {"Request Location", "Navigate to", "Details", "Remove from list"};
        static Actions fromInt(final int code) {
            return (code >= values().length) ? null : (code < 0 ? null : values()[code]);
        }
    }

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

        final String displayName = PEOPLEDATA.getDataEntry(mAddr).getDisplayName();

        return new AlertDialog.Builder(getActivity())
                .setTitle("Select Action")
                .setNegativeButton("Cancel", null)
                .setItems(Actions.LIST, (DialogInterface dialog, int which) -> {
                    final Actions action = Actions.fromInt(which);
                    if (action == null) {
                        LOGDATA.addLogEntry("BUG: Invalid action in person action dialog " + which);
                        dialog.dismiss();
                    }
                    else switch(action) {
                        case REQUEST:
                            LOGDATA.addLogEntry("Requesting location from: " + displayName);
                            try {
                                _sendSmsLocationQuery();
                            }
                            catch (Exception e) {
                                final Context ctx = PersonActionDialogFragment.this.requireContext();
                                LogFile.getInstance(ctx).addLogEntry("ERROR: " + e.getMessage());
                                _showErrorDialog(e.getMessage()); //"App needs SEND_SMS permission for this action");
                            }
                            break;

                        case REMOVE:
                            final Context ctx = PersonActionDialogFragment.this.requireContext();
                            LOGDATA.addLogEntry("Removing person: " + displayName);
                            PEOPLEDATA.removeDataEntry(mAddr); //no need to remove day data, we store unlisted people anyway
                            ctx.sendBroadcast(SmsLoc_Intents.generateIntent(ctx, mAddr, SmsLoc_Intents.ACTION_PERSON_REMOVED));
                            break;

                        case NAVIGATE:
                            if (!_navigateTo()) {
                                _showErrorDialog("No valid location to navigate to.");
                            }
                            break;

                        case DETAILS:
                            new AlertDialog.Builder(getActivity())
                                    .setTitle(String.format("%s: %s", displayName, mAddr))
                                    .setMessage(_generateDetailsStr(GPSDATA.getDataEntry(mAddr)))
                                    .setNegativeButton("Close", null)
                                    .create().show();
                            break;
                    }
                })
                .create();
    }

    private void _showErrorDialog(String msg)
    {
        new AlertDialog.Builder(getActivity())
                .setTitle("Error").setMessage(msg).setNegativeButton("Close", null)
                .show();
    }

    private void _sendSmsLocationQuery()
    {
        //TODO-Minor
        //Here we have an option to have a pending intent that checks if SMS was sent
        SmsUtils.sendSmsAndThrow(getContext(), mAddr, SmsUtils.REQUEST_CODE);

        final SmsDayDataFile DAYDATA = SmsDayDataFile.getInstance(getContext());
        synchronized (DAYDATA.getLockObject())
        {
            DAYDATA.referenceOrCreateObject_unlocked(mAddr).requestSent();
        }
        DAYDATA.writeFileAsync();

        requireContext().sendBroadcast(
            SmsLoc_Intents.generateIntent(requireContext(), mAddr, SmsLoc_Intents.ACTION_REQUEST_SENT));
    }

    private boolean _navigateTo()
    {
        final GpsData geoData =
                SmsDayDataFile.getInstance(getContext()).getDataEntry(mAddr).getLastValidLocation();

        if (geoData == null) {
            return false;
        }

        final String label = String.format("%s at %s",
                PeopleDataFile.getInstance(getContext()).getDataEntry(mAddr).getDisplayName(),
                Utils.msToStr(geoData.utc));

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                String.format(SmsLoc_Common.LOCALE, "geo:0,0?q=%f,%f(%s)", geoData.lat, geoData.lon, Uri.encode(label)))));

        return true;
    }

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
            sb.append("\n\tElapsed time: ")
                    .append(Utils.timeToNowStr(lastValidLocation.utc))
                    .append("\n\tDistance from me: ")
                    .append((mMyLocation == null) ?
                            "My Location Fix failed" :
                            String.format(SmsLoc_Common.LOCALE, "%.4f km", lastValidLocation.distanceFrom(mMyLocation)/1000.0));
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
        sb.append(String.format(SmsLoc_Common.LOCALE,
                        "\n\tReq. sent/Resp. received: %d/%d", locData.numSentReq(), locData.numResponses()))
                .append(String.format(SmsLoc_Common.LOCALE,
                        "\n\tReq. received: %d", locData.numReceivedReq()));

        return sb.toString();
    }
}
