package com.surcumference.fingerprint.util.paydialog;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.util.Tools;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.log.L;

import java.util.ArrayList;
import java.util.List;

public class WeChatPayDialog {

        public ViewGroup passwordLayout;
        public EditText inputEditText;
        public List<View> keyboardViews = new ArrayList<>();
        @Nullable
        public static WeChatPayDialog findFrom(int versionCode, ViewGroup rootView) {
            try {
                WeChatPayDialog payDialog = new WeChatPayDialog();

                List<View> childViews = new ArrayList<>();
                com.surcumference.fingerprint.util.ViewUtils.getChildViews(rootView, childViews);
                for (View view : childViews) {
                    if (view == null) {
                        continue;
                    }
                    if (view.getClass().getName().endsWith(".EditHintPasswdView")) {
                        L.d("mPasswordLayout:" + view);
                        if (view instanceof ViewGroup) {
                            payDialog.passwordLayout = (ViewGroup)view;
                        }
                    } else if (view.getClass().getName().endsWith(".TenpaySecureEditText")) {
                        L.d("密码输入框:" + ViewUtils.getViewInfo(view)
                                + " shown: " + ViewUtils.isShown(view)
                                + " shownInScreen: " + ViewUtils.isShownInScreen(view)
                                + " view instanceof EditText: " + (view instanceof EditText)
                        );
                        //charge amount EditText
                        if (versionCode >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_30)
                            if (view.getTag() != null) {
                                continue;
                            }
                        if (view instanceof EditText) {
                            if (payDialog.inputEditText != null) {
                                if (!ViewUtils.isShownInScreen(view)) {
                                    continue;
                                }
                            }
                            payDialog.inputEditText = (EditText)view;
                        }
                    } else if ((view.getClass().getName().endsWith(".MyKeyboardWindow")
                            || view.getClass().getName().endsWith(".HkWxKeyboardWindow")
                            || view.getClass().getName().endsWith(".MiniAppKeyboardWindow"))) {
                        L.d("密码键盘:" + ViewUtils.getViewInfo(view)
                                + " shown: " + ViewUtils.isShown(view)
                                + " shownInScreen: " + ViewUtils.isShownInScreen(view)
                                + " attachToParent: " + (view.getParent() != null));
                        if (view.getParent() != null) {
                            payDialog.keyboardViews.add((View)view.getParent());
                        }
                    }
                }

                if (payDialog.passwordLayout == null) {
                    Tools.doUnSupportVersionUpload(rootView.getContext(), "[WeChat passwordLayout NOT FOUND]  " + com.surcumference.fingerprint.util.ViewUtils.viewsDesc(childViews));
                    return null;
                }

                if (payDialog.inputEditText == null) {
                    Tools.doUnSupportVersionUpload(rootView.getContext(), "[WeChat inputEditText NOT FOUND]  " + com.surcumference.fingerprint.util.ViewUtils.viewsDesc(childViews));
                    return null;
                }

                if (payDialog.keyboardViews.size() == 0) {
                    Tools.doUnSupportVersionUpload(rootView.getContext(), "[WeChat keyboardView NOT FOUND]  " + com.surcumference.fingerprint.util.ViewUtils.viewsDesc(childViews));
                    return null;
                }

                return payDialog;
            } catch (Exception e) {
                L.e(e);
            }
            return null;
        }

        @Override
        public String toString() {
            return "PayDialog{" +
                    "passwordLayout=" + passwordLayout +
                    ", inputEditText=" + inputEditText +
                    ", keyboardView=" + keyboardViews +
                    '}';
        }
    }