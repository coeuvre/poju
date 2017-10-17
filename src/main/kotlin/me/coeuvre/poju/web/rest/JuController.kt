package me.coeuvre.poju.web.rest

import me.coeuvre.poju.service.ExportItemApplyFormDetailsRequest
import me.coeuvre.poju.service.JuService
import me.coeuvre.poju.util.Utils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class JuController(@Autowired val juService: JuService) {

    @PostMapping("/api/ju/ExportItemApplyFormDetails")
    fun exportItemApplyFormDetails(@Validated @RequestBody request: ExportItemApplyFormDetailsRequest): Mono<ResponseEntity<ByteArray>> =
        juService.exportItemApplyFormDetails(request).map { workbook ->
            Utils.createExcelResponseEntity(workbook, "JU_ActivityItems")
        }
}