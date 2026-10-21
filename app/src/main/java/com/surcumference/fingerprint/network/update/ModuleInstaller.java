package com.surcumference.fingerprint.network.update;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.log.L;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModuleInstaller {
    private static final String[] ZIP_MANAGERS = {"me.weishu.kernelsu", "com.rifsxd.ksunext"};
    private ModuleInstaller() {}

    public static boolean isAvailable(Context context) {
        return hasAPatch(context) || !zipInstallers(context, MediaStore.Files.getContentUri("external", 0)).isEmpty();
    }

    public static Intent prepare(Context context, File archive, String version) throws Exception {
        validate(archive);
        Uri uri = publish(context, archive, version);
        try {
            return installationIntent(context, uri);
        } catch (Exception error) {
            context.getContentResolver().delete(uri, null, null);
            throw error;
        }
    }

    static Intent installationIntent(Context context, Uri uri) throws Exception {
        List<Intent> installers = zipInstallers(context, uri);
        if (hasAPatch(context)) {
            try { installers.add(APatchModuleInstaller.prepare(context, uri)); }
            catch (Exception error) {
                if (installers.isEmpty()) throw error;
                L.e(error);
            }
        }
        if (installers.isEmpty()) throw new IOException(Lang.getString(R.id.root_manager_missing));
        if (installers.size() == 1) return installers.get(0);
        Intent chooser = Intent.createChooser(installers.remove(0), Lang.getString(R.id.open_root_manager));
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, installers.toArray(new Intent[0]));
        return grantArchive(chooser, uri);
    }

    private static boolean hasAPatch(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(APatchModuleInstaller.PACKAGE) != null;
    }

    private static List<Intent> zipInstallers(Context context, Uri uri) {
        List<Intent> installers = new ArrayList<>();
        for (String manager : ZIP_MANAGERS) {
            Intent view = new Intent(Intent.ACTION_VIEW).setPackage(manager).setDataAndType(uri, "application/zip");
            for (ResolveInfo resolved : context.getPackageManager().queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)) {
                ActivityInfo activity = resolved.activityInfo;
                if (activity == null || !manager.equals(activity.packageName) || !activity.exported || !activity.enabled
                        || (activity.permission != null && context.checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED)) continue;
                installers.add(grantArchive(view.setClassName(manager, activity.name), uri));
                break;
            }
        }
        return installers;
    }

    private static Intent grantArchive(Intent intent, Uri uri) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("FingerprintPay module", uri));
        return intent;
    }

    static void validate(File archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry("module.prop");
            if (entry == null || entry.getSize() < 0 || entry.getSize() > 8192) throw new IOException("Invalid module metadata");
            Properties properties = new Properties();
            try (InputStream input = zip.getInputStream(entry)) { properties.load(input); }
            String id = properties.getProperty("id");
            if (!"zygisk-module-xfingerprint-pay-all".equals(id) && !"xfingerprint-pay-batch-installer".equals(id))
                throw new IOException("The download is not a FingerprintPay all-in-one module");
            if (zip.getEntry("META-INF/com/google/android/update-binary") == null || zip.getEntry("customize.sh") == null)
                throw new IOException("Missing module installer");
        }
    }

    // A system-owned provider keeps the installer alive if the host process is reclaimed.
    private static Uri publish(Context context, File archive, String version) throws IOException {
        String name = "FingerprintPay-" + version.replaceAll("[^A-Za-z0-9._-]", "_") + ".zip";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FingerprintPay");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            collection = MediaStore.Files.getContentUri("external");
            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create download directory");
            values.put(MediaStore.MediaColumns.DATA, File.createTempFile("FingerprintPay-", ".zip", directory).getPath());
        }
        Uri uri = context.getContentResolver().insert(collection, values);
        if (uri == null) throw new IOException("Cannot create module download");
        try {
            try (InputStream input = new FileInputStream(archive); OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Cannot write module download");
                byte[] buffer = new byte[32768];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, values, null, null) != 1) throw new IOException("Cannot publish module download");
            }
            return uri;
        } catch (IOException | RuntimeException error) {
            context.getContentResolver().delete(uri, null, null);
            throw error;
        }
    }
}
