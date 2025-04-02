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

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;

import io.github.wandomium.smsloc.R;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.file.SmsDayDataFile;
import io.github.wandomium.smsloc.defs.SmsLoc_Common;
import io.github.wandomium.smsloc.mapdata.OsmdroidTracksDisplay;


public class OsmdroidMapFragment extends AMapFragment
{
    private MapView mMapView;
    private int myLocationOverlayIdx;
    private final double mDefaultZoom = 12.0;

    protected OsmdroidMapFragment() { super(R.layout.fragment_osmdroid);}

    public Location getLastFix()
    {
        // getMyLocation calls getLastFix and converts from android.Location to GeoPoint
        return ((MyLocationNewOverlay)mMapView.getOverlays().get(myLocationOverlayIdx)).getLastFix();
    }

    @Override
    public void _clearPopups() { }

    @Override
    protected void _zoomToLastPoint()
    {
        try {
            mMapView.getController().animateTo(new GeoPoint(mLastUpdateLoc.lat, mLastUpdateLoc.lon), mDefaultZoom, null);
        } catch (NullPointerException e) {}
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        //load/initialize the osmdroid configuration, this can be done
        final Context ctx = this.getActivity();
        Configuration.getInstance().setUserAgentValue(SmsLoc_Common.Consts.APP_NAME);
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's
        //tile servers will get you banned based on this string

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        //inflate and create the map
        mMapView = (MapView) view.findViewById(R.id.mapOsmDroid);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);

        mMapView.setMultiTouchControls(true);
        mMapView.setHorizontalMapRepetitionEnabled(false);
        mMapView.setVerticalMapRepetitionEnabled(false);
        mMapView.setTilesScaledToDpi(true);
        mMapView.getController().setZoom(3.0);

        mTracksDisplay = new OsmdroidTracksDisplay(mMapView, PeopleDataFile.getInstance(getContext()));
        mTracksDisplay.initFromDayData(SmsDayDataFile.getInstance(getContext()).getDataAll());

        // Initial bounds show all the tracks
        final BoundingBox initBounds = ((OsmdroidTracksDisplay) mTracksDisplay).getBounds();
        if (initBounds != null) {
            mMapView.addOnFirstLayoutListener(
                    (View v, int left, int top, int right, int bottom) -> {
                        ((MapView) v).zoomToBoundingBox(initBounds, true, 200);
                    });
        }

        //Display copyright
        mMapView.getOverlays().add(new CopyrightOverlay(getContext()));

        //On screen compass
        {
            final CompassOverlay overlay = new CompassOverlay(getContext(), new InternalCompassOrientationProvider(getContext()), mMapView);
            overlay.enableCompass();
            mMapView.getOverlays().add(overlay);
        }

        //Scale bar
        {
            final ScaleBarOverlay overlay = new ScaleBarOverlay(mMapView);
            overlay.setAlignBottom(true);
            overlay.setAlignRight(true);
            //overlay.setScaleBarOffset(getActivity().getResources().getDisplayMetrics().widthPixels / 2, 20);
            mMapView.getOverlays().add(overlay);
        }

        //My location
        {
            final MyLocationNewOverlay overlay = new MyLocationNewOverlay(new GpsMyLocationProvider(getContext()), mMapView);
            overlay.enableMyLocation();
            //overlay.enableFollowLocation();
            overlay.setDrawAccuracyEnabled(true);

            // If we have no data we zoom to my location
            if (initBounds == null) {
                overlay.runOnFirstFix(() -> {
                    final Activity act = OsmdroidMapFragment.this.getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> {
                            if (overlay.getMyLocation() != null)
                            {
                                OsmdroidMapFragment.this.mMapView.getController().animateTo(overlay.getMyLocation(), mDefaultZoom, null);
                            }
                            Toast.makeText(OsmdroidMapFragment.this.getContext(),
                                    "GPS fix " + overlay.getMyLocation() != null ? "GPS fix OK" : "GPS fix FAIL", Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }
            mMapView.getOverlays().add(overlay);
            myLocationOverlayIdx = mMapView.getOverlays().size() - 1;
        }
    }

    @Override
    public void onDestroyView()
    {
        mTracksDisplay.removeAll();
        mTracksDisplay = null;

//        mMapView.getOverlayManager().removeAll(mMapView.getOverlays());
        mMapView.getOverlays().clear();
        mMapView = null;

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        mMapView.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause()
    {
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        Configuration.getInstance().save(this, prefs);


        //!! will call onPause on overlays. will for ex. disable location overlay
        mMapView.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        super.onPause();
    }
}
