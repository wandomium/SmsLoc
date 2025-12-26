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
package io.github.wandomium.smsloc.ui.main;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.i18n.phonenumbers.NumberParseException;

import io.github.wandomium.smsloc.MainActivity;
import io.github.wandomium.smsloc.R;
import io.github.wandomium.smsloc.LocationRetriever;
import io.github.wandomium.smsloc.SmsUtils;
import io.github.wandomium.smsloc.data.file.LogFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.SmsLocData;
import io.github.wandomium.smsloc.data.unit.PersonData;
import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcv;
import io.github.wandomium.smsloc.ui.dialogs.PersonActionDialogFragment;
import io.github.wandomium.smsloc.defs.SmsLoc_Settings;
import io.github.wandomium.smsloc.defs.SmsLoc_Intents;
import io.github.wandomium.smsloc.toolbox.Utils;
import io.github.wandomium.smsloc.ui.dialogs.SmsSendFailDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;


//TODO handle a situation where we received response from a list_item_person not on the list

public class PeopleFragment extends ABaseFragment implements LocationRetriever.LocCb
{
    private ListView mListView;
    private ActivityResultLauncher<Intent> mContactPickerLauncher;

    private final long LOC_DELAY_MS = 100;
    private boolean mSecondLocCall = false;

    public PeopleFragment() { super(R.layout.fragment_people); }
    public static PeopleFragment newInstance(final int position) {
        final PeopleFragment newInstance = new PeopleFragment();
        _initInstance(newInstance, position);
        return newInstance;
    }

