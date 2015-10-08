package party.treesquaredcode.android.tickerview;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A horizontally scrolling news ticker style view.
 * Created by Ryan Hiroaki Tsukamoto 1st October 2015
 */
public class TickerView extends View {
    private static final String TAG = TickerView.class.getCanonicalName();

    private List<Item> itemList = new ArrayList<>();
    private List<Item> itemRecycler = new ArrayList<>();
    private float offsetX;
    private int nextItemIndex;
    private long lastFrameTime;
    private boolean hasLastFrameTime;
    private boolean doesMarquee;
    private boolean hasWidth;
    private int width;
    private int height;
    private float rightX;
    private float touchX;
    private float touchY;
    private boolean isTouched;

    private TextPaint textPaint = new TextPaint();
    private float speed;
    private float spacing;
    private int defaultTextColor = Color.argb(255, 0, 0, 0);
    private int pressedTextColor = Color.argb(255, 64, 64, 64);
    private Adapter adapter;
    private boolean isPaused = true;

    public TickerView(Context context) {
        super(context);
    }

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSpeed(float speed) {
        if (speed > 0) {
            Log.d("TickerView", "positive speed not supported.");
            return;
        }
        this.speed = speed;
    }

    public void setSpeedDp(float speedDp) {
        setSpeed(floatPixelsForDp(speedDp));
    }

    public void setSpeedDimensionResource(int speedDimenResId) {
        setSpeed(getContext().getResources().getDimension(speedDimenResId));
    }

    public void setTextPaint(TextPaint textPaint) {
        if (adapter != null) {
            Log.d(TAG, "Setting textPaint while adapter is attached is not supported.");
        }
        this.textPaint = textPaint;
    }

    public void setTextSize(float textSize) {
        if (adapter != null) {
            Log.d(TAG, "Setting text size while adapter is attached is not supported.");
        }
        textPaint.setTextSize(textSize);
    }

    public void setTextSizeDimensionResource(int textSizeDimenResId) {
        setTextSize(getContext().getResources().getDimension(textSizeDimenResId));
    }

    public void setTextSizeDp(float textSizeDp) {
        setTextSize(floatPixelsForDp(textSizeDp));
    }

    public void setSpacing(float spacing) {
        if (adapter != null) {
            Log.d(TAG, "Setting spacing while adapter is attached is not supported.");
            return;
        }
        this.spacing = spacing;
    }

    public void setSpacingDp(float spacingDp) {
        setSpacing(floatPixelsForDp(spacingDp));
    }

    public void setSpacingDimensionResource(int spacingDimenResId) {
        setSpacing(getContext().getResources().getDimension(spacingDimenResId));
    }

    public void setDefaultTextColor(int defaultTextColor) {
        this.defaultTextColor = defaultTextColor;
    }

    public void setDefaultTextColorResource(int defaultTextColorResId) {
        setDefaultTextColor(getContext().getResources().getColor(defaultTextColorResId));
    }

    public void setPressedTextColor(int pressedTextColor) {
        this.pressedTextColor = pressedTextColor;
    }

    public void setPressedTextColorResource(int pressedTextColorResId) {
        setPressedTextColor(getContext().getResources().getColor(pressedTextColorResId));
    }

    public void setAdapter(Adapter adapter) {
        if (adapter == null) {
            doesMarquee = false;
        } else if (adapter != this.adapter) {
            if (hasWidth) {
                reset();
            }
        }
        this.adapter = adapter;
    }

    public void pause() {
        if (isPaused) {
            return;
        }
        isPaused = !isPaused;
    }

    public void resume() {
        if (!isPaused) {
            return;
        }
        hasLastFrameTime = false;
        isPaused = !isPaused;
    }

