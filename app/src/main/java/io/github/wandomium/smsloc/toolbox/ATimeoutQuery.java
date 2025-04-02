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
package io.github.wandomium.smsloc.toolbox;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for query with timeout.
 *
 * Once shutdown is called, the resources are freed and the query cannot be reused
 *
 * @param <T>
 */
public abstract class ATimeoutQuery<T>
{
    private Callback mCallback;

    private ScheduledThreadPoolExecutor mScheduler;

    private Runnable mTimeoutRunnable;
    private ScheduledFuture mTimeoutFuture;

    private Runnable mQueryRunnable;
    private ScheduledFuture mQueryFuture;

    @FunctionalInterface
    public interface Callback<T>
    {
        void onQueryDone(T result);
    }

    /**
     * @brief The main query thread, should be a synchronous call
     *
     * Note: This should be interruptible trough overrride (or rather extend) of
     * default cancelCall call or
     * by checking for thread interrupted exception (initiated trough future.cancel in
     * default cancelCall implementation)
     *
     * @return Value retrieved by query, can be null on failure
     */
    protected abstract T _execute();

    public ATimeoutQuery(@NonNull Callback callback)
    {
        mCallback = callback;

        mScheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
        mScheduler.setRemoveOnCancelPolicy(true);

        mTimeoutRunnable = () -> {
            cancelCall();
            mCallback.onQueryDone(null);
        };
        mQueryRunnable = () -> {
            final T result = _execute();
            mTimeoutFuture.cancel(true);
            mCallback.onQueryDone(result);
        };
    }

    public boolean enqueueCall(int timeoutMs)
    {
        // If scheduler is shut down it means we stopped everything
        // most references are already null
        if (mCallback == null || mScheduler.getActiveCount() != 0 || mScheduler.isShutdown()) {
            return false;
        }

        // remove any scheduled tasks that were not canceled, mem cleanup
        // has no efect on execution
        mScheduler.purge();

        // runnable is assigned to the Future object returned here
        // scheduled runnables are not plced directly into queue. What is in
        // the queue is the returned Future
        mTimeoutFuture = mScheduler.schedule(mTimeoutRunnable, timeoutMs, TimeUnit.MILLISECONDS);
        mQueryFuture = mScheduler.schedule(mQueryRunnable, 0, TimeUnit.NANOSECONDS);

        return true;
    }

    /**
     * @brief should stop the process being executed in _execute.
     * Default implementation calls future.cancel and expects the
     * process in _execute to be interruptible
     */
    public boolean cancelCall()
    {
        Log.i("TimeoutQuery", "cancellCall");
        // Canceled tasks get removed immediately due to policy set at the beginning
        try {
            mTimeoutFuture.cancel(true);
            mQueryFuture.cancel(true);
        }
        catch (NullPointerException e) {
            Log.i("TEST", "test");} //if no call was issued
        // TODO check this: tryTerminate()  must be called after bla bl
        return mScheduler.getActiveCount() == 0;
    }

    public boolean shutdown()
    {
        mScheduler.shutdown();
        cancelCall();

        // purge is already called in cancel
        // removing runnables does nothing because they are not directly in the queue, future objects are

        // future holds reference to runnable that holds reference to cb and this class
        mCallback = null;

        mTimeoutRunnable = null;
        mTimeoutFuture = null;

        mQueryRunnable = null;
        mQueryFuture = null;

//        Log.i("TimeoutQuery", "scheduler terminated: "  + (mScheduler.isTerminated() ? "TRUE" : "FALSE"));
//        Log.i("TimeoutQuery", "active count: "  + mScheduler.getActiveCount());
//        Log.i("TimeoutQuery", "queue count: "  + mScheduler.getQueue().size());
//        Log.i("TimeoutQuery", "task count: "  + mScheduler.getTaskCount());

        return mScheduler.isTerminated(); //TODO
    }
}