    // We access the adapter only trough the list view
    private PeopleListAdapter _listAdapter() { return (PeopleListAdapter) mListView.getAdapter(); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        /* People List */
        HashSet<String> people = new HashSet<>();
        for (PersonData p : PeopleDataFile.getInstance(getContext()).getDataAll().values()) {
            if (p.getId().compareTo(SmsLoc_Common.Consts.UNAUTHORIZED_ID) != 0) {
                people.add(p.getAddr());
            }
        }
        mListView = view.findViewById(R.id.people_list);
        mListView.setAdapter(
                new PeopleListAdapter(requireContext(), android.R.layout.simple_list_item_1, people));
        mListView.setOnItemClickListener(
                (AdapterView<?> parent, View view2, int position, long id) ->
                {
                    final String addr = (String) parent.getItemAtPosition(position);
                    final PersonActionDialogFragment df =
                        new PersonActionDialogFragment(addr, PeopleFragment.this._listAdapter().mMyLocation);
                    df.show(getParentFragmentManager(), df.getCustomTag());
                });

        /* Refresh my location */
        ((SwipeRefreshLayout) view.findViewById(R.id.refresh_people_list))
                .setOnRefreshListener(() -> {
                    LocationRetriever.getLocation(LOC_DELAY_MS, PeopleFragment.this, requireContext()); //getActivity() may return null here
                });

        /* Whitelist CB */
        CheckBox whitelistCb = view.findViewById(R.id.ignoreWhitelist);
        whitelistCb.setChecked(SmsLoc_Settings.IGNORE_WHITELIST.getBool(requireContext()));
        whitelistCb.setOnClickListener((View v) -> {
            final boolean ignoreWhitelist = ((CheckBox) v).isChecked();
            SmsLoc_Settings.IGNORE_WHITELIST.set(v.getContext(),ignoreWhitelist);
        });

        /* Add person functionality */
        mContactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == FragmentActivity.RESULT_OK)
                        //This action is rare so we don't care about a potential performance hit with catching the exception
                        //noinspection DataFlowIssue - Just catching and ignorig null here is fine
                        try (final Cursor cursor = requireContext().getContentResolver().query(result.getData().getData(),
                                null, null, null, null)) {
                            //noinspection DataFlowIssue - Same as above
                            cursor.moveToFirst();

                            PeopleFragment.this._addPerson(
                                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                        }
                        catch (NumberParseException | IllegalArgumentException | NullPointerException e) {
                            LogFile.getInstance(getContext()).addLogEntry(e.getMessage());
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                });
        view.findViewById(R.id.add_person).setOnClickListener((v) -> mContactPickerLauncher.launch(
                new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        ));
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
        // Disable when asking for permissions. To many calls - on every dialog close, etc.
        if (!((MainActivity) requireActivity()).permissionCheckActive()) {
            LocationRetriever.getLocation(LOC_DELAY_MS, this, requireActivity());
        }
    }

    @Override
    protected void _createBroadcastReceivers()
    {
        mReceiverList.add(new ABaseBrdcstRcv<>(PeopleFragment.this,
                new String[]{SmsLoc_Intents.ACTION_REQUEST_SENT, SmsLoc_Intents.ACTION_RESPONSE_RCVD,
                        SmsLoc_Intents.ACTION_NEW_LOCATION, SmsLoc_Intents.ACTION_DAY_DATA_CLR,
                        SmsLoc_Intents.ACTION_PERSON_REMOVED, SmsLoc_Intents.ACTION_SMS_SEND_FAIL}) {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action != null) {
                    // TODO: check if there is a better place to put this
                    if (action.equals(SmsLoc_Intents.ACTION_SMS_SEND_FAIL)) {
                        SmsSendFailDialog.showDialog(context, intent);
                        return;
                    }
                    if (_listAdapter().mMyLocation == null) {
                        LocationRetriever.getLocation(LOC_DELAY_MS,
                                PeopleFragment.this, PeopleFragment.this.requireActivity());
                    }
                    else if (action.equals(SmsLoc_Intents.ACTION_PERSON_REMOVED)) {
                        final String addr = intent.getStringExtra(SmsLoc_Intents.EXTRA_ADDR);
                        if (addr != null) {
                            mParent.get()._listAdapter().remove(addr);
                        }
                    }
                    else {
                        mParent.get()._listAdapter().notifyDataSetChanged();
                    }
                }
            }
        });
    }

    @Override
    public void onLocationRcvd(Location loc, String msg)
    {
        final FragmentActivity activity = PeopleFragment.this.getActivity();
        if (activity == null) {
            return;
        }
        if (loc == null && !mSecondLocCall) {
            mSecondLocCall = true;
            //TODO:
            // This is a problem for API29 because the getLocationUpdates takes main looper by default
            if (Build.VERSION.SDK_INT > 29) {
                // OK to use, we are running app in the foreground
                LocationRetriever.getLocationWithNetwork(LOC_DELAY_MS, this, activity);
            }

            return;
        }

        mSecondLocCall = false;
        activity.runOnUiThread(() -> {
            try {
                ((SwipeRefreshLayout) PeopleFragment.this.mViewBinding
                        .findViewById(R.id.refresh_people_list)).setRefreshing(false);
                _listAdapter().mMyLocation = loc;
                _listAdapter().notifyDataSetChanged();
            } catch (NullPointerException ignored) {}
        });
    }

    private void _addPerson(@NonNull final String addrIn, @NonNull String name) throws NumberParseException, IllegalArgumentException
    {
        final String addr = SmsUtils.convertToE164PhoneNumFormat(addrIn, null);

        final PeopleDataFile PEOPLEDATA = PeopleDataFile.getInstance(requireContext());
        if (PEOPLEDATA.containsId(addr)) {
            throw new IllegalArgumentException("Contact exists");
        }

        int color = Color.GRAY;
        try (final TypedArray colors = getResources().obtainTypedArray(R.array.letter_tile_colors)) {
            color = colors.getColor((new Random()).nextInt(colors.length()), Color.DKGRAY);
        }
        catch (Resources.NotFoundException ignored) {}

        synchronized (PEOPLEDATA.getLockObject()) {
            PersonData personData = PEOPLEDATA.referenceOrCreateObject_unlocked(addr);
            personData.setDisplayName(name);
            personData.setColor(color);
        }
        PEOPLEDATA.writeFileAsync();

        _listAdapter().add(addr);
        LogFile.getInstance(requireContext()).addLogEntry("Added contact: " + name);
        requireContext().sendBroadcast(SmsLoc_Intents.generateIntentWithAddr(requireContext(), addr, SmsLoc_Intents.ACTION_NEW_PERSON));
    }


    private static class PeopleListAdapter extends ArrayAdapter<String>
    {
        public Location mMyLocation = null;
        private final HashSet<String> mUniqueList;
        private final LayoutInflater  mLayoutInflater;

        private static class ViewHolder {
            TextView  mPersonIcon;
            TextView  mText;
            ImageView mStatusIcon;
        }

        // Set forces the elements to be unique
        public PeopleListAdapter(@NonNull Context context, int resource, @NonNull Set<String> objects) {
                super(context, resource, new ArrayList<>(objects));
                this.mUniqueList = new HashSet<>(objects);
                this.mLayoutInflater = LayoutInflater.from(context);
                this.setNotifyOnChange(true); // we always want to update list when change happens
        }

        // Prevent duplicates
        @Override
        public void add(String addr) {
            if (mUniqueList.add(addr)) { super.add(addr); }
        }
        @Override
        public void remove(String addr) {
            if (mUniqueList.remove(addr)) { super.remove(addr); }
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent)
        {
            final PersonData person  = PeopleDataFile.getInstance(getContext()).getDataEntry(getItem(position));
            final SmsLocData locData = SmsDayDataFile.getInstance(getContext()).getDataEntry(person.getAddr());

            if (convertView == null) {
                // initialize the view
                convertView = mLayoutInflater.inflate(R.layout.list_item_person, parent, false);

                ViewHolder holder = new ViewHolder();
                holder.mPersonIcon = convertView.findViewById(R.id.person_icon);
                holder.mText       = convertView.findViewById(R.id.text);
                holder.mStatusIcon = convertView.findViewById(R.id.status_icon);

                convertView.setTag(holder);

                /* Initials icon */
                //initials with color circle used on map - we don't support custom color so this remains fixed
                final Drawable personIcon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_circle_24, null);
                if (personIcon != null) {
                    personIcon.setColorFilter(new BlendModeColorFilter(person.getColor(), BlendMode.SRC_ATOP));
                    holder.mPersonIcon.setBackground(personIcon);
                }
                else {
                    LogFile.getInstance(getContext()).addLogEntry("BUG: Could not load person icon");
                }
                holder.mPersonIcon.setText(person.getInitials());
            }

            /* gps status icon */
            final int color = _getStatusColor(locData);
            final Drawable statusIcon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_location_marker_24, null);
            if (statusIcon != null) {
                statusIcon.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_ATOP));
                ((ViewHolder) convertView.getTag()).mStatusIcon.setImageDrawable(statusIcon);
            }
            else {
                LogFile.getInstance(getContext()).addLogEntry("BUG: Could not load status icon");
            }

            /* detail text */
            final String text = _getLocationText(locData);
            ((ViewHolder)convertView.getTag()).mText.setText(String.format("%s\n%s", person.getDisplayName(), text));

            return convertView;
        }

        private int _getStatusColor(SmsLocData locData)
        {
            if (locData == null) {
                return Color.GRAY;
            }
            if (!locData.requestPending() && !locData.hasLocationData()) {
                // we only received requests from this person
                return Color.GRAY;
            }
            if (locData.locationUpToDate()) {
                return Color.rgb(39,204,44);
            }
            if (locData.requestPending()) {
                return Color.rgb(255,174,66);
            }
            if (!locData.lastResponseValid()) {
                return Color.RED;
            }
            return Color.GRAY;
        }

        private String _getLocationText(SmsLocData locData) {
            String text = "No location data";
            if (locData != null && locData.getLastValidLocation() != null) {
                text = String.format("Last valid location: %s\n\tElapsed: %s\n",
                        Utils.msToStr(locData.getLastValidLocation().utc),
                        Utils.timeToNowStr(locData.getLastValidLocation().utc));

                Float distance = null;
                if (mMyLocation != null) {
                    distance = locData.getLastValidLocation().distanceFrom(mMyLocation);
                }
                text += (distance == null) ?
                        "\tDistance: Get My Loc Failed" //locRetriever.getInvalidLocReasonSimple())
                      : String.format(SmsLoc_Common.LOCALE, "\tDistance: %.4f km", distance/1000.0);
            }
            return text;
        }
    }
}
