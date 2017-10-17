package me.coeuvre.poju.manager

import me.coeuvre.poju.util.NamedByteArrayResource
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.awt.Color

data class RowDef<T>(
    val name: String,
    val get: Function1<T, String>,
    val set: Function2<T, String, T>
)

data class QueryPagedItemsRequest(val currentPage: Int, val pageCount: Int, val pageSize: Int, val totalCount: Int)

data class QueryPagedItemsResponse<T>(
    val currentPage: Int,
    val totalCount: Int,
    val itemList: List<T>
)

data class GetItemApplyFormDetailRequest<T>(
    val currentCount: Int,
    val totalCount: Int,
    val item: T
)

data class GetItemApplyFormDetailResponse<T>(
    val itemApplyFormDetail: T,
    val isSuccess: Boolean,
    val errorMessage: String?
)

data class UpdateItemApplyFormDetailRequest<T>(
    val currentCount: Int,
    val totalCount: Int,
    val item: T
)

data class UploadImageRequest<T>(
    val item: T,
    val rowDef: RowDef<T>,
    val image: HttpEntity<NamedByteArrayResource>
)

const val ZIP_PROTOCOL = "zip://"

@Component
class JuLikeFlowManager {
    fun <I, D> exportItemApplyFormDetails(queryTotalCount: () -> Mono<Int>,
                                          queryPagedItems: (QueryPagedItemsRequest) -> Mono<QueryPagedItemsResponse<I>>,
                                          getItemApplyFormDetail: (GetItemApplyFormDetailRequest<I>) -> Mono<GetItemApplyFormDetailResponse<D>>,
                                          rowDefList: List<RowDef<D>>): Mono<XSSFWorkbook> {
        return queryTotalCount().flatMapMany { totalCount ->
            val pageSize = 20
            val pageCount = Math.ceil(totalCount * 1.0 / pageSize).toInt()
            val queryItemsRequestList = (1..pageCount).map {
                QueryPagedItemsRequest(currentPage = it, pageCount = pageCount, pageSize = pageSize, totalCount = totalCount)
            }
            queryItemsRequestList.toFlux()
                .flatMapSequential { queryPagedItems(it) }
                .flatMapSequential { queryItemsResponse ->
                    queryItemsResponse.itemList.withIndex().toFlux().flatMap { (index, item) ->
                        getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                            (queryItemsResponse.currentPage - 1) * pageSize + index + 1,
                            totalCount,
                            item)
                        )
                    }
                }
        }.collectList().map { generateWorkbook(rowDefList, it) }
    }

    private fun <T> generateWorkbook(rowDefList: List<RowDef<T>>, getItemApplyFormDetailResponseList: List<GetItemApplyFormDetailResponse<T>>): XSSFWorkbook {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet()

        val titleStyle = workbook.createCellStyle()
        val font = workbook.createFont()
        font.setColor(XSSFColor(Color(255, 255, 255)))
        titleStyle.setFont(font)
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        titleStyle.setFillForegroundColor(XSSFColor(Color(0, 0, 0)))

        val errorStyle = workbook.createCellStyle()
        errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        errorStyle.setFillForegroundColor(XSSFColor(Color(255, 199, 206)))

        // Create title row
        val titleRow = sheet.createRow(0)
        rowDefList.withIndex().forEach { (cellIndex, rowDef) ->
            val cell = titleRow.createCell(cellIndex)
            cell.cellStyle = titleStyle
            cell.setCellValue(rowDef.name)
        }

        getItemApplyFormDetailResponseList.withIndex().forEach { (rowIndex, result) ->
            val row = sheet.createRow(1 + rowIndex)
            rowDefList.withIndex().forEach { (cellIndex, rowDef) ->
                val cell = row.createCell(cellIndex)
                if (!result.isSuccess) {
                    cell.cellStyle = errorStyle
                }
                cell.setCellValue(rowDef.get(result.itemApplyFormDetail))
            }
            if (!result.isSuccess) {
                row.createCell(rowDefList.size).setCellValue(result.errorMessage)
            }
        }

        return workbook
    }

    fun <T> updateItemApplyFormDetails(workbook: XSSFWorkbook,
                                       zipImagesMap: Map<String, ByteArray>?,
                                       rowDefList: List<RowDef<T>>,
                                       itemTemplate: T,
                                       uploadImage: (UploadImageRequest<T>) -> Mono<String>,
                                       updateItemApplyFormDetail: (UpdateItemApplyFormDetailRequest<T>) -> Mono<Void>): Mono<XSSFWorkbook> {
        val sheet = workbook.getSheetAt(0)

        // Check title row
        val titleRow = sheet.getRow(0)
        rowDefList.withIndex().map { (index, rowDef) ->
            val cell = titleRow.getCell(index)
            if (cell == null || rowDef.name != cell.stringCellValue) {
                throw IllegalArgumentException("Excel 格式错误: 第${index}列应该是 ${rowDef.name}")
            }
        }

        // Read data rows
        val itemApplyFormDetailList = (1..sheet.lastRowNum).map { rowIndex ->
            val row = sheet.getRow(rowIndex)
            var itemApplyFormDetail = itemTemplate
            rowDefList.withIndex().map { (index, rowDef) ->
                val cell = row.getCell(index)
                if (cell != null) {
                    val value = when (cell.cellTypeEnum) {
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        else -> cell.stringCellValue
                    }
                    itemApplyFormDetail = rowDef.set(itemApplyFormDetail, value)
                }
            }
            itemApplyFormDetail
        }

        val totalCount = itemApplyFormDetailList.size

        return itemApplyFormDetailList.withIndex().toFlux()
            .concatMap { (index, item) ->
                updateItemApplyFormDetail(index, totalCount, zipImagesMap, item, rowDefList, uploadImage, updateItemApplyFormDetail)
                    .map { GetItemApplyFormDetailResponse(item, true, null) }
                    .onErrorResume { e ->
                        println(e.message)
                        Mono.just(GetItemApplyFormDetailResponse(item, false, e.message))
                    }
            }
            .collectList()
            .flatMap {
                val results = it.filter { !it.isSuccess }
                if (results.isNotEmpty()) {
                    println("Found ${results.size} errors")
                    Mono.just(generateWorkbook(rowDefList, results))
                } else {
                    Mono.empty()
                }
            }
    }

    private fun <T> updateItemApplyFormDetail(index: Int, totalCount: Int, zipImagesMap: Map<String, ByteArray>?,
                                              item: T, rowDefList: List<RowDef<T>>,
                                              uploadImage: (UploadImageRequest<T>) -> Mono<String>,
                                              updateItemApplyFormDetail: (UpdateItemApplyFormDetailRequest<T>) -> Mono<Void>): Mono<Void> {
        val itemId = rowDefList.first().get(item)
        println("Updating item $itemId ${index + 1}/$totalCount")
        return getUploadImageRequestFlux(zipImagesMap, rowDefList, item)
            .concatMap { uploadImageRequest ->
                println("Uploading ${uploadImageRequest.image.body?.filename} for ${uploadImageRequest.rowDef.name} ($itemId)")
                uploadImage(uploadImageRequest).map { Pair(uploadImageRequest.rowDef, it) }
            }
            .collectList()
            .map { it.fold(item, { item, (rowDef, value) -> rowDef.set(item, value) }) }
            .flatMap { updateItemApplyFormDetail(UpdateItemApplyFormDetailRequest(index + 1, totalCount, it)) }
    }

    private fun <T> getUploadImageRequestFlux(zipImagesMap: Map<String, ByteArray>?, rowDefList: List<RowDef<T>>, item: T): Flux<UploadImageRequest<T>> {
        return rowDefList.filter { shouldUploadImage(it.get(item)) }.toFlux()
            .flatMap { rowDef -> loadImage(zipImagesMap, rowDef.get(item)).map { Pair(rowDef, it) } }
            .map { (rowDef, image) -> UploadImageRequest(item, rowDef, image) }
    }

    private fun shouldUploadImage(value: String): Boolean = value.startsWith(ZIP_PROTOCOL) || value.startsWith("http://") || value.startsWith("https://")

    private fun loadImage(zipImagesMap: Map<String, ByteArray>?, value: String): Mono<HttpEntity<NamedByteArrayResource>> {
        // TODO(coeuvre): Handle http images
        if (value.startsWith(ZIP_PROTOCOL)) {
            return Mono.create<HttpEntity<NamedByteArrayResource>> { sink ->
                val filename = value.substring(ZIP_PROTOCOL.length)
                val fileContent = zipImagesMap?.get(filename) ?: throw IllegalArgumentException("Zip 压缩包中没有图片 $filename")

                val headers = HttpHeaders()
                headers.contentType = if (filename.endsWith(".png")) {
                    MediaType.IMAGE_PNG
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    MediaType.IMAGE_JPEG
                } else {
                    throw IllegalArgumentException("只支持 PNG 和 JPG 图片格式")
                }

                sink.success(HttpEntity(NamedByteArrayResource(filename, fileContent), headers))
            }
        } else {
            throw UnsupportedOperationException("非法图片路径 $value")
        }
    }
}