package com.italankin.nestedscrolling;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SlidingLayout extends NestedScrollingRelativeLayout implements GestureDetector.OnGestureListener {

    private static final String TAG = "SLLT";

    private GestureDetector mDetector;

    private View mPrimary;
    private View mSecondary;

    private int mOffset = 0;
    private int mMaxOffset;

    private ValueAnimator mTargetAnimation;

    private boolean mDragging = false;
    private long mDraggingStart;
    private float mDraggingDy;

    private OnDragListener mListener;

    public SlidingLayout(Context context) {
        this(context, null, 0, 0);
    }

    public SlidingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public SlidingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SlidingLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mDetector = new GestureDetector(context, this);
    }

    public void setOnDragListener(OnDragListener listener) {
        mListener = listener;
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (Math.abs(dyUnconsumed) > 0 && !mDragging && hasTargets()) {
            startDrag();
        }
        if (mDragging) {
            onDrag(dyUnconsumed);
            return;
        }
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (mDragging) {
            onDrag(dy);
            consumed[1] = dy;
            dy = 0;
        }
        super.onNestedPreScroll(target, dx, dy, consumed);
    }

    @Override
    public void onStopNestedScroll(View target) {
        super.onStopNestedScroll(target);
        long now = System.currentTimeMillis();
        if (Math.abs(mDraggingDy / (now - mDraggingStart)) > 2 && mDraggingDy < 0) {
            hide();
            return;
        }
        if (mDragging) {
            releaseDrag();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mDetector.onTouchEvent(event);
        if (mDragging && event.getAction() == MotionEvent.ACTION_UP) {
            releaseDrag();
            return true;
        }
        return super.onTouchEvent(event);
    }

    // Main

    private boolean hasTargets() {
        return mPrimary != null && mSecondary != null;
    }

    private void ensureTargets() {
        if (getChildCount() > 1) {
            mPrimary = getChildAt(0);
            mSecondary = getChildAt(1);
            mMaxOffset = mSecondary.getHeight() - mOffset;
            if (mTargetAnimation == null) {
                mTargetAnimation = ObjectAnimator.ofFloat(mSecondary, "translationY", 0, 0);
                mTargetAnimation.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mDragging = false;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
            } else {
                mTargetAnimation.setTarget(mSecondary);
            }
        }
    }

    public void startDrag() {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        mDraggingStart = System.currentTimeMillis();
        mDraggingDy = 0;
        mDragging = true;
    }

    public void onDrag(float dy) {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        float ty = mSecondary.getTranslationY() - dy;
        mDraggingDy += dy;
        if (ty < 0) {
            mSecondary.setTranslationY(0);
            return;
        }
        if (Math.abs(ty) < mMaxOffset) {
            mSecondary.setTranslationY(ty);
            if (mListener != null) {
                mListener.onDragProgress(Math.abs(ty) / mMaxOffset);
            }
        } else {
            mSecondary.setTranslationY(mMaxOffset);
        }
    }

    public void releaseDrag() {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        if (mDragging) {
            if (Math.abs(mSecondary.getTranslationY()) > mMaxOffset / 2) {
                hide();
            } else {
                show();
            }
        }
    }

    private void hide() {
        if (mSecondary.getTranslationY() >= mMaxOffset) {
            return;
        }
        mTargetAnimation.setFloatValues(mSecondary.getTranslationY(), mMaxOffset);
        mTargetAnimation.start();
    }

    private void show() {
        if (mSecondary.getTranslationY() <= 0) {
            return;
        }
        mTargetAnimation.setFloatValues(mSecondary.getTranslationY(), 0);
        mTargetAnimation.start();
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
                show();
            } else {
                hide();
            }
        }
        return true;
    }

    public interface OnDragListener {
        void onDragProgress(float percent);
    }

}
