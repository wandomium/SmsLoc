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

import android.content.Context;
import android.content.Intent;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.LocationRetriever;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.SmsLocData;
import io.github.wandomium.smsloc.data.unit.PersonData;
import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcvr;
import io.github.wandomium.smsloc.ui.dialogs.PersonActionDialogFragment;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.Utils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


//TODO handle a situation where we received response from a list_item_person not on the list

public class PeopleFragment extends ABaseFragment implements LocationRetriever.LocCb
{
    private ListView mListView;

    private final long LOC_DELAY_MS = 100;
    private boolean mSecondLocCall = false;

    public PeopleFragment()
    {
        super(R.layout.fragment_people);
    }

    // Helper for typecast
    private PeopleListAdapter _listAdapter()
    {
        return (PeopleListAdapter)mListView.getAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        WeakReference<PeopleFragment> THISW = new WeakReference<>(this);

        /** Generate a list of all people */
        ArrayList<PersonData> people = new ArrayList<>();
        for (PersonData p : PeopleDataFile.getInstance(getContext()).getDataAll().values()) {
            if (p.getId().compareTo(SmsLoc_Common.Consts.UNAUTHORIZED_ID) != 0) {
                people.add(p);
            }
        }

        /** People list */
        mListView = view.findViewById(R.id.people_list);
        mListView.setAdapter(
                new PeopleListAdapter(getContext(), android.R.layout.simple_list_item_1, people));
        mListView.setOnItemClickListener(
                (AdapterView<?> arg0, View arg1, int position, long arg3) ->
                {
                    final PersonActionDialogFragment df =
                        new PersonActionDialogFragment(THISW.get()._listAdapter().getItem(position).addr, THISW.get()._listAdapter().mMyLocation);
                    df.show(getParentFragmentManager(), df.getCustomTag());
                });

        /** Refresh my location */
        ((SwipeRefreshLayout) view.findViewById(R.id.refresh_people_list))
                .setOnRefreshListener(() -> {
                    LocationRetriever.getLocation(LOC_DELAY_MS, THISW.get(), getActivity());
                });

        /** Whitelist CB */
        CheckBox whitelistCb = view.findViewById(R.id.ignoreWhitelist);
//        whitelistCb.setOnClickListener((View v) -> {
//            LocationRetrieverInstance.getLocationSynchronous();
//            mListAdapter.notifyDataSetChanged();
//        });
        whitelistCb.setChecked(SmsLoc_Settings.IGNORE_WHITELIST.getBool(getContext()));
        whitelistCb.setOnClickListener((View v) -> {
            final boolean ignoreWhitelist = ((CheckBox) v).isChecked();
            SmsLoc_Settings.IGNORE_WHITELIST.set(v.getContext(),ignoreWhitelist);
        });
    }

    @Override
    public void onDestroyView()
    {
        // Clean up references to current context/view
        _listAdapter().clear();
        mListView.setAdapter(null);
        mListView.setOnItemClickListener(null);
        mListView = null;

        super.onDestroyView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        LocationRetriever.getLocation(LOC_DELAY_MS, this, getActivity());
    }

    @Override
    protected void _createBroadcastReceivers()
    {
        /** Person add/remove */
        mReceiverList.add(new ABaseBrdcstRcvr<PeopleFragment>(PeopleFragment.this,
                new String[]{SmsLoc_Intents.ACTION_NEW_PERSON, SmsLoc_Intents.ACTION_PERSON_REMOVED}) {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
                switch (intent.getAction())
                {
                    case SmsLoc_Intents.ACTION_NEW_PERSON:
                        mParent.get().addPerson(addr);
                        break;
                    case SmsLoc_Intents.ACTION_PERSON_REMOVED:
                        mParent.get().removePerson(addr);
                        break;
                }
            }
        });

