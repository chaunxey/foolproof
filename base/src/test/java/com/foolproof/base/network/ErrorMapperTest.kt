package com.foolproof.base.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun `socket timeout maps to Timeout`() {
        assertEquals(NetworkError.Timeout, ErrorMapper.map(SocketTimeoutException("timeout")))
    }

    @Test
    fun `unknown host maps to NoNetwork`() {
        assertEquals(NetworkError.NoNetwork, ErrorMapper.map(UnknownHostException("no dns")))
    }

    @Test
    fun `io exception maps to NoNetwork`() {
        assertEquals(NetworkError.NoNetwork, ErrorMapper.map(IOException("broken pipe")))
    }

    @Test
    fun `serialization failure maps to Parse`() {
        assertEquals(NetworkError.Parse, ErrorMapper.map(SerializationException("bad json")))
    }

    @Test
    fun `unclassified error keeps the cause`() {
        val cause = IllegalStateException("boom")

        val error = ErrorMapper.map(cause)

        assertTrue(error is NetworkError.Unknown)
        assertEquals(cause, (error as NetworkError.Unknown).cause)
    }
}
