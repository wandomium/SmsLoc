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
package io.github.wandomium.smsloc.data.unit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;


public class GeoJson {

    public static final class PointCollection {
        private final String type = "FeatureCollection";
        private final Point[] features;

        public PointCollection(Point[] features) {
            this.features = features.clone();
        }
    }

    /** helper class to create a point feature **/
    public static final class Point {
        public final String type = "Feature";
        public final Geometry geometry;
        public final GpsData properties;

        private Point(GpsData data) {
            this.properties = data;
            this.geometry = new Geometry("Point", data.lon, data.lat, data.alt_m);
        }

        public static Point fromGpsData(GpsData data) {
            return new Point(data);
        }

        public static Point fromJson(String json) {
            try {
                return (new Gson()).fromJson(json, Point.class);
            }
            catch (JsonSyntaxException e) { return null; }
            catch (JsonParseException e)  { return null; }
        }
        public String toJson() {
            return (new Gson()).toJson(this);
        }

        /**
         * coordinates:
         * An OPTIONAL third-position element SHALL be the height in meters
         * above or below the WGS 84 reference ellipsoid.
         *
         * This can be found under "4. Coordinate Reference System" in RFC 7946
         * available at https://datatracker.ietf.org/doc/rfc7946/?include_text=1
         */
        public final static class Geometry {
            private final String type;
            private final double[] coordinates; //move to primitive so that clone will create a deep copy

            public Geometry(String type, double lon, double lat, double alt) {
                this.type = type;
                this.coordinates = new double[]{lon, lat, alt};
            }

            public double[] getCoordinates() {
                return coordinates.clone();
            }
            public String getType() {
                return type;
            }

            //These two are not really needed, geometry is immutable
    /*
            public Geometry(Geometry geometry) {
                this.type = geometry.type;
                this.coordinates = geometry.coordinates.clone();
            }

            public Geometry deepCopy() {
                return new Geometry(this.type, this.coordinates);
            }
    */
        }
    /*
        public static class SortByGpsUtc implements Comparator<GeoJsonPoint> {
            protected final int mOrder;
            public SortByGpsUtc(int order) {
                mOrder = (order < 0) ? -1 : 1;
            }

            @Override
            public int compare(GeoJsonPoint o1, GeoJsonPoint o2) {
                //if (o1.utc_s == null || o2.utc_s == null) {return 0;}
                if (o1.properties.utc > o2.properties.utc)  { return mOrder;}  //{ return 1;  }
                if (o1.properties.utc == o2.properties.utc) { return 0;  }
                if (o1.properties.utc < o2.properties.utc)  { return -mOrder;} //{ return -1; }
                return 0;
            }
        }
     */
    }
}
