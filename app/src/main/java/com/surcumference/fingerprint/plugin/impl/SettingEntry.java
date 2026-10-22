package com.surcumference.fingerprint.plugin.impl;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.log.L;

/**
 * 在宿主应用（微信 / 支付宝 / 淘宝 / QQ / 云闪付）的设置页注入「指纹支付」入口。
 *
 * <p>背景：上游自 v7.0.0 起移除了应用内设置入口（CHANGELOG：「新增统一配置页面，替代应用内设置入口」），
 * 同时删除了 {@code view/SettingsView.java} 及其依赖的 6 个视图类。v6.1.0 尚保留该入口，
 * 因此这里把入口恢复回来。
 *
 * <p>与 v6.1.0 的关键差异：入口点击后<b>不再</b>打开 v6.1.0 的本地 {@code SettingsView}，原因是
 * 该对话框通过 {@code Config.from(context).setOn()/setPasswordEncrypted()} 直接写宿主侧的
 * SharedPreferences，而 7.x 的配置已改为 {@code ConfigStores} 的 revision/patch JSON 协议
 * （Zygisk 后端落 {@code /data/adb/fingerprintpay/user-0.json}，Xposed 后端落 APK 侧
 * {@code unified_config_v1}）。若沿用旧对话框，会出现「开关能拨动但实际不生效」的假配置。
 * 因此入口改为直接拉起 7.x 的统一配置页 {@code ConfigActivity}，所改配置必然生效。
 *
 * <p>跨进程说明：本类代码运行在宿主应用进程内，与指纹支付 APK 不同 UID，因此
 * 必须 {@code FLAG_ACTIVITY_NEW_TASK}，且目标 Activity 必须在 Manifest 中 exported=true。
 */
public final class SettingEntry {

    /**
     * 组件包名 = applicationId。
     */
    private static final String HOST_PACKAGE = BuildConfig.APPLICATION_ID;

    /**
     * 组件类名必须用 <b>namespace</b>（com.surcumference.fingerprint），不能用 applicationId。
     * 本工程两者不同：namespace=com.surcumference.fingerprint，applicationId=com.surcumference.fingerprintpay，
     * 而 Manifest 里的相对名 {@code .activity.ConfigActivity} 是按 namespace 解析的，
     * 已核对构建产物中的真实组件名为 com.surcumference.fingerprint.activity.ConfigActivity。
     * 这里用字符串而非 {@code ConfigActivity.class} 是为了避免在宿主进程里触发类加载。
     */
    private static final String CONFIG_ACTIVITY_CLASS = "com.surcumference.fingerprint.activity.ConfigActivity";

    private SettingEntry() {
    }

    /** 打开指纹支付的统一配置页。 */
    public static void open(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(HOST_PACKAGE, CONFIG_ACTIVITY_CLASS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Context app = context.getApplicationContext();
            (app == null ? context : app).startActivity(intent);
        } catch (Exception e) {
            // 常见原因：ConfigActivity 被改成 exported=false，或 APK 未安装/被停用
            L.e("SettingEntry: failed to open config page", e);
        }
    }

    /**
     * 构建一行设置入口：左侧「指纹支付」，右侧版本号，整行可点击。
     *
     * @param darkMode      宿主是否处于深色模式
     * @param hPaddingDp    右侧版本号的水平内边距（dp），各宿主取值不同以对齐原生样式
     * @param cornerRadiusDp 圆角半径（dp），0 表示直角
     */
    public static LinearLayout buildRow(Context context, boolean darkMode, int hPaddingDp, int cornerRadiusDp) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(1);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(itemBackground(context, darkMode, cornerRadiusDp));
        row.setOnClickListener(view -> open(view.getContext()));
        // 幂等标记：同一页面重复注入时靠它判重
        row.setTag(R.id.app_settings_name, Boolean.TRUE);

