package com.surcumference.fingerprint.util;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.surcumference.fingerprint.util.log.L;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ActivityViewObserver {

    private WeakReference<Activity> mActivityRef;
    private boolean mRunning = false;
    private long mGeneration;
    private Task.Handle mPending;
    private String mViewIdentifyType;
    private String[] mViewIdentifyTexts;

    private IActivityViewFinder mIActivityViewFinder;

    private boolean mWatchActivityViewOnly = false;

    public ActivityViewObserver(Activity weakRefActivity) {
        this.mActivityRef = new WeakReference<>(weakRefActivity);
    }

    public void setViewIdentifyType(String viewIdentifyType) {
        this.mViewIdentifyType = viewIdentifyType;
    }

    public void setViewIdentifyText(String ...viewIdentifyTexts) {
        this.mViewIdentifyTexts = viewIdentifyTexts;
    }

    public void setActivityViewFinder(IActivityViewFinder viewFinder) {
        this.mIActivityViewFinder = viewFinder;
    }

    public void setWatchActivityViewOnly(boolean on) {
        this.mWatchActivityViewOnly = on;
    }

    public void start(long loopMSec, IActivityViewListener listener) {
        Task.runOnMain(() -> startOnMain(loopMSec, listener));
    }

    private void startOnMain(long loopMSec, IActivityViewListener listener) {
        if (TextUtils.isEmpty(this.mViewIdentifyType)
                && (mViewIdentifyTexts == null || mViewIdentifyTexts.length == 0)
                && mIActivityViewFinder == null) {
            throw new IllegalArgumentException("Error: ViewIdentifyType or ViewIdentifyTexts not set");
        }
        if (mRunning) {
            return;
        }
        mRunning = true;
        task(loopMSec, listener, ++mGeneration);
    }

    long generation() { return mGeneration; }

    public void stop() {
        Task.runOnMain(() -> {
            mRunning = false;
            ++mGeneration;
            if (mPending != null) mPending.cancel();
            mPending = null;
        });
    }

    @Nullable
    public Activity getTargetActivity() {
        return mActivityRef.get();
    }

    private void task(long loopMSec, IActivityViewListener listener, long generation) {
        if (!mRunning || generation != mGeneration) {
            return;
        }
        Activity activity = mActivityRef.get();
        if (activity == null) {
            mRunning = false;
            return;
        }
        if (activity.isFinishing()) {
            mRunning = false;
            return;
        }
        if (activity.isDestroyed()) {
            mRunning = false;
            return;
        }

        List<View> viewList = new ArrayList<>();
        List<View> decorViewList = mWatchActivityViewOnly
                ? Collections.singletonList(activity.getWindow().getDecorView())
                : ViewUtils.getWindowManagerViews();
        for (View decorView : decorViewList) {
            if (decorView instanceof ViewGroup) {
            } else {
                continue;
            }
            String viewIdentifyType = this.mViewIdentifyType;
            if (!TextUtils.isEmpty(viewIdentifyType)) {
                ViewUtils.getChildViewsByType((ViewGroup) decorView, viewIdentifyType, viewList);
                if (viewList.size() > 0) {
                    break;
                }
            }
            String[] viewIdentifyTexts = this.mViewIdentifyTexts;
            if (viewIdentifyTexts != null) {
                for (String viewIdentifyText: this.mViewIdentifyTexts) {
                    if (!TextUtils.isEmpty(viewIdentifyText)) {
                        ViewUtils.getChildViews((ViewGroup) decorView, viewIdentifyText, viewList);
                        if (viewList.size() > 0) {
                            break;
                        }
                    }
                }
            }
            IActivityViewFinder viewFinder = mIActivityViewFinder;
            if (viewFinder != null) {
                viewFinder.find(viewList);
                if (viewList.size() > 0) {
                    break;
                }
            }
        }
        if (viewList.size() > 0) {
            Set<View> viewLinkedHashSet = new LinkedHashSet<>(viewList);
            for (View targetView : viewLinkedHashSet) {
                if (!mRunning || generation != mGeneration) return;
                if (ViewUtils.isViewVisibleInScreen(targetView.getRootView())) {
                    onViewFounded(listener, targetView);
                    // 好像是故意不加的
                    // break;
                }
            }
        }
        if (mRunning && generation == mGeneration) {
            mPending = Task.onMain(Math.max(1, loopMSec), () -> task(loopMSec, listener, generation));
        }
    }

    private void onViewFounded(IActivityViewListener listener, View view) {
        try {
            listener.onViewFounded(this, view);
        } catch (Exception e) {
            L.e(e);
        }
    }

    public interface IActivityViewListener {
        void onViewFounded(ActivityViewObserver observer, View view);
    }

    public interface IActivityViewFinder {
        void find(List<View> outViewList);
    }
}
