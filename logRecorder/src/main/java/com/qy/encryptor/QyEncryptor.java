package com.qy.encryptor;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class QyEncryptor {

    // Used to load the library on application startup.
    static {
        System.loadLibrary("qyEncryptor");
    }

    //AES加密, CBC, PKCS5Padding
    public static native String methodForEn(String str);

    //AES底层解密, CBC, PKCS5Padding
    public static native String methodForDe(String str);

    //AES上层解密, CBC, PKCS5Padding
    public static String execDenCrypt(String encryptStr, String keyStr, String ivStr){
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyStr.getBytes(), "AES"),
                    new IvParameterSpec(ivStr.getBytes()));
            byte[] result = cipher.doFinal(parseHexStr2Byte(encryptStr));
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr.length() < 1)return null;
        byte[] result = new byte[hexStr.length() / 2];
        for (int i = 0; i < hexStr.length() / 2; i++) {
            int high = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 1), 16);
            int low = Integer.parseInt(hexStr.substring(i * 2 + 1, i * 2 + 2), 16);
            result[i] = (byte)(high * 16 + low);
        }//end of for
        return result;
    }
}