    public void toggle() {
        if (isPaused) {
            resume();
        } else {
            pause();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        height = h;
        if (w != oldw) {
            width = w;
            hasWidth = true;
            if (adapter != null) {
                reset();
            }
        }
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isReady()) {
            if (doesMarquee && !isPaused) {
                long t = new Date().getTime();
                if (!hasLastFrameTime) {
                    lastFrameTime = t;
                    hasLastFrameTime = true;
                }
                long dt = t - lastFrameTime;
                float dx = speed * dt / 1000.0f;
                offsetX += dx;
                rightX += dx;
                lastFrameTime = t;
                stripLeft();
                fillRight();
            }

            float x = offsetX + 0.5f * spacing;
            float y = (getMeasuredHeight() - (textPaint.ascent() + textPaint.descent())) / 2;
            for (Item item : itemList) {
                textPaint.setColor((isTouched && x < touchX && touchX <= x + item.width && 0 <= touchX && touchX <= width && 0 <= touchY && touchY <= height) ? pressedTextColor : defaultTextColor);
                canvas.drawText(item.text, x, y, textPaint);
                x += item.width;
            }
        }
        super.onDraw(canvas);
        if (doesMarquee) {
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isReady()) {
            return super.onTouchEvent(event);
        }
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isTouched) {
                    return false;
                } else {
                    isTouched = true;
                }
                touchX = x;
                touchY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                touchX = x;
                touchY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (0 <= x && x <= width && 0 <= y && y <= height) {
                    if (itemList.size() > 0) {
                        int index;
                        if ((index = getIndexFromTouchX(x)) != -1) {
                            playSoundEffect(SoundEffectConstants.CLICK);
                            adapter.onItemSelectedWithIndex(index);
                        }
                    }
                }
                isTouched = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                isTouched = false;
        }
        return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }

    private int getIndexFromTouchX(float touchX) {
        float x0;
        float x1 = offsetX;
        for (Item item : itemList) {
            x0 = x1;
            x1 += item.width;
            if (x0 < touchX && touchX <= x1) {
                return item.index;
            }
        }
        return -1;
    }

    public void reset() {
        if (!isReady()) {
            Log.d(TAG, "Cannot reset without width and adapter.");
            return;
        }
        itemList.clear();
        offsetX = 0f;
        nextItemIndex = 0;
        rightX = 0;
        hasLastFrameTime = false;
        doesMarquee = computeDoesMarquee();
        fillRight();
    }

    private boolean computeDoesMarquee() {
        if (!isReady()) {
            Log.d(TAG, "Cannot compute does marquee without width and adapter.");
            return false;
        }
        float filledWidth = 0f;
        Rect rect = new Rect();
        String s;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            s = adapter.getStringAtIndex(i);
            textPaint.getTextBounds(s, 0, s.length(), rect);
            filledWidth += spacing + rect.width();
            if (filledWidth > width) {
                return true;
            }
        }
        return false;
    }

    private void stripLeft() {
        float itemRightX = offsetX;
        float w;
        boolean b = itemRightX < 0f;
        while (b && itemList.size() > 0) {
            itemRightX += w = itemList.get(0).width;
            if (b = itemRightX < 0f) {
                offsetX += w;
                itemRecycler.add(itemList.remove(0));
            }
        }
    }

    private void fillRight() {
        if (!isReady()) {
            Log.d(TAG, "Cannot fill right without width and adapter.");
        }
        Rect rect = new Rect();
        String s;
        float w;
        Item item;
        while (rightX + offsetX < width) {
            s = adapter.getStringAtIndex(nextItemIndex);
            textPaint.getTextBounds(s, 0, s.length(), rect);
            rightX += w = rect.width() + spacing;
            if (!itemRecycler.isEmpty()) {
                item = itemRecycler.remove(0);
                item.text = s;
                item.width = w;
                item.index = nextItemIndex;
            } else {
                item = new Item(s, w, nextItemIndex);
            }
            itemList.add(item);
            nextItemIndex = ++nextItemIndex % adapter.getItemCount();
            if (nextItemIndex == 0 && !doesMarquee) {
                return;
            }
        }
    }

    private boolean isReady() {
        return hasWidth && adapter != null;
    }

    private class Item {
        String text;
        float width;
        int index;

        public Item(String text, float width, int index) {
            this.text = text;
            this.width = width;
            this.index = index;
        }
    }

    private float floatPixelsForDp(float dp) {
        return getContext().getResources().getDisplayMetrics().density * dp + 0.5f;
    }

    public interface Adapter {
        int getItemCount();
        String getStringAtIndex(int i);
        void onItemSelectedWithIndex(int i);
    }
}
