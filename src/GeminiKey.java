package com.codex.rootlessmacros;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class GeminiKey {
    private static final String ALIAS = "social_macro_gemini_v1";
    private final SharedPreferences storage;

    GeminiKey(Context context) {
        storage = context.getSharedPreferences("private_gemini", Context.MODE_PRIVATE);
    }

    boolean isConfigured() {
        return storage.contains("ciphertext") && storage.contains("iv");
    }

    void save(String value) throws Exception {
        KeyStore store = keyStore();
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, (SecretKey) store.getKey(ALIAS, null));
        String ciphertext = Base64.encodeToString(
                cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        boolean saved = storage.edit()
                .putString("ciphertext", ciphertext)
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .commit();
        if (!saved) throw new IllegalStateException("Could not save key");
    }

    String read() throws Exception {
        if (!isConfigured()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, (SecretKey) keyStore().getKey(ALIAS, null),
                new GCMParameterSpec(128, Base64.decode(storage.getString("iv", ""), Base64.NO_WRAP)));
        byte[] value = Base64.decode(storage.getString("ciphertext", ""), Base64.NO_WRAP);
        return new String(cipher.doFinal(value), StandardCharsets.UTF_8);
    }

    private KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
}
