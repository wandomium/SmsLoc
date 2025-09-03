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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TabPagerAdapter extends FragmentStateAdapter
{
    private final String TAG = getClass().getSimpleName();

    public enum Tabs {
        People("LIST"), Map("MAP"), Log("Activity Log");
        Tabs(final String title) { this.cTitle = title; }

        public final String     cTitle;
        //Because having this define in enum is not a compile time constant. MAKE SURE THIS IS ALWAYS UP TO DATE!!!!!
        public static final int cCount = values().length;
        public static Tabs fromInt(final int position) {
            return position > Log.ordinal() ? null : (position < 0 ? null : values()[position]);
        }
    }

    public TabPagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @Override
    public int getItemCount() { return Tabs.cCount;}

    //This is only called if instantiateItem method does not have an instance of this fragment
    //when the fragment needs to be displayed
    @NonNull
    @Override
    public Fragment createFragment(int position)
    {
        final Tabs tab = Tabs.fromInt(position);
        if (tab == null) {
            Log.wtf(TAG, "Invalid fragment id: " + position);
            throw new IllegalArgumentException("Invalid fragment id: " + position);
        }

        return switch (tab) {
            case People -> PeopleFragment.newInstance(position);
            case Map -> OsmdroidMapFragment.newInstance(position);
            case Log -> LogFragment.newInstance(position);
        };
    }
}
