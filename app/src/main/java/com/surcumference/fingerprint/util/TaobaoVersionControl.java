package com.surcumference.fingerprint.util;

public class TaobaoVersionControl {

    public static DigitPasswordKeyPad getDigitPasswordKeyPad(int taobaoVersion) {
        //9.20.0
        if (taobaoVersion >= 323) {
            return new DigitPasswordKeyPad("com.taobao.taobao",
                    new String[]{"au_num_1"},
                    new String[]{"au_num_2"},
                    new String[]{"au_num_3"},
                    new String[]{"au_num_4"},
                    new String[]{"au_num_5"},
                    new String[]{"au_num_6"},
                    new String[]{"au_num_7"},
                    new String[]{"au_num_8"},
                    new String[]{"au_num_9"},
                    new String[]{"au_num_0"});
        } else {
            return new DigitPasswordKeyPad("com.taobao.taobao",
                    new String[]{"key_num_1"},
                    new String[]{"key_num_2"},
                    new String[]{"key_num_3"},
                    new String[]{"key_num_4", "key_4"},
                    new String[]{"key_num_5"},
                    new String[]{"key_num_6"},
                    new String[]{"key_num_7"},
                    new String[]{"key_num_8"},
                    new String[]{"key_num_9"},
                    new String[]{"key_num_0"});
        }
    }

    public static class DigitPasswordKeyPad extends com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo {
        public DigitPasswordKeyPad(String packageName, String[] one, String[] two, String[] three,
                                   String[] four, String[] five, String[] six, String[] seven,
                                   String[] eight, String[] nine, String[] zero) {
            super(packageName, one, two, three, four, five, six, seven, eight, nine, zero);
        }
    }
}