        TextView name = new TextView(context);
        name.setText(Lang.getString(R.id.app_settings_name));
        name.setTextColor(darkMode ? 0xFFD3D3D3 : 0xFF353535);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setPadding(DpUtils.dip2px(context, 16), 0, 0, 0);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_BIG);

        TextView version = new TextView(context);
        version.setText(BuildConfig.VERSION_NAME);
        version.setGravity(Gravity.CENTER_VERTICAL);
        version.setPadding(0, 0, DpUtils.dip2px(context, hPaddingDp), 0);
        version.setTextColor(darkMode ? 0xFF656565 : 0xFF999999);
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_SMALL);

        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    /** 构建「分割线 + 入口行」的竖向容器，供需要整块插入的宿主（微信 / 淘宝）使用。 */
    public static LinearLayout buildSection(Context context, boolean darkMode, int hPaddingDp) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        View divider = new View(context);
        divider.setBackgroundColor(darkMode ? 0xFF2E2E2E : 0xFFD5D5D5);

        section.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        section.addView(buildRow(context, darkMode, hPaddingDp, 0),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DpUtils.dip2px(context, 55)));
        return section;
    }

    /**
     * 通用注入：以 {@code anchorTexts} 在设置页里定位到一个已知设置项，
     * 把入口插到该行上方。锚点找不到时静默返回 false，不抛异常、不影响宿主。
     *
     * <p>相比上游 v6.1.0 为每个宿主硬编码「父容器往上第 N 层」，这里改为逐层试探
     * 列表行容器，宿主改版时更容易存活。
     */
    public static boolean injectAboveAnchor(Activity activity, String... anchorTexts) {
        if (activity == null) {
            return false;
        }
        try {
            View decorView = activity.getWindow().getDecorView();
            if (hasEntry(decorView)) {
                return false;
            }
            View anchor = ViewUtils.findViewByText(decorView, anchorTexts);
            if (anchor == null) {
                return false;
            }
            View row = findRow(anchor);
            if (row == null) {
                return false;
            }
            View parentView = (View) row.getParent();
            if (!(parentView instanceof ViewGroup)) {
                return false;
            }
            ViewGroup parent = (ViewGroup) parentView;
            int index = parent.indexOfChild(row);
            if (index < 0) {
                return false;
            }
            LinearLayout section = buildSection(activity, StyleUtils.isDarkMode(activity), 15);
            parent.addView(section, index, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            L.d("SettingEntry injected above anchor: " + anchorTexts[0]);
            return true;
        } catch (Exception e) {
            L.e("SettingEntry.injectAboveAnchor failed", e);
            return false;
        }
    }

    /**
     * 从锚点文字视图向上找出「一行」：其父容器含多个子视图、且自身宽度铺满。
     * 最多上溯 4 层，避免把整页根容器当成一行。
     */
    private static View findRow(View anchor) {
        View current = anchor;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            View parentView = (View) current.getParent();
            if (!(parentView instanceof ViewGroup)) {
                return null;
            }
            ViewGroup parent = (ViewGroup) parentView;
            ViewGroup.LayoutParams params = current.getLayoutParams();
            boolean fullWidth = params != null
                    && (params.width == ViewGroup.LayoutParams.MATCH_PARENT || params.width < 0);
            if (parent.getChildCount() >= 2 && fullWidth) {
                return current;
            }
            current = parent;
        }
        return null;
    }

    /**
     * 判重：视图树里是否已经存在本模块注入的入口。
     * 用 tag 而非文字匹配，避免宿主自身存在同名文案时误判。
     */
    public static boolean hasEntry(View root) {
        if (root == null) {
            return false;
        }
        if (Boolean.TRUE.equals(root.getTag(R.id.app_settings_name))) {
            return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasEntry(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 条目背景：普通态 + 按下态，用 StateListDrawable 替代上游已删除的 XDrawable。 */
    private static Drawable itemBackground(Context context, boolean darkMode, int cornerRadiusDp) {
        int normal = darkMode ? 0xFF191919 : Color.WHITE;
        int pressed = darkMode ? 0xFF1D1D1D : 0xFFE5E5E5;
        int radius = cornerRadiusDp > 0 ? DpUtils.dip2px(context, cornerRadiusDp) : 0;

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, fill(pressed, radius));
        states.addState(new int[]{}, fill(normal, radius));
        return states;
    }

    private static GradientDrawable fill(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        if (radius > 0) {
            drawable.setCornerRadius(radius);
        }
        return drawable;
    }
}
