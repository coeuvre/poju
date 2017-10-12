package me.coeuvre.poju.rest

import me.coeuvre.poju.service.ExportActivityItemsRequest
import me.coeuvre.poju.service.TaoQingCangService
import me.coeuvre.poju.service.UpdateActivityItemsRequest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.io.*

@RestController
class TaoQingCangController(@Autowired val service: TaoQingCangService) {

    private fun sendExcel(workbook: XSSFWorkbook, filename: String): ResponseEntity<ByteArray> {
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

    @PostMapping("/api/tqc/ExportActivityItems")
    fun exportActivityItems(@RequestBody request: Mono<ExportActivityItemsRequest>): Mono<ResponseEntity<ByteArray>> {
        return request.flatMap { r ->
            service.exportActivityItems(r).map { sendExcel(it, "TQC_ActivityItems") }
        }
    }

    data class UpdateActivityItemsModel(
            val tbToken: String,
            val cookie2: String,
            val sg: String,
            val workbook: Part
    )

    @PostMapping("/api/tqc/UpdateActivityItems")
    fun updateActivityItems(@ModelAttribute model: UpdateActivityItemsModel): Mono<ResponseEntity<ByteArray>> {
        return model.workbook.content().map { dataBuffer ->
            dataBuffer.asInputStream()
        }.collectList().map { inputStreamList ->
            inputStreamList.reduce { a, b -> SequenceInputStream(a, b) }
        }.flatMap { inputStream ->
            val workbook = XSSFWorkbook(inputStream)
            service.updateActivityItems(UpdateActivityItemsRequest(
                    tbToken = model.tbToken,
                    cookie2 = model.cookie2,
                    sg = model.sg,
                    workbook = workbook
            )).map { errorWorkbook: XSSFWorkbook? ->
                if (errorWorkbook != null) {
                    sendExcel(errorWorkbook, "TQC_ErrorItems")
                } else {
                    ResponseEntity.ok().body(ByteArray(0))
                }
            }
        }
    }

}