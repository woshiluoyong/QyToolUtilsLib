package com.qy.encryptor;

public class QyEncryptor {
    static {
        System.loadLibrary("qyEncryptor");
    }

    //AES加密, CBC, PKCS5Padding
    public static native String methodForEn(String str);

    //AES底层解密, CBC, PKCS5Padding
    public static native String methodForDe(String str);
}
