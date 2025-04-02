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
package io.github.wandomium.smsloc.ui.main;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcvr;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;

import java.lang.ref.WeakReference;

public class LogFragment extends ABaseFragment
{
    private ListView mListView;

    public LogFragment()
    {
        super(R.layout.fragment_log);
    }

    private ArrayAdapter<String> _listAdapter()
    {
        return (ArrayAdapter<String>) mListView.getAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        /** Log entries list display */
        mListView = view.findViewById(R.id.log_list);
        mListView.setAdapter(new ArrayAdapter<String>(
                getContext(), android.R.layout.simple_list_item_1, LogFile.getInstance(getContext()).readLog()));

        /** Clear log btn */
        final WeakReference<LogFragment> THISW = new WeakReference<>(this);
        ((Button) view.findViewById(R.id.clear_log))
                .setOnClickListener((View v) -> {
                    LogFile.getInstance(v.getContext()).clearLog();
                    THISW.get()._listAdapter().notifyDataSetChanged();
                });

        /** Unauthorized requests btn */
        ((Button) view.findViewById(R.id.unauthorized_requests))
                .setOnClickListener((View v) -> {
                    final PeopleDataFile peopleData = PeopleDataFile.getInstance(v.getContext());
                    final String msg = peopleData.containsId(SmsLoc_Common.Consts.UNAUTHORIZED_ID) ?
                            peopleData.getDataEntry(SmsLoc_Common.Consts.UNAUTHORIZED_ID).displayName
                            : "No unauthorized requests received";
                    new AlertDialog.Builder(v.getContext())
                            .setTitle("Unauthorized Requests").setMessage(msg)
                            .setNegativeButton("Dismiss", null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                });
    }

    @Override
    public void onDestroyView()
    {
        mListView.setAdapter(null);
        mListView = null;

        super.onDestroyView();
    }

    @Override
    protected void _createBroadcastReceivers()
    {
        mReceiverList.add(new ABaseBrdcstRcvr<LogFragment>(this,
                new String[]{SmsLoc_Intents.ACTION_LOG_UPDATED}) {
            @Override
            public void onReceive(Context context, Intent intent) {
                mParent.get()._listAdapter().notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        _listAdapter().notifyDataSetChanged();
    }
}
