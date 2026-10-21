package com.surcumference.fingerprint.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.TextView;


/**
 * Created by Jason on 2017/9/9.
 */

public class StyleUtils {

    public static final float TEXT_SIZE_DEFAULT = 15.0f;
    public static final float TEXT_SIZE_BIG = 18.0f;
    public static final float TEXT_SIZE_SMALL = 12.0f;

    public static void apply(TextView textView) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DEFAULT);
        textView.setTextColor(textColor(textView.getContext()));
    }

    public static int textColor(Context context) { return isDarkMode(context) ? 0xFFE0ECE4 : 0xFF192B23; }
    public static int secondaryTextColor(Context context) { return isDarkMode(context) ? 0xFFA4B9AC : 0xFF62746A; }
    public static int accentColor(Context context) { return isDarkMode(context) ? 0xFF73C5A4 : 0xFF286752; }
    public static int dialogColor(Context context) { return isDarkMode(context) ? 0xFF1C2923 : Color.WHITE; }
    public static int pressedColor(Context context) { return isDarkMode(context) ? 0xFF324B3E : 0xFFE3EFE8; }

    public static boolean isDarkMode(Context context) {
        return Configuration.UI_MODE_NIGHT_YES == (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
    }

}
