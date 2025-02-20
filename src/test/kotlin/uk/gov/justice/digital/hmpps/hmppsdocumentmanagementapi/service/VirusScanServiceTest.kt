package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentFileVirusScanException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsClamAVProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.VirusScanStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets

class VirusScanServiceTest {

  @Test
  fun `ping returning pong is true`() {
    val virusScanService = setupVirusScanService("PONG\u0000")

    val result = virusScanService.ping()
    assertThat(result).isTrue()
  }

  @Test
  fun `ping returning something else is false`() {
    val virusScanService = setupVirusScanService("SOMETHING ELSE")
    val result = virusScanService.ping()
    assertThat(result).isFalse()
  }

  @Test
  fun `ping throwing error is false`() {
    val socket: Socket = mock()
    whenever(socket.getOutputStream()).thenThrow(IOException("OutputStream exception"))
    val virusScanService = object : VirusScanService(HmppsClamAVProperties()) {
      override fun createSocket(): Socket = socket
    }
    val result = virusScanService.ping()
    assertThat(result).isFalse()
  }

  @Test
  fun `virus scan with empty result is an error status`() {
    val virusScanService = setupVirusScanService("")
    val result = virusScanService.scan(ByteArrayInputStream(byteArrayOf()))
    assertThat(result.status).isEqualTo(VirusScanStatus.ERROR)
  }

  @Test
  fun `virus scan with response ok is a passed status`() {
    val virusScanService = setupVirusScanService("stream: OK")
    val result = virusScanService.scan(ByteArrayInputStream(byteArrayOf()))
    assertThat(result.status).isEqualTo(VirusScanStatus.PASSED)
  }

  @Test
  fun `virus scan ending with error is an error status`() {
    val virusScanService = setupVirusScanService("some ERROR")
    val result = virusScanService.scan(ByteArrayInputStream(byteArrayOf()))
    assertThat(result.status).isEqualTo(VirusScanStatus.ERROR)
  }

  @Test
  fun `virus scan returning something else is a failed status`() {
    val virusScanService = setupVirusScanService("Something else happened")
    val result = virusScanService.scan(ByteArrayInputStream(byteArrayOf()))
    assertThat(result.status).isEqualTo(VirusScanStatus.FAILED)
  }

  @Test
  fun `a found virus is returned in the signature`() {
    val virusScanService = setupVirusScanService("stream: virus1234 FOUND")
    val result = virusScanService.scan(ByteArrayInputStream(byteArrayOf()))
    assertThat(result.signature).isEqualTo("virus1234")
  }

  @Test
  fun `scan and throw with any status other than passed throws error`() {
    val virusScanService = setupVirusScanService("stream: virus1234 FOUND")
    assertThatThrownBy { virusScanService.scanAndThrow(ByteArrayInputStream(byteArrayOf())) }
      .isInstanceOf(DocumentFileVirusScanException::class.java)
  }

  private fun setupVirusScanService(returnedOutputStreamValue: String): VirusScanService {
    val socket: Socket = mock()
    val byteArrayOutputStream = ByteArrayOutputStream()
    val inputStream = ByteArrayInputStream(StandardCharsets.UTF_8.encode(returnedOutputStreamValue).array())
    whenever(socket.getOutputStream()).thenReturn(byteArrayOutputStream)
    whenever(socket.getInputStream()).thenReturn(inputStream)
    return object : VirusScanService(HmppsClamAVProperties()) {
      override fun createSocket(): Socket = socket
    }
  }
}
