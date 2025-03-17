package com.example.gesturecontrolapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class PoseOverlayView extends View {
    private List<NormalizedLandmark> landmarks;
    private final Paint landmarkPaint = new Paint();
    private final Paint connectionPaint = new Paint();

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    private void initPaint() {
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(10f);

        connectionPaint.setColor(Color.BLUE);
        connectionPaint.setStyle(Paint.Style.STROKE);
        connectionPaint.setStrokeWidth(5f);
    }

    public void setLandmarks(List<NormalizedLandmark> landmarks) {
        this.landmarks = landmarks;
        invalidate(); // Refresh the overlay
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (landmarks == null || landmarks.isEmpty()) return;

        float canvasWidth = getWidth();
        float canvasHeight = getHeight();

        int[][] connections = {
                {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
                {11, 23}, {12, 24}, {23, 24}, {23, 25}, {24, 26}, {25, 27}, {26, 28},
                {27, 29}, {28, 30}, {29, 31}, {30, 32}
        };

        // **Apply 90-degree rotation in the opposite direction**
        for (int[] pair : connections) {
            if (pair[0] < landmarks.size() && pair[1] < landmarks.size()) {
                NormalizedLandmark l1 = landmarks.get(pair[0]);
                NormalizedLandmark l2 = landmarks.get(pair[1]);

                // Swap X and Y, but reverse transformation
                float x1 = (1 - l1.y()) * canvasWidth;
                float y1 = (1 - l1.x()) * canvasHeight;

                float x2 = (1 - l2.y()) * canvasWidth;
                float y2 = (1 - l2.x()) * canvasHeight;

                canvas.drawLine(x1, y1, x2, y2, connectionPaint);
            }
        }

        // **Draw Landmarks (after 90-degree fix)**
        for (NormalizedLandmark landmark : landmarks) {
            float x = (1 - landmark.y()) * canvasWidth; // Swap X and Y, reversed
            float y = (1 - landmark.x()) * canvasHeight;
            canvas.drawCircle(x, y, 10, landmarkPaint);
        }
    }
}
