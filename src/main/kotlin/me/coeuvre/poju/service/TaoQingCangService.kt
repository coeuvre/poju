package me.coeuvre.poju.service

import me.coeuvre.poju.thirdparty.taoqingcang.*
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.awt.Color

data class ExportActivityItemsRequest(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val activityEnterId: Long,
        val itemStatusCode: String,
        val actionStatus: String
)

data class RowDef(
        val name: String,
        val writeValue: Function1<ItemApplyFormDetail, String>
)

@Service
class TaoQingCangService(@Autowired val taoQingCangClient: TaoQingCangClient) {
    private data class GetItemApplyFormDetailResult(
            val item: Item,
            val isSuccess: Boolean,
            val errorMessage: String?,
            val itemApplyFormDetail: ItemApplyFormDetail?
    )

    fun exportActivityItems(request: ExportActivityItemsRequest): Mono<XSSFWorkbook> {
        return queryItemsTotalItem(request)
                .flatMap { totalItem -> queryAllItems(request, totalItem) }
                .flatMap { itemList -> getAllItemApplyFormDetails(request, itemList) }
                .map { results ->
                    val workbook = XSSFWorkbook()
                    val sheet = workbook.createSheet()

                    val titleStyle = workbook.createCellStyle()
                    val font = workbook.createFont()
                    font.setColor(XSSFColor(Color(255, 255, 255)));
                    titleStyle.setFont(font)
                    titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
                    titleStyle.setFillForegroundColor(XSSFColor(Color(0, 0, 0)))

                    val errorStyle = workbook.createCellStyle()
                    errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
                    errorStyle.setFillForegroundColor(XSSFColor(Color(255, 199, 206)))

                    val rowDefs = listOf(
                            RowDef("juId", { it.juId.toString() }),
                            RowDef("商品ID/itemId", { it.itemId.toString() }),
                            RowDef("platformId", { it.platformId.toString() }),
                            RowDef("活动ID/activityEnterId", { it.activityEnterId.toString() }),
                            RowDef("activityId", { it.activityId.toString() }),
                            RowDef("报名方式/skuType", { it.skuType }),
                            RowDef("活动价格/activityPrice", { it.activityPrice }),
                            RowDef("priceType", { it.priceType }),
                            RowDef("库存类型/inventoryType", { it.inventoryType }),
                            RowDef("报名数量/itemCount", { it.itemCount }),
                            RowDef("宝贝标题/shortTitle", { it.shortTitle }),
                            RowDef("透明底模特图/itemMainPic", { it.itemMainPic }),
                            RowDef("透明底平铺图/itemTaobaoAppMaterial", { it.itemTaobaoAppMaterial }),
                            RowDef("新版商品标签/itemTqcNewTag", { it.itemTqcNewTag }),
                            RowDef("设置隐藏选项/itemHiddenSearchTag", { it.itemHiddenSearchTag }),
                            RowDef("运费/payPostage", { it.payPostage }),
                            RowDef("每个ID限购/limitNum", { it.limitNum })
                    )

                    // Create title row
                    val titleRow = sheet.createRow(0)
                    rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                        val cell = titleRow.createCell(cellIndex)
                        cell.cellStyle = titleStyle
                        cell.setCellValue(rowDef.name)
                    }

                    results.withIndex().forEach { (rowIndex, result) ->
                        val row = sheet.createRow(1 + rowIndex)
                        if (result.isSuccess) {
                            rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                                val cell = row.createCell(cellIndex)
                                cell.setCellValue(rowDef.writeValue(result.itemApplyFormDetail!!))
                            }
                        } else {
                            rowDefs.withIndex().forEach { (cellIndex, _) ->
                                val cell = row.createCell(cellIndex)
                                cell.cellStyle = errorStyle
                                cell.setCellValue(when (cellIndex) {
                                    0 -> result.item.juId.toString()
                                    1 -> result.item.itemId.toString()
                                    3 -> request.activityEnterId.toString()
                                    10 -> result.item.itemName
                                    else -> ""
                                })
                            }
                            row.createCell(rowDefs.size).setCellValue(result.errorMessage)
                        }
                    }

                    workbook
                }
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

        return Flux.fromIterable(queryItemsRequestList)
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
        return Flux.fromIterable(itemList.withIndex())
                .flatMap { (index, item) ->
                    println("Fetching item ${item.juId} (${index + 1}/${itemList.count()})")
                    taoQingCangClient.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                            tbToken = request.tbToken,
                            cookie2 = request.cookie2,
                            sg = request.sg,
                            juId = item.juId
                    )).map { itemApplyFormDetail ->
                        Pair(index, GetItemApplyFormDetailResult(item = item, isSuccess = true, itemApplyFormDetail = itemApplyFormDetail, errorMessage = null))
                    }.onErrorResume { e ->
                        println(e.message)
                        Mono.just(Pair(index, GetItemApplyFormDetailResult(item = item, isSuccess = false, itemApplyFormDetail = null, errorMessage = e.message)))
                    }
                }
                .collectSortedList { a, b -> a.first.compareTo(b.first) }
                .map { it.map { it.second } }
    }
}