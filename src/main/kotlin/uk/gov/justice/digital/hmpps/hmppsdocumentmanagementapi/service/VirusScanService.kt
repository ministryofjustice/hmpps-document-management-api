package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.service

import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.DocumentFileVirusScanException
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.config.HmppsClamAVProperties
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.VirusScanStatus
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.model.VirusScanResult
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@Service
class VirusScanService(private val hmppsClamAVProperties: HmppsClamAVProperties) {

  fun ping(): Boolean {
    try {
      val response = processCommand("zPING\u0000".toByteArray(StandardCharsets.UTF_8)).trim { it <= ' ' }
      return response.equals(PONG, ignoreCase = true)
    } catch (e: Exception) {
      log.error("Error pinging ClamAV", e)
      return false
    }
  }
  private fun processCommand(cmd: ByteArray): String {
    var response = ""

    createSocket().use { socket ->
      DataOutputStream(socket.getOutputStream()).use { dos ->
        dos.write(cmd)
        dos.flush()

        val socketInputStream: InputStream = socket.getInputStream()

        var read: Int = hmppsClamAVProperties.chunkSize
        val buffer = ByteArray(hmppsClamAVProperties.chunkSize)
        while (read == hmppsClamAVProperties.chunkSize) {
          try {
            read = socketInputStream.read(buffer)
          } catch (e: IOException) {
            log.error("Error reading result from socket", e)
            break
          }
          response = String(buffer, 0, read, StandardCharsets.UTF_8)
        }
      }
    }
    return response
  }

  fun scanAndThrow(inputStream: InputStream) {
    val virusScanResult = scan(inputStream)
    if (virusScanResult.status != VirusScanStatus.PASSED) {
      throw DocumentFileVirusScanException(virusScanResult)
    }
  }

  fun scan(fileInputStream: InputStream): VirusScanResult {
    createSocket().use { socket ->
      BufferedOutputStream(socket.getOutputStream()).use { socketOutputStream ->
        socketOutputStream.write("zINSTREAM\u0000".toByteArray(StandardCharsets.UTF_8))
        socketOutputStream.flush()

        val buffer = ByteArray(hmppsClamAVProperties.chunkSize)
        socket.getInputStream().use { socketInputStream ->
          var read = fileInputStream.read(buffer)
          while (read >= 0) {
            val chunkSize: ByteArray = ByteBuffer.allocate(4).putInt(read).array()
            socketOutputStream.write(chunkSize)
            socketOutputStream.write(buffer, 0, read)

            if (socketInputStream.available() > 0) {
              val reply: ByteArray = IOUtils.toByteArray(socketInputStream)
              throw IOException(
                "Reply from server: " + String(reply, StandardCharsets.UTF_8),
              )
            }
            read = fileInputStream.read(buffer)
          }
          socketOutputStream.write(byteArrayOf(0, 0, 0, 0))
          socketOutputStream.flush()
          return populateVirusScanResult(String(IOUtils.toByteArray(socketInputStream), StandardCharsets.UTF_8).trim { it <= ' ' })
        }
      }
    }
  }

  protected fun createSocket(): Socket {
    val socket = Socket()
    socket.connect(InetSocketAddress(hmppsClamAVProperties.host, hmppsClamAVProperties.port), hmppsClamAVProperties.connectionTimeout)
    socket.setSoTimeout(hmppsClamAVProperties.readTimeout)
    return socket
  }

  private fun populateVirusScanResult(result: String): VirusScanResult {
    val status: VirusScanStatus = if (result.isEmpty()) {
      VirusScanStatus.ERROR
    } else if (RESPONSE_OK == result) {
      VirusScanStatus.PASSED
    } else if (result.endsWith(ERROR_SUFFIX)) {
      VirusScanStatus.ERROR
    } else {
      VirusScanStatus.FAILED
    }
    val signature: String? = (
      if (result.endsWith(FOUND_SUFFIX)) {
        result.substring(STREAM_PREFIX.length, result.lastIndexOf(FOUND_SUFFIX) - 1).trim { it <= ' ' }
      } else {
        null
      }
      )?.toString()
    return VirusScanResult(status, result, signature)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val PONG = "PONG"
    private const val RESPONSE_OK = "stream: OK"
    private const val FOUND_SUFFIX = "FOUND"
    private const val ERROR_SUFFIX = "ERROR"
    private const val STREAM_PREFIX = "stream:"
  }
}
