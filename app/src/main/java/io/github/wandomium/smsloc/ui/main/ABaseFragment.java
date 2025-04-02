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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcvr;

import java.util.ArrayList;

public abstract class ABaseFragment extends Fragment
{
    public static final String ARG_SECTION_NUMBER = "section_number";

    protected ArrayList<ABaseBrdcstRcvr> mReceiverList;
    protected final int mLayoutId;

    protected abstract void _createBroadcastReceivers();

    protected ABaseFragment(int layoutId)
    {
        mLayoutId = layoutId;
        mReceiverList = new ArrayList<ABaseBrdcstRcvr>();
    }

    // It is recommended to only inflate the layout in this method and move logic that operates on
    // the returned View to onViewCreated(View, Bundle).
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        return inflater.inflate(mLayoutId, container, false);
    }

    //Create and register all the intent receivers
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        _createBroadcastReceivers();
        for (ABaseBrdcstRcvr r : mReceiverList) {
            getActivity().registerReceiver(r, r.getIntentFilter());
        }
    }

    //Unregister all receivers. We don't need to display updates if there is no view
    //This one is always called, onDestroy not necessarily!!
    @Override
    public void onDestroyView()
    {
        for (ABaseBrdcstRcvr r : mReceiverList) {
            getActivity().unregisterReceiver(r);
        }

        super.onDestroyView();
    }
}
