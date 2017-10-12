package me.coeuvre.poju.service

import me.coeuvre.poju.thirdparty.taoqingcang.*
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
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
        val workbook: XSSFWorkbook
)

@Service
class TaoQingCangService(@Autowired val taoQingCangClient: TaoQingCangClient) {
    private data class GetItemApplyFormDetailResult(
            val itemApplyFormDetail: ItemApplyFormDetail,
            val isSuccess: Boolean,
            val errorMessage: String?
    )

    private data class RowDef(
            val name: String,
            val get: Function1<ItemApplyFormDetail, String>,
            val set: Function2<ItemApplyFormDetail, String, ItemApplyFormDetail>
    )

    private val rowDefs = listOf(
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

    fun exportActivityItems(request: ExportActivityItemsRequest): Mono<XSSFWorkbook> {
        return queryItemsTotalItem(request)
                .flatMap { totalItem -> queryAllItems(request, totalItem) }
                .flatMap { itemList -> getAllItemApplyFormDetails(request, itemList) }
                .map { exportItemApplyFormDetailsToWorkbook(it) }
    }

    private fun queryItemsTotalItem(request: ExportActivityItemsRequest): Mono<Int> {
        val queryItemsRequest = QueryItemsRequest(
                tbToken = request.tbToken,
                cookie2 = request.cookie2,
                sg = request.sg,
                activityEnterId = request.activityEnterId,
                itemStatusCode = request.itemStatusCode,
                actionStatus = request.actionStatus,
                currentPage = 1,
                pageSize = 0
        )

        return taoQingCangClient.queryItems(queryItemsRequest).map { it.totalItem }
    }

    private fun queryAllItems(request: ExportActivityItemsRequest, totalItem: Int): Mono<List<Item>> {
        val pageSize = 20
        val pageCount = Math.ceil(totalItem * 1.0 / pageSize).toInt()
        println("Activity(${request.activityEnterId}) has $totalItem items ($pageCount pages)")

        val queryItemsRequestList = (1..pageCount).map { currentPage ->
            QueryItemsRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    activityEnterId = request.activityEnterId,
                    itemStatusCode = request.itemStatusCode,
                    actionStatus = request.actionStatus,
                    currentPage = currentPage,
                    pageSize = pageSize
            )
        }

        return queryItemsRequestList.toFlux()
                .flatMap { queryItemsRequest ->
                    println("Fetching page ${queryItemsRequest.currentPage}/$pageCount")
                    taoQingCangClient.queryItems(queryItemsRequest)
                            .map { response ->
                                if (response == null || !response.success) {
                                    throw IllegalStateException(response?.message)
                                }
                                Pair(queryItemsRequest, response.itemList)
                            }
                }
                .collectSortedList { a, b -> a.first.currentPage.compareTo(b.first.currentPage) }
                .map { it.map { it.second }.flatten() }
    }

    private fun getAllItemApplyFormDetails(request: ExportActivityItemsRequest, itemList: List<Item>): Mono<List<GetItemApplyFormDetailResult>> {
        return itemList.withIndex().toFlux()
                .flatMap { (index, item) ->
                    println("Fetching item ${item.juId} (${index + 1}/${itemList.count()})")
                    taoQingCangClient.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                            tbToken = request.tbToken,
                            cookie2 = request.cookie2,
                            sg = request.sg,
                            juId = item.juId
                    )).map { itemApplyFormDetail ->
                        Pair(index, GetItemApplyFormDetailResult(itemApplyFormDetail = itemApplyFormDetail, isSuccess = true, errorMessage = null))
                    }.onErrorResume { e ->
                        println(e.message)
                        Mono.just(Pair(index, GetItemApplyFormDetailResult(
                                itemApplyFormDetail = ItemApplyFormDetail.empty.copy(juId = item.juId, itemId = item.itemId, shortTitle = item.itemName),
                                isSuccess = false,
                                errorMessage = e.message
                        )))
                    }
                }
                .collectSortedList { a, b -> a.first.compareTo(b.first) }
                .map { it.map { it.second } }
    }

    private fun exportItemApplyFormDetailsToWorkbook(getItemApplyFormDetailResult: List<GetItemApplyFormDetailResult>): XSSFWorkbook {
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

    fun updateActivityItems(request: UpdateActivityItemsRequest): Mono<XSSFWorkbook?> {
        val sheet = request.workbook.getSheetAt(0)

        val titleRow = sheet.getRow(0)
        val isValidInput = rowDefs.withIndex().map { (index, rowDef) ->
            val cell = titleRow.getCell(index)
            cell != null && rowDef.name == cell.stringCellValue
        }.reduce { a, b -> a && b }

        if (!isValidInput) {
            throw IllegalArgumentException("Excel 格式错误")
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
                .flatMap { (index, itemApplyFormDetail) ->
                    println("Updating item ${itemApplyFormDetail.juId} (${index + 1}/${itemApplyFormDetailList.size})")
                    taoQingCangClient.submitItemApplyForm(request.tbToken, request.cookie2, request.sg, itemApplyFormDetail)
                            .map { response ->
                                Triple(index, itemApplyFormDetail, response)
                            }
                }
                .collectSortedList { a, b -> a.first.compareTo(b.first) }
                .map { it.map { Pair(it.second, it.third) } }
                .map { rows ->
                    val results = rows.filter { !it.second.success }.map { (itemApplyFormDetail, submitItemApplyFormResponse) ->
                        GetItemApplyFormDetailResult(
                                itemApplyFormDetail = itemApplyFormDetail,
                                isSuccess = submitItemApplyFormResponse.success,
                                errorMessage = submitItemApplyFormResponse.errorInfo
                        )
                    }

                    if (results.isNotEmpty()) {
                        println("Found ${results.size} errors")
                        exportItemApplyFormDetailsToWorkbook(results)
                    } else {
                        null
                    }
                }
    }
}