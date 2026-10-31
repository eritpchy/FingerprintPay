package com.surcumference.fingerprint.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;

final class FingerprintPromptPalette {
    final int surface, foreground, secondary, action, badge, button, outline, ripple;

    private FingerprintPromptPalette(int surface, int foreground, int secondary, int action,
                                     int badge, int button, int outline) {
        this.surface = surface;
        this.foreground = foreground;
        this.secondary = secondary;
        this.action = action;
        this.badge = badge;
        this.button = button;
        this.outline = outline;
        ripple = Color.argb(36, Color.red(action), Color.green(action), Color.blue(action));
    }

    static FingerprintPromptPalette read(Context context) {
        boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return night ? new FingerprintPromptPalette(
                    color(context, android.R.color.system_neutral1_800, 0xff2d303a),
                    color(context, android.R.color.system_neutral1_50, 0xfff1f2f5),
                    color(context, android.R.color.system_neutral2_200, 0xffc5c8d0),
                    color(context, android.R.color.system_accent1_200, 0xffb3c5ff),
                    color(context, android.R.color.system_neutral2_700, 0xff434654),
                    color(context, android.R.color.system_neutral2_700, 0xff434654),
                    color(context, android.R.color.system_neutral2_600, 0xff5a5e6c))
                    : new FingerprintPromptPalette(
                    color(context, android.R.color.system_neutral1_50, 0xfff5f6f8),
                    color(context, android.R.color.system_neutral1_900, 0xff181b25),
                    color(context, android.R.color.system_neutral2_600, 0xff5a5e6c),
                    color(context, android.R.color.system_accent1_700, 0xff003fa4),
                    color(context, android.R.color.system_neutral1_100, 0xffe0e2ef),
                    color(context, android.R.color.system_neutral1_100, 0xffe0e2ef),
                    color(context, android.R.color.system_neutral2_200, 0xffc3c6d6));
        }
        return night ? new FingerprintPromptPalette(0xff2d303a, 0xfff1f2f5, 0xffc5c8d0,
                0xffb3c5ff, 0xff434654, 0xff434654, 0xff5a5e6c)
                : new FingerprintPromptPalette(0xfff5f6f8, 0xff181b25, 0xff5a5e6c,
                0xff003fa4, 0xffe0e2ef, 0xffe0e2ef, 0xffc3c6d6);
    }

    private static int color(Context context, int resource, int fallback) {
        try { return context.getColor(resource); }
        catch (Resources.NotFoundException unavailable) { return fallback; }
    }
}
