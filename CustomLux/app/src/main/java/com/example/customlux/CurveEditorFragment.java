package com.example.customlux;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Locale;

/**
 * Fragment for editing the adaptive brightness curve.
 * Allows users to drag points on a graph to define how brightness reacts to ambient light.
 */
public class CurveEditorFragment extends Fragment {

    private SeekBar seekMin;
    private SeekBar seekMax;
    private TextView labelMin, labelMax, labelPointCount;
    private SharedPreferences prefs;
    private ImageView drawingView;
    private int[] curvePoints;
    private int currentPointCount = 5;
    private boolean isDirty = false; // Tracks if there are unsaved changes

    // Padding constants for the graph layout
    private static final float PAD_L = 85f;
    private static final float PAD_B = 90f;
    private static final float PAD_R = 40f;
    private static final float PAD_T = 30f;

    private final float MAX_LUX = 10000f;
    private final float MIN_LUX = 1f;

    /**
     * Checks if there are any pending changes that haven't been saved to preferences.
     */
    public boolean hasUnsavedChanges() {
        return isDirty;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_curve_editor, container, false);

        // UI element initialization
        seekMin = view.findViewById(R.id.seek_min_brightness);
        seekMax = view.findViewById(R.id.seek_max_brightness);
        labelMin = view.findViewById(R.id.label_min_brightness);
        labelMax = view.findViewById(R.id.label_max_brightness);
        labelPointCount = view.findViewById(R.id.label_point_count);
        SeekBar seekPointCount = view.findViewById(R.id.seek_point_count);
        drawingView = view.findViewById(R.id.curve_drawing_view);
        Button btnSave = view.findViewById(R.id.btn_save_curve);

        Context context = getContext();
        if (context != null) {
            prefs = context.getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

            // Load existing curve settings from preferences
            int savedMin = prefs.getInt("min_brightness", 10);
            int savedMax = prefs.getInt("max_brightness", 100);
            currentPointCount = prefs.getInt("point_count", 5);
            if (currentPointCount < 1) currentPointCount = 1;

            seekMin.setProgress(savedMin);
            seekMax.setProgress(savedMax);
            seekPointCount.setProgress(currentPointCount - 1);
            labelPointCount.setText(getString(R.string.label_movable_points, currentPointCount));
            updateLabels(savedMin, savedMax);

            curvePoints = new int[15];
            String savedPoints = prefs.getString("curve_points_data", "");
            int totalPoints = currentPointCount + 2;

            // Initialize curve points from storage or defaults
            if (!savedPoints.isEmpty()) {
                String[] splitPoints = savedPoints.split(",");
                if (splitPoints.length == totalPoints) {
                    for (int i = 0; i < splitPoints.length; i++) {
                        curvePoints[i] = Integer.parseInt(splitPoints[i]);
                    }
                    curvePoints[0] = savedMin;
                    curvePoints[totalPoints - 1] = savedMax;
                } else {
                    initDefaultPoints(currentPointCount);
                }
            } else {
                initDefaultPoints(currentPointCount);
            }
        }

