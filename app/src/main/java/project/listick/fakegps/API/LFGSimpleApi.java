package project.listick.fakegps.API;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import project.listick.fakegps.AsyncWebClient;
import project.listick.fakegps.Enumerations.ERouteTransport;

// Created by LittleAngry
// powered by OpenRouteService
public class LFGSimpleApi {

    public static final String API_BASE_URL = "https://k92321.hostch02.fornex.host/lfg/";

    public interface ElevationCallback {
        void onRequestSuccess(float altitude);
        void onRequestError();
    }

    public LFGSimpleApi() { }

    public static class Geocoder {
        public static String getAutcompleteURL(String address, double latitude, double longitude) {

            StringBuilder builder = new StringBuilder();
            builder.append(API_BASE_URL)
                    .append("autocomplete.php")
                    .append("?text=")
                    .append(address)
                    .append("&focus_point_lon=")
                    .append(longitude)
                    .append("&focus_point_lat=")
                    .append(latitude);


            return builder.toString();
        }
    }

    public static class Elevation {
        private final File mCacheDir;

        public Elevation(File cacheDir) {
            this.mCacheDir = cacheDir;
        }

        private String getElevationServiceURL(double latitude, double longitude) {
            StringBuilder builder = new StringBuilder();
            builder.append(API_BASE_URL)
                    .append("get_altitude.php")
                    .append("?geometry=")
                    .append(longitude).append(",").append(latitude);
            return builder.toString();
        }

        public void getElevation(double latitude, double longitude, ElevationCallback callback) {
            AsyncWebClient webClient = new AsyncWebClient(null, mCacheDir);
            webClient.connect(getElevationServiceURL(latitude, longitude), new AsyncWebClient.Callback() {
                @Override
                public void onSuccess(ResponseBody responseBody) {
                    String body = null;
                    try {
                        body = responseBody.string();
                    } catch (IOException e) {
                        android.util.Log.d(project.listick.fakegps.BuildConfig.APPLICATION_ID, null, e);
                    }
                    if (body == null) {
                        onError();
                        return;
                    }

                    try {
                        JSONObject bodyObj = new JSONObject(body);
                        JSONArray geometry = bodyObj.getJSONArray("geometry");
                        float altitude = (float) geometry.getDouble(2);
                        callback.onRequestSuccess(altitude);
                    } catch (JSONException e) {
                        android.util.Log.d(project.listick.fakegps.BuildConfig.APPLICATION_ID, null, e);
                        onError();
                    }
                }

                @Override
                public void onError() {
                    callback.onRequestError();
                }
            });
        }
    }

    public static class Directions {
        private final double sourcelat;
        private final double sourcelong;
        private final double destlat;
        private final double destlong;

        private double distance;

        private final ERouteTransport transport;

        public Directions(double sourcelat, double sourcelong, double destlat, double destlong, ERouteTransport transport) {
            this.sourcelat = sourcelat;
            this.sourcelong = sourcelong;
            this.destlat = destlat;
            this.destlong = destlong;
            this.transport = transport;
        }

        private String getRouteBuilderUrl() {
            return API_BASE_URL + "build_route.php?profile=" + transport.name();
        }

        public String getContent() {
            AsyncWebClient.Sync syncWeb = new AsyncWebClient.Sync(getRouteBuilderUrl());
            Map<String, Object[]> coordinates = new HashMap<>();
            coordinates.put("coordinates", new Object[] {
                    new Object[]{sourcelong, sourcelat},
                    new Object[]{destlong, destlat},
            });

            JSONObject parameter = new JSONObject(coordinates);
            try {
                parameter.put("elevation", String.valueOf(true));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            syncWeb.addHeader("Content-Type", "application/json; charset=utf-8");
            syncWeb.post(parameter);
            Log.d("SuperPsycho", "elevation: " + parameter.toString());

            return syncWeb.connect();
        }

        public ArrayList<GeoPoint> downloadRoute(String content, boolean is3d) {
            try {
                JSONObject contentObject = new JSONObject(content);
                JSONArray routeArray = contentObject.getJSONArray("routes");
                JSONObject routes = routeArray.getJSONObject(0);
                String encodedString = routes.getString("geometry");
                ArrayList<GeoPoint> points = decodePolyline(encodedString, is3d);

                JSONObject jsonObject = routes.getJSONObject("summary");
                distance = jsonObject.getDouble("distance");

                return points;
            } catch (Exception e) {
                android.util.Log.d(project.listick.fakegps.BuildConfig.APPLICATION_ID, null, e);
                return null;
            }
        }

        public double getDistance(){
            return distance;
        }

        public static ArrayList<GeoPoint> decodePolyline(String encoded, boolean is3D) {
            ArrayList<GeoPoint> poly = new ArrayList<>();
            int index = 0;
            int len = encoded.length();
            int lat = 0, lng = 0, ele = 0;
            while (index < len) {
                // latitude
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += deltaLatitude;

                // longitude
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += deltaLongitude;

                if (is3D) {
                    // elevation
                    shift = 0;
                    result = 0;
                    do {
                        b = encoded.charAt(index++) - 63;
                        result |= (b & 0x1f) << shift;
                        shift += 5;
                    } while (b >= 0x20);
                    int deltaElevation = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                    ele += deltaElevation;

                    GeoPoint p = new GeoPoint((double) lat / 1e5, (double) lng / 1e5, (double) ele / 100);
                    poly.add(p);
                } else {
                    GeoPoint p = new GeoPoint((double) lat / 1e5, (double) lng / 1e5, Integer.MIN_VALUE);
                    poly.add(p);
                }
            }
            return poly;
        }


    }

}

