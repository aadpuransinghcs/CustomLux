package com.example.customlux;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CurveEditorActivity extends AppCompatActivity {

    private SeekBar seekMin, seekMax;
    private TextView labelMin, labelMax, labelPointCount;
    private SharedPreferences prefs;
    private ImageView drawingView;
    private int[] curvePoints;
    private int currentPointCount = 5;

    // Graph Layout Constants
    private final float PAD_L = 70f;
    private final float PAD_B = 90f;
    private final float PAD_R = 30f;
    private final float PAD_T = 30f;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_curve_editor);

        seekMin = findViewById(R.id.seek_min_brightness);
        seekMax = findViewById(R.id.seek_max_brightness);
        labelMin = findViewById(R.id.label_min_brightness);
        labelMax = findViewById(R.id.label_max_brightness);
        labelPointCount = findViewById(R.id.label_point_count);
        Button btnSave = findViewById(R.id.btn_save_curve);
        drawingView = findViewById(R.id.curve_drawing_view);
        SeekBar seekPointCount = findViewById(R.id.seek_point_count);

        prefs = getSharedPreferences("CustomLuxPrefs", MODE_PRIVATE);

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

        // Listeners
        seekMin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels(progress, seekMax.getProgress());
                curvePoints[0] = progress;
                drawCurve();
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
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> {
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
                    .commit();
            Toast.makeText(this, "Curve Settings Saved", Toast.LENGTH_SHORT).show();
            finish();
        });

        drawingView.setOnTouchListener((v, event) -> {
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
            }
            return true;
        });

        seekPointCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentPointCount = progress + 1;
                labelPointCount.setText("Movable Points: " + currentPointCount);
                initDefaultPoints(currentPointCount);
                drawCurve();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        drawingView.post(this::drawCurve);
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

    // Draws the brightness curve
    private void drawCurve() {
        if (drawingView.getWidth() <= 0) return;

        int w = drawingView.getWidth();
        int h = drawingView.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float graphW = w - PAD_L - PAD_R;
        float graphH = h - PAD_B - PAD_T;

        // Background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#FFFFFF"));
        canvas.drawRect(PAD_L, PAD_T, PAD_L + graphW, PAD_T + graphH, bgPaint);

        // Grid Paints
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(26f);
        textPaint.setAntiAlias(true);

        // Horizontal Grid & Y Labels
        for (int i = 0; i <= 100; i += 25) {
            float y = PAD_T + graphH - (i * graphH / 100f);
            canvas.drawLine(PAD_L, y, PAD_L + graphW, y, gridPaint);
            canvas.drawText(String.valueOf(i), 15, y + 10, textPaint);
            canvas.drawLine(PAD_L - 10, y, PAD_L, y, textPaint); // Tick
        }

        // Vertical Grid & X Labels
        int total = currentPointCount + 2;
        for (int i = 0; i < total; i++) {
            float x = PAD_L + (graphW / (float)(total - 1)) * i;
            canvas.drawLine(x, PAD_T, x, PAD_T + graphH, gridPaint);

            int lux = (int) (1000.0 * i / (total - 1));
            String label = String.valueOf(lux);
            canvas.drawText(label, x - (textPaint.measureText(label) / 2), PAD_T + graphH + 35, textPaint);
            canvas.drawLine(x, PAD_T + graphH, x, PAD_T + graphH + 12, textPaint); // Tick
        }

        // Axis Title
        textPaint.setTextSize(30f);
        String title = "Ambient Light (lux)";
        canvas.drawText(title, PAD_L + (graphW - textPaint.measureText(title)) / 2, h - 15, textPaint);

        // Curve Line
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#6200EE"));
        linePaint.setStrokeWidth(10);
        linePaint.setStyle(Paint.Style.STROKE);

        Path path = new Path();
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < total; i++) {
            float x = PAD_L + (graphW / (float)(total - 1)) * i;
            float y = PAD_T + graphH - (curvePoints[i] * graphH / 100f);

            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);

            dotPaint.setColor( (i == 0 || i == total - 1) ? Color.GRAY : Color.parseColor("#6200EE") );
            canvas.drawCircle(x, y, 18, dotPaint);
        }
        canvas.drawPath(path, linePaint);
        drawingView.setImageBitmap(bitmap);
    }
}