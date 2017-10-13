package me.coeuvre.poju.rest

import me.coeuvre.poju.service.ExportActivityItemsRequest
import me.coeuvre.poju.service.TaoQingCangService
import me.coeuvre.poju.service.UpdateActivityItemsRequest
import me.coeuvre.poju.thirdparty.taoqingcang.NamedByteArrayResource
import me.coeuvre.poju.thirdparty.taoqingcang.UploadItemMainPicRequest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.io.*
import java.util.zip.ZipInputStream

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
            val workbook: Part,
            val picZip: Part?
    )

    private fun Part.getContentAsInputStream(): Mono<InputStream> = content().map { dataBuffer ->
        dataBuffer.asInputStream()
    }.collectList().map { inputStreamList ->
        inputStreamList.reduce { a, b -> SequenceInputStream(a, b) }
    }

    @PostMapping("/api/tqc/UpdateActivityItems")
    fun updateActivityItems(@ModelAttribute modelMono: Mono<UpdateActivityItemsModel>): Mono<ResponseEntity<ByteArray>> {
        return modelMono.flatMap { model ->
            model.workbook.getContentAsInputStream().map { XSSFWorkbook(it) }
                    .flatMap { workbook ->
                        if (model.picZip != null) {
                            model.picZip.getContentAsInputStream().map { inputStream ->
                                val zipInputStream = ZipInputStream(inputStream)
                                val buffer = ByteArray(4096)
                                val byteArrayMap = mutableMapOf<String, ByteArray>()

                                // Iterate over Zip entries
                                while (true) {
                                    val zipEntry = zipInputStream.nextEntry ?: break
                                    val byteArrayOutputStream = ByteArrayOutputStream()

                                    // Read Zip entry content into ByteArray
                                    while (true) {
                                        val len = zipInputStream.read(buffer)
                                        if (len <= 0) { break; }
                                        byteArrayOutputStream.write(buffer, 0, len)
                                    }
                                    byteArrayOutputStream.close()

                                    byteArrayMap.put(zipEntry.name, byteArrayOutputStream.toByteArray())
                                }
                                zipInputStream.closeEntry()
                                zipInputStream.close()
                                Pair(workbook, byteArrayMap)
                            }
                        } else {
                            Mono.just(Pair(workbook, null))
                        }
                    }
                    .flatMap { (workbook, zipContentMap) ->
                        service.updateActivityItems(UpdateActivityItemsRequest(
                                tbToken = model.tbToken,
                                cookie2 = model.cookie2,
                                sg = model.sg,
                                workbook = workbook,
                                zipContentMap = zipContentMap
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

    data class UploadItemMainPicModel(
            val tbToken: String,
            val cookie2: String,
            val sg: String,
            val platformId: Long,
            val itemId: Long,
            val pic: Part
    )

    @PostMapping("/api/tqc/UploadItemMainPic")
    fun uploadItemMainPic(@ModelAttribute modelMono: Mono<UploadItemMainPicModel>): Mono<String> =
            modelMono.flatMap { model ->
                model.pic.getContentAsInputStream().flatMap { inputStream ->
                    val byteArray = inputStream.readBytes()
                    service.taoQingCangClient.uploadItemMainPic(UploadItemMainPicRequest(
                            tbToken = model.tbToken,
                            cookie2 = model.cookie2,
                            sg = model.sg,
                            platformId = model.platformId,
                            itemId = model.itemId,
                            pic = HttpEntity(NamedByteArrayResource(model.pic.headers().contentDisposition.filename ?: "", byteArray), model.pic.headers())
                    ))
                }
            }

}