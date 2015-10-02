package party.treesquaredcode.android.tickerview;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A horizontally scrolling news ticker style view.
 * Created by Ryan Hiroaki Tsukamoto 1st October 2015
 */
public class TickerView extends FrameLayout {
    private static final double DEFAULT_SCROLL_SPEED_PIXELS_PER_SECOND = 64;
    private static final int DEFAULT_ITEMS_PER_CYCLE = 4;
    private int nextItemIndex;
    private Adapter adapter;
    private LinearLayout linearLayout;
    private boolean isAnimating;
    private ObjectAnimator objectAnimator;
    private int previousLayoutWidth;
    private int previousLayoutHeight;
    private boolean canScroll;
    private int lastItemViewLeftX;
    private int lastItemViewRightX;
    private int layoutWidth;
    private List<View> itemViewPool = new ArrayList<>();
    private List<View> linearLayoutChildren = new ArrayList<>();
    private List<Integer> visibleItemWidthList = new ArrayList<>();
    private double scrollSpeedPixelsPerSecond = DEFAULT_SCROLL_SPEED_PIXELS_PER_SECOND;
    private int itemsPerCycle = DEFAULT_ITEMS_PER_CYCLE;
    private boolean wasAnimating;
    private boolean canAnimate = true;

    public TickerView(Context context) {
        super(context);
    }

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public double getScrollSpeedPixelsPerSecond() {
        return scrollSpeedPixelsPerSecond;
    }

    public void setScrollSpeedPixelsPerSecond(double scrollSpeedPixelsPerSecond) {
        this.scrollSpeedPixelsPerSecond = scrollSpeedPixelsPerSecond;
    }

    public int getItemsPerCycle() {
        return itemsPerCycle;
    }

