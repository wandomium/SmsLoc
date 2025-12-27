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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.DefaultConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;

import io.github.wandomium.smsloc.BuildConfig;
import io.github.wandomium.smsloc.R;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.mapdata.OsmdroidTracksDisplay;
import io.github.wandomium.smsloc.toolbox.Utils;


public class OsmdroidMapFragment extends AMapFragment
{
    private MapView mMapView;
    private int myLocationOverlayIdx;
    private final double mDefaultZoom = 12.0;

    private boolean mMapScrollModeOn = false;
    private OnBackPressedCallback mBackPressedCb;

    public OsmdroidMapFragment() { super(R.layout.fragment_osmdroid);}
    public static OsmdroidMapFragment newInstance(final int position) {
        final OsmdroidMapFragment newInstance = new OsmdroidMapFragment();
        _initInstance(newInstance, position);
        return newInstance;
    }

    public static class PredefinedConfigProvider extends DefaultConfigurationProvider
    {
        public PredefinedConfigProvider(Context ctx) {
            userAgentValue = BuildConfig.APPLICATION_ID;
            osmdroidBasePath = new File(ctx.getExternalFilesDir(null), "osmdroid");
            osmdroidTileCache = new File(osmdroidBasePath, "tiles");
            if (!osmdroidTileCache.exists()) {
                if (!osmdroidTileCache.mkdirs()) {
                    /* Set this to null and have osmdroid deal with it */
                    osmdroidTileCache = null;
                    osmdroidBasePath = null;
                }
            }
        }

        /* TODO: this is strange. version 6.1.20 should no longer call mkdirs in this getter (based
         * on github. But it is clear that it's called from StrictMode reports
         *
         * Override to prevent StrictMode violations as much as we can
         */
        @Override
        public File getOsmdroidTileCache() {
            return osmdroidTileCache;
        }

        @Override
        public File getOsmdroidTileCache(Context ctx) {
            return osmdroidTileCache;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
//            _loadConfig(this.requireActivity().getApplicationContext());
            Configuration.setConfigurationProvider(new PredefinedConfigProvider(
                    this.requireActivity().getApplicationContext()
            ));

            requireActivity().runOnUiThread(() -> {
                /* Create map view. This will violate strict mode because osmdroid does I/O
                   TODO: check if this can be fixed by using a custom config provider
                 */
                final String msg = OsmdroidMapFragment.class.getSimpleName() + ":new MapView(getContext())";
                Utils.Debug.strictModeDiskIOOff(msg);
                mMapView = new MapView(getContext());
                Utils.Debug.strictModeDiskIOOn(msg);

                /* add to container */
                FrameLayout container = view.findViewById(R.id.map_container);
                container.addView(mMapView);

                /* Configure map */
                _configureMapView();
                _configureMapScroll();
            });

            executor.shutdownNow();
        });
    }

    @Override
    public void onDestroyView()
    {
        mTracksDisplay.removeAll();
        mTracksDisplay = null;

        mMapView.getOverlays().clear();
        mMapView.onDetach();
        mMapView = null;

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume(); //needed for compass, my location overlays, v6.0.0 and up
        }
    }

    @Override
    public void onPause()
    {
        //!! will call onPause on overlays. will for ex. disable location overlay
        if (mMapView != null) {
            mMapView.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        }
        super.onPause();
    }

    public Location getLastFix() {
        // getMyLocation calls getLastFix and converts from android.Location to GeoPoint
        return ((MyLocationNewOverlay)mMapView.getOverlays().get(myLocationOverlayIdx)).getLastFix();
    }

    private void _configureMapView()
    {
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.setHorizontalMapRepetitionEnabled(false);
        mMapView.setVerticalMapRepetitionEnabled(false);
        mMapView.setTilesScaledToDpi(true);
        mMapView.getController().setZoom(3.0);

        mTracksDisplay = new OsmdroidTracksDisplay(mMapView, PeopleDataFile.getInstance(getContext()));
        mTracksDisplay.initFromDayData(SmsDayDataFile.getInstance(getContext()).getDataAll());

        /* Initial bounds show all the tracks */
        final BoundingBox initBounds = ((OsmdroidTracksDisplay) mTracksDisplay).getBounds();
        if (initBounds != null) {
            mMapView.addOnFirstLayoutListener(
                    (View v, int left, int top, int right, int bottom) -> ((MapView) v).zoomToBoundingBox(initBounds, true, 200));
        }

        /* Display copyright */
        mMapView.getOverlays().add(new CopyrightOverlay(requireContext()));

        /* On screen compass */
        {
            final CompassOverlay overlay = new CompassOverlay(requireContext(), new InternalCompassOrientationProvider(requireContext()), mMapView);
            overlay.enableCompass();
            mMapView.getOverlays().add(overlay);
        }

        /* Scale bar */
        {
            final ScaleBarOverlay overlay = new ScaleBarOverlay(mMapView);
            overlay.setAlignBottom(true);
            overlay.setAlignRight(true);
            //overlay.setScaleBarOffset(getActivity().getResources().getDisplayMetrics().widthPixels / 2, 20);
            mMapView.getOverlays().add(overlay);
        }

        /* My location */
        {
            final MyLocationNewOverlay overlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mMapView);
            overlay.enableMyLocation();
            //This would automatically center on user location but we don't want it. We want to center on
            //location updates received as responses
            //overlay.enableFollowLocation();
            overlay.setDrawAccuracyEnabled(true);

            // If we have no data we zoom to my location
            if (initBounds == null) {
                overlay.runOnFirstFix(() -> {
                    final Activity act = OsmdroidMapFragment.this.getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> {
                            if (overlay.getMyLocation() != null) {
                                OsmdroidMapFragment.this.mMapView.getController().animateTo(overlay.getMyLocation(), mDefaultZoom, null);
                            }
                            Toast.makeText(OsmdroidMapFragment.this.getContext(),
                                    overlay.getMyLocation() != null ? "GPS fix OK" : "GPS fix FAIL", Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }
            mMapView.getOverlays().add(overlay);
            myLocationOverlayIdx = mMapView.getOverlays().size() - 1;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void _configureMapScroll() {
        mBackPressedCb = new OnBackPressedCallback(mMapScrollModeOn) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(mMapScrollModeOn = false);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), mBackPressedCb);

        mMapView.setOnTouchListener((v, event) -> {
            if (!mMapScrollModeOn && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                VelocityTracker velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000); // px/sec
                final float vx = velocityTracker.getXVelocity();
                // zeros are flukes
                if (vx != 0.0f && Math.abs(vx) < 100f) {
                    mBackPressedCb.setEnabled(mMapScrollModeOn = true);
                }
                velocityTracker.recycle();
            }

            if (mMapScrollModeOn) {
                // avoid keeping direct reference to viewPager
                ViewParent parent = v.getParent();
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                    parent = parent.getParent();
                }
            }

            return false; // let MapView handle zoom/pan normally
        });
    }

    @Override
    protected void _clearPopups() { }

    @Override
    protected void _zoomToLastPoint() {
        if (mLastUpdateLoc != null && mLastUpdateLoc.dataValid()) {
            mMapView.getController().animateTo(new GeoPoint(mLastUpdateLoc.lat, mLastUpdateLoc.lon), mDefaultZoom, null);
        }
    }
}
