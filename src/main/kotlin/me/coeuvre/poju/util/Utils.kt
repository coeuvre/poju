package me.coeuvre.poju.util

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.util.StreamUtils
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

fun Part.getContentAsByteArray(): Mono<ByteArray> {
    val byteArrayOutputStream = ByteArrayOutputStream()
    return content().concatMap { dataBuffer ->
        StreamUtils.copy(dataBuffer.asInputStream(), byteArrayOutputStream)
        Mono.empty<Void>()
    }.collectList().map {
        byteArrayOutputStream.toByteArray()
    }
}

object Utils {

    fun createExcelResponseEntity(workbook: XSSFWorkbook, filename: String): ResponseEntity<ByteArray> {
        val file = File.createTempFile(filename, ".xlsx")
        workbook.write(FileOutputStream(file))
        println("Saved to ${file.absolutePath}")

        val output = ByteArrayOutputStream()
        workbook.write(output)
        val bytes = output.toByteArray()
        return ResponseEntity.ok()
            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header("Content-disposition", "attachment; filename=$filename.xlsx")
            .contentLength(bytes.size.toLong())
            .body(bytes)
    }

    fun readContentMapFromZipByteArray(byteArray: ByteArray): Map<String, ByteArray> {
        val zipInputStream = ZipInputStream(ByteArrayInputStream(byteArray))
        val buffer = ByteArray(4096)
        val byteArrayMap = mutableMapOf<String, ByteArray>()

        // Iterate over Zip entries
        while (true) {
            val zipEntry = zipInputStream.nextEntry ?: break
            val byteArrayOutputStream = ByteArrayOutputStream()

            // Read Zip entry content into ByteArray
            while (true) {
                val len = zipInputStream.read(buffer)
                if (len <= 0) {
                    break; }
                byteArrayOutputStream.write(buffer, 0, len)
            }
            byteArrayOutputStream.close()

            byteArrayMap.put(zipEntry.name, byteArrayOutputStream.toByteArray())
        }
        zipInputStream.closeEntry()
        zipInputStream.close()
        return byteArrayMap
    }
}