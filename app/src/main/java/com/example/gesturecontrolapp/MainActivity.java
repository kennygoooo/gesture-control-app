package com.example.gesturecontrolapp;

import android.widget.TextView;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import android.webkit.WebView;
import android.webkit.WebSettings;

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

    private int controlMode = 0; // 0 = off, 1 = full, 2 = move-only



    private boolean gestureModeEnabled = false;
    private boolean toggleInProgress = false;
    private long palmsCloseStartTime = 0;
    private static final long TOGGLE_HOLD_DURATION_MS = 1000; // 2 seconds->2000
    private TextView gestureStatus;
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

        gestureStatus = findViewById(R.id.gestureStatus);

        WebView webView = findViewById(R.id.streamView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        String html = "<html><head><style>" +
                "body { margin:0; padding:0; overflow:hidden; background:black; } " +
                "img { position:absolute; top:50%; left:50%; width:100%; height:100%;" +
                "object-fit:contain; transform:translate(-25%, -30%) rotate(90deg) scale(1.9); }" +
                "</style></head><body>" +
                "<img src='http://192.168.4.1/stream'/>" +
                "</body></html>";





        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

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
                            .setMinPoseDetectionConfidence(0.7f)
                            .setMinPosePresenceConfidence(0.7f)
                            .setMinTrackingConfidence(0.7f)
                            .setResultListener((result, timestamp) -> {
                                if (result == null || result.landmarks() == null || result.landmarks().isEmpty()) {
                                    Log.e("POSE", "No landmarks detected.");
                                    return;
                                }

                                //Log.d("POSE_OUTPUT", "Landmarks detected: " + result.landmarks().size());

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

        float imageHeight = 1000f;
        float imageWidth = 1000f;

        float rightPalmY = landmarks.get(16).y() * imageHeight;
        float leftPalmY = landmarks.get(15).y() * imageHeight;
        float rightPalmX = landmarks.get(16).x() * imageWidth;
        float leftPalmX = landmarks.get(15).x() * imageWidth;
        float rightHeelY = landmarks.get(30).y() * imageHeight;
        float rightToeY = landmarks.get(32).y() * imageHeight;

        float palmDistance = Math.abs(rightPalmX - leftPalmX);

        if (palmDistance < 40) {
            if (palmsCloseStartTime == 0) {
                palmsCloseStartTime = System.currentTimeMillis();
            }

            long heldDuration = System.currentTimeMillis() - palmsCloseStartTime;

            if (heldDuration >= TOGGLE_HOLD_DURATION_MS && !toggleInProgress) {
                // Cycle control mode: 0 → 1 → 2 → 0
                controlMode = (controlMode + 1) % 3;
                toggleInProgress = true;

                runOnUiThread(() -> {
                    String label;
                    String bgColor;

                    switch (controlMode) {
                        case 1:
                            label = "Gesture Mode 1";
                            bgColor = "#AA00C853"; // Green
                            break;
                        case 2:
                            label = "Gesture Mode 2";
                            bgColor = "#AAFFC107"; // Yellow
                            break;
                        default:
                            label = "Gesture Mode OFF";
                            bgColor = "#AAFF5252"; // Red
                    }

                    gestureStatus.setText(label);
                    gestureStatus.setBackgroundColor(Color.parseColor(bgColor));
                });

                Log.i("GESTURE_TOGGLE", "Mode changed to: " + controlMode);

                // Debounce toggle for 1s
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    toggleInProgress = false;
                    palmsCloseStartTime = 0;
                }, 1000);
            }
        } else {
            palmsCloseStartTime = 0;
        }


        String output = "";
        String movePart = "";
        String handPart = "";

        if (controlMode == 1) { // FULL
            if (rightToeY < rightHeelY - 30) {
                movePart = "move1";
            }

            if (rightPalmY < leftPalmY - 250 || rightPalmY < leftPalmY - 100) {
                handPart = "right1";
            } else if (leftPalmY < rightPalmY - 250 || leftPalmY < rightPalmY - 100) {
                handPart = "left1";
            }
        } else if (controlMode == 2) { // MOVE-ONLY
            if (rightToeY < rightHeelY - 30) {
                movePart = "move2";

                // only allow left/right if move is active
                if (rightPalmY < leftPalmY - 250 || rightPalmY < leftPalmY - 100) {
                    handPart = "right2";
                } else if (leftPalmY < rightPalmY - 250 || leftPalmY < rightPalmY - 100) {
                    handPart = "left2";
                }
            }
        }
        // else controlMode == 0 → no commands

        if (!movePart.isEmpty() && !handPart.isEmpty()) {
            output = movePart + ", " + handPart;
        } else if (!movePart.isEmpty()) {
            output = movePart;
        } else if (!handPart.isEmpty()) {
            output = handPart;
        } else {
            output = "0";
        }

        Log.i("POSE_OUTPUT", output);

        if (poseOverlay != null) {
            poseOverlay.setLandmarks(landmarks);
        }

        sendCommandToESP32(output);
    }


    private void sendCommandToESP32(String command) {
        new Thread(() -> {
            try {
                String esp32Ip = "192.168.4.1"; // IP of the ESP32 hotspot
                int port = 4210;                // Same port ESP32 listens on

                DatagramSocket socket = new DatagramSocket();
                byte[] buffer = command.getBytes();
                InetAddress address = InetAddress.getByName(esp32Ip);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(packet);
                socket.close();

                Log.d("WIFI_SEND", "Sent command to ESP32: " + command);
            } catch (Exception e) {
                Log.e("WIFI_SEND", "Failed to send command", e);
            }
        }).start();
    }






    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}

