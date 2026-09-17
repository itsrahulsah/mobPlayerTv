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
    }
}
