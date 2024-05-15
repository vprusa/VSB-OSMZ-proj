package com.vsb.kru13.osmzhttpserver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * THere was a problem using the old Camera API
 *  https://stackoverflow.com/questions/57045984/why-was-camera-startpreview-deprecated
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2CaptureSession {
    private static final String TAG = "Camera2CaptureSession";
    private final Context context;
    private final CameraManager cameraManager;
    private final Handler backgroundHandler;
    private final AppLogger logger;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private boolean streaming;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Camera2CaptureSession(Context context, AppLogger logger) {
        this.logger = logger;
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        HandlerThread handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        this.backgroundHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * Start camera streaming.
     * @param out
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startCameraStream(OutputStream out) {
        try {
            streaming = true;
            String cameraId = getCameraId();
            if (cameraId == null) {
                Log.e(TAG, "No suitable camera found");
                out.flush();
                return;
            }

            String boundary = "OSMZ_boundary";
            // Not gonna use HttpResponse this time because of the possible changes in
            // next assignment
            out.write(("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n" +
                    "\r\n").getBytes());
            out.flush();

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireNextImage()) {
                    if (image != null && streaming) {
                        byte[] jpegData = convertYUVToJPEG(image);
                        if (jpegData != null) {
                            // Not gonna use HttpResponse this time because of the possible changes in
                            // next assignment
                            out.write(("--" + boundary + "\r\n").getBytes());
                            out.write(("Content-Type: image/jpeg\r\n" +
                                    "Content-Length: " + jpegData.length + "\r\n" +
                                    "\r\n").getBytes());
                            out.write(jpegData);
                            out.write("\r\n".getBytes());
                            out.flush();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing JPEG data", e);
                    stopCameraStream();
                }
            }, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    logger.logError("CAM_PERM", "Need camera permissions. Quitting.");
                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                    stopCameraStream();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    stopCameraStream();
                }
            }, backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error starting camera stream", e);
            stopCameraStream();
            try {
                out.flush();
            } catch (IOException ioException) {
                Log.e(TAG, "Error closing client socket", ioException);
            }
        }
    }

    /**
     * Get camera sessions.
     * @return
     * @throws CameraAccessException
     */
    private String getCameraId() throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return null;
    }

    /**
     * Creates capture session.
     * Configures camera, etc.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSession() {
        try {
            SurfaceTexture texture = new SurfaceTexture(10);
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);

            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                // Log capture results if needed
                            }
                        }, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error starting capture session", e);
                        stopCameraStream();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Configuration failed");
                    stopCameraStream();
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating capture session", e);
            stopCameraStream();
        }
    }

    /**
     * Converts image to bytes.
     *
     * @param image
     * @return
     */
    private byte[] convertYUVToJPEG(Image image) {
        try {
            ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream();
            Image.Plane[] planes = image.getPlanes();
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] yuvBytes = new byte[width * height * 3 / 2];
            planes[0].getBuffer().get(yuvBytes, 0, width * height);
            planes[1].getBuffer().get(yuvBytes, width * height, width * height / 4);
            planes[2].getBuffer().get(yuvBytes, width * height + width * height / 4, width * height / 4);
            YuvImage yuvImage = new YuvImage(yuvBytes, ImageFormat.NV21, width, height, null);
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, jpegOutputStream);
            return jpegOutputStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV to JPEG", e);
            return null;
        }
    }

    /**
     * Stop camera stream.
     */
    public void stopCameraStream() {
        if (streaming) {
            streaming = false;
            cameraDevice.close();
            imageReader.close();
        }
    }
}
