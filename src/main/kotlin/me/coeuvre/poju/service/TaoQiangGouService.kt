package me.coeuvre.poju.service

import me.coeuvre.poju.manager.GetItemApplyFormDetailResponse
import me.coeuvre.poju.manager.JuLikeFlowManager
import me.coeuvre.poju.manager.QueryPagedItemsResponse
import me.coeuvre.poju.manager.RowDef
import me.coeuvre.poju.thirdparty.ju.PublishItemRequest
import me.coeuvre.poju.thirdparty.taoqianggou.*
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.awt.Color

@Service
class TaoQiangGouService(@Autowired val juLikeFlowManager: JuLikeFlowManager, @Autowired val taoQiangGouClient: TaoQiangGouClient) {
    val log = LoggerFactory.getLogger(JuService::class.java)

    private val rowDefs: List<RowDef<ItemApplyFormDetail>> = listOf(
        RowDef("juId", { it.juId }, { item, value -> item.copy(juId = value) }),
        RowDef("商品ID/itemId", { it.itemId }, { item, value -> item.copy(itemId = value) }),
        RowDef("platformId", { it.platformId }, { item, value -> item.copy(platformId = value) }),
        RowDef("活动ID/activityEnterId", { it.activityEnterId }, { item, value -> item.copy(activityEnterId = value) }),
        RowDef("activityId", { it.activityId }, { item, value -> item.copy(activityId = value) }),
        RowDef("报名方式/skuType", { it.skuType }, { item, value -> item.copy(skuType = value) }),
        RowDef("活动价格/activityPrice", { it.activityPrice }, { item, value -> item.copy(activityPrice = value) }),
        RowDef("priceType", { it.priceType }, { item, value -> item.copy(priceType = value) }),
        RowDef("库存类型/inventoryType", { it.inventoryType }, { item, value -> item.copy(inventoryType = value) }),
        RowDef("报名数量/itemCount", { it.itemCount }, { item, value -> item.copy(itemCount = value) }),
        RowDef("宝贝标题/shortTitle", { it.shortTitle }, { item, value -> item.copy(shortTitle = value) }),
        RowDef("图片/itemMainPic", { it.itemMainPic }, { item, value -> item.copy(itemMainPic = value) }),
        RowDef("商品素材图/itemTaobaoAppMaterial", { it.itemTaobaoAppMaterial }, { item, value -> item.copy(itemTaobaoAppMaterial = value) }),
        RowDef("商品利益点/itemBenefitPoints", { it.itemBenefitPoints }, { item, value -> item.copy(itemBenefitPoints = value) }),
        RowDef("是否进口商品/itemBenefitPoints", { it.isImport }, { item, value -> item.copy(isImport = value) }),
        RowDef("运费/payPostage", { it.payPostage }, { item, value -> item.copy(payPostage = value) }),
        RowDef("每个ID限购/limitNum", { it.limitNum }, { item, value -> item.copy(limitNum = value) })
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

        return juLikeFlowManager.exportItemApplyFormDetails({ queryPagedItemsRequest ->
            taoQiangGouClient.queryItems(queryItemsRequestTemplate.copy(
                currentPage = queryPagedItemsRequest.currentPage,
                pageSize = queryPagedItemsRequest.pageSize
            )).map { queryItemsResponse ->
                QueryPagedItemsResponse(
                    currentPage = queryPagedItemsRequest.currentPage,
                    totalCount = queryItemsResponse.totalItem,
                    itemList = queryItemsResponse.itemList
                )
            }
        }, { getItemApplyFormDetailRequest ->
            val item = getItemApplyFormDetailRequest.item
            taoQiangGouClient.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                tbToken = request.tbToken,
                cookie2 = request.cookie2,
                sg = request.sg,
                juId = item.juId
            )).map { GetItemApplyFormDetailResponse(it, true, null) }.onErrorResume { e ->
                Mono.just(GetItemApplyFormDetailResponse(
                    itemApplyFormDetail = ItemApplyFormDetail.empty.copy(juId = item.juId, itemId = item.itemId, shortTitle = item.itemName),
                    isSuccess = false,
                    errorMessage = e.message
                ))
            }
        }, rowDefs)
    }

    fun updateItemApplyFormDetail(request: UpdateItemApplyFormDetail): Mono<XSSFWorkbook> = juLikeFlowManager.updateItemApplyFormDetails(
        request.workbook,
        request.zipImagesMap,
        rowDefs,
        ItemApplyFormDetail.empty,
        { uploadImageRequest ->
            val name = uploadImageRequest.rowDef.name
            val item = uploadImageRequest.item
            when {
                name.contains("itemMainPic") -> taoQiangGouClient.uploadItemMainPic(UploadItemMainPicRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = item.platformId,
                    itemId = item.itemId,
                    pic = uploadImageRequest.image
                ))
                name.contains("itemTaobaoAppMaterial") -> taoQiangGouClient.uploadItemTaobaoAppMaterial(UploadItemTaobaoAppMaterialRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = item.platformId,
                    itemId = item.itemId,
                    activityEnterId = item.activityEnterId,
                    pic = uploadImageRequest.image
                ))
                else -> Mono.error(IllegalArgumentException("字段 $name 不支持上传图片"))
            }
        },
        { updateItemApplyFormDetailRequest ->
            taoQiangGouClient.submitItemApplyForm(request.tbToken, request.cookie2, request.sg, updateItemApplyFormDetailRequest.item)
        }
    )

    fun publishItems(request: PublishItemsRequest): Mono<XSSFWorkbook?> {
        log.info("Starting publish items")
        return taoQiangGouClient.queryItems(QueryItemsRequest(request.tbToken, request.cookie2, request.sg, request.activityEnterId, request.itemStatusCode, request.actionStatus, 1, 0))
            .map { it.totalItem }
            .flatMapMany { totalCount ->
                log.info("Publishing $totalCount items")
                val pageSize = 20
                val pageCount = Math.ceil(totalCount * 1.0 / pageSize).toInt()
                (1..pageCount).map { currentPage ->
                    QueryItemsRequest(request.tbToken, request.cookie2, request.sg, request.activityEnterId, request.itemStatusCode, request.actionStatus, currentPage, pageSize)
                }.toFlux().flatMapSequential { queryItemsRequest ->
                    log.info("Fetching page ${queryItemsRequest.currentPage}/$pageCount")
                    taoQiangGouClient.queryItems(queryItemsRequest).map { Triple(totalCount, queryItemsRequest, it) }
                }
            }
            .flatMapSequential { (totalCount, queryItemsRequest, queryItemsResponse) ->
                queryItemsResponse.itemList.withIndex().map { (index, item) -> Triple(index + 1 + (queryItemsRequest.currentPage - 1) * queryItemsRequest.pageSize, totalCount, item) }.toFlux()
            }
            .flatMapSequential({ (index, count, item) ->
                log.info("Publishing item ${item.juId} ($index/$count)")
                taoQiangGouClient.publishItem(PublishItemRequest(request.tbToken, request.cookie2, request.sg, item.juId))
                    .map { _ -> Pair<Item, String?>(item, null) }
                    .onErrorResume { e -> log.error(e.message); Mono.just(Pair(item, e.message)) }
            }, 1)
            .collectList()
            .map { list ->
                val result = list.filter { it.second != null }

                if (result.isEmpty()) {
                    return@map null
                }

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
                val rowDefs = listOf(
                    RowDef<Item>("juId", { it.juId }, { item, value -> item.copy(juId = value) }),
                    RowDef("商品ID/itemId", { it.itemId }, { item, value -> item.copy(itemId = value) }),
                    RowDef("商品名称/itemName", { it.itemName }, { item, value -> item.copy(itemName = value) })
                )

                rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                    val cell = titleRow.createCell(cellIndex)
                    cell.cellStyle = titleStyle
                    cell.setCellValue(rowDef.name)
                }

                result.withIndex().forEach { (rowIndex, result) ->
                    val row = sheet.createRow(1 + rowIndex)
                    rowDefs.withIndex().forEach { (cellIndex, rowDef) ->
                        val cell = row.createCell(cellIndex)
                        cell.cellStyle = errorStyle
                        cell.setCellValue(rowDef.get(result.first))
                    }
                    row.createCell(rowDefs.size).setCellValue(result.second)
                }

                workbook
            }
    }
}