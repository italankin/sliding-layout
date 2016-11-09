package com.italankin.slidinglayout;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import java.util.ArrayList;

public class SlidingLayout extends NestedScrollingViewGroup implements GestureDetector.OnGestureListener {

    public static final int STATE_GONE = 0;
    public static final int STATE_VISIBLE = 1;
    public static final float MIN_FLING_VELOCITY = 2f;

    private final GestureDetector mDetector;

    private View mContent;
    private View mOverlay;

    private int mOffset = 0;
    private int mMaxOffset;
    private float mParallaxFactor = 0;
    private float mMinScrollPercent = 0.25f;
    private boolean mClipContent = true;
    private final Rect mContentClip = new Rect();
    /**
     * Measured in px/ms
     */
    private float mFlingVelocity;

    private boolean mInterceptTouchEvents = false;

    private int mState = STATE_GONE;

    private boolean mDragging = false;
    private float mDraggingDy;
    private long mDraggingStart;
    private boolean mNestedScrollInProgress = false;
    private float mDragPercent = -1;

    private ValueAnimator mAnimContent;
    private ValueAnimator mAnimOverlay;
    private Interpolator mAnimInterpolator = new DecelerateInterpolator();
    private int mAnimDuration = 300;

    private ArrayList<OnDragProgressListener> mDragProgressListeners = new ArrayList<>(0);

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    public SlidingLayout(Context context) {
        this(context, null);
    }

