package com.vsb.kru13.osmzhttpserver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import com.vsb.kru13.osmzhttpserver.controllers.TelemetryCollector;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private SocketServer s;
    private static final int READ_EXTERNAL_STORAGE = 1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private TelemetryCollector telemetryCollector;
    private AppLogger appLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn1 = (Button)findViewById(R.id.button1);
        Button btn2 = (Button)findViewById(R.id.button2);

        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);

        // for devel purposes lets start the web server at start of the app
        // TODO add integration tests ...
        File sdcard = Environment.getExternalStorageDirectory();
        this.appLogger =  new AppLogger(sdcard);
        this.telemetryCollector = new TelemetryCollector(this.getApplicationContext(), appLogger);
        s = new SocketServer(this.getApplicationContext(), sdcard, this.telemetryCollector);
        requestGPSPermissions();
        s.start();
    }

    /**
     * Requests GPS permissions.
     */
    private void requestGPSPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission has already been granted
            telemetryCollector.startLocationUpdates();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button1) {
            int permissionCheck = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
            );
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE
                );
            } else {
                File sdcard = Environment.getExternalStorageDirectory();
                s = new SocketServer(this.getApplicationContext(), sdcard, this.telemetryCollector);
                s.start();
            }
        }
        if (v.getId() == R.id.button2) {
            if (s != null) {
                s.close();
                try {
                    s.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case READ_EXTERNAL_STORAGE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    File sdcard = Environment.getExternalStorageDirectory();
                    s = new SocketServer(this.getApplicationContext(), sdcard, this.telemetryCollector);
                    s.start();
                }
                break;
            default:
                break;
        }
    }
}
