package com.surcumference.fingerprint.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.UserManager;

import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.view.MessageView;

import java.lang.reflect.Method;

/**
 * Created by Jason on 2017/11/1.
 */

public class Tools {

    public static void doUnSupportVersionUpload(Context context, String message) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            int versionCode = packageInfo.versionCode;
            String versionName = packageInfo.versionName;

            new MessageView(context).text(String.format(Lang.getString(R.id.message_version_not_supported), versionName, versionCode, BuildConfig.VERSION_NAME)).showInDialog();

            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("UnSupport: versionName:").append(versionName)
                    .append(" versionCode:").append(versionCode)
                    .append(" viewInfos:").append(message);

            L.e(stringBuffer);
        } catch (Exception e) {
            L.e(e);
        }
    }

    public static boolean isCurrentUserOwner(Context context) {
        try {
            Method getUserHandle = UserManager.class.getMethod("getUserHandle");
            int userHandle = (Integer) getUserHandle.invoke(context.getSystemService(Context.USER_SERVICE));
            return userHandle == 0;
        } catch (Exception ex) {
            return false;
        }
    }

}
