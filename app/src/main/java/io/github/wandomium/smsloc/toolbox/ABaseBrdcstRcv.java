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
package io.github.wandomium.smsloc.toolbox;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

import io.github.wandomium.smsloc.ui.main.ABaseFragment;

public abstract class ABaseBrdcstRcv<T extends ABaseFragment> extends BroadcastReceiver
{
    protected final WeakReference<T> mParent;
    protected final String[] mActions;

    protected IntentFilter mFilter;

    public ABaseBrdcstRcv(@NonNull T parent, @NonNull String[] actions)
    {
        this.mParent = new WeakReference<>(parent);
        this.mActions = actions.clone();
    }

    final public IntentFilter getIntentFilter()
    {
        if (mFilter == null)
        {
            mFilter = new IntentFilter();
            for (String a : mActions) {
                mFilter.addAction(a);
            }
        }
        return mFilter;
    }
}
