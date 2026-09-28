package com.mobplayer.tv.repository

import com.mobplayer.tv.models.RemoteActionEvent
import com.mobplayer.tv.models.RemoteIconType
import com.mobplayer.tv.viewmodel.ConnectionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ServerRepositoryTest {

    private lateinit var serverRepository: ServerRepository

    @Before
    fun setup() {
        serverRepository = ServerRepository()
        serverRepository.hideDialogs()
    }

    @Test
    fun `test showPin emits PinRequested event`() {
        val testPin = "1234"
        serverRepository.showPin(testPin)
        
        val event = serverRepository.connectionEvent.value
        assertTrue(event is ConnectionEvent.PinRequested)
        assertEquals(testPin, (event as ConnectionEvent.PinRequested).pin)
    }

    @Test
    fun `test requestPin generates and emits PinRequested event`() {
        serverRepository.onRequestNewPin = { "9876" }
        val generatedPin = serverRepository.requestPin()
        assertEquals("9876", generatedPin)

        val event = serverRepository.connectionEvent.value
        assertTrue(event is ConnectionEvent.PinRequested)
        assertEquals("9876", (event as ConnectionEvent.PinRequested).pin)
        serverRepository.onRequestNewPin = null
    }

    @Test
    fun `test promptConnectionConflict emits ConnectionConflict event`() {
        val deviceName = "TestDevice"
        serverRepository.promptConnectionConflict(deviceName) {}
        
        val event = serverRepository.connectionEvent.value
        assertTrue(event is ConnectionEvent.ConnectionConflict)
        assertEquals(deviceName, (event as ConnectionEvent.ConnectionConflict).deviceName)
    }

    @Test
    fun `test hideDialogs emits None event`() {
        serverRepository.showPin("1234")
        serverRepository.hideDialogs()
        
        val event = serverRepository.connectionEvent.value
        assertTrue(event is ConnectionEvent.None)
        assertTrue(serverRepository.isConnected.value)
    }

    @Test
    fun `test onClientConnected updates connection state`() {
        serverRepository.onClientConnected("Living Room Phone")
        assertTrue(serverRepository.isConnected.value)
        assertEquals("Living Room Phone", serverRepository.connectedDeviceName.value)
        assertTrue(serverRepository.connectionEvent.value is ConnectionEvent.None)
    }

    @Test
    fun `test onClientDisconnected resets connection state`() {
        serverRepository.onClientConnected("Living Room Phone")
        serverRepository.onClientDisconnected()
        assertFalse(serverRepository.isConnected.value)
        assertNull(serverRepository.connectedDeviceName.value)
    }

    @Test
    fun `test closeActiveSession invokes onCloseSession callback`() {
        var closed = false
        serverRepository.onCloseSession = { closed = true }
        serverRepository.closeActiveSession()
        assertTrue(closed)
        serverRepository.onCloseSession = null
    }

    @Test
    fun `test enterDemoMode configures demo controller`() {
        serverRepository.enterDemoMode()
        assertTrue(serverRepository.isConnected.value)
        assertEquals("Demo Controller", serverRepository.connectedDeviceName.value)
        assertTrue(serverRepository.connectionEvent.value is ConnectionEvent.None)
    }

    @Test
    fun `test setActiveMediaTitle updates title`() {
        serverRepository.setActiveMediaTitle("Cyberpunk 2099")
        assertEquals("Cyberpunk 2099", serverRepository.activeMediaTitle.value)
    }

    @Test
    fun `test openPlayer and closePlayer manage player state`() {
        serverRepository.openPlayer("Test Movie")
        assertTrue(serverRepository.isPlayerActive.value)
        assertEquals("Test Movie", serverRepository.activeMediaTitle.value)

        serverRepository.closePlayer()
        assertFalse(serverRepository.isPlayerActive.value)
        assertEquals("", serverRepository.activeMediaTitle.value)
    }

    @Test
    fun `test postRemoteAction updates remoteActionEvent`() {
        val action = RemoteActionEvent(
            action = "PAUSE",
            displayName = "Paused",
            details = "Playback paused",
            iconType = RemoteIconType.PAUSE
        )
        serverRepository.postRemoteAction(action)
        assertEquals(action, serverRepository.remoteActionEvent.value)
    }

    @Test
    fun `test injectKey invokes listener`() {
        var receivedKey = 0
        serverRepository.onInjectKeyEvent = { keyCode ->
            receivedKey = keyCode
        }
        serverRepository.injectKey(19)
        // Check key invocation (might be async on android but synchronous in unit test environment fallback)
        Thread.sleep(50)
        assertEquals(19, receivedKey)
        serverRepository.onInjectKeyEvent = null
    }
}
