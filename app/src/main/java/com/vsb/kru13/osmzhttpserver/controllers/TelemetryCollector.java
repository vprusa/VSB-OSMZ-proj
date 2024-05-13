package com.vsb.kru13.osmzhttpserver.controllers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.vsb.kru13.osmzhttpserver.AppLogger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Telemetry controller.
 *
 * - Reimplementing the standard MVC system.
 * - There is a problem with empty/unused implemented methods with @Override.
 *     Empty @Override methods are bad practice and suggest need for splitting the interface to multiple
 *     smalled interfaces.
 */
public class TelemetryCollector implements SensorEventListener, LocationListener {
    private final SensorManager sensorManager;
    private final LocationManager locationManager;
    private final JSONObject telemetryData = new JSONObject();
    private final AppLogger logger;
    private static final long MIN_TIME_BW_UPDATES = 1000; // Every second
    private static final float MIN_DISTANCE_CHANGE_FOR_UPDATES = 0.1f; // 0.1 meters

    private final static String ERR_TAG = "GPS";
    private final Context context;

    // TODO analyse why the compiler complains regardless of `hasGPSPermissions`
    @SuppressLint("MissingPermission")
    public TelemetryCollector(Context context, AppLogger logger) {
        this.context = context;
        this.logger = logger;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // Register sensor listeners and location updates
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        if (!hasGPSPermissions(context)) {
            // idea ide generated...
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            logger.logError(ERR_TAG, "Missing ACCESS_FINE_LOCATION permissions.");
            return;
        }
//        logger.logAccess("GPS", "Missing ACCESS_FINE_LOCATION permissions.");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                5000, 5, this, Looper.getMainLooper());

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this, Looper.getMainLooper());
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this, Looper.getMainLooper());
    }

    /**
     * Check wheather the app has the propper permissions, but breaks compilation and force use of
     * the `@SuppressLint("MissingPermission")` annotation, TODO fix that?
     *
     * @param context
     * @return
     */
    private static boolean hasGPSPermissions(Context context) {
        return ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    // Add this method to manage starting location updates cleanly
    @SuppressLint("MissingPermission") // Handle permission check before calling this method
    public void startLocationUpdates() {
        if (hasGPSPermissions(context)) { // Ensure context is stored or passed
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this, Looper.getMainLooper());
            logger.logAccess("GPS", "Started location updates.");
        } else {
            logger.logError(ERR_TAG, "Attempt to start location updates without sufficient permissions.");
        }
    }

    /**
     * Updates the data.
     *
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Process sensor data and store it in telemetryData
        synchronized (this) {
            try {
                telemetryData.put("accelerometer_x", event.values[0]);
                telemetryData.put("accelerometer_y", event.values[1]);
                telemetryData.put("accelerometer_z", event.values[2]);
            } catch (Exception e) {
//                e.printStackTrace();
                logger.logError(ERR_TAG, e.getMessage());
            }
        }
    }

    /**
     * Updates the data.
     *
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        synchronized (this) {
            try {
                telemetryData.put("latitude", location.getLatitude());
                telemetryData.put("longitude", location.getLongitude());
                telemetryData.put("altitude", location.getAltitude());
            } catch (Exception e) {
                logger.logError(ERR_TAG, e.getMessage());
            }
        }
    }

    /**
     * Sample data:
     *      `{"accelerometer_x":0,"accelerometer_y":9.776321411132812,"accelerometer_z":0.812345027923584}`
     * @return
     * @throws JSONException
     */
    public JSONObject getTelemetryData() throws JSONException {
        synchronized (this) {
            return new JSONObject(telemetryData.toString()); // Return a copy to avoid concurrent modifications
        }
    }

    // Implement other necessary listener methods
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO nothing never happens
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO nothing for now
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO nothing for now
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO nothing for now
    }
}
