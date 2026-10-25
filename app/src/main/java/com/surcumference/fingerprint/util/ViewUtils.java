package com.surcumference.fingerprint.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.*;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.QuickContactBadge;
import android.widget.RadioButton;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.ButtonBarLayout;
import androidx.appcompat.widget.SwitchCompat;

import com.surcumference.fingerprint.listener.OnKeyEventListener;
import com.surcumference.fingerprint.util.log.L;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Jason on 2017/9/9.
 */

public class ViewUtils {

    public static boolean performActionClick(View view) {
        if (view == null) return false;
        long now = SystemClock.uptimeMillis();
        float x = Math.max(0, view.getWidth()) / 2f;
        float y = Math.max(0, view.getHeight()) / 2f;
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        try {
            boolean pressed = view.dispatchTouchEvent(down);
            boolean released = view.dispatchTouchEvent(up);
            return pressed && released;
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    @Nullable
    public static View findViewByName(Activity activity, String packageName, String... names) {
        View rootView = activity.getWindow().getDecorView();
        return findViewByName(rootView, packageName, names);
    }

    @Nullable
    public static View findViewByName(View rootView, String packageName, String... names) {
        Resources resources = rootView.getResources();
        for (String name : names) {
            int id = resources.getIdentifier(name, "id", packageName);
            if (id == 0) {
                continue;
            }
            List<View> viewList = new ArrayList<>();
            if (rootView instanceof ViewGroup) {
                getChildViews((ViewGroup) rootView, id, viewList);
            }
            sortViewListByYPosition(viewList);
            int outViewListSize = viewList.size();
            if (outViewListSize == 1) {
                return viewList.get(0);
            } else if (outViewListSize > 1) {
                for (View view : viewList) {
                    if (view.isShown()) {
                        return view;
                    }
                }
                return viewList.get(0);
            }
        }
        return null;
    }

    @Nullable
    public static View findViewByText(View rootView, String... names) {
        for (String name : names) {
            List<View> viewList = new ArrayList<>();
            if (rootView instanceof ViewGroup) {
                getChildViews((ViewGroup) rootView, name, viewList);
            }
            int outViewListSize = viewList.size();
            if (outViewListSize == 1) {
                return viewList.get(0);
            } else if (outViewListSize > 1) {
                for (View view : viewList) {
                    if (view.isShown()) {
                        return view;
                    }
                }
                return viewList.get(0);
            }
        }
        return null;
    }

    private static Class sRecycleViewClz;

    private static String getViewBaseDesc(View view) {
        if (sRecycleViewClz == null) {
            try {
                sRecycleViewClz = Class.forName("android.support.v7.widget.RecyclerView");
            } catch (ClassNotFoundException e) {
            }
        }

        if (view instanceof FrameLayout) {
            return FrameLayout.class.getName();
        } else if (view instanceof RatingBar) {
            return RatingBar.class.getName();
        } else if (view instanceof SeekBar) {
            return SeekBar.class.getName();
        } else if (view instanceof TableLayout) {
            return TableLayout.class.getName();
        } else if (view instanceof ButtonBarLayout) {
            return ButtonBarLayout.class.getName();
        } else if (view instanceof TableRow) {
            return TableRow.class.getName();
        } else if (view instanceof LinearLayout) {
            return LinearLayout.class.getName();
        } else if (view instanceof RelativeLayout) {
            return RelativeLayout.class.getName();
        } else if (view instanceof GridLayout) {
            return GridLayout.class.getName();
        } else if (view instanceof CheckBox) {
            return CheckBox.class.getName();
        } else if (view instanceof RadioButton) {
            return RadioButton.class.getName();
        } else if (view instanceof CheckedTextView) {
            return CheckedTextView.class.getName();
        } else if (view instanceof Spinner) {
            return Spinner.class.getName();
        } else if (view instanceof ProgressBar) {
            return ProgressBar.class.getName();
        } else if (view instanceof QuickContactBadge) {
            return QuickContactBadge.class.getName();
        } else if (view instanceof SwitchCompat) {
            return SwitchCompat.class.getName();
        } else if (view instanceof Switch) {
            return Switch.class.getName();
        } else if (view instanceof Space) {
            return Space.class.getName();
        } else if (view instanceof TextView) {
            return TextView.class.getName();
        } else if (view instanceof AppCompatImageView) {
            return AppCompatImageView.class.getName();
        } else if (view instanceof ImageView) {
            return ImageView.class.getName();
        } else if (view instanceof ListView) {
            return ListView.class.getName();
        } else if (view instanceof GridView) {
            return ListView.class.getName();
        } else if (sRecycleViewClz != null && view.getClass().isAssignableFrom(sRecycleViewClz)) {
            return sRecycleViewClz.getName();
        }
        return view.getClass().getName();
    }

    public static String getViewInfo(@Nullable View view) {
        StringBuffer stringBuffer = new StringBuffer();
        if (view == null) {
            return "null";
        }
        stringBuffer.append(String.valueOf(view));
        stringBuffer.append(" type:").append(getViewBaseDesc(view));
        stringBuffer.append(" clz:").append(view.getClass().getName());
        if (view instanceof EditText) {
            stringBuffer.append(" text:").append(((EditText) view).getText()).append(" hint:").append(((EditText) view).getHint());
        } else if (view instanceof TextView) {
            stringBuffer.append(" text:").append(((TextView) view).getText());
        }
        int []location = new int[]{0,0};
        view.getLocationOnScreen(location);
        stringBuffer.append(" cor x:").append(location[0]).append(" y:").append(location[1]);
        stringBuffer.append(" w:").append(view.getWidth()).append(" h:").append(view.getHeight());
        CharSequence desc = view.getContentDescription();
        if (!TextUtils.isEmpty(desc)) {
            stringBuffer.append(" desc:").append(desc);
        }
        stringBuffer.append(" tag:").append(view.getTag());
        if (view instanceof ViewGroup) {
            stringBuffer.append(" child:").append(((ViewGroup) view).getChildCount());
        }
        stringBuffer.append(" shownInScreen:").append(isShownInScreen(view));
        stringBuffer.append(" viewVisibleInScreen:").append(isViewVisibleInScreen(view));
        stringBuffer.append(" shown:").append(isShown(view));
        return stringBuffer.toString();
    }

    public static void getChildViews(ViewGroup parent, String text, List<View> outList) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child == null) {
                continue;
            }
            if (child instanceof EditText) {
                if (text.equals(String.valueOf(((TextView) child).getText()))) {
                    outList.add(child);
                } else if (text.equals(String.valueOf(((EditText) child).getHint()))) {
                    outList.add(child);
                }
            } else if (child instanceof TextView) {
                if (text.equals(String.valueOf(((TextView) child).getText()))) {
                    outList.add(child);
                }
            }
            if (!outList.contains(child)) {
                if (text.equals(String.valueOf(child.getContentDescription()))) {
                    outList.add(child);
                }
            }
            if (child instanceof ViewGroup) {
                getChildViews((ViewGroup) child, text, outList);
            } else {
            }
        }
    }

    public static void getChildViewsByRegex(ViewGroup parent, String regex, List<View> outList) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child == null) {
                continue;
            }
            if (child instanceof EditText) {
                if (String.valueOf(((TextView) child).getText()).matches(regex)) {
                    outList.add(child);
                } else if (regex.equals(String.valueOf(((EditText) child).getHint()))) {
                    outList.add(child);
                }
            } else if (child instanceof TextView) {
                if (String.valueOf(((TextView) child).getText()).matches(regex)) {
                    outList.add(child);
                }
            }
            if (!outList.contains(child)) {
                if (String.valueOf(child.getContentDescription()).matches(regex)) {
                    outList.add(child);
                }
            }
            if (child instanceof ViewGroup) {
                getChildViewsByRegex((ViewGroup) child, regex, outList);
            } else {
            }
        }
    }

    public static void getChildViews(ViewGroup parent, int id, List<View> outList) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child == null) {
                continue;
            }
            if (id == child.getId()) {
                outList.add(child);
            }
            if (child instanceof ViewGroup) {
                getChildViews((ViewGroup) child, id, outList);
            } else {
            }
        }
    }

    public static void getChildViewsByType(ViewGroup parent, String type, List<View> outList) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child == null) {
                continue;
            }
            if (child.getClass().getName().contains(type)) {
                outList.add(child);
            }
            if (child instanceof ViewGroup) {
                getChildViewsByType((ViewGroup) child, type, outList);
            } else {
            }
        }
    }

    public static void getChildViews(ViewGroup parent, List<View> outList) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child == null) {
                continue;
            }
            outList.add(child);
            if (child instanceof ViewGroup) {
                getChildViews((ViewGroup) child, outList);
            } else {
            }
        }
    }

    private static Comparator<View> sYLocationLHCompator = new Comparator<View>() {
        @Override
        public int compare(View o1, View o2) {
            int y1 = getViewYPosInScreen(o1);
            int y2 = getViewYPosInScreen(o2);
            return Integer.compare(y1, y2);
        }
    };

    public static void sortViewListByYPosition (List<View> viewList) {
        Collections.sort(viewList, sYLocationLHCompator);
    }

    public static int getViewYPosInScreen(View v) {
        int[] location = new int[]{0, 0};
        v.getLocationOnScreen(location);
        return location[1];
    }

    public static void removeFromSuperView(View v) {
        ViewParent parentView = v.getParent();
        if (parentView == null) {
            return;
        }
        if (parentView instanceof ViewGroup) {
            ViewGroup parentLayout = (ViewGroup) parentView;
            parentLayout.removeView(v);
        }
    }

    public static String viewsDesc(List<View> childView) {
        StringBuffer stringBuffer = new StringBuffer();
        for (View view: childView) {
            stringBuffer.append(ViewUtils.getViewInfo(view)).append("\n");
        }
        return stringBuffer.toString();
    }

    /**
     *
     * @param viewGroup
     * @param childView
     * @return found >= 0, not found -1
     */
    public static int findChildViewPosition(ViewGroup viewGroup, View childView) {
        int childViewCount = viewGroup.getChildCount();
        for (int i = 0; i < childViewCount; i++) {
            View view = viewGroup.getChildAt(i);
            if (view == childView) {
                return i;
            }
        }
        return -1;
    }

    public static List<View> getWindowManagerViews() {
        try {
            Class wmgClass = Class.forName("android.view.WindowManagerGlobal");
            Object wmgInstance = wmgClass.getMethod("getInstance").invoke(null);
            return viewsFromWM(wmgClass, wmgInstance);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<View>();
    }

    private static List<View> viewsFromWM(Class wmClass, Object wmInstance) throws Exception {

        Field viewsField = wmClass.getDeclaredField("mViews");
        viewsField.setAccessible(true);
        Object views = viewsField.get(wmInstance);

        if (views instanceof List) {
            return new ArrayList<View>((List<View>) viewsField.get(wmInstance));
        } else if (views instanceof View[]) {
            return Arrays.asList((View[])viewsField.get(wmInstance));
        }

        return new ArrayList<View>();
    }

    public static boolean isShown(@Nullable View v) {
        if (v == null) {
            return false;
        }
        Rect r = new Rect();
        v.getGlobalVisibleRect(r);
        if (r.left == 0 && r.right == 0 && r.top == 0 && r.bottom == 0) {
            return false;
        }
        return true;
    }

    public static boolean isShownInScreen(@Nullable View view) {
        if (view == null || view.getWindowToken() == null || !view.isShown()
                || view.getWindowVisibility() != View.VISIBLE) return false;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current.getAlpha() <= 0) return false;
        }
        Rect visible = new Rect();
        Rect window = new Rect();
        // Both rectangles use the owning root's coordinates, including resized and floating windows.
        return view.getGlobalVisibleRect(visible) && !visible.isEmpty()
                && view.getRootView().getGlobalVisibleRect(window) && !window.isEmpty()
                && Rect.intersects(visible, window);
    }

    public static boolean isViewVisibleInScreen(View view) {
        if (!isShown(view)) {
            return false;
        }
        if (!view.isAttachedToWindow()) {
            return false;
        }
        if (view.getAlpha() == 0) {
            return false;
        }
        if (view.getWidth() <= 0 && view.getHeight() <= 0) {
            return false;
        }
        return view.getWindowVisibility() == View.VISIBLE;
    }

    public static void setAlpha(AlertDialog dialog, float value) {
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        View view = window.getDecorView();
        if (view == null) {
            return;
        }
        view.setAlpha(value);
    }

    public static void setDimAmount(AlertDialog dialog, float value) {
        try {
            if (dialog == null) {
                return;
            }
            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }
            window.setDimAmount(value);
        }  catch (Exception e) {
            // java.lang.IllegalArgumentException: View=DecorView@2d8132f[PayPwdDialogActivity] not attached to window manager
            L.e(e);
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("PrivateApi")
    public static View.OnClickListener getOnClickListener(View view) {
        View.OnClickListener retrievedListener = null;
        String viewStr = "android.view.View";
        String lInfoStr = "android.view.View$ListenerInfo";
        try {
            Field listenerField = Class.forName(viewStr).getDeclaredField("mListenerInfo");
            Object listenerInfo = null;
            if (listenerField != null) {
                listenerField.setAccessible(true);
                listenerInfo = listenerField.get(view);
            }
            Field clickListenerField = Class.forName(lInfoStr).getDeclaredField("mOnClickListener");
            if (clickListenerField != null && listenerInfo != null) {
                retrievedListener = (View.OnClickListener) clickListenerField.get(listenerInfo);
            }
        } catch (NoSuchFieldException ex) {
            L.e("Reflection: No Such Field.");
        } catch (IllegalAccessException ex) {
            L.e("Reflection: Illegal Access.");
        } catch (ClassNotFoundException ex) {
            L.e("Reflection: Class Not Found.");
        }
        return retrievedListener;
    }

    public static void registerVolumeKeyDownEventListener(@Nullable Window window, OnKeyEventListener keyEventListener) {
        if (window == null) {
            return;
        }
        Window.Callback callback = window.getCallback();
        if (callback instanceof XWindowCallback) {
            return;
        }
        XWindowCallback newCallback = new XWindowCallback(callback);
        newCallback.addKeyEventListener(event -> {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN &&
                    event.getAction() == KeyEvent.ACTION_DOWN) {
                return keyEventListener.onKeyEvent(event);
            }
            return false;
        });
        window.setCallback(newCallback);
    }

    public static void unregisterVolumeKeyDownEventListener(@Nullable Window window) {
        if (window == null) {
            return;
        }
        Window.Callback callback = window.getCallback();
        if (callback instanceof XWindowCallback) {
            window.setCallback(((XWindowCallback) callback).getOriginalCallback());
        }
    }
}
