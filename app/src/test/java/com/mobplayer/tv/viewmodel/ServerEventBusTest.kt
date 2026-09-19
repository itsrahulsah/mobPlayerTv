package com.mobplayer.tv.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerEventBusTest {

    @Before
    fun setup() {
        ServerEventBus.hideDialogs()
    }

    @Test
    fun `test showPin emits PinRequested event`() {
        val testPin = "1234"
        ServerEventBus.showPin(testPin)
        
        val event = ServerEventBus.connectionEvent.value
        assertTrue(event is ConnectionEvent.PinRequested)
        assertEquals(testPin, (event as ConnectionEvent.PinRequested).pin)
    }

    @Test
    fun `test requestPin generates and emits PinRequested event`() {
        ServerEventBus.onRequestNewPin = { "9876" }
        val generatedPin = ServerEventBus.requestPin()
        assertEquals("9876", generatedPin)

        val event = ServerEventBus.connectionEvent.value
        assertTrue(event is ConnectionEvent.PinRequested)
        assertEquals("9876", (event as ConnectionEvent.PinRequested).pin)
        ServerEventBus.onRequestNewPin = null
    }

    @Test
    fun `test promptConnectionConflict emits ConnectionConflict event`() {
        val deviceName = "TestDevice"
        ServerEventBus.promptConnectionConflict(deviceName) {}
        
        val event = ServerEventBus.connectionEvent.value
        assertTrue(event is ConnectionEvent.ConnectionConflict)
        assertEquals(deviceName, (event as ConnectionEvent.ConnectionConflict).deviceName)
    }

    @Test
    fun `test hideDialogs emits None event`() {
        ServerEventBus.showPin("1234")
        ServerEventBus.hideDialogs()
        
        val event = ServerEventBus.connectionEvent.value
        assertTrue(event is ConnectionEvent.None)
        assertTrue(ServerEventBus.isConnected.value)
    }

    @Test
    fun `test onClientConnected updates connection state`() {
        ServerEventBus.onClientConnected("Living Room Phone")
        assertTrue(ServerEventBus.isConnected.value)
        assertEquals("Living Room Phone", ServerEventBus.connectedDeviceName.value)
        assertTrue(ServerEventBus.connectionEvent.value is ConnectionEvent.None)
    }

    @Test
    fun `test onClientDisconnected resets connection state`() {
        ServerEventBus.onClientConnected("Living Room Phone")
        ServerEventBus.onClientDisconnected()
        org.junit.Assert.assertFalse(ServerEventBus.isConnected.value)
        org.junit.Assert.assertNull(ServerEventBus.connectedDeviceName.value)
    }

    @Test
    fun `test enterDemoMode configures demo controller`() {
        ServerEventBus.enterDemoMode()
        assertTrue(ServerEventBus.isConnected.value)
        assertEquals("Demo Controller", ServerEventBus.connectedDeviceName.value)
        assertTrue(ServerEventBus.connectionEvent.value is ConnectionEvent.None)
    }

    @Test
    fun `test setActiveMediaTitle updates title`() {
        ServerEventBus.setActiveMediaTitle("Cyberpunk 2099")
        assertEquals("Cyberpunk 2099", ServerEventBus.activeMediaTitle.value)
    }

    @Test
    fun `test openPlayer and closePlayer manage player state`() {
        ServerEventBus.openPlayer("Test Movie")
        assertTrue(ServerEventBus.isPlayerActive.value)
        assertEquals("Test Movie", ServerEventBus.activeMediaTitle.value)

        ServerEventBus.closePlayer()
        org.junit.Assert.assertFalse(ServerEventBus.isPlayerActive.value)
        assertEquals("", ServerEventBus.activeMediaTitle.value)
    }

    @Test
    fun `test postRemoteAction updates remoteActionEvent`() {
        val action = com.mobplayer.tv.models.RemoteActionEvent(
            action = "PAUSE",
            displayName = "Paused",
            details = "Playback paused",
            iconType = com.mobplayer.tv.models.RemoteIconType.PAUSE
        )
        ServerEventBus.postRemoteAction(action)
        assertEquals(action, ServerEventBus.remoteActionEvent.value)
    }

    @Test
    fun `test injectKey invokes listener`() {
        var receivedKey = 0
        ServerEventBus.onInjectKeyEvent = { keyCode ->
            receivedKey = keyCode
        }
        ServerEventBus.injectKey(19)
        // Check key invocation
        Thread.sleep(50)
        assertEquals(19, receivedKey)
        ServerEventBus.onInjectKeyEvent = null
    }
}
