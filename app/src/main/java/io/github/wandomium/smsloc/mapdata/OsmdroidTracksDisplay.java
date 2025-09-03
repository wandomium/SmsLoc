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
package io.github.wandomium.smsloc.mapdata;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.milestones.MilestoneManager;
import org.osmdroid.views.overlay.milestones.MilestoneVertexLister;

import io.github.wandomium.smsloc.data.file.PeopleDataFile;
import io.github.wandomium.smsloc.data.unit.GpsData;
import io.github.wandomium.smsloc.data.unit.PersonData;
import io.github.wandomium.smsloc.ui.main.AMapFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class OsmdroidTracksDisplay extends AMapTracksDisplay
{
    private final WeakReference<MapView> mMapView;

    public OsmdroidTracksDisplay(MapView mapView, PeopleDataFile peopledata)
    {
        super(peopledata);
        mMapView = new WeakReference<>(mapView);
    }

    @Override
    protected IMapTrack _createTrack(PersonData person)
    {
        OsmdroidTrack track = new OsmdroidTrack(person, mMapView.get());
        mMapView.get().getOverlayManager().add(track);

        return track;
    }

    @Override
    public IMapTrack removeTrack(String addr)
    {
        final IMapTrack track = super.removeTrack(addr);
        if (track instanceof OsmdroidTrack) {
            if (mMapView.get().getOverlayManager().remove(track)) {
                return track;
            }
        }
        return null;
    }

    /**
     * @return null if no points were added to the display, otherwise returns a bounding box
     *         Bounding box minimum lat and lon span is 1.0
     */
    public BoundingBox getBounds()
    {
        BoundingBox box = null;

        for (IMapTrack track : mTracks.values()) {
            if (box == null) {
                box = ((OsmdroidTrack)track).getBounds();
            }
            else {
                box = ((OsmdroidTrack)track).getBounds().concat(box);
            }
        }

        if (box != null) {
            if (box.getLatitudeSpan() < 1.0) {
                box.setLatNorth(box.getLatNorth() + 0.5);
                box.setLatSouth(box.getLatSouth() - 0.5);
            }
            if (box.getLongitudeSpanWithDateLine() < 1.0)
            {
                box.setLonEast(box.getLonEast() + 0.5);
                box.setLonWest(box.getLonWest() - 0.5);
            }
        }

        return box;
    }

    protected static class GeoPointExt extends GeoPoint
    {
        public final GpsData gpsData;

        public GeoPointExt(GpsData gpsData) {
            super(gpsData.lat, gpsData.lon, gpsData.alt_m);
            this.gpsData = gpsData;
        }

        @NonNull
        @Override
        public String toString()
        {
            return AMapFragment.markerDataString(this.gpsData);
        }
    }

    protected static class OsmdroidTrack extends Polyline implements IMapTrack
    {
        protected final PersonData mPerson;

        private OsmdroidTrack(@NonNull PersonData personData, @NonNull MapView mapView)
        {
            super(mapView);

            mPerson = personData.getUnitCopy();

            List<MilestoneManager> mngrs = new ArrayList<>(1);
            mngrs.add(new MilestoneManager(new MilestoneVertexLister(),
                    new TrackPointsDisplay(mPerson.getColor(), mPerson.getInitials())));
            this.setMilestoneManagers(mngrs);

            this.getOutlinePaint().setColor(Color.BLACK);
            this.setTitle(mPerson.getDisplayName());
        }

        @Override
        public void onDetach(MapView mapView)
        {
            super.onDetach(mapView);
        }

        /** Polyline click listener */
        @Override
        public boolean onClickDefault(Polyline polyline, MapView mapView, GeoPoint geoPoint)
        {
            for (GeoPoint p : this.getActualPoints())
            {
                if (p.distanceToAsDouble(geoPoint) < 100.0) //TODO: adapt this based on zoom value
                {
                    final GeoPointExt clickedPoint = (GeoPointExt) p;

                    // default info window statement
                    this.setInfoWindowLocation(clickedPoint);
                    this.setSubDescription(AMapFragment.markerDataString(clickedPoint.gpsData));
                    this.showInfoWindow();

                    return true;
                }
            }
            mapView.zoomToBoundingBox(polyline.getBounds(), true, 200);
            polyline.closeInfoWindow();
            return false;
        }

        /** IMapTrack */
        @Override
        public void updateData(GpsData point)
        {
            this.addPoint(new GeoPointExt(point));

            //Unfortunately it seems that there is a bug where if there is only one point,
            //the milestone sometimes doesn't draw
            //Does not happen always but we want to be sure (TODO: check if release and debug problem)
            if (this.getActualPoints().size() == 1) {
                this.addPoint(this.getActualPoints().get(0));
            }
        }
        @Override
        public boolean equals(Object o)
        {
            if (o instanceof OsmdroidTrack) {
                return ((OsmdroidTrack)o).mPerson.getAddr().equals(this.mPerson.getAddr());
            }
            return false;
        }

        /** Display markers - received gps datapoints */
        private static class TrackPointsDisplay extends org.osmdroid.views.overlay.milestones.MilestoneDisplayer
        {
            private final Paint  mTextPaint;
            private final Paint  mMarkerPaint;
            private final String mInitials;

            private TrackPointsDisplay(@ColorInt int color, String initials)
            {
                super(0, false);
                mInitials = initials != null ? initials : "??";
                mTextPaint = new Paint();
                mTextPaint.setColor(Color.WHITE);
                mTextPaint.setTextSize(40);
                mTextPaint.setAntiAlias(true);
                mMarkerPaint = new Paint();
                mMarkerPaint.setColor(color);
                mMarkerPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            }

            @Override
            protected void draw(Canvas pCanvas, Object pParameter)
            {
                final boolean last = (int) pParameter < 0;
                final float radius = last ? 40 : 20;
                pCanvas.drawCircle(0, 0, radius, mMarkerPaint);
                if (last) {
                    pCanvas.drawText(mInitials, -25, 15, mTextPaint);
                }
            }
        }
    }
}
