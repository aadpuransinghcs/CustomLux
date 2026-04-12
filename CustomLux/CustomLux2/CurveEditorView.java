package ca.brocku.customlux;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CurveEditorView extends View {

    private Paint linePaint;
    private Paint pointPaint;
    private List<PointF> controlPoints;
    private int selectedPointIndex = -1;


    public CurveEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(8f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        pointPaint = new Paint();
        pointPaint.setColor(Color.WHITE);
        pointPaint.setAntiAlias(true);

        // Initialize default linear points (normalized 0.0 to 1.0)
        controlPoints = new ArrayList<>();
        controlPoints.add(new PointF(0.0f, 1.0f)); // Min Lux, Min Brightness
        controlPoints.add(new PointF(0.5f, 0.5f)); // Mid point
        controlPoints.add(new PointF(1.0f, 0.0f)); // Max Lux, Max Brightness
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // Draw the connecting path
        Path path = new Path();
        for (int i = 0; i < controlPoints.size(); i++) {
            float px = controlPoints.get(i).x * w;
            float py = controlPoints.get(i).y * h;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        canvas.drawPath(path, linePaint);

        // Draw interactive points
        for (PointF pt : controlPoints) {
            canvas.drawCircle(pt.x * w, pt.y * h, 20f, pointPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() / getWidth();
        float y = event.getY() / getHeight();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check if user touched a point
                selectedPointIndex = findNearestPoint(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (selectedPointIndex != -1) {
                    // Update point position
                    controlPoints.get(selectedPointIndex).set(
                            Math.max(0, Math.min(1, x)),
                            Math.max(0, Math.min(1, y))
                    );
                    invalidate(); // Trigger redraw
                }
                break;
            case MotionEvent.ACTION_UP:
                selectedPointIndex = -1;
                break;
        }
        return true;
    }

    private int findNearestPoint(float x, float y) {
        for (int i = 0; i < controlPoints.size(); i++) {
            float dx = controlPoints.get(i).x - x;
            float dy = controlPoints.get(i).y - y;
            if (Math.sqrt(dx * dx + dy * dy) < 0.1) return i;
        }
        return -1;
    }

    public List<PointF> getPoints() {
        return controlPoints;
    }
}