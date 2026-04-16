package com.xiaomo.hermes.config

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 BuiltInKeyProvider 能正确解密内置 OpenRouter Key
 */
@RunWith(AndroidJUnit4::class)
class BuiltInKeyProviderTest {

    @Test
    fun getKey_returnsNonNullValidKey() {
        val key = BuiltInKeyProvider.getKey()
        assertNotNull("内置 Key 不应为 null", key)
        assertTrue("内置 Key 应以 sk-or- 开头", key!!.startsWith("sk-or-"))
    }

    @Test
    fun encryptAndDecrypt_roundtrip() {
        val testKey = "sk-or-v1-test-roundtrip-key-12345"
        val encrypted = BuiltInKeyProvider.encrypt(testKey)

        // 加密后的结果不等于原文
        assertNotEquals(testKey, encrypted)

        // 解密后得到原文
        val data = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(
            "HermesKeyProviderSecret!".toByteArray(Charsets.UTF_8), "AES"
        )
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decrypted = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        assertEquals("加解密 roundtrip 应一致", testKey, decrypted)
    }
}
