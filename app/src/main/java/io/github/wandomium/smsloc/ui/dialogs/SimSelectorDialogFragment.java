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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import java.util.List;

import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;

public class SimSelectorDialogFragment extends DialogFragment
{
    public static final String TAG = "SimSelector";
    public static final int SIM_SELECT_REQUEST_ID = 7777;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle("Select SIM Card for SMS responses");

        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.READ_PHONE_STATE) != PERMISSION_GRANTED) {

            builder
                .setMessage("To read SIM information, extra permissions are needed.\n\n" + getString(R.string.sim_select_msg))
                .setNegativeButton("Close (use default)", null)
                .setPositiveButton("Request", (DialogInterface dialog, int which) -> ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_PHONE_STATE}, SIM_SELECT_REQUEST_ID));

            return builder.create();
        }

        final SubscriptionManager telephonyManager =
                (SubscriptionManager) requireActivity().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        final List<SubscriptionInfo> subsList =
                telephonyManager.getActiveSubscriptionInfoList();

        // We use default for voice, because it's hard to
        // final int systemDefaultId =  telephonyManager.getDefaultSmsSubscriptionId();
        final int systemDefaultId = SmsUtils.getDefaultSimId();

        if (subsList == null || subsList.isEmpty()) {
            builder.setMessage("No active SIM cards found").setNegativeButton("Close", null);
            return builder.create();
        }

        final int[] subId = {SmsLoc_Settings.SMS_SUB_ID.getInt(requireContext())};
        int labelIdx = 0;

        String[] labels = new String[subsList.size() + 1];
        labels[0] = "System default (currently id = " + systemDefaultId + " ) \nUses default for calls";
        for (int i = 1; i < labels.length; i++) {
            SubscriptionInfo subsInfo = subsList.get(i-1);
            labels[i] = subsInfo.getDisplayName().toString() + " ( id = " + subsInfo.getSubscriptionId() + " )";
            if (subsInfo.getSubscriptionId() == subId[0]) {
                labelIdx = i;
            }
        }

        builder
            .setSingleChoiceItems(labels, labelIdx, (dialog, which) -> {
                    subId[0] = (which == 0) ?
                        SmsLoc_Settings.SMS_SUB_ID_DEFAULT : subsList.get(which - 1).getSubscriptionId();
                    showNoDefaultSelectionAlert(subId[0], systemDefaultId);
                })
            .setPositiveButton("Ok", (dialog, which) -> SmsLoc_Settings.SMS_SUB_ID.set(requireActivity(), subId[0]))
            .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> showNoDefaultSelectionAlert(subId[0], systemDefaultId));

        return dialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        requireActivity().invalidateOptionsMenu();
    }

    public void showNoDefaultSelectionAlert(final int selected, final int defopt)
    {
        if (selected == SmsLoc_Settings.SMS_SUB_ID_DEFAULT && defopt == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            new AlertDialog.Builder(getActivity()).setMessage(R.string.sim_select_msg).setPositiveButton("Close", null).show();
        }
    }
}
