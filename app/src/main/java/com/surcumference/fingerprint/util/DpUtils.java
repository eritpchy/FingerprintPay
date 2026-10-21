package com.surcumference.fingerprint.util;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Created by Jason on 2017/9/9.
 */

public class DpUtils {

    public static int dip2px(Context context, float dipValue) {
        return (int) (dipValue * getDensity(context) + 0.5f);
    }

    private static float getDensity(Context context){
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.density;
    }
}
