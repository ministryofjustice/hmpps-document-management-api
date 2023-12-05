package uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.hmppsdocumentmanagementapi.enumeration.DocumentType

class MultipartFileUtilsTest {
  private val documentType = DocumentType.HMCTS_WARRANT

  @Nested
  @DisplayName("filename")
  inner class FilenameTest {
    @Test
    fun `uses document type when original filename is null`() {
      val file = mock<MultipartFile>()

      assertThat(file.filename(documentType)).isEqualTo(documentType.toString())
    }

    @Test
    fun `uses document type when original filename is empty`() {
      val file = mockFile(originalFilename = "")

      assertThat(file.filename(documentType)).isEqualTo(documentType.toString())
    }

    @Test
    fun `uses document type when original filename is whitespace`() {
      val file = mockFile(originalFilename = "    ")

      assertThat(file.filename(documentType)).isEqualTo(documentType.toString())
    }

    @Test
    fun `trims original filename`() {
      val file = mockFile(originalFilename = "   test.pdf  ")

      assertThat(file.filename(documentType)).isEqualTo("test.pdf")
    }

    @Test
    fun `removes any Windows style filepath`() {
      val file = mockFile(originalFilename = "%userprofile%\\Downloads\\test.pdf")

      assertThat(file.filename(documentType)).isEqualTo("test.pdf")
    }

    @Test
    fun `removes any nix style filepath`() {
      val file = mockFile(originalFilename = "~/Downloads/test.pdf")

      assertThat(file.filename(documentType)).isEqualTo("test.pdf")
    }
  }

  @Nested
  @DisplayName("filename without extension")
  inner class FilenameWithoutExtensionTest {
    @Test
    fun `removes file extension`() {
      val file = mockFile(originalFilename = "test.pdf")

      assertThat(file.filenameWithoutExtension(documentType)).isEqualTo("test")
    }

    @Test
    fun `accepts no file extension`() {
      val file = mockFile(originalFilename = "test")

      assertThat(file.filenameWithoutExtension(documentType)).isEqualTo("test")
    }
  }

  @Nested
  @DisplayName("file extension")
  inner class FileExtensionTest {
    @Test
    fun `returns file extension`() {
      val file = mockFile(originalFilename = "test.pdf")

      assertThat(file.fileExtension(documentType)).isEqualTo("pdf")
    }

    @Test
    fun `accepts no file extension`() {
      val file = mockFile(originalFilename = "test")

      assertThat(file.fileExtension(documentType)).isEmpty()
    }
  }

  @Nested
  @DisplayName("mime type")
  inner class MimeTypeTest {
    @Test
    fun `returns content type`() {
      val file = mockFile(contentType = "application/pdf")

      assertThat(file.mimeType()).isEqualTo("application/pdf")
    }

    @Test
    fun `returns octet stream when content type is null`() {
      val file = mockFile(contentType = null)

      assertThat(file.mimeType()).isEqualTo("application/octet-stream")
    }

    @Test
    fun `returns octet stream when content type is empty`() {
      val file = mockFile(contentType = "")

      assertThat(file.mimeType()).isEqualTo("application/octet-stream")
    }

    @Test
    fun `returns octet stream when content type is whitespace`() {
      val file = mockFile(contentType = "    ")

      assertThat(file.mimeType()).isEqualTo("application/octet-stream")
    }
  }

  private fun mockFile(
    originalFilename: String? = "test.pdf",
    contentType: String? = "application/pdf",
    size: Int = 0,
  ) =
    MockMultipartFile("file", originalFilename, contentType, ByteArray(size))
}
