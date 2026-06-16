package com.panorama.app.live

import com.panorama.android.camera.CameraConnection
import com.panorama.android.camera.ConnectTransport
import com.panorama.android.camera.ConnectionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {

    private val engine = mockk<com.panorama.android.sensor.OrientationEngine>(relaxed = true)

    private fun fakeConnection(state: MutableStateFlow<ConnectionState>): CameraConnection {
        val conn = mockk<CameraConnection>(relaxed = true)
        every { conn.state } returns state
        return conn
    }

    @Test
    fun `mirrors connection state into ui state`() = runTest {
        val flow = MutableStateFlow(ConnectionState.DISCONNECTED)
        val vm = LiveViewModel(fakeConnection(flow), engine, backgroundScope = backgroundScope)
        flow.value = ConnectionState.STREAMING
        advanceTimeBy(10)
        assertEquals(ConnectionState.STREAMING, vm.state.value.connection)
    }

    @Test
    fun `connect delegates to the connection`() = runTest {
        val flow = MutableStateFlow(ConnectionState.DISCONNECTED)
        val conn = fakeConnection(flow)
        val vm = LiveViewModel(conn, engine, backgroundScope = backgroundScope)
        vm.connect(ConnectTransport.WIFI)
        verify { conn.connect(ConnectTransport.WIFI) }
    }
}
