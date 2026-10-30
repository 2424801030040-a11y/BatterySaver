package com.example.batterysaver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Small dependency-free bar chart used by the usage summary. */
public class UsageChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] labels = new String[0];
    private float[] first = new float[0];
    private float[] second;
    private String firstName;
    private String secondName;

    public UsageChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public UsageChartView(Context context) { super(context); init(); }

    private void init() {
        paint.setTextSize(dp(11));
        paint.setStrokeWidth(dp(1));
    }

    public void setData(String[] labels, float[] values, String seriesName) {
        this.labels = labels;
        this.first = values;
        this.second = null;
        this.firstName = seriesName;
        invalidate();
    }

    public void setComparison(String[] labels, float[] current, float[] previous,
                              String currentName, String previousName) {
        this.labels = labels;
        this.first = current;
        this.second = previous;
        this.firstName = currentName;
        this.secondName = previousName;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(30), right = getWidth() - dp(8), top = dp(38), bottom = getHeight() - dp(27);
        if (labels.length == 0 || first.length == 0) {
            paint.setColor(0xff777777);
            canvas.drawText("No usage data", left, getHeight() / 2f, paint);
            return;
        }
        float max = 0;
        for (float v : first) max = Math.max(max, v);
        if (second != null) for (float v : second) max = Math.max(max, v);
        max = Math.max(1, max);
        paint.setColor(0xff888888);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(left, bottom, right, bottom, paint);
        paint.setStyle(Paint.Style.FILL);
        float slot = (right - left) / labels.length;
        float barWidth = Math.min(dp(24), slot * (second == null ? .56f : .30f));
        for (int i = 0; i < labels.length; i++) {
            float center = left + slot * (i + .5f);
            float firstX = center - (second == null ? barWidth / 2 : barWidth);
            drawBar(canvas, firstX, bottom, top, first[i], max, barWidth, 0xff5268d8);
            drawValue(canvas, firstX + barWidth / 2, bottom, top, first[i], max);
            if (second != null && i < second.length) {
                drawBar(canvas, center, bottom, top, second[i], max, barWidth, 0xff75a9df);
                drawValue(canvas, center + barWidth / 2, bottom, top, second[i], max);
            }
            paint.setColor(0xff666666);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[i], center, getHeight() - dp(8), paint);
        }
        drawLegend(canvas, left, dp(13), 0xff5268d8, firstName);
        if (second != null) drawLegend(canvas, left + dp(100), dp(13), 0xff75a9df, secondName);
    }

    private void drawBar(Canvas c, float x, float bottom, float top, float value, float max, float width, int color) {
        float height = (bottom - top) * value / max;
        paint.setColor(color);
        c.drawRoundRect(x, bottom - height, x + width, bottom, dp(3), dp(3), paint);
    }

    private void drawValue(Canvas canvas, float centerX, float bottom, float top, float hours, float max) {
        int minutes = Math.max(0, Math.round(hours * 60));
        String value = minutes >= 60
                ? (minutes / 60) + "h" + (minutes % 60 == 0 ? "" : String.format(java.util.Locale.ROOT, "%02d", minutes % 60))
                : minutes + "m";
        float barTop = bottom - (bottom - top) * hours / max;
        paint.setColor(hours > 0 ? 0xffffffff : 0xff555555);
        paint.setTextSize(dp(8));
        paint.setTextAlign(Paint.Align.CENTER);
        if (hours > 0) {
            float centerY = (barTop + bottom) / 2f;
            canvas.save();
            canvas.rotate(-90, centerX, centerY);
            canvas.drawText(value, centerX, centerY + dp(3), paint);
            canvas.restore();
        } else {
            canvas.drawText(value, centerX, bottom - dp(3), paint);
        }
        paint.setTextSize(dp(11));
    }

    private void drawLegend(Canvas c, float x, float y, int color, String text) {
        paint.setColor(color);
        c.drawRect(x, y - dp(8), x + dp(9), y + dp(1), paint);
        paint.setColor(0xff666666);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(text == null ? "" : text, x + dp(13), y, paint);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