    public void setItemsPerCycle(int itemsPerCycle) {
        this.itemsPerCycle = itemsPerCycle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            return;
        }
        if (getChildCount() > 0) {
            final View child = getChildAt(0);
            int width = getMeasuredWidth();
            if (child.getMeasuredWidth() < width) {
                final LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, 0, layoutParams.height);
                int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        int childWidthMeasureSpec;
        int childHeightMeasureSpec;
        childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, 0, layoutParams.height);
        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams layoutParams = (MarginLayoutParams) child.getLayoutParams();
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, layoutParams.topMargin + layoutParams.bottomMargin + heightUsed, layoutParams.height);
        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(layoutParams.leftMargin + layoutParams.rightMargin, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!changed) {
            return;
        }
        int newLayoutWidth = right - left;
        int newLayoutHeight = bottom - top;
        if (newLayoutWidth == previousLayoutWidth && newLayoutHeight == previousLayoutHeight) {
            return;
        }
        layoutWidth = newLayoutWidth;
        if (linearLayout == null) {
            linearLayout = new LinearLayout(getContext());
        } else {
            removeLinearLayoutChildren();
        }
        removeAllViews();
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addView(linearLayout, layoutParams);
        fillLinearLayout();
        previousLayoutWidth = newLayoutWidth;
        previousLayoutHeight = newLayoutHeight;
    }

    @Override
    protected void onDetachedFromWindow() {
        wasAnimating = isAnimating;
        if (adapter != null && isAnimating) {
            pauseAnimation();
        }
        canAnimate = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        canAnimate = true;
        if (adapter != null && wasAnimating) {
            resumeAnimation();
        }
    }

    public void setAdapter(Adapter adapter) {
        if (this.adapter == adapter) {
            return;
        }
        if (this.adapter != null) {
            detachAdapter();
            if (adapter == null) {
                return;
            }
        }
        this.adapter = adapter;
        if (linearLayout == null) {
            return;
        }
        fillLinearLayout();
    }

    public void pauseAnimation() {
        if (canAnimate) {
            if (!isAnimating) {
                return;
            }
            if (objectAnimator != null) {
                objectAnimator.cancel();
                isAnimating = false;
            }
        } else {
            wasAnimating = false;
        }
    }

    public void resumeAnimation() {
        if (canAnimate) {
            if (isAnimating) {
                return;
            }
            setupAnimationToLastItem();
            isAnimating = true;
        } else {
            wasAnimating = true;
        }
    }

    public void toggleAnimation() {
        if (canAnimate) {
            if (isAnimating) {
                pauseAnimation();
            } else {
                resumeAnimation();
            }
        } else if (wasAnimating) {
            pauseAnimation();
        } else {
            resumeAnimation();
        }
    }

    private void detachAdapter() {
        if (adapter == null) {
            return;
        }
        removeLinearLayoutChildren();
        adapter = null;
    }

    private void removeLinearLayoutChildren() {
        if (objectAnimator != null) {
            objectAnimator.cancel();
        }
        for (View view : linearLayoutChildren) {
            adapter.unbindView(view);
        }
        linearLayout.removeAllViews();
        nextItemIndex = 0;
        isAnimating = false;
        canScroll = false;
        lastItemViewLeftX = 0;
        lastItemViewRightX = 0;
        itemViewPool.clear();
        linearLayoutChildren.clear();
        visibleItemWidthList.clear();
        linearLayout.setTranslationX(0f);
    }

    private void fillLinearLayout() {
        canScroll = false;
        lastItemViewRightX = 0;
        nextItemIndex = 0;
        if (adapter == null) {
            return;
        }
        while (!(enqueueItemView() || (canScroll = layoutWidth < lastItemViewRightX))){}
        if (canScroll) {
            enqueueItemView();
            setupAnimationToLastItem();
        }
    }

    private boolean enqueueItemView() {
        View view;
        if (itemViewPool.size() == 0) {
            view = adapter.spawnView(getContext());
        } else {
            view = itemViewPool.remove(0);
        }
        adapter.bindViewForItemAtIndex(view, nextItemIndex);
        linearLayout.addView(view);
        linearLayout.measure(0, 0);
        lastItemViewLeftX = lastItemViewRightX;
        lastItemViewRightX = linearLayout.getMeasuredWidth();
        visibleItemWidthList.add(lastItemViewRightX - lastItemViewLeftX);
        nextItemIndex += 1;
        nextItemIndex %= adapter.size();
        return nextItemIndex == 0;
    }

    private void setupAnimationToLastItem() {
        float currentTranslationX = linearLayout.getTranslationX();
        float targetTranslationX = layoutWidth - lastItemViewLeftX;
        float dX = currentTranslationX - targetTranslationX;
        double dT = dX / getScrollSpeedPixelsPerSecond();
        objectAnimator = ObjectAnimator.ofFloat(linearLayout, "translationX", currentTranslationX, targetTranslationX);
        objectAnimator.setInterpolator(new LinearInterpolator());
        objectAnimator.setDuration((long) (dT * 1000.0));
        objectAnimator.addListener(new Animator.AnimatorListener() {
            boolean wasCancelled = false;

            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (wasCancelled) {
                    return;
                }
                removeExpiredItemViews();
                int itemsPerCycle = getItemsPerCycle();
                for (int i = 0; i < itemsPerCycle; i++) {
                    enqueueItemView();
                }
                setupAnimationToLastItem();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                wasCancelled = true;
                isAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        objectAnimator.start();
        isAnimating = true;
    }

    private void removeExpiredItemViews() {
        float x;
        int w;
        while (visibleItemWidthList.size() > 0 && (w = visibleItemWidthList.get(0)) < (x = -linearLayout.getTranslationX())) {
            linearLayout.setTranslationX(w - x);
            View view  = linearLayout.getChildAt(0);
            adapter.unbindView(view);
            itemViewPool.add(linearLayout.getChildAt(0));
            linearLayout.removeViewAt(0);
            visibleItemWidthList.remove(0);
            lastItemViewLeftX -= w;
            lastItemViewRightX -= w;
        }
    }

    interface Adapter<T extends View> {
        T spawnView(Context context);
        int size();
        void bindViewForItemAtIndex(T view, int index);
        void unbindView(T view);
    }

    public static abstract class LayoutResAdapter implements Adapter {
        int layoutResId;

        public LayoutResAdapter(int layoutResId) {
            this.layoutResId = layoutResId;
        }

        @Override
        public View spawnView(Context context) {
            return LayoutInflater.from(context).inflate(layoutResId, null);
        }
    }
}
