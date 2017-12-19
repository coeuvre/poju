package me.coeuvre.poju.web.rest

import me.coeuvre.poju.service.ExportActivityItemsRequest
import me.coeuvre.poju.service.PublishItemsRequest
import me.coeuvre.poju.service.TaoQingCangService
import me.coeuvre.poju.service.UpdateItemApplyFormDetail
import me.coeuvre.poju.thirdparty.taoqingcang.UploadItemMainPicRequest
import me.coeuvre.poju.util.NamedByteArrayResource
import me.coeuvre.poju.util.Utils
import me.coeuvre.poju.util.getContentAsByteArray
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.io.*

@RestController
class TaoQingCangController(@Autowired val taoQingCangService: TaoQingCangService) {

    @PostMapping("/api/tqc/ExportItemApplyFormDetails")
    fun exportActivityItems(@RequestBody request: Mono<ExportActivityItemsRequest>): Mono<ResponseEntity<ByteArray>> {
        return request.flatMap { r ->
            taoQingCangService.exportItemApplyFormDetails(r).map { Utils.createExcelResponseEntity(it, "TQC_ActivityItems") }
        }
    }

    data class UpdateActivityItemsModel(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val workbook: Part,
        val picZip: Part?
    )

    @PostMapping("/api/tqc/UpdateItemApplyFormDetails")
    fun updateActivityItems(@ModelAttribute modelMono: Mono<UpdateActivityItemsModel>): Mono<ResponseEntity<ByteArray>> {
        return modelMono.flatMap { model ->
            model.workbook.getContentAsByteArray().map { XSSFWorkbook(ByteArrayInputStream(it)) }
                .flatMap { workbook ->
                    if (model.picZip != null) {
                        model.picZip.getContentAsByteArray().map { Pair(workbook, Utils.readContentMapFromZipByteArray(it)) }
                    } else {
                        Mono.just(Pair(workbook, null))
                    }
                }
                .flatMap { (workbook, zipContentMap) ->
                    taoQingCangService.updateItemApplyFormDetail(UpdateItemApplyFormDetail(
                        tbToken = model.tbToken,
                        cookie2 = model.cookie2,
                        sg = model.sg,
                        workbook = workbook,
                        zipImagesMap = zipContentMap
                    )).map { errorWorkbook: XSSFWorkbook? ->
                        if (errorWorkbook != null) {
                            Utils.createExcelResponseEntity(errorWorkbook, "TQC_ErrorItems")
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
        val platformId: String,
        val itemId: String,
        val pic: Part
    )

    @PostMapping("/api/tqc/UploadItemMainPic")
    fun uploadItemMainPic(@ModelAttribute modelMono: Mono<UploadItemMainPicModel>): Mono<String> =
        modelMono.flatMap { model ->
            model.pic.getContentAsByteArray().flatMap { byteArray ->
                taoQingCangService.taoQingCangClient.uploadItemMainPic(UploadItemMainPicRequest(
                    tbToken = model.tbToken,
                    cookie2 = model.cookie2,
                    sg = model.sg,
                    platformId = model.platformId,
                    itemId = model.itemId,
                    pic = HttpEntity(NamedByteArrayResource(model.pic.headers().contentDisposition.filename ?: "", byteArray), model.pic.headers())
                ))
            }
        }


    @PostMapping("/api/tqc/PublishItems")
    fun publishItems(@RequestBody requestMono: Mono<PublishItemsRequest>): Mono<ResponseEntity<ByteArray>> {
        return requestMono.flatMap { request ->
            taoQingCangService.publishItems(request)
        }.map { errorWorkbook: XSSFWorkbook? ->
            if (errorWorkbook != null) {
                Utils.createExcelResponseEntity(errorWorkbook, "TQC_PublishItemsError")
            } else {
                ResponseEntity.ok().body(ByteArray(0))
            }
        }
    }
}