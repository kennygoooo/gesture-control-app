package com.example.gesturecontrolapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.concurrent.ExecutionException;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import android.graphics.YuvImage;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import com.google.mediapipe.tasks.core.BaseOptions;


public class MainActivity extends AppCompatActivity {
    private ExecutorService cameraExecutor;
    private PoseLandmarker poseLandmarker;
    private final CountDownLatch landmarkerLatch = new CountDownLatch(1);
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String MODEL_PATH = "pose_landmarker_lite.task";
    private PoseOverlayView poseOverlay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        poseOverlay = findViewById(R.id.poseOverlay); // Initialize PoseOverlayView
        requestCameraPermissions();

        if (!isModelFileAvailable()) {
            Log.e("POSE", "Model file missing: " + MODEL_PATH);
            return;
        }

        new Thread(this::setupPoseLandmarker).start();

        PreviewView viewFinder = findViewById(R.id.viewFinder);
        cameraExecutor = Executors.newSingleThreadExecutor();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider, viewFinder);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean isModelFileAvailable() {
        try {
            InputStream modelStream = getAssets().open(MODEL_PATH);
            modelStream.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void requestCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("CAMERA", "Camera permission granted!");
            } else {
                Log.e("CAMERA", "Camera permission denied!");
            }
        }
    }

    private void bindCamera(ProcessCameraProvider cameraProvider, PreviewView viewFinder) {
        try {
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();

            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(cameraExecutor, this::processImage);

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e("Camera", "Failed to bind camera. Retrying...", e);
            viewFinder.postDelayed(() -> bindCamera(cameraProvider, viewFinder), 2000);
        }
    }

    private void setupPoseLandmarker() {
        try {
            Log.d("POSE", "Initializing PoseLandmarker...");
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_PATH)
                    .build();

            poseLandmarker = PoseLandmarker.createFromOptions(this,
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setMinPoseDetectionConfidence(0.5f)
                            .setMinPosePresenceConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setResultListener((result, timestamp) -> {
                                if (result == null || result.landmarks() == null || result.landmarks().isEmpty()) {
                                    Log.e("POSE", "No landmarks detected.");
                                    return;
                                }

                                Log.d("POSE_OUTPUT", "Landmarks detected: " + result.landmarks().size());

                                for (List<NormalizedLandmark> personLandmarks : result.landmarks()) {
                                    detectGestures(personLandmarks);
                                }
                            })
                            .build());

        } catch (Exception e) {
            Log.e("POSE", "Error initializing PoseLandmarker", e);
        }
    }




    private void processImage(ImageProxy imageProxy) {
        Bitmap bitmap = imageProxyToBitmap(imageProxy);
        if (bitmap == null) {
            imageProxy.close();
            return;
        }
        try {
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            poseLandmarker.detectAsync(mpImage, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e("POSE", "Error processing image", e);
        } finally {
            imageProxy.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        Image image = imageProxy.getImage();
        if (image == null) return null;

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Copy the Y, U, and V channel data into the nv21 array
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        // Convert NV21 to JPEG
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, outputStream);

        byte[] jpegData = outputStream.toByteArray();
        return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
    }

    private void detectGestures(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) {
            Log.e("POSE", "No landmarks detected.");
            return;
        }

        float frameHeight = 1.0f; // Normalized scale (0 to 1)

        // Key landmark positions
        float headY = landmarks.get(0).y() * frameHeight; // Nose (head reference)
        float rightWristY = landmarks.get(16).y() * frameHeight;
        float leftWristY = landmarks.get(15).y() * frameHeight;
        float rightHeelY = landmarks.get(30).y() * frameHeight;
        float rightToeY = landmarks.get(32).y() * frameHeight;

        float threshold = frameHeight * 0.15f;

        // Separate outputs
        String handOutput = "";
        String footOutput = "";

//        // Compute height difference
//        float handHeightDifference = Math.abs(leftWristY - rightWristY);
//        Log.d("POSE_DEBUG", "Left Wrist Y: " + leftWristY + ", Right Wrist Y: " + rightWristY);
//
//        if (handHeightDifference >= 0.10) {
//            // Determine which hand is higher
//            if (leftWristY < rightWristY) {
//                handOutput = "left"; // Left hand is higher → Turn Left
//            } else {
//                handOutput = "right"; // Right hand is higher → Turn Right
//            }
//        } else {
//            handOutput = "straight"; // Hands are close → Go Straight
//        }
//
//        // Log the final gesture output
//        Log.d("POSE_OUTPUT_HAND", "Hand Gesture: " + (handOutput.isEmpty() ? "None" : handOutput));


        // Check for foot movement
        float footHeightDifference = Math.abs(rightToeY - rightHeelY);

        if (footHeightDifference <= 0.02) {
            footOutput = "move";
        } else {
            footOutput = "stop";
        }

        // Log both outputs separately
        Log.d("POSE_OUTPUT_FOOT", "Foot Gesture: " + (footOutput.isEmpty() ? "None" : footOutput));

        if (poseOverlay == null) {
            Log.e("POSE", "PoseOverlayView is null. Ensure it's initialized in onCreate().");
            return;
        }
        // **Update overlay**
        poseOverlay.setLandmarks(landmarks);
    }






    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
