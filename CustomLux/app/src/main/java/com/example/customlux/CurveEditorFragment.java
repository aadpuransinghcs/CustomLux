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
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class CurveEditorFragment extends Fragment {

    private SeekBar seekMin, seekMax, seekPointCount;
    private TextView labelMin, labelMax, labelPointCount;
    private SharedPreferences prefs;
    private ImageView drawingView;
    private int[] curvePoints;
    private int currentPointCount = 5;
    private boolean isDirty = false;

    // Graph Layout Constants
    private final float PAD_L = 80f; // Increased for labels
    private final float PAD_B = 90f;
    private final float PAD_R = 40f;
    private final float PAD_T = 30f;

    private final float MAX_LUX = 10000f;
    private final float MIN_LUX = 1f;

    public boolean hasUnsavedChanges() {
        return isDirty;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_curve_editor, container, false);

        seekMin = view.findViewById(R.id.seek_min_brightness);
        seekMax = view.findViewById(R.id.seek_max_brightness);
        labelMin = view.findViewById(R.id.label_min_brightness);
        labelMax = view.findViewById(R.id.label_max_brightness);
        labelPointCount = view.findViewById(R.id.label_point_count);
        seekPointCount = view.findViewById(R.id.seek_point_count);
        drawingView = view.findViewById(R.id.curve_drawing_view);
        Button btnSave = view.findViewById(R.id.btn_save_curve);

        prefs = getContext().getSharedPreferences("CustomLuxPrefs", Context.MODE_PRIVATE);

        int savedMin = prefs.getInt("min_brightness", 10);
        int savedMax = prefs.getInt("max_brightness", 100);
        currentPointCount = prefs.getInt("point_count", 5);
        if (currentPointCount < 1) currentPointCount = 1;

        seekMin.setProgress(savedMin);
        seekMax.setProgress(savedMax);
        seekPointCount.setProgress(currentPointCount - 1);
        labelPointCount.setText("Movable Points: " + currentPointCount);
        updateLabels(savedMin, savedMax);

        curvePoints = new int[15];
        String savedPoints = prefs.getString("curve_points_data", "");
        int totalPoints = currentPointCount + 2;

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

        seekPointCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentPointCount = progress + 1;
                labelPointCount.setText("Movable Points: " + currentPointCount);
                initDefaultPoints(currentPointCount);
                drawCurve();
                if (fromUser) isDirty = true;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        drawingView.setOnTouchListener((v, event) -> {
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

                int pointIndex = Math.round((event.getX() - PAD_L) / (graphW / (float)(total - 1)));
                pointIndex = Math.max(0, Math.min(pointIndex, total - 1));

                if (pointIndex == 0 || pointIndex == total - 1) return true;

                int brightness = (int) (100 - ((event.getY() - PAD_T) / graphH * 100));
                curvePoints[pointIndex] = Math.max(0, Math.min(brightness, 100));
                drawCurve();
                isDirty = true;
            }
            return true;
        });

        btnSave.setOnClickListener(v -> saveChanges());

        drawingView.post(this::drawCurve);

        return view;
    }

    public void saveChanges() {
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

    public void discardChanges() {
        isDirty = false;
    }

    private void updateLabels(int min, int max) {
        labelMin.setText("Minimum Brightness: " + min + "%");
        labelMax.setText("Maximum Brightness: " + max + "%");
    }

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

    private String formatLuxLabel(float lux) {
        if (lux < 1000) {
            return String.valueOf((int) lux);
        } else {
            return String.format(Locale.US, "%.1fk", lux / 1000f);
        }
    }

    private float cleanLuxValue(float val) {
        if (val < 10) return Math.round(val);
        if (val < 100) return Math.round(val / 5) * 5;
        if (val < 1000) return Math.round(val / 10) * 10;
        return Math.round(val / 100) * 100;
    }

    private void drawCurve() {
        if (drawingView == null || drawingView.getWidth() <= 0) return;

        int w = drawingView.getWidth();
        int h = drawingView.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float graphW = w - PAD_L - PAD_R;
        float graphH = h - PAD_B - PAD_T;

        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(PAD_L, PAD_T, PAD_L + graphW, PAD_T + graphH, bgPaint);

        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        // Y Axis Labels (Brightness %)
        for (int i = 0; i <= 100; i += 25) {
            float y = PAD_T + graphH - (i * graphH / 100f);
            canvas.drawLine(PAD_L, y, PAD_L + graphW, y, gridPaint);
            canvas.drawText(i + "%", 10, y + 10, textPaint);
        }

        // X Axis Labels (Logarithmic Lux)
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

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#6200EE"));
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

            dotPaint.setColor((i == 0 || i == total - 1) ? Color.GRAY : Color.parseColor("#6200EE"));
            canvas.drawCircle(x, y, 15, dotPaint);
        }
        canvas.drawPath(path, linePaint);
        drawingView.setImageBitmap(bitmap);
    }
}