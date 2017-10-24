package me.coeuvre.poju.web.rest

import me.coeuvre.poju.service.ExportActivityItemsRequest
import me.coeuvre.poju.service.TaoQiangGouService
import me.coeuvre.poju.service.UpdateItemApplyFormDetail
import me.coeuvre.poju.util.Utils
import me.coeuvre.poju.util.getContentAsByteArray
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream

@RestController
class TaoQiangGouController(@Autowired val taoQiangGouService: TaoQiangGouService) {

    @PostMapping("/api/tqg/ExportItemApplyFormDetails")
    fun exportActivityItems(@RequestBody request: Mono<ExportActivityItemsRequest>): Mono<ResponseEntity<ByteArray>> {
        return request.flatMap { r ->
            taoQiangGouService.exportItemApplyFormDetails(r).map { Utils.createExcelResponseEntity(it, "TQG_ActivityItems") }
        }
    }

    @PostMapping("/api/tqg/UpdateItemApplyFormDetails")
    fun updateActivityItems(@ModelAttribute modelMono: Mono<TaoQingCangController.UpdateActivityItemsModel>): Mono<ResponseEntity<ByteArray>> {
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
                    taoQiangGouService.updateItemApplyFormDetail(UpdateItemApplyFormDetail(
                        tbToken = model.tbToken,
                        cookie2 = model.cookie2,
                        sg = model.sg,
                        workbook = workbook,
                        zipImagesMap = zipContentMap
                    )).map { errorWorkbook: XSSFWorkbook? ->
                        if (errorWorkbook != null) {
                            Utils.createExcelResponseEntity(errorWorkbook, "TQQ_ErrorItems")
                        } else {
                            ResponseEntity.ok().body(ByteArray(0))
                        }
                    }
                }
        }
    }

}