package com.rozmus.terry.subtitler;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.FrameLayout;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private boolean cameraActive = false;
    private CameraPreview mPreview;
    private FrameLayout preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the camera view FrameLayout
        preview = (FrameLayout) findViewById(R.id.camera_view);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCamera();
            }
        });
    }

    private void toggleCamera() {
        // Open the camera
        if (deviceHasCamera(this)) {
            if (cameraActive) {
                cameraActive = false;
                stopCamera();
            } else {
                cameraActive = true;
                startCamera();
            }
        }
    }

    private void startCamera() {
        // Start Camera
        mCamera = getCameraInstance();

        // Create camera preview and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview.addView(mPreview);
    }

    private void stopCamera() {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        preview.removeAllViews();
    }

    private boolean deviceHasCamera(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

}
