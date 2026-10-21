package com.surcumference.fingerprint;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by Jason on 2017/9/17.
 */

public class Lang {

    private static int sLang;

    public static final int LANG_ZH_CN = 0;
    public static final int LANG_ZH_TW = 1;
    public static final int LANG_EN = 2;

    static {
        Locale locale = Locale.getDefault();
        if (locale.getLanguage().toLowerCase().contains("zh")) {
            String country = locale.getCountry().toLowerCase();
            if (country.contains("tw") || country.contains("hk")) {
                sLang = LANG_ZH_TW;
            } else {
                sLang = LANG_ZH_CN;
            }
        } else {
            sLang = LANG_EN;
        }
    }

    private static final Map<Integer, String> STRINGS = new HashMap<>(148);

    static {
        STRINGS.put(R.string.app_name, tr("指纹支付", "指纹支付", "Fingerprint Pay"));
        STRINGS.put(R.id.found_new_version, tr("发现新版本", "發現新版本 ", "New version: "));
        STRINGS.put(R.id.skip_this_version, tr("跳过这个版本", "跳過這個版本 ", "Skip"));
        STRINGS.put(R.id.cancel, tr("取消", "取消", "Cancel"));
        STRINGS.put(R.id.goto_update_page, tr("前往更新页", "前往更新頁 ", "Update page"));
        STRINGS.put(R.id.update_now, tr("立即更新", "立即更新 ", "UPDATE"));
        STRINGS.put(R.id.module_update_note, tr("请在 Root 管理器的模块列表中找到指纹支付，使用模块的更新按钮下载并安装。安装完成后重启手机生效。",
                "請在 Root 管理器的模組清單中找到指紋支付，使用模組的更新按鈕下載並安裝。安裝完成後重新啟動手機生效。",
                "Find Fingerprint Pay in your Root manager's module list and use its Update button to download and install. Restart your phone after installation."));
        STRINGS.put(R.id.root_manager_missing, tr("未找到 Root 管理器，请打开你使用的 Root 管理器更新指纹支付模块。",
                "未找到 Root 管理器，請開啟你使用的 Root 管理器更新指紋支付模組。",
                "No Root manager was found. Open your Root manager to update the Fingerprint Pay module."));
        STRINGS.put(R.id.update_failed, tr("更新未完成", "更新未完成", "Update incomplete"));
        STRINGS.put(R.id.open_root_manager, tr("打开 Root 管理器", "開啟 Root 管理器", "Open Root manager"));
        STRINGS.put(R.id.downloading, tr("下载中", "下載中", "Downloading"));
        STRINGS.put(R.id.download_title_failed, tr("下载出错", "下載出錯", "Download failed"));
        STRINGS.put(R.id.ok, tr("确定", "确定", "OK"));
        STRINGS.put(R.id.settings_title_taobao, tr("淘宝", "淘寶", "Taobao"));
        STRINGS.put(R.id.settings_title_alipay, tr("支付宝", "支付寶", "Alipay"));
        STRINGS.put(R.id.settings_title_wechat, tr("微信", "微信", "WeChat"));
        STRINGS.put(R.id.settings_title_qq, tr("腾讯QQ", "騰訊QQ", "Tencent QQ"));
        STRINGS.put(R.id.settings_title_unionpay, tr("云闪付", "雲閃付", "Union Pay"));
        STRINGS.put(R.id.fingerprint_verification, tr("请验证指纹", "請驗證指紋", "Fingerprint verification"));
        STRINGS.put(R.id.qq_payview_fingerprint_title, tr("请验证指纹", "請驗證指紋", "Verify fingerprint"));
        STRINGS.put(R.id.qq_payview_password_switch_text, tr("使用密码", "使用密碼", "Password"));
        STRINGS.put(R.id.qq_payview_fingerprint_switch_text, tr("使用指纹", "使用指紋", "Fingerprint"));
        STRINGS.put(R.id.update_time, tr("更新日期", "更新日期", "Update time"));
        STRINGS.put(R.id.toast_checking_update, tr("正在检查更新", "正在檢查更新", "Checking"));
        STRINGS.put(R.id.toast_no_update, tr("已经是最新版本了", "暫無更新", "You already have the latest version"));
        STRINGS.put(R.id.toast_check_update_fail_net_err, tr("网络错误, 检查更新失败", "網絡錯誤, 檢查更新失敗", "Network error"));
        STRINGS.put(R.id.toast_fingerprint_not_match, tr("指纹识别失败", "指紋識別失敗", "Fingerprint NOT MATCH"));
        STRINGS.put(R.id.toast_fingerprint_retry_ended, tr("多次尝试错误，请使用密码输入", "多次嘗試錯誤，請使用密碼輸入", "Too many incorrect verification attempts, switch to password verification"));
        STRINGS.put(R.id.toast_fingerprint_failed, tr("指纹验证未完成，请使用密码输入", "指紋驗證未完成，請使用密碼輸入", "Fingerprint verification did not complete, enter your password"));
        STRINGS.put(R.id.toast_fingerprint_unlock_reboot, tr("系统限制，重启后必须验证密码后才能使用指纹验证", "系統限制，重啟後必須驗證密碼後才能使用指紋驗證", "Reboot and enable fingerprint verification with your PIN"));
        STRINGS.put(R.id.toast_fingerprint_not_enable, tr("系统指纹功能未启用", "系統指紋功能未啟用", "Fingerprint verification has been closed by system"));
        STRINGS.put(R.id.toast_fingerprint_temporary_disabled, tr("指纹支付已临时禁用1分钟", "指紋支付已臨時禁用1分鐘", "Fingerprint payment has been temporarily disabled for 1 minute"));
        STRINGS.put(R.id.toast_password_auto_enter_fail, tr("Oops.. 输入失败了. 请手动输入密码", "Oops.. 輸入失敗了. 請手動輸入密碼", "Oops... auto input failure, switch to manual input"));
        STRINGS.put(R.id.toast_need_qq_7_2_5, tr("您的QQ版本过低, 不支持指纹功能, 请升级至7.2.5以上的版本", "您的QQ版本過低, 不支持指紋功能, 請升級至7.2.5以上的版本", "Your QQ version is too low, does not support the fingerprint function, please upgrade to version 7.2.5 and above"));
        STRINGS.put(R.id.message_version_not_supported, tr("当前应用版本%s(%s)与模块版本%s不兼容，请反馈问题\uD83D\uDC1B并使用兼容的模块版本", "當前應用版本%s(%s)與模塊版本%s不兼容，請反饋問題\uD83D\uDC1B並使用兼容的模塊版本", "The current application version %s(%s) is incompatible with module version %s. Please report this issue \uD83D\uDC1B and use a compatible module version."));
    }

    public static String getString(int res) {
        String value = STRINGS.get(res);
        return value == null ? "" : value;
    }

    private static String tr(String ...c) {
        return c[sLang];
    }
}