    public SlidingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlidingLayout);

        float parallaxFactor = -1;
        float minScroll = -1;

        try {
            mState = a.getInt(R.styleable.SlidingLayout_sl_initialOverlayState, STATE_GONE);
            minScroll = a.getFloat(R.styleable.SlidingLayout_sl_minScroll, mMinScrollPercent);
            parallaxFactor = a.getFloat(R.styleable.SlidingLayout_sl_parallaxFactor, 0);
            mOffset = a.getDimensionPixelSize(R.styleable.SlidingLayout_sl_offset, 0);
            mClipContent = a.getBoolean(R.styleable.SlidingLayout_sl_clipContent, mClipContent);
        } finally {
            a.recycle();
        }

        if (parallaxFactor != -1) {
            setParallaxFactor(parallaxFactor);
        }

        if (minScroll != -1) {
            setMinScroll(minScroll);
        }

        mFlingVelocity = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_FLING_VELOCITY,
                context.getResources().getDisplayMetrics());
        mDetector = new GestureDetector(context, this);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public setters
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Initial state of the view. Must be one of {@link #STATE_GONE} or {@link #STATE_VISIBLE}.
     * Equal to XML attribute {@code ol_initialOverlayState}.
     *
     * @param state state value
     */
    public void setInitialOverlayState(int state) {
        if (state != STATE_GONE && state != STATE_VISIBLE) {
            throw new IllegalArgumentException(
                    "state must be one of OverlayLayout.STATE_GONE or OverlayLayout.STATE_VISIBLE");
        }
        mState = state;
    }

    /**
     * Sets the parallax factor.
     *
     * @param factor value in range [0; 1]
     */
    public void setParallaxFactor(float factor) {
        if (factor < 0 || factor > 1) {
            throw new IllegalArgumentException("factor must be in range [0;1], found: " + factor);
        }
        mParallaxFactor = factor;
        updateViewsState();
    }

    /**
     * Set additional vertical offset. Negative value means that {@code offset} will be substracted
     * from view's height.
     *
     * @param offset offset value
     */
    public void setOffset(int offset) {
        mOffset = offset;
        updateViewsState();
    }

    /**
     * Set sticky margin value (percentage), which will determine overlay's behavior when releasing
     * a drag. The value
     * {@code (view height - } {@link #setOffset(int)}{@code ) * } {@code margin}
     * is the minimum distance to drag before changing overlay state.<br>
     * Value must be in range [0.1; 0.9].
     *
     * @param margin value
     */
    public void setMinScroll(float margin) {
        if (margin < 0.1 || margin > 0.9) {
            throw new IllegalArgumentException(
                    "margin must be a value in range [0.1, 0.9], found: " + margin);
        }
        mMinScrollPercent = margin;
    }

    /**
     * Set animation interpolator when returning views to their appropriate state.
     *
     * @param interpolator {@link Interpolator} object
     */
    public void setAnimationInterpolator(Interpolator interpolator) {
        mAnimInterpolator = interpolator;
        if (mAnimContent != null) {
            mAnimContent.setInterpolator(interpolator);
            mAnimOverlay.setInterpolator(interpolator);
        }
    }

    public void setInterceptTouchEvents(boolean intercept) {
        mInterceptTouchEvents = intercept;
    }

    /**
     * Set animation duration when returning view's to their appropriate state.
     *
     * @param duration value
     */
    public void setReleaseAnimationDuration(int duration) {
        mAnimDuration = duration;
        if (mAnimContent != null) {
            mAnimContent.setDuration(duration);
            mAnimOverlay.setDuration(duration);
        }
    }

    /**
     * Set content clip. If {@code true}, when content view will not be drawn under the overlay.
     * If you have transparent overlay, disable this option.
     * This setting is {@code true} by default.
     *
     * @param clip should content view be clipped or not
     */
    public void setClipContent(boolean clip) {
        if (mClipContent != clip) {
            mClipContent = clip;
            if (!hasTargets()) {
                return;
            }
            if (mClipContent) {
                dispatchDragCurrentProgress();
            } else {
                ViewCompat.setClipBounds(mContent, null);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Hide overlay view.
     */
    public void hideOverlay() {
        hideOverlayInternal();
    }

    /**
     * Show overlay view.
     */
    public void showOverlay() {
        showOverlayInternal();
    }

    /**
     * Add listener to subscribe to drag events.
     *
     * @param listener object
     */
    public void addOnDragProgressListener(OnDragProgressListener listener) {
        if (listener != null) {
            mDragProgressListeners.add(listener);
        }
    }

    /**
     * Remove previously added listener.
     *
     * @param listener object
     */
    public void removeOnDragProgressListener(OnDragProgressListener listener) {
        mDragProgressListeners.remove(listener);
    }

    /**
     * @return state of overlay view
     */
    public boolean isOverlayShowing() {
        return mState == STATE_VISIBLE;
    }

    /**
     * @return overlay child view
     */
    public View getOverlayView() {
        return mOverlay;
    }

    /**
     * @return content child view
     */
    public View getContentView() {
        return mContent;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Nested scrolling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        mNestedScrollInProgress = true;
        super.onNestedScrollAccepted(child, target, nestedScrollAxes);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (mDragging) {
            onDrag(dy);
            consumed[1] = dy;
            dy = 0;
        }
        super.onNestedPreScroll(target, dx, dy, consumed);
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (!hasTargets()) {
            ensureTargets();
        }
        // if we have unconsumed values and scroll is happening downwards
        // start dragging
        if (dyUnconsumed < 0 && !mDragging && hasTargets()) {
            startDrag();
        }
        if (mDragging) {
            onDrag(dyUnconsumed);
            return;
        }
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
    }

    @Override
    public void onStopNestedScroll(View target) {
        super.onStopNestedScroll(target);
        mNestedScrollInProgress = false;
        long now = System.currentTimeMillis();
        if (Math.abs(mDraggingDy / (now - mDraggingStart)) > mFlingVelocity && mDraggingDy < 0) {
            // fling happened
            hideOverlay();
            return;
        }
        if (mDragging) {
            releaseDrag();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mNestedScrollInProgress || !mInterceptTouchEvents && event.getY() < mOverlay.getY() &&
                !mDragging) {
            return false;
        }
        mDetector.onTouchEvent(event);
        if (mDragging && event.getAction() == MotionEvent.ACTION_UP) {
            releaseDrag();
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mNestedScrollInProgress || !mInterceptTouchEvents && event.getY() < mOverlay.getY() &&
                !mDragging) {
            return false;
        }
        mDetector.onTouchEvent(event);
        if (mDragging && event.getAction() == MotionEvent.ACTION_UP) {
            releaseDrag();
            return true;
        }
        return super.onTouchEvent(event);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internal
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        ensureTargets();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState s = new SavedState(superState);
        s.state = mState;
        return s;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState s = (SavedState) state;
            super.onRestoreInstanceState(s.getSuperState());
            mState = s.state;
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    /**
     * Find targets and setup initial values.
     */
    private void ensureTargets() {
        if (getChildCount() > 2) {
            throw new IllegalArgumentException("OverlayLayout can host only two child views");
        } else if (getChildCount() > 1) {
            // basically assuming the first added view is content and the second is overlay
            mContent = getChildAt(0);
            mOverlay = getChildAt(1);
            if (mAnimOverlay == null) {
                mAnimOverlay = ObjectAnimator.ofFloat(mOverlay, "translationY", 0, 0);
                mAnimOverlay.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        dispatchDragCurrentProgress();
                    }
                });
                mAnimOverlay.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mDragging = false;
                        dispatchDragCurrentProgress();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                mAnimOverlay.setDuration(mAnimDuration);
                mAnimOverlay.setInterpolator(mAnimInterpolator);
            } else {
                mAnimOverlay.setTarget(mOverlay);
            }
            if (mAnimContent == null) {
                mAnimContent = ObjectAnimator.ofFloat(mContent, "translationY", 0, 0);
                mAnimContent.setDuration(mAnimDuration);
                mAnimContent.setInterpolator(mAnimInterpolator);
            } else {
                mAnimContent.setTarget(mContent);
            }
            updateViewsState();
        } else {
            mAnimOverlay = null;
            mAnimContent = null;
        }
    }

    private boolean hasTargets() {
        return mContent != null && mOverlay != null;
    }

    private void startDrag() {
        if (mAnimOverlay.isRunning()) {
            return;
        }
        mDraggingStart = System.currentTimeMillis();
        mDraggingDy = 0;
        mDragging = true;
    }

    private void onDrag(float dy) {
        if (mAnimOverlay.isRunning()) {
            return;
        }
        float ty = mOverlay.getTranslationY() - dy;
        mDraggingDy += dy;
        if (ty < 0) {
            // overlay is fully visible and attempts to move further up
            mOverlay.setTranslationY(0);
            if (mParallaxFactor > 0) {
                mContent.setTranslationY(-mMaxOffset * mParallaxFactor);
            }
            dispatchDragProgress(0);
            return;
        }
        if (Math.abs(ty) < mMaxOffset) {
            // drag is happening
            mOverlay.setTranslationY(ty);
            // if we are using ability to offset content view, set translation of content
            if (mParallaxFactor > 0) {
                mContent.setTranslationY(mContent.getTranslationY() - dy * mParallaxFactor);
            }
            dispatchDragProgress(Math.abs(ty) / mMaxOffset);
        } else {
            // overlay is fully invisible and attempts to move further down
            mOverlay.setTranslationY(mMaxOffset);
            if (mParallaxFactor > 0) {
                mContent.setTranslationY(0);
            }
            dispatchDragProgress(1);
        }
    }

    private void releaseDrag() {
        if (mAnimOverlay.isRunning()) {
            return;
        }
        if (mDragging) {
            float ty = Math.abs(mOverlay.getTranslationY());
            if (mState == STATE_VISIBLE) {
                if (ty > mMaxOffset * mMinScrollPercent) {
                    hideOverlayInternal();
                } else {
                    showOverlayInternal();
                }
            } else {
                if (ty > mMaxOffset - mMaxOffset * mMinScrollPercent) {
                    hideOverlayInternal();
                } else {
                    showOverlayInternal();
                }
            }
        }
    }

    private void hideOverlayInternal() {
        if (mAnimOverlay.isRunning()) {
            return;
        }
        mState = STATE_GONE;
        if (mParallaxFactor > 0) {
            mAnimContent.setFloatValues(mContent.getTranslationY(), 0);
            mAnimContent.start();
        }
        mAnimOverlay.setFloatValues(mOverlay.getTranslationY(), mMaxOffset);
        mAnimOverlay.start();
    }

    private void showOverlayInternal() {
        if (mAnimOverlay.isRunning()) {
            return;
        }
        mState = STATE_VISIBLE;
        if (mParallaxFactor > 0) {
            mAnimContent.setFloatValues(mContent.getTranslationY(),
                    -mMaxOffset * mParallaxFactor);
            mAnimContent.start();
        }
        mAnimOverlay.setFloatValues(mOverlay.getTranslationY(), 0);
        mAnimOverlay.start();
    }

    private void updateViewsState() {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (!hasTargets()) {
            return;
        }
        mMaxOffset = mOverlay.getHeight() - mOffset;
        if (mState == STATE_GONE) {
            mContent.setTranslationY(0);
            mOverlay.setTranslationY(mMaxOffset);
            dispatchDragProgress(1);
        } else {
            mContent.setTranslationY(-mMaxOffset * mParallaxFactor);
            mOverlay.setTranslationY(0);
            dispatchDragProgress(0);
        }
    }

    private void dispatchDragCurrentProgress() {
        dispatchDragProgress(mOverlay.getTranslationY() / mMaxOffset);
    }

    private void dispatchDragProgress(float percent) {
        if (mDragPercent == percent) {
            return;
        }
        mDragPercent = percent;
        // if parallax factor is 1 we dont need to clip content as it will be not overlapped by
        // overlay (content translates the same value as overlay)
        if (mParallaxFactor != 1 && mClipContent) {
            int overlayTranslationY = (int) mOverlay.getTranslationY();
            int contentTranslationY = (int) mContent.getTranslationY();
            int bottom = overlayTranslationY - contentTranslationY;
            int visibility = mContent.getVisibility();
            if (bottom > 0) {
                if (visibility != VISIBLE) {
                    mContent.setVisibility(VISIBLE);
                }
                mContentClip.set(0, 0, mContent.getWidth(), bottom);
                ViewCompat.setClipBounds(mContent, mContentClip);
            } else {
                if (visibility != INVISIBLE) {
                    mContent.setVisibility(INVISIBLE);
                }
            }
        }
        // notify listeners
        for (OnDragProgressListener listener : mDragProgressListeners) {
            listener.onDragProgress(percent);
        }
    }

    // OnGestureListener

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (!mDragging && hasTargets()) {
            startDrag();
        }
        if (mDragging) {
            onDrag(distanceY);
            return true;
        }
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityX) < Math.abs(velocityY)) {
            if (velocityY < 0) {
                showOverlay();
            } else {
                hideOverlay();
            }
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interfaces, listeners, etc.
    ///////////////////////////////////////////////////////////////////////////

    private static class SavedState extends BaseSavedState {
        int state;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            state = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(state);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public interface OnDragProgressListener {
        void onDragProgress(float percent);
    }

}
