package com.surcumference.fingerprint.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.surcumference.fingerprint.Constant;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Created by Jason on 2017/9/10.
 */
public final class DonateUtils {
    public static String packageName(String channel) {
        if ("alipay".equals(channel)) return Constant.PACKAGE_NAME_ALIPAY;
        if ("wechat".equals(channel)) return Constant.PACKAGE_NAME_WECHAT;
        if ("qq".equals(channel)) return Constant.PACKAGE_NAME_QQ;
        throw new IllegalArgumentException("Unknown donation channel");
    }
    public static JSONObject request(Context context, String op, String channel, String id) throws Exception {
        return com.surcumference.fingerprint.storage.ConfigStores.forContext(context).request(
                new JSONObject().put("op", op).put("package", packageName(channel)).put("channel", channel).put("id", id));
    }
    public static JSONObject openFromSettings(Context context, String channel) throws Exception {
        String pkg = packageName(channel);
        if ("alipay".equals(channel) || pkg.equals(context.getPackageName())) return open(context, channel);
        Intent launcher = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launcher == null) throw new IllegalStateException("此用户未安装所选应用");
        String id = request(context, "beginDonation", channel, null).getJSONObject("pending").getString("id");
        try { context.startActivity(launcher); }
        catch (RuntimeException error) { request(context, "cancelDonation", channel, id); throw error; }
        return new JSONObject().put("ok", true).put("mode", "pending").put("id", id);
    }
    public static JSONObject pending(Context context) throws Exception {
        if (com.surcumference.fingerprint.plugin.PluginApp.getCurrentType() != com.surcumference.fingerprint.bean.PluginType.Xposed) return null;
        String pkg = context.getPackageName(), channel;
        if (Constant.PACKAGE_NAME_WECHAT.equals(pkg)) channel = "wechat";
        else if (Constant.PACKAGE_NAME_QQ.equals(pkg)) channel = "qq";
        else return null;
        JSONObject pending = request(context, "pendingDonation", channel, null).getJSONObject("pending");
        return pending.has("id") ? pending : null;
    }
    public static void dispatch(Context context, JSONObject pending) throws Exception {
        String channel = pending.getString("channel"), id = pending.getString("id");
        if (!packageName(channel).equals(context.getPackageName())) throw new SecurityException("Wrong donation host");
        // Claim immediately before launch so canceled or expired requests cannot launch later.
        request(context, "claimDonation", channel, id);
        JSONObject result = open(context, channel);
        com.surcumference.fingerprint.storage.ConfigStores.forContext(context).request(
                new JSONObject().put("op", "completeDonation").put("package", context.getPackageName()).put("id", id).put("result", result));
    }
    public static List<Intent> intents(String channel) throws Exception {
        List<Intent> intents = new ArrayList<>();
        if ("alipay".equals(channel)) {
            intents.add(new Intent(Intent.ACTION_VIEW, Uri.parse("alipayqr://platformapi/startapp?saId=10000007"
                    + "&clientVersion=3.7.0.0718&qrcode=" + Uri.encode(Constant.DONATE_ID_ALIPAY + "?_s=web-other")
                    + "&_t=" + System.currentTimeMillis())).setPackage(Constant.PACKAGE_NAME_ALIPAY));
        } else if ("wechat".equals(channel)) {
            intents.add(new Intent().setClassName(Constant.PACKAGE_NAME_WECHAT,
                    "com.tencent.mm.plugin.remittance.ui.RemittanceAdapterUI")
                    .putExtra("scene", 1).putExtra("pay_channel", 13).putExtra("receiver_name", Constant.DONATE_ID_WECHAT));
        } else if ("qq".equals(channel)) {
            String data = new JSONObject().put("targetUin", Constant.AUTHOR_QQ).put("targetNickname", Constant.AUTHOR_QQ_NAME)
                    .put("sign", "").put("trans_fee", "").put("source", "1").put("desc", "").toString();
            for (String activity : new String[] {"com.tencent.mobileqq.qwallet.transaction.impl.TransactionActivity",
                    "com.tencent.mobileqq.activity.qwallet.TransactionActivity"}) {
                intents.add(new Intent().setClassName(Constant.PACKAGE_NAME_QQ, activity).putExtra("come_from", 5)
                        .putExtra("extra_data", data).putExtra("app_info", "appid#20000001|bargainor_id#1000026901|channel#wallet")
                        .putExtra("callbackSn", "0"));
            }
            intents.add(new Intent(Intent.ACTION_VIEW, Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=" + Constant.AUTHOR_QQ))
                    .setPackage(Constant.PACKAGE_NAME_QQ));
        } else throw new IllegalArgumentException("Unknown donation channel");
        return intents;
    }

    public static JSONObject open(Context context, String channel) throws Exception {
        for (Intent intent : intents(channel)) {
            try {
                context.startActivity(intent);
                return new JSONObject().put("ok", true).put("mode",
                        "qq".equals(channel) && Intent.ACTION_VIEW.equals(intent.getAction()) ? "contact" : "payment");
            } catch (android.content.ActivityNotFoundException | SecurityException ignored) {}
        }
        return new JSONObject().put("ok", false).put("error", "无法打开赞助页面，请使用收款码或账号手动转账");
    }
    private DonateUtils() {}
}
