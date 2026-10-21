package com.surcumference.fingerprint.util;

import android.os.Build;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.surcumference.fingerprint.listener.OnKeyEventListener;

import java.util.ArrayList;
import java.util.List;

public class XWindowCallback implements Window.Callback {

    private Window.Callback originalCallback;
    private List<OnKeyEventListener> keyEventListeners = new ArrayList<>();

    public XWindowCallback(Window.Callback originalCallback) {
        this.originalCallback = originalCallback;
    }

    public Window.Callback getOriginalCallback() {
        return this.originalCallback;
    }

    public void addKeyEventListener(OnKeyEventListener listener) {
        if (!keyEventListeners.contains(listener)) {
            keyEventListeners.add(listener);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        for (OnKeyEventListener listener : keyEventListeners) {
            if (listener.onKeyEvent(keyEvent)) {
                return true;
            }
        }
        return this.originalCallback.dispatchKeyEvent(keyEvent);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent keyEvent) {
        return this.originalCallback.dispatchKeyShortcutEvent(keyEvent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        return this.originalCallback.dispatchTouchEvent(motionEvent);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent motionEvent) {
        return this.originalCallback.dispatchTrackballEvent(motionEvent);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent motionEvent) {
        return this.originalCallback.dispatchGenericMotionEvent(motionEvent);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        return this.originalCallback.dispatchPopulateAccessibilityEvent(accessibilityEvent);
    }

    @Nullable
    @Override
    public View onCreatePanelView(int i) {
        return this.originalCallback.onCreatePanelView(i);
    }

    @Override
    public boolean onCreatePanelMenu(int i, @NonNull Menu menu) {
        return this.originalCallback.onCreatePanelMenu(i, menu);
    }

    @Override
    public boolean onPreparePanel(int i, @Nullable View view, @NonNull Menu menu) {
        return this.originalCallback.onPreparePanel(i, view, menu);
    }

    @Override
    public boolean onMenuOpened(int i, @NonNull Menu menu) {
        return this.originalCallback.onMenuOpened(i, menu);
    }

    @Override
    public boolean onMenuItemSelected(int i, @NonNull MenuItem menuItem) {
        return this.originalCallback.onMenuItemSelected(i, menuItem);
    }

    @Override
    public void onWindowAttributesChanged(WindowManager.LayoutParams layoutParams) {
        this.originalCallback.onWindowAttributesChanged(layoutParams);
    }

    @Override
    public void onContentChanged() {
        this.originalCallback.onContentChanged();
    }

    @Override
    public void onWindowFocusChanged(boolean b) {
        this.originalCallback.onWindowFocusChanged(b);
    }

    @Override
    public void onAttachedToWindow() {
        this.originalCallback.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        this.originalCallback.onDetachedFromWindow();
    }

    @Override
    public void onPanelClosed(int i, @NonNull Menu menu) {
        this.originalCallback.onPanelClosed(i, menu);
    }

    @Override
    public boolean onSearchRequested() {
        return this.originalCallback.onSearchRequested();
    }

    @Override
    public boolean onSearchRequested(SearchEvent searchEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.originalCallback.onSearchRequested(searchEvent);
        }
        return false;
    }

    @Nullable
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return this.originalCallback.onWindowStartingActionMode(callback);
    }

    @Nullable
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.originalCallback.onWindowStartingActionMode(callback, i);
        }
        return null;
    }

    @Override
    public void onActionModeStarted(ActionMode actionMode) {
        this.originalCallback.onActionModeStarted(actionMode);
    }

    @Override
    public void onActionModeFinished(ActionMode actionMode) {
        this.onActionModeFinished(actionMode);
    }
}
