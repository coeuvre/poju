package me.coeuvre.poju.service

import me.coeuvre.poju.manager.GetItemApplyFormDetailResponse
import me.coeuvre.poju.manager.JuLikeFlowManager
import me.coeuvre.poju.manager.QueryPagedItemsResponse
import me.coeuvre.poju.manager.RowDef
import me.coeuvre.poju.thirdparty.taoqingcang.*
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.awt.Color

data class ExportActivityItemsRequest(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val activityEnterId: Long,
    val itemStatusCode: String,
    val actionStatus: String
)

data class UpdateActivityItemsRequest(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val workbook: XSSFWorkbook,
    val zipContentMap: Map<String, ByteArray>?
)

@Service
class TaoQingCangService(@Autowired val juLikeFlowManager: JuLikeFlowManager, @Autowired val taoQingCangClient: TaoQingCangClient) {
    private val rowDefs: List<RowDef<ItemApplyFormDetail>> = listOf(
        RowDef("juId", { it.juId.toString() }, { item, value -> item.copy(juId = value.toLong()) }),
        RowDef("商品ID/itemId", { it.itemId.toString() }, { item, value -> item.copy(itemId = value.toLong()) }),
        RowDef("platformId", { it.platformId.toString() }, { item, value -> item.copy(platformId = value.toLong()) }),
        RowDef("活动ID/activityEnterId", { it.activityEnterId.toString() }, { item, value -> item.copy(activityEnterId = value.toLong()) }),
        RowDef("activityId", { it.activityId.toString() }, { item, value -> item.copy(activityId = value.toLong()) }),
        RowDef("报名方式/skuType", { it.skuType }, { item, value -> item.copy(skuType = value) }),
        RowDef("活动价格/activityPrice", { it.activityPrice }, { item, value -> item.copy(activityPrice = value) }),
        RowDef("priceType", { it.priceType }, { item, value -> item.copy(priceType = value) }),
        RowDef("库存类型/inventoryType", { it.inventoryType }, { item, value -> item.copy(inventoryType = value) }),
        RowDef("报名数量/itemCount", { it.itemCount }, { item, value -> item.copy(itemCount = value) }),
        RowDef("宝贝标题/shortTitle", { it.shortTitle }, { item, value -> item.copy(shortTitle = value) }),
        RowDef("透明底模特图/itemMainPic", { it.itemMainPic }, { item, value -> item.copy(itemMainPic = value) }),
        RowDef("透明底平铺图/itemTaobaoAppMaterial", { it.itemTaobaoAppMaterial }, { item, value -> item.copy(itemTaobaoAppMaterial = value) }),
        RowDef("新版商品标签/itemTqcNewTag", { it.itemTqcNewTag }, { item, value -> item.copy(itemTqcNewTag = value) }),
        RowDef("设置隐藏选项/itemHiddenSearchTag", { it.itemHiddenSearchTag }, { item, value -> item.copy(itemHiddenSearchTag = value) }),
        RowDef("运费/payPostage", { it.payPostage }, { item, value -> item.copy(payPostage = value) }),
        RowDef("每个ID限购/limitNum", { it.limitNum }, { item, value -> item.copy(limitNum = value) }),
        RowDef("品牌名称/itemBrandName", { it.itemBrandName }, { item, value -> item.copy(itemBrandName = value) })
    )