        /** Dataset update */
        mReceiverList.add(new ABaseBrdcstRcvr<PeopleFragment>(PeopleFragment.this,
                new String[]{SmsLoc_Intents.ACTION_REQUEST_SENT, SmsLoc_Intents.ACTION_RESPONSE_RCVD,
                        SmsLoc_Intents.ACTION_NEW_LOCATION, SmsLoc_Intents.ACTION_DAY_DATA_CLR}) {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                mParent.get()._listAdapter().notifyDataSetChanged(); //we don't hold a copy but simply load every time
            }
        });

        return;
    }

    private void addPerson(String addr)
    {
        _listAdapter().add(PeopleDataFile.getInstance(getContext()).getDataEntry(addr));
    }
    private void removePerson(String addr)
    {
        int numPeople = _listAdapter().getCount();
        for(int i = 0; i < numPeople; i++) {
            PersonData p = _listAdapter().getItem(i);
            if (p.addr.compareTo(addr) == 0) {
                _listAdapter().remove(p);
                break;
            }
        }
    }

    @Override
    public void onLocationRcvd(Location loc, String msg)
    {
        final FragmentActivity activity = PeopleFragment.this.getActivity();

        if (loc == null && !mSecondLocCall) {
            mSecondLocCall = true;
            // OK to use, we are running app in the foreground
            LocationRetriever.getLocationWithNetwork(LOC_DELAY_MS, this, getActivity());

            return;
        }

        mSecondLocCall = false;
        activity.runOnUiThread(() -> {
            try {
                ((SwipeRefreshLayout) PeopleFragment.this.getView()
                        .findViewById(R.id.refresh_people_list)).setRefreshing(false);
                _listAdapter().mMyLocation = loc;
                _listAdapter().notifyDataSetChanged();
            } catch (NullPointerException e) {}
        });
    }

    private static class PeopleListAdapter extends ArrayAdapter<PersonData>
    {
        public Location mMyLocation = null;

        public PeopleListAdapter(@NonNull Context context, int resource, @NonNull List<PersonData> objects) {
//            super(context.getApplicationContext(), resource, objects);
                super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            //TextView textView = (TextView) super.getView(position, convertView, parent);

            View rowView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_person, null);

            final PersonData person = getItem(position);
            final SmsLocData locData = SmsDayDataFile.getInstance(getContext()).getDataEntry(person.addr);

            int color = Color.GRAY;
            String text = "No location data";

            if (locData != null)
            {
                if (locData.locationUpToDate_ver2())    { color = Color.rgb(39,204,44); }
                else if (locData.requestPending())      { color = Color.rgb(255,174,66); }
                else if (!locData.lastResponseValid())  { color = Color.RED; }

                if (locData.getLastValidLocation() != null) {
                    Long utc = locData.getLastValidLocation().utc;

                    text = String.format("Last valid location: %s\n\tElapsed: %s\n",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(utc)),
                            Utils.timeToNowStr(locData.getLastValidLocation().utc));

                    Float distance = locData.getLastValidLocation().distanceFrom(mMyLocation);
                    text +=
                        (distance == null) ?
                            String.format("\tDistance: %s", "Get My Loc Failed") //locRetriever.getInvalidLocReasonSimple())
                          : String.format("\tDistance: %.4f km", distance/1000.0);
                }
            }

            //initials with color circle used on map
            Drawable personIcon = parent.getResources().getDrawable(R.drawable.ic_circle_24, null);
            personIcon.setColorFilter(new BlendModeColorFilter(person.color, BlendMode.SRC_ATOP));
            ((TextView)rowView.findViewById(R.id.person_icon)).setBackground(personIcon);
            ((TextView)rowView.findViewById(R.id.person_icon)).setText(person.initials);

            //gps status icon
            Drawable statusIcon = parent.getResources().getDrawable(R.drawable.ic_location_marker_24, null);
            statusIcon.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_ATOP));
            ((ImageView)rowView.findViewById(R.id.status_icon)).setImageDrawable(statusIcon);

            //detail text
            ((TextView)rowView.findViewById(R.id.text)).setText(
                    String.format("%s\n%s", person.displayName, text));

            return rowView;
        }
    }
}
