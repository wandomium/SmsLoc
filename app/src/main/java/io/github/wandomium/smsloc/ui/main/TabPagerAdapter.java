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

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class TabPagerAdapter extends FragmentPagerAdapter
{
    public static final int PEOPLE_TAB_ID = 0;
    public static final int MAP_TAB_ID = 1;
    public static final int LOG_TAB_ID = 2;
//    public static final int DEBUG_TAB_ID = 3;
    public static final int NUM_TABS = 3;
    public static final String[] TAB_TITLES = new String[]{"List", "Map", "Tx/Rx Log", "Debug"};

    //TODO-Minor
    //Recheck this behaviour and if this is something we need or can we keep default (but default
    //is depreciated
    public TabPagerAdapter(FragmentManager fragmentManager)
    {
        super(fragmentManager, FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return TAB_TITLES[position];
    }

    @Override
    public int getCount() { return NUM_TABS; }

    //This is only called if instantiateItem method does not have an instance of this fragment
    //when the fragment needs to be displayed
    @Override
    public Fragment getItem(int position)
    {
        Fragment fragment;

        switch (position)
        {
            case PEOPLE_TAB_ID:
                fragment = new PeopleFragment(); break;
            case MAP_TAB_ID:
                fragment = new OsmdroidMapFragment(); break;
            case LOG_TAB_ID:
                fragment = new LogFragment(); break;
//            case DEBUG_TAB_ID:
//                fragment = new MapboxMapFragment(); break;
            default:
                return null;
        }

        Bundle bundle = new Bundle();
        bundle.putInt(ABaseFragment.ARG_SECTION_NUMBER, position);
        fragment.setArguments(bundle);

        return fragment;
    }
}