    fun exportItemApplyFormDetails(request: ExportActivityItemsRequest): Mono<XSSFWorkbook> {
        val queryItemsRequestTemplate = QueryItemsRequest(
            tbToken = request.tbToken,
            cookie2 = request.cookie2,
            sg = request.sg,
            activityEnterId = request.activityEnterId,
            itemStatusCode = request.itemStatusCode,
            actionStatus = request.actionStatus,
            currentPage = 1,
            pageSize = 0
        )

        return juLikeFlowManager.exportItemApplyFormDetails({
            taoQingCangClient.queryItems(queryItemsRequestTemplate).map {
                println("Activity(${request.activityEnterId}) has ${it.totalItem} items")
                it.totalItem
            }
        }, { queryPagedItemsRequest ->
            println("Fetching page ${queryPagedItemsRequest.currentPage}/${queryPagedItemsRequest.pageCount}")

            taoQingCangClient.queryItems(queryItemsRequestTemplate.copy(
                currentPage = queryPagedItemsRequest.currentPage,
                pageSize = queryPagedItemsRequest.pageSize
            )).map { queryItemsResponse ->
                QueryPagedItemsResponse(
                    currentPage = queryPagedItemsRequest.currentPage,
                    itemList = queryItemsResponse.itemList
                )
            }
        }, { getItemApplyFormDetailRequest ->
            val item = getItemApplyFormDetailRequest.item
            println("Fetching item ${item.juId} (${getItemApplyFormDetailRequest.currentIndex}/${getItemApplyFormDetailRequest.totalCount})")
            taoQingCangClient.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                tbToken = request.tbToken,
                cookie2 = request.cookie2,
                sg = request.sg,
                juId = item.juId
            )).map { GetItemApplyFormDetailResponse(it, true, null) }.onErrorResume { e ->
                println(e.message)
                Mono.just(GetItemApplyFormDetailResponse(
                    itemApplyFormDetail = ItemApplyFormDetail.empty.copy(juId = item.juId, itemId = item.itemId, shortTitle = item.itemName),
                    isSuccess = false,
                    errorMessage = e.message
                ))
            }
        }, rowDefs)
    }

    private fun exportItemApplyFormDetailsToWorkbook(getItemApplyFormDetailResult: List<GetItemApplyFormDetailResponse<ItemApplyFormDetail>>): XSSFWorkbook {
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
        rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
            val cell = titleRow.createCell(cellIndex)
            cell.cellStyle = titleStyle
            cell.setCellValue(rowDef.name)
        }

        getItemApplyFormDetailResult.withIndex().forEach { (rowIndex, result) ->
            val row = sheet.createRow(1 + rowIndex)
            rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                val cell = row.createCell(cellIndex)
                if (!result.isSuccess) {
                    cell.cellStyle = errorStyle
                }
                cell.setCellValue(rowDef.get(result.itemApplyFormDetail))
            }
            if (!result.isSuccess) {
                row.createCell(rowDefs.size).setCellValue(result.errorMessage)
            }
        }

        return workbook
    }

    fun updateActivityItems(request: UpdateActivityItemsRequest): Mono<XSSFWorkbook> {
        val sheet = request.workbook.getSheetAt(0)

        val titleRow = sheet.getRow(0)
        rowDefs.withIndex().map { (index, rowDef) ->
            val cell = titleRow.getCell(index)
            if (cell == null || rowDef.name != cell.stringCellValue) {
                throw IllegalArgumentException("Excel 格式错误: 第${index}列应该是 ${rowDef.name}")
            }
        }

        val itemApplyFormDetailList = (1..sheet.lastRowNum).map { rowIndex ->
            val row = sheet.getRow(rowIndex)
            var itemApplyFormDetail = ItemApplyFormDetail.empty
            rowDefs.withIndex().map { (index, rowDef) ->
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

        return itemApplyFormDetailList.withIndex().toFlux()
            .concatMap { (index, itemApplyFormDetail) ->
                println("Updating item ${itemApplyFormDetail.juId} (${index + 1}/${itemApplyFormDetailList.size})")
                updateActivityItem(request, itemApplyFormDetail)
                    .map { Pair<ItemApplyFormDetail, String?>(itemApplyFormDetail, null) }
                    .onErrorResume { e ->
                        println(e.message)
                        Mono.just(Pair(itemApplyFormDetail, e.message))
                    }
            }
            .collectList()
            .flatMap { rows ->
                val results = rows.filter { it.second != null }.map { (itemApplyFormDetail, errorMessage) ->
                    GetItemApplyFormDetailResponse(
                        itemApplyFormDetail = itemApplyFormDetail,
                        isSuccess = false,
                        errorMessage = errorMessage
                    )
                }

                if (results.isNotEmpty()) {
                    println("Found ${results.size} errors")
                    Mono.just(exportItemApplyFormDetailsToWorkbook(results))
                } else {
                    Mono.empty()
                }
            }
    }

    private fun updateActivityItem(request: UpdateActivityItemsRequest, itemApplyFormDetail: ItemApplyFormDetail): Mono<Void> {
        return uploadItemMainPicIfNeed(request, itemApplyFormDetail)
            .flatMap { uploadItemTaobaoMaterialIfNeed(request, it) }
            .flatMap { taoQingCangClient.submitItemApplyForm(request.tbToken, request.cookie2, request.sg, it) }
    }

    val ZIP_PROTOCOL = "zip://"

    private fun getFileFromRequest(request: UpdateActivityItemsRequest, filename: String): ByteArray =
        request.zipContentMap?.get(filename) ?: throw IllegalArgumentException("Zip 压缩包中没有图片 $filename")

    private fun guessContentType(filename: String): MediaType =
        if (filename.endsWith(".png")) {
            MediaType.IMAGE_PNG
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            MediaType.IMAGE_JPEG
        } else {
            throw IllegalArgumentException("只支持 PNG 和 JPG 图片格式")
        }

    private fun uploadItemMainPicIfNeed(request: UpdateActivityItemsRequest, itemApplyFormDetail: ItemApplyFormDetail): Mono<ItemApplyFormDetail> {
        return if (itemApplyFormDetail.itemMainPic.startsWith(ZIP_PROTOCOL)) {
            Mono.create<UploadItemMainPicRequest> { sink ->
                val filename = itemApplyFormDetail.itemMainPic.substring(ZIP_PROTOCOL.length)
                val fileContent = getFileFromRequest(request, filename)
                println("Upload ${filename} for itemMainPic (${itemApplyFormDetail.juId})")

                val headers = HttpHeaders()
                headers.contentType = guessContentType(filename)

                sink.success(UploadItemMainPicRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = itemApplyFormDetail.platformId,
                    itemId = itemApplyFormDetail.itemId,
                    pic = HttpEntity(NamedByteArrayResource(filename, fileContent), headers)
                ))
            }.flatMap {
                taoQingCangClient.uploadItemMainPic(it)
            }.map {
                itemApplyFormDetail.copy(itemMainPic = it)
            }
        } else {
            Mono.just(itemApplyFormDetail)
        }
    }

    private fun uploadItemTaobaoMaterialIfNeed(request: UpdateActivityItemsRequest, itemApplyFormDetail: ItemApplyFormDetail): Mono<ItemApplyFormDetail> {

        return if (itemApplyFormDetail.itemTaobaoAppMaterial.startsWith(ZIP_PROTOCOL)) {
            Mono.create<UploadItemTaobaoAppMaterialRequest> { sink ->
                val filename = itemApplyFormDetail.itemTaobaoAppMaterial.substring(ZIP_PROTOCOL.length)
                val fileContent = getFileFromRequest(request, filename)
                println("Upload ${filename} for itemTaobaoMaterial (${itemApplyFormDetail.juId})")

                val headers = HttpHeaders()
                headers.contentType = guessContentType(filename)

                sink.success(UploadItemTaobaoAppMaterialRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = itemApplyFormDetail.platformId,
                    itemId = itemApplyFormDetail.itemId,
                    activityEnterId = itemApplyFormDetail.activityEnterId,
                    pic = HttpEntity(NamedByteArrayResource(filename, fileContent), headers)
                ))
            }.flatMap {
                taoQingCangClient.uploadItemTaobaoAppMaterial(it)
            }.map {
                itemApplyFormDetail.copy(itemTaobaoAppMaterial = it)
            }
        } else {
            Mono.just(itemApplyFormDetail)
        }
    }
}