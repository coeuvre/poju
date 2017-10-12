package me.coeuvre.poju.rest

import me.coeuvre.poju.service.ExportActivityItemsRequest
import me.coeuvre.poju.service.TaoQingCangService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@RestController
class TaoQingCangController(@Autowired val service: TaoQingCangService) {

    @PostMapping("/api/tqc/ExportActivityItems")
    fun exportActivityItems(@RequestBody request: Mono<ExportActivityItemsRequest>): Mono<ResponseEntity<ByteArray>> {
        return request.flatMap { r ->
            service.exportActivityItems(r).map { workbook ->
                val filename = "TQC_ActivityItems"
                val file = File.createTempFile(filename, ".xlsx")
                workbook.write(FileOutputStream(file))
                println("Saved to ${file.absolutePath}")

                val output = ByteArrayOutputStream()
                workbook.write(output)
                val bytes = output.toByteArray()
                ResponseEntity.ok()
                        .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .header("Content-disposition", "attachment; filename=$filename.xls")
                        .contentLength(bytes.size.toLong())
                        .body(bytes)
            }
        }
    }

}