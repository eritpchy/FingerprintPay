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
        STRINGS.put(R.id.settings_title_help_wechat, tr("微信指纹", "微信指纹", "WeChat fingerprint pay"));
        STRINGS.put(R.id.settings_title_help_alipay, tr("支付宝指纹", "支付寶指纹", "Alipay fingerprint pay"));
        STRINGS.put(R.id.settings_title_help_taobao, tr("淘宝指纹", "淘宝指纹", "Taobao fingerprint pay"));
        STRINGS.put(R.id.settings_title_help_qq, tr("QQ指纹", "QQ指纹", "QQ fingerprint pay"));
        STRINGS.put(R.id.settings_title_help_unionpay, tr("云闪付指纹", "雲閃付指纹", "UnionPay fingerprint pay"));
        STRINGS.put(R.id.settings_title_qq_group, tr("QQ交流群", "QQ交流群", "QQ Group"));
        STRINGS.put(R.id.settings_title_help_faq, tr("常见问题", "常見問題", "FAQ"));
        STRINGS.put(R.id.settings_title_license, tr("许可协议", "許可協議", "License"));
        STRINGS.put(R.id.settings_title_checkupdate, tr("检查更新", "檢查更新", "Check for update"));
        STRINGS.put(R.id.settings_title_webside, tr("项目主页", "項目主頁", "Project homepage"));
        STRINGS.put(R.id.settings_title_version, tr("当前版本", "当前版本", "Version"));
        STRINGS.put(R.id.settings_sub_title_help_wechat, tr("查看使用教程", "查看使用教程", "Tutorial"));
        STRINGS.put(R.id.settings_sub_title_help_alipay, tr("查看使用教程", "查看使用教程", "Tutorial"));
        STRINGS.put(R.id.settings_sub_title_help_taobao, tr("查看使用教程", "查看使用教程", "Tutorial"));
        STRINGS.put(R.id.settings_sub_title_help_qq, tr("查看使用教程", "查看使用教程", "Tutorial"));
        STRINGS.put(R.id.settings_sub_title_help_unionpay, tr("查看使用教程", "查看使用教程", "Tutorial"));
        STRINGS.put(R.id.settings_sub_title_qq_group, tr("665167891", "665167891", "665167891"));
        STRINGS.put(R.id.settings_sub_title_help_faq, tr("出现问题请看这里", "出現問題請看這裏", "Having a problem?"));
        STRINGS.put(R.id.settings_sub_title_license, tr("查看许可协议", "查看許可協議", "Check the License Agreement"));
        STRINGS.put(R.id.settings_sub_title_checkupdate, tr("点击检查软件更新", "點擊檢查软件更新", "Press to begin"));
        STRINGS.put(R.id.settings_sub_title_webside, tr("访问項目主页", "訪問項目主頁", "Home page"));
        STRINGS.put(R.id.found_new_version, tr("发现新版本", "發現新版本 ", "New version: "));
        STRINGS.put(R.id.skip_this_version, tr("跳过这个版本", "跳過這個版本 ", "Skip"));
        STRINGS.put(R.id.cancel, tr("取消", "取消", "Cancel"));
        STRINGS.put(R.id.goto_update_page, tr("前往更新页", "前往更新頁 ", "Update page"));
        STRINGS.put(R.id.goto_update_page_mirror, tr("(国内镜像)", "(國內鏡像) ", "(Mirror)"));
        STRINGS.put(R.id.update_now, tr("立即更新", "立即更新 ", "UPDATE"));
        STRINGS.put(R.id.update_success_note, tr("安装更新成功, 请重启手机使插件生效", "安裝更新成功, 請重啟手機使插件生效",
                        "Update Successfully, please restart the phone to enable the plug-in"));
        STRINGS.put(R.id.downloading, tr("下载中", "下載中", "Downloading"));
        STRINGS.put(R.id.download_title_failed, tr("下载出错", "下載出錯", "Download failed"));
        STRINGS.put(R.id.download_complete_file_size_miss_match, tr("文件大小不匹配, 当前 %d, 应为 %d", "文件大小不匹配, 當前 %d, 應為 %d", "File size miss match, got %d, expected %d"));
        STRINGS.put(R.id.ok, tr("确定", "确定", "OK"));
        STRINGS.put(R.id.settings_title_taobao, tr("淘宝", "淘寶", "Taobao"));
        STRINGS.put(R.id.settings_title_alipay, tr("支付宝", "支付寶", "Alipay"));
        STRINGS.put(R.id.settings_title_wechat, tr("微信", "微信", "WeChat"));
        STRINGS.put(R.id.settings_title_qq, tr("腾讯QQ", "騰訊QQ", "Tencent QQ"));
        STRINGS.put(R.id.settings_title_unionpay, tr("云闪付", "雲閃付", "Union Pay"));
        STRINGS.put(R.id.enter_password, tr("使用密码", "使用密碼", "Enter password"));
        STRINGS.put(R.id.settings_title_switch, tr("启用", "啟用", "Enable"));
        STRINGS.put(R.id.settings_title_password, tr("支付密码", "支付密碼", "Payment Password"));
        STRINGS.put(R.id.settings_title_no_fingerprint_icon, tr("显示指纹图标", "顯示指紋圖標", "Fingerprint Icon"));
        STRINGS.put(R.id.settings_title_donate, tr("赞助我", "贊助我", "Donate me"));
        STRINGS.put(R.id.settings_title_advance, tr("通用设置", "一般选项", "General"));
        STRINGS.put(R.id.settings_title_use_biometric_api, tr("使用 Biometric Api", "使用 Biometric Api", "Use Biometric Api"));
        STRINGS.put(R.id.settings_title_volume_down_fingerprint_temporary_disable, tr("音量\uD83D\uDC47切换密码输入", "音量\uD83D\uDC47禁用切換密碼輸入", "Vol- for password input"));
        STRINGS.put(R.id.settings_title_start_logcat, tr("开始记录日志", "開始記錄日誌", "Start logging"));
        STRINGS.put(R.id.settings_title_stop_logcat, tr("停止记录日志", "停止記錄日誌", "Stop logging"));
        STRINGS.put(R.id.settings_sub_title_switch_alipay, tr("启用支付宝指纹支付", "啟用支付宝指紋支付", "Enable fingerprint payment for Alipay"));
        STRINGS.put(R.id.settings_sub_title_switch_wechat, tr("启用微信指纹支付", "啟用微信指紋支付", "Enable fingerprint payment for WeChat"));
        STRINGS.put(R.id.settings_sub_title_switch_qq, tr("启用QQ指纹支付", "啟用QQ指紋支付", "Enable fingerprint payment for QQ"));
        STRINGS.put(R.id.settings_sub_title_switch_unionpay, tr("启用云闪付指纹支付", "啟用雲閃付指紋支付", "Enable fingerprint payment for Union Pay"));
        STRINGS.put(R.id.settings_sub_title_password_alipay, tr("请输入支付宝的支付密码, 密码会加密后保存, 请放心", "請輸入支付宝的支付密碼, 密碼會加密后保存, 請放心", "Please enter your Payment password"));
        STRINGS.put(R.id.settings_sub_title_password_wechat, tr("请输入微信的支付密码, 密码会加密后保存, 请放心", "請輸入微信的支付密碼, 密碼會加密后保存, 請放心", "Please enter your Payment password"));
        STRINGS.put(R.id.settings_sub_title_no_fingerprint_icon, tr("非屏下指纹手机需要显示指纹图标", "非屏下指紋手機需要顯示指紋圖標", "Non IN-DISPLAY fingerprint phone need to display the fingerprint icon"));
        STRINGS.put(R.id.settings_sub_title_password_qq, tr("请输入QQ的支付密码, 密码会加密后保存, 请放心", "請輸入QQ的支付密碼, 密碼會加密后保存, 請放心", "Please enter your Payment password"));
        STRINGS.put(R.id.settings_sub_title_password_unionpay, tr("请输入云闪付的支付密码, 密码会加密后保存, 请放心", "請輸入雲閃付的支付密碼, 密碼會加密后保存, 請放心", "Please enter your Payment password"));
        STRINGS.put(R.id.settings_sub_title_donate, tr("如果您觉得本软件好用, 欢迎赞助, 多少都是心意", "如果您覺得本軟件好用, 歡迎贊助, 多少都是心意", "Donate me, If you like this project"));
        STRINGS.put(R.id.settings_sub_title_advance, tr("指纹图标、Biometric Api...", "指紋圖標、Biometric Api...", "Fingerprint icon, Biometric API..."));
        STRINGS.put(R.id.settings_sub_title_update_modules_same_time, tr("将同时升级以下模块", "將同時升級以下模塊", "The following modules will be upgraded at the same time"));
        STRINGS.put(R.id.settings_sub_title_use_biometric_api, tr("实验性, 仅 Android 9+ 可用", "實驗性, 僅 Android 9+ 可用", "Experimental, available only on Android 9+"));
        STRINGS.put(R.id.settings_sub_title_volume_down_fingerprint_temporary_disable, tr("按下按键会临时禁用指纹支付1分钟(仅应用内认证有效)", "按下按鍵會臨時禁用指紋支付1分鐘(僅應用內認證有效)", "Pressing the button will temporarily disable fingerprint payment for 1 minute (only valid for in-app authentication)"));
        STRINGS.put(R.id.settings_sub_title_start_logcat, tr("开始 --> 你的表演 --> 停止 --> 发送给开发者", "開始 --> 你的表演 --> 停止 --> 發送給開發者", "Start --> Payment operation --> Stop --> Send to developer"));
        STRINGS.put(R.id.settings_sub_title_stop_logcat, tr("开始 --> 你的表演 --> 停止 --> 发送给开发者", "開始 --> 你的表演 --> 停止 --> 發送給開發者", "Start --> Payment operation --> Stop --> Send to developer"));
        STRINGS.put(R.id.fingerprint_verification, tr("请验证指纹", "請驗證指紋", "Fingerprint verification"));
        STRINGS.put(R.id.wechat_general, tr("通用", "一般", "General"));
        STRINGS.put(R.id.app_settings_name, tr("指纹设置", "指紋設置", "Fingerprint"));
        STRINGS.put(R.id.wechat_payview_fingerprint_title, tr("　请验证指纹　", "　請驗證指紋　", "　Verify fingerprint　"));
        STRINGS.put(R.id.wechat_payview_password_title, tr("请输入支付密码", "請輸入付款密碼", "Enter payment password"));
        STRINGS.put(R.id.wechat_payview_password_switch_text, tr("使用密码", "使用密碼", "Password"));
        STRINGS.put(R.id.wechat_payview_fingerprint_switch_text, tr("使用指纹", "使用指紋", "Fingerprint"));
        STRINGS.put(R.id.qq_payview_fingerprint_title, tr("请验证指纹", "請驗證指紋", "Verify fingerprint"));
        STRINGS.put(R.id.qq_payview_password_title, tr("请输入支付密码", "請輸入付款密碼", "Enter payment password"));
        STRINGS.put(R.id.qq_payview_password_switch_text, tr("使用密码", "使用密碼", "Password"));
        STRINGS.put(R.id.qq_payview_fingerprint_switch_text, tr("使用指纹", "使用指紋", "Fingerprint"));
        STRINGS.put(R.id.disagree, tr("不同意", "不同意", "Disagree"));
        STRINGS.put(R.id.agree, tr("同意", "同意", "I agree"));
        STRINGS.put(R.id.update_time, tr("更新日期", "更新日期", "Update time"));
        STRINGS.put(R.id.update_no_root, tr("当前应用未获取到ROOT权限, 无法进行自动更新, 请前往更新页面手动获取更新", "當前應用未獲取到ROOT權限, 無法進行自動更新, 請前往更新頁面手動獲取更新", "Update failed, the current application cannot obtain root permission, please go to update page to manually obtain the updates"));
        STRINGS.put(R.id.update_at_least_select_one, tr("请至少少选择一项", "請至少少選擇一項", "Please select at least one item"));
        STRINGS.put(R.id.update_file_corrupted, tr("文件损坏了, 请重试", "文件損壞了, 請重試", "File is corrupted, please try again"));
        STRINGS.put(R.id.update_file_missing, tr("更新文件丢失, 请前往更新页面手动获取更新", "更新文件丟失, 請前往更新頁面手動獲取更新", "Update file is missing, please go to update page to manually obtain the updates"));
        STRINGS.put(R.id.update_installation_failed, tr("更新安装失败, 错误码:", "更新安裝失敗, 錯誤碼:", "Update installation failed, error code:"));
        STRINGS.put(R.id.toast_give_me_star, tr("如果您拥有Github账户, 别忘了给我的项目+个Star噢", "如果您擁有Github賬戶, 別忘了給我的項目+個Star噢", "Give me a star, if you like this project"));
        STRINGS.put(R.id.toast_checking_update, tr("正在检查更新", "正在檢查更新", "Checking"));
        STRINGS.put(R.id.toast_no_update, tr("已经是最新版本了", "暫無更新", "You already have the latest version"));
        STRINGS.put(R.id.toast_check_update_fail_net_err, tr("网络错误, 检查更新失败", "網絡錯誤, 檢查更新失敗", "Network error"));
        STRINGS.put(R.id.toast_fingerprint_not_match, tr("指纹识别失败", "指紋識別失敗", "Fingerprint NOT MATCH"));
        STRINGS.put(R.id.toast_fingerprint_retry_ended, tr("多次尝试错误，请使用密码输入", "多次嘗試錯誤，請使用密碼輸入", "Too many incorrect verification attempts, switch to password verification"));
        STRINGS.put(R.id.toast_fingerprint_unlock_reboot, tr("系统限制，重启后必须验证密码后才能使用指纹验证", "系統限制，重啟後必須驗證密碼後才能使用指紋驗證", "Reboot and enable fingerprint verification with your PIN"));
        STRINGS.put(R.id.toast_fingerprint_not_enable, tr("系统指纹功能未启用", "系統指紋功能未啟用", "Fingerprint verification has been closed by system"));
        STRINGS.put(R.id.toast_fingerprint_password_enc_success, tr("支付密码加密成功", "支付密碼加密成功", "Payment password encryption successful"));
        STRINGS.put(R.id.toast_fingerprint_password_dec_failed, tr("支付密码解密失败, 请重新设定支付密码", "支付密码解密失败, 请重新设定支付密码", "Decryption of payment password failed, please reset the payment password"));
        STRINGS.put(R.id.toast_fingerprint_operation_cancel, tr("操作已取消", "操作已取消", "The operation has been canceled"));
        STRINGS.put(R.id.toast_fingerprint_temporary_disabled, tr("指纹支付已临时禁用1分钟", "指紋支付已臨時禁用1分鐘", "Fingerprint payment has been temporarily disabled for 1 minute"));
        STRINGS.put(R.id.toast_password_not_set_alipay, tr("未设定支付密码，请前往設置->指紋設置中设定支付宝的支付密码", "未設定支付密碼，請前往設置 -> 指紋設置中設定支付寶的支付密碼", "Payment password not set, please goto Settings -> Fingerprint to enter you payment password"));
        STRINGS.put(R.id.toast_password_not_set_taobao, tr("未设定支付密码，请前往設置->指紋設置中设定淘宝的支付密码", "未設定支付密碼，請前往設置 -> 指紋設置中設定淘寶的支付密碼", "Payment password not set, please goto Settings -> Fingerprint to enter you payment password"));
        STRINGS.put(R.id.toast_password_not_set_wechat, tr("未设定支付密码，请前往設置->指紋設置中设定微信的支付密码", "未設定支付密碼，請前往設置 -> 指紋設置中設定微信的支付密碼", "Payment password not set, please goto Settings -> Fingerprint to enter you payment password"));
        STRINGS.put(R.id.toast_password_not_set_qq, tr("未设定支付密码，请前往設置->指紋設置中设定QQ的支付密码", "未設定支付密碼，請前往設置 -> 指紋設置中設定QQ的支付密碼", "Payment password not set, please goto Settings -> Fingerprint to enter you payment password"));
        STRINGS.put(R.id.toast_password_not_set_generic, tr("未设定支付密码，请前往設置->指紋設置中设定支付密码", "未設定支付密碼，請前往設置 -> 指紋設置中設定支付密碼", "Payment password not set, please goto Settings -> Fingerprint to enter you payment password"));
        STRINGS.put(R.id.toast_password_not_set_switch_on_failed, tr("启用失败, 请先设定支付密码", "啟用失敗, 請先設定支付密碼", "Enabled failed, please set a payment password first"));
        STRINGS.put(R.id.toast_password_auto_enter_fail, tr("Oops.. 输入失败了. 请手动输入密码", "Oops.. 輸入失敗了. 請手動輸入密碼", "Oops... auto input failure, switch to manual input"));
        STRINGS.put(R.id.toast_goto_donate_page_fail_alipay, tr("调用支付宝捐赠页失败, 您可以手动转账捐赠哦, 账号: " + Constant.AUTHOR_ALIPAY, "調用支付寶捐贈頁失敗, 您可以手動轉賬捐贈哦, 帳號: " + Constant.AUTHOR_ALIPAY, "Can't jump to Alipay donate page, You can do it manually by transfer to account: " + Constant.AUTHOR_ALIPAY));
        STRINGS.put(R.id.toast_goto_donate_page_fail_wechat, tr("调用微信捐赠页失败, 您可以手动转账捐赠哦, 账号: " + Constant.AUTHOR_WECHAT, "調用微信捐贈頁失敗, 您可以手動轉賬捐贈哦, 帳號: " + Constant.AUTHOR_WECHAT, "Can't jump to WeChat donate page, You can do it manually by transfer to account: " + Constant.AUTHOR_WECHAT));
        STRINGS.put(R.id.toast_goto_donate_page_fail_qq, tr("调用QQ捐赠页失败, 您可以手动转账捐赠哦, 账号: " + Constant.AUTHOR_QQ, "調用QQ捐贈頁失敗, 您可以手動轉賬捐贈哦, 帳號: " + Constant.AUTHOR_QQ, "Can't jump to QQ donate page, You can do it manually by transfer to account: " + Constant.AUTHOR_QQ));
        STRINGS.put(R.id.toast_need_qq_7_2_5, tr("您的QQ版本过低, 不支持指纹功能, 请升级至7.2.5以上的版本", "您的QQ版本過低, 不支持指紋功能, 請升級至7.2.5以上的版本", "Your QQ version is too low, does not support the fingerprint function, please upgrade to version 7.2.5 and above"));
        STRINGS.put(R.id.toast_start_logging, tr("请开始你的表演, 日志已开始记录\n日志路径: %s", "請開始你的表演, 日誌已開始記錄\n日誌路徑: %s", "Star logging\nlog path: %s"));
        STRINGS.put(R.id.toast_stop_logging, tr("表演结束, 请将日志文件分享给开发者\n日志路径: %s", "表演结束, 请将日志文件分享给开发者\n日誌路徑: %s", "Stop logging\nlog path: %s"));
        STRINGS.put(R.id.toast_update_available, tr("请前往Magisk框架更新模块, 或进入Github(🪜)官网下载模块刷入", "請前往Magisk框架更新模組，或進入Github(🪜)官网下载模組刷入", "Please go to the Magisk framework to update the module, or visit the Github website to download and flash the module."));
        STRINGS.put(R.id.message_version_not_supported, tr("当前应用版本%s(%s)与模块版本%s不兼容，请反馈问题\uD83D\uDC1B并使用兼容的模块版本", "當前應用版本%s(%s)與模塊版本%s不兼容，請反饋問題\uD83D\uDC1B並使用兼容的模塊版本", "The current application version %s(%s) is incompatible with module version %s. Please report this issue \uD83D\uDC1B and use a compatible module version."));
        STRINGS.put(R.id.template, tr("", "", ""));
    }

    public static String getString(int res) {
        String value = STRINGS.get(res);
        return value == null ? "" : value;
    }

    private static String tr(String ...c) {
        return c[sLang];
    }
}
