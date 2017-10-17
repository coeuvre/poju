package me.coeuvre.poju.util

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.ResponseEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

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
            .header("Content-disposition", "attachment; filename=$filename.xls")
            .contentLength(bytes.size.toLong())
            .body(bytes)
    }
}