package com.mobplayer.tv.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthManagerTest {

    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        authManager = AuthManager(appContext)
        authManager.clearToken() // Reset before each test
    }

    @Test
    fun testGeneratePin_isFourDigits() {
        val pin = authManager.generatePin()
        assertEquals(4, pin.length)
        assertTrue(pin.toInt() in 1000..9999)
    }

    @Test
    fun testVerifyPin() {
        val pin = authManager.generatePin()
        assertTrue(authManager.verifyPin(pin, pin))
        assertFalse(authManager.verifyPin(pin, "0000"))
    }

    @Test
    fun testTokenGenerationAndValidation() {
        assertFalse(authManager.isValidToken("random_fake_token"))
        
        val token = authManager.generateAndSaveToken()
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        
        assertTrue(authManager.isValidToken(token))
    }

    @Test
    fun testClearToken() {
        val token = authManager.generateAndSaveToken()
        assertTrue(authManager.isValidToken(token))
        
        authManager.clearToken()
        assertFalse(authManager.isValidToken(token))
    }
}