        // Handle minimum brightness slider changes
        seekMin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels(progress, seekMax.getProgress());
                curvePoints[0] = progress;
                drawCurve();
                if (fromUser) isDirty = true;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Handle maximum brightness slider changes
        seekMax.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels(seekMin.getProgress(), progress);
                curvePoints[currentPointCount + 1] = progress;
                drawCurve();
                if (fromUser) isDirty = true;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Handle point count slider changes
        seekPointCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentPointCount = progress + 1;
                labelPointCount.setText(getString(R.string.label_movable_points, currentPointCount));
                initDefaultPoints(currentPointCount);
                drawCurve();
                if (fromUser) isDirty = true;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Handle touch interactions with the graph to move points
        drawingView.setOnTouchListener((v, event) -> {
            // Prevent the ScrollView from intercepting dragging on the graph
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                ViewParent parent = v.getParent();
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                    parent = parent.getParent();
                }
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float graphW = drawingView.getWidth() - PAD_L - PAD_R;
                float graphH = drawingView.getHeight() - PAD_B - PAD_T;
                int total = currentPointCount + 2;

                // Identify which point is being dragged based on X position
                int pointIndex = Math.round((event.getX() - PAD_L) / (graphW / (float)(total - 1)));
                pointIndex = Math.max(0, Math.min(pointIndex, total - 1));

                // Min and Max Brightness points are controlled by specific sliders, not direct graph dragging
                if (pointIndex == 0 || pointIndex == total - 1) return true;

                // Update the selected point's brightness value
                int brightness = (int) (100 - ((event.getY() - PAD_T) / graphH * 100));
                curvePoints[pointIndex] = Math.max(0, Math.min(brightness, 100));
                drawCurve();
                isDirty = true;
            }
            return true;
        });

        btnSave.setOnClickListener(v -> saveChanges());

        // Wait for view layout to be finished before drawing the first time
        drawingView.post(this::drawCurve);

        return view;
    }

    /**
     * Persists the current curve configuration to shared preferences.
     */
    public void saveChanges() {
        if (prefs == null) return;
        int total = currentPointCount + 2;
        StringBuilder sbPoints = new StringBuilder();
        for (int i = 0; i < total; i++) {
            sbPoints.append(curvePoints[i]);
            if (i < total - 1) sbPoints.append(",");
        }
        prefs.edit()
                .putInt("min_brightness", seekMin.getProgress())
                .putInt("max_brightness", seekMax.getProgress())
                .putInt("point_count", currentPointCount)
                .putString("curve_points_data", sbPoints.toString())
                .apply();
        isDirty = false;
        Toast.makeText(getContext(), "Curve Settings Saved", Toast.LENGTH_SHORT).show();
    }

    /**
     * Resets the dirty flag if user chooses to discard changes.
     */
    public void discardChanges() {
        isDirty = false;
    }

    private void updateLabels(int min, int max) {
        labelMin.setText(getString(R.string.label_min_brightness, min));
        labelMax.setText(getString(R.string.label_max_brightness, max));
    }

    /**
     * Initializes a linear curve between min and max brightness as a starting point.
     */
    private void initDefaultPoints(int middleCount) {
        int total = middleCount + 2;
        int min = seekMin.getProgress();
        int max = seekMax.getProgress();
        curvePoints[0] = min;
        curvePoints[total - 1] = max;
        for (int i = 1; i < total - 1; i++) {
            curvePoints[i] = (int) (min + ((float) i / (total - 1)) * (max - min));
        }
    }

    /**
     * Helper to format lux values for the X-axis labels (e.g., 1500 becomes 1.5k).
     */
    private String formatLuxLabel(float lux) {
        if (lux < 1000) {
            return String.valueOf((int) lux);
        } else {
            return String.format(Locale.US, "%.1fk", lux / 1000f);
        }
    }

    /**
     * Rounds lux values to "clean" numbers for cleaner grid labeling.
     */
    private float cleanLuxValue(float val) {
        if (val < 10) return Math.round(val);
        if (val < 100) return Math.round(val / 5) * 5;
        if (val < 1000) return Math.round(val / 10) * 10;
        return Math.round(val / 100) * 100;
    }

    /**
     * Core drawing logic that renders the interactive graph onto a Bitmap.
     */
    private void drawCurve() {
        Context context = getContext();
        if (drawingView == null || drawingView.getWidth() <= 0 || context == null) return;

        int w = drawingView.getWidth();
        int h = drawingView.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float graphW = w - PAD_L - PAD_R;
        float graphH = h - PAD_B - PAD_T;

        // Semantic background and text
        int bgColor = ContextCompat.getColor(context, R.color.bg_app);
        int textColor = ContextCompat.getColor(context, R.color.text_main);
        int curveColor = ContextCompat.getColor(context, R.color.purple_curve);
        int gridColor = Color.argb(80, Color.red(textColor), Color.green(textColor), Color.blue(textColor));

        canvas.drawColor(bgColor);

        // Draw inner grid background
        Paint gridBgPaint = new Paint();
        gridBgPaint.setColor(ContextCompat.getColor(context, R.color.bg_accent));
        canvas.drawRect(PAD_L, PAD_T, PAD_L + graphW, PAD_T + graphH, gridBgPaint);

        Paint gridPaint = new Paint();
        gridPaint.setColor(gridColor);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        Paint textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        // Draw Y-axis labels (Brightness %)
        for (int i = 0; i <= 100; i += 25) {
            float y = PAD_T + graphH - (i * graphH / 100f);
            canvas.drawLine(PAD_L, y, PAD_L + graphW, y, gridPaint);
            canvas.drawText(i + "%", 10, y + 10, textPaint);
        }

        // Draw X-axis labels (Logarithmic Ambient Light)
        int total = currentPointCount + 2;
        double minLog = Math.log10(MIN_LUX);
        double maxLog = Math.log10(MAX_LUX);
        double step = (maxLog - minLog) / (total - 1);

        for (int i = 0; i < total; i++) {
            float x = PAD_L + (graphW / (float)(total - 1)) * i;
            canvas.drawLine(x, PAD_T, x, PAD_T + graphH, gridPaint);
            
            double logValue = minLog + (i * step);
            float rawLux = (float) Math.pow(10, logValue);
            float lux = cleanLuxValue(rawLux);
            
            String label = formatLuxLabel(lux);
            float labelWidth = textPaint.measureText(label);
            canvas.drawText(label, x - (labelWidth / 2), PAD_T + graphH + 35, textPaint);
        }

        // Draw the curve line
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(curveColor);
        linePaint.setStrokeWidth(8);
        linePaint.setStyle(Paint.Style.STROKE);

        Path path = new Path();
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < total; i++) {
            float x = PAD_L + (graphW / (float)(total - 1)) * i;
            float y = PAD_T + graphH - (curvePoints[i] * graphH / 100f);

            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);

            // Highlight interactive points with the primary theme color
            dotPaint.setColor((i == 0 || i == total - 1) ? Color.GRAY : curveColor);
            canvas.drawCircle(x, y, 15, dotPaint);
        }
        canvas.drawPath(path, linePaint);
        drawingView.setImageBitmap(bitmap);
    }
}
