package com.android.example.cameraxapp;

import android.app.Application;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;

import androidx.annotation.NonNull;
import android.hardware.Camera;

public class CamParams {
    float horizonalAngle;
    float verticalAngle;
    private Application application;

    public CamParams(@NonNull Application application) {
        super();
        this.application = application;
    }
    public void calculateFOV() {
        try {
            CameraManager cManager = (CameraManager) application.getSystemService(android.content.Context.CAMERA_SERVICE);
            for (final String cameraId : cManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    float width = size.getWidth();
                    float height = size.getHeight();
                    verticalAngle = (float) (2 * Math.atan(width / (maxFocus[0] * 2)));
                    horizonalAngle = (float) (2*Math.atan(height/(maxFocus[0]*2)));
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*private static Object sAccesLock = new Object();
    private static Camera sOpenedCamera = null;
    private static float[] fov = null;

    public static float[] getCameraFOV() {
        if (fov == null) {
            synchronized (sAccesLock) {
                if (sOpenedCamera != null) {
                    fov = new float[2];
                    fov[0] = sOpenedCamera.getParameters()
                            .getHorizontalViewAngle();
                    fov[1] = sOpenedCamera.getParameters()
                            .getVerticalViewAngle();
                } else {
                    try {
                        Camera camera = Camera.open();
                        fov = new float[2];
                        fov[0] = camera.getParameters()
                                .getHorizontalViewAngle();
                        fov[1] = camera.getParameters()
                                .getVerticalViewAngle();
                        camera.release();
                    } catch (RuntimeException e) {
                        fov = new float[] { 60, 45 };
                    }
                }
            }
        }
        return fov;
    }*/
}
