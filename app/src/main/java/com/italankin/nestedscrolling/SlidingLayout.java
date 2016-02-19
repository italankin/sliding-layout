package com.italankin.nestedscrolling;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SlidingLayout extends NestedScrollingRelativeLayout implements GestureDetector.OnGestureListener {

    private static final String TAG = "SLLT";

    private GestureDetector mDetector;

    private View mTargetBottom;
    private View mTarget;

    private ValueAnimator mTargetAnimation;

    private boolean mDragging = false;
    private long mDraggingStart;
    private float mDraggingDy;

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

    private boolean hasTargets() {
        return mTargetBottom != null && mTarget != null;
    }

    private void ensureTargets() {
        if (getChildCount() > 1) {
            mTargetBottom = getChildAt(0);
            mTarget = getChildAt(1);
            if (mTargetAnimation == null) {
                mTargetAnimation = ObjectAnimator.ofFloat(mTarget, "translationY", 0, 0);
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
                mTargetAnimation.setTarget(mTarget);
            }
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (Math.abs(dyUnconsumed) > 0 && !mDragging && hasTargets()) {
            startDragging();
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
        long now = System.currentTimeMillis();
        if (Math.abs(mDraggingDy / (now - mDraggingStart)) > 2 && mDraggingDy < 0) {
            hide();
            return;
        }
        if (mDragging) {
            onReleaseDrag();
        }
        super.onStopNestedScroll(target);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mDetector.onTouchEvent(event);
        if (mDragging && event.getAction() == MotionEvent.ACTION_UP) {
            onReleaseDrag();
            return true;
        }
        return super.onTouchEvent(event);
    }

    // Dragging view

    private void startDragging() {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        mDraggingStart = System.currentTimeMillis();
        mDraggingDy = 0;
        mDragging = true;
    }

    private void onDrag(float dy) {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        float ty = mTarget.getTranslationY() - dy;
        mDraggingDy += dy;
        if (ty < 0) {
            mTarget.setTranslationY(0);
            return;
        }
        if (Math.abs(ty) < mTarget.getHeight()) {
            mTarget.setTranslationY(ty);
        } else {
            mTarget.setTranslationY(mTarget.getHeight());
        }
    }

    private void onReleaseDrag() {
        if (mTargetAnimation.isRunning()) {
            return;
        }
        if (mDragging) {
            if (Math.abs(mTarget.getTranslationY()) > mTarget.getHeight() / 2) {
                hide();
            } else {
                show();
            }
        }
    }

    private void hide() {
        if (mTarget.getTranslationY() >= mTarget.getHeight()) {
            return;
        }
        mTargetAnimation.setFloatValues(mTarget.getTranslationY(), mTarget.getHeight());
        mTargetAnimation.start();
    }

    private void show() {
        if (mTarget.getTranslationY() <= 0) {
            return;
        }
        mTargetAnimation.setFloatValues(mTarget.getTranslationY(), 0);
        mTargetAnimation.start();
    }

    // OnGestureListener

    @Override
    public boolean onDown(MotionEvent e) {
        if (!hasTargets()) {
            ensureTargets();
        }
        if (!mDragging && hasTargets()) {
            startDragging();
        }
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
        if (mDragging && hasTargets()) {
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

}
