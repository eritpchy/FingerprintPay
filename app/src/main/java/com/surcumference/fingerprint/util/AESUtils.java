package com.surcumference.fingerprint.util;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.surcumference.fingerprint.util.log.L;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

///* AES对称加密解密类 */ 
public class AESUtils implements XBiometricIdentify.ICryptoHandler {

    public static final AESUtils INSTANCE = new AESUtils();
    private static final String CipherMode = "AES/ECB/PKCS5Padding";

// /** 创建密钥 **/
    private static SecretKeySpec createKey(String password) {
        byte[] data = null;
        if (password == null) {
            password = "";
        }
        StringBuffer sb = new StringBuffer(32);
        sb.append(password);
        while (sb.length() < 32) {
            sb.append("0");
        }
        if (sb.length() > 32) {
            sb.setLength(32);
        }

        try {
            data = sb.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return new SecretKeySpec(data, "AES");
    }

    public static byte[] encrypt(Cipher cipher, byte[] content) {
        try {
            return cipher.doFinal(content);
        } catch (Exception e) {
            L.e(e);
        }
        return null;
    }

        // /** 加密字节数据 **/
    public static byte[] encrypt(byte[] content, String password) {
        try {
            SecretKeySpec key = createKey(password);
            System.out.println(key);
            Cipher cipher = Cipher.getInstance(CipherMode);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return encrypt(cipher, content);
        } catch (Exception e) {
            L.e(e);
        }
        return null;
    }


    @Override
    public String encrypt(@NonNull Cipher cipher, @NonNull String content) {
        if (cipher == null) {
            throw new IllegalArgumentException("cipher must not null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not null");
        }
        return AESUtils.byte2hex(encrypt(cipher, content.getBytes(StandardCharsets.UTF_8)));
    }

    // /** 加密(结果为16进制字符串) **/
    public static String encrypt(String content, String password) {
        byte[] data = null;
        try {
            data = content.getBytes("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        data = encrypt(data, password);
        return byte2hex(data);
    }

    public static byte[] decrypt(Cipher cipher, byte[] content) {
        try {
            return cipher.doFinal(content);
        } catch (Exception e) {
            L.e(e);
        }
        return null;
    }

    // /** 解密字节数组 **/
    public static byte[] decrypt(byte[] content, String password) {
        try {
            SecretKeySpec key = createKey(password);
            Cipher cipher = Cipher.getInstance(CipherMode);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return decrypt(cipher, content);
        } catch (Exception e) {
            L.e(e);
        }
        return null;
    }

    @Nullable
    @Override
    public String decrypt(@NonNull Cipher cipher, @NonNull String content) {
        if (cipher == null) {
            throw new IllegalArgumentException("cipher must not null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not null");
        }
        byte[] data = decrypt(cipher, AESUtils.hex2byte(content));
        if (data == null) {
            return null;
        }
        return new String(data);
    }

    // /** 解密16进制的字符串为字符串 **/
    public static String decrypt(String content, String password) {
        byte[] data = null;
        try {
            data = hex2byte(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
        data = decrypt(data, password);
        if (data == null)
            return null;
        String result = null;
        try {
            result = new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    // /** 字节数组转成16进制字符串 **/
    public static String byte2hex(byte[] b) { // 一个字节的数，
        if (b == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer(b.length * 2);
        String tmp = "";
        for (int n = 0; n < b.length; n++) {
            // 整数转成十六进制表示
            tmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
            if (tmp.length() == 1) {
                sb.append("0");
            }
            sb.append(tmp);
        }
        return sb.toString().toUpperCase(); // 转成大写
    }

    // /** 将hex字符串转换成字节数组 **/
    public static byte[] hex2byte(String inputString) {
        if (inputString == null || inputString.length() < 2) {
            return new byte[0];
        }
        inputString = inputString.toLowerCase();
        int l = inputString.length() / 2;
        byte[] result = new byte[l];
        for (int i = 0; i < l; ++i) {
            String tmp = inputString.substring(2 * i, 2 * i + 2);
            result[i] = (byte) (Integer.parseInt(tmp, 16) & 0xFF);
        }
        return result;
    }
}