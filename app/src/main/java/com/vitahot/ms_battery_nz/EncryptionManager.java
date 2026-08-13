package com.vitahot.ms_battery_nz;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class EncryptionManager {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final String TAG = "EncryptionManager";

    // Test flag - set to false to enable encryption, true to disable
    public static final boolean USE_UNENCRYPTED = false;

    private static final String ENCRYPTION_PREFIX = "ENCRYPTED:";

    /**
     * Encrypt data using AES
     */
    public static String encrypt(String data, SecretKey key) throws Exception {
        if (USE_UNENCRYPTED) {
            return data; // Return unencrypted for testing
        }

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] encryptedData = cipher.doFinal(data.getBytes());
        byte[] iv = cipher.getIV();

        // Combine IV + encrypted data and encode to Base64
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

        String encoded = Base64.encodeToString(combined, Base64.DEFAULT);
        return ENCRYPTION_PREFIX + encoded;
    }

    /**
     * Decrypt data using AES
     */
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        if (USE_UNENCRYPTED) {
            return encryptedData; // Return as-is for testing
        }

        // Check if data is encrypted
        if (!encryptedData.startsWith(ENCRYPTION_PREFIX)) {
            // Data is not encrypted, return as-is (backwards compatibility)
            return encryptedData;
        }

        String encoded = encryptedData.substring(ENCRYPTION_PREFIX.length());
        byte[] combined = Base64.decode(encoded, Base64.DEFAULT);

        // Extract IV and encrypted data
        byte[] iv = new byte[16]; // IV size for AES is always 16 bytes
        byte[] encData = new byte[combined.length - iv.length];

        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encData, 0, encData.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] decrypted = cipher.doFinal(encData);
        return new String(decrypted);
    }

    /**
     * Generate or retrieve encryption key
     */
    public static SecretKey getOrCreateKey(android.content.Context context) throws Exception {
        // In production, use Android Keystore for better security
        // For now, derive a key from device-specific data
        String keyMaterial = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
        );

        // Create a fixed-size key from the material
        byte[] decodedKey = keyMaterial.getBytes();
        byte[] keyBytes = new byte[32]; // 256 bits for AES-256

        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = decodedKey[i % decodedKey.length];
        }

        return new SecretKeySpec(keyBytes, 0, keyBytes.length, KEY_ALGORITHM);
    }
}