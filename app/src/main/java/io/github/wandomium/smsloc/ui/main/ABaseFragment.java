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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import io.github.wandomium.smsloc.toolbox.ABaseBrdcstRcv;

import java.util.ArrayList;

public abstract class ABaseFragment extends Fragment
{
    private static final String ARG_SECTION_NUMBER = "section_number";

    protected final ArrayList<ABaseBrdcstRcv<? extends ABaseFragment>> mReceiverList;
    protected final int mLayoutId;
    protected View mViewBinding;

    protected abstract void _createBroadcastReceivers();

    protected ABaseFragment(int layoutId) {
        mLayoutId = layoutId;
        mReceiverList = new ArrayList<>();
    }

    protected static void _initInstance(final ABaseFragment instance, final int position) {
        Bundle bundle = new Bundle();
        bundle.putInt(ABaseFragment.ARG_SECTION_NUMBER, position);
        instance.setArguments(bundle);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    // It is recommended to only inflate the layout in this method and move logic that operates on
    // the returned View to onViewCreated(View, Bundle).
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        mViewBinding = inflater.inflate(mLayoutId, container, false);
        return mViewBinding;
    }

    //Create and register all the intent receivers to this fragment context
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        _createBroadcastReceivers();
        for (ABaseBrdcstRcv<? extends ABaseFragment> r : mReceiverList) {
                // requireContext().registerReceiver(r, r.getIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
                // This one also works on API < 33
                ContextCompat.registerReceiver(requireContext(), r, r.getIntentFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    //Unregister all receivers. We don't need to display updates if there is no view
    //This one is always called, onDestroy not necessarily!!
    @Override
    public void onDestroyView()
    {
        for (ABaseBrdcstRcv<? extends ABaseFragment> r : mReceiverList) {
            requireContext().unregisterReceiver(r);
        }
        super.onDestroyView();
        mViewBinding = null;
    }
}
