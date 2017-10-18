package me.coeuvre.poju.web.rest

import me.coeuvre.poju.service.ExportItemApplyFormDetailsRequest
import me.coeuvre.poju.service.JuService
import me.coeuvre.poju.util.Utils
import me.coeuvre.poju.util.getContentAsByteArray
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@RestController
class JuController(@Autowired val juService: JuService) {

    @PostMapping("/api/ju/ExportItemApplyFormDetails")
    fun exportItemApplyFormDetails(@Validated @RequestBody request: ExportItemApplyFormDetailsRequest): Mono<ResponseEntity<ByteArray>> =
        juService.exportItemApplyFormDetails(request).map { workbook ->
            Utils.createExcelResponseEntity(workbook, "JU_ActivityItems")
        }

    data class DownloadArticleImagesModel(
        val workbook: Part
    )

    @PostMapping("/api/ju/DownloadArticleImages")
    fun downloadArticleImages(@ModelAttribute modelMono: Mono<DownloadArticleImagesModel>): Mono<ResponseEntity<ByteArray>> {
        return modelMono.flatMap { model ->
            model.workbook.getContentAsByteArray().map { XSSFWorkbook(ByteArrayInputStream(it)) }
                .flatMap { workbook ->
                    juService.downloadArticleImages(workbook)
                }
                .map { byteArray ->
                    val filename = "ArticleImages"
                    val file = File.createTempFile(filename, ".zip")
                    FileOutputStream(file).write(byteArray)
                    println("Saved to ${file.absolutePath}")

                    ResponseEntity<ByteArray>(HttpStatus.OK)
                }
        }
    }
}