package me.coeuvre.poju.service

import me.coeuvre.poju.manager.GetItemApplyFormDetailResponse
import me.coeuvre.poju.manager.JuLikeFlowManager
import me.coeuvre.poju.manager.QueryPagedItemsResponse
import me.coeuvre.poju.manager.RowDef
import me.coeuvre.poju.thirdparty.ju.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

data class ExportItemApplyFormDetailsRequest(
    val cookie2: String,
    val tbToken: String,
    val sg: String,
    val activityEnterId: String,
    val itemStatusCode: String,
    val actionStatus: String
)

@Service
class JuService(@Autowired val manager: JuLikeFlowManager, @Autowired val client: JuClient) {

    private val rowDefs = listOf(
        RowDef<ItemApplyFormDetail>("juId", { it.juId }, { item, value -> item.copy(juId = value) }),
        RowDef<ItemApplyFormDetail>("商品ID/itemId", { it.itemId }, { item, value -> item.copy(itemId = value) }),
        RowDef<ItemApplyFormDetail>("platformId", { it.platformId }, { item, value -> item.copy(platformId = value) }),
        RowDef<ItemApplyFormDetail>("活动ID/activityEnterId", { it.activityEnterId }, { item, value -> item.copy(activityEnterId = value) }),
        RowDef<ItemApplyFormDetail>("报名方式/skuType", { it.skuType }, { item, value -> item.copy(skuType = value) }),
        RowDef<ItemApplyFormDetail>("活动价格/activityPrice", { it.activityPrice }, { item, value -> item.copy(activityPrice = value) }),
        RowDef<ItemApplyFormDetail>("价格方式/priceType", { it.priceType }, { item, value -> item.copy(priceType = value) }),
        RowDef<ItemApplyFormDetail>("库存类型/inventoryType", { it.inventoryType }, { item, value -> item.copy(inventoryType = value) }),
        RowDef<ItemApplyFormDetail>("报名数量/itemCount", { it.itemCount }, { item, value -> item.copy(itemCount = value) }),
        RowDef<ItemApplyFormDetail>("当前库存量/currentCount", { it.currentCount }, { item, value -> item.copy(currentCount = value) }),
        RowDef<ItemApplyFormDetail>("宝贝标题/shortTitle", { it.shortTitle }, { item, value -> item.copy(shortTitle = value) }),
        RowDef<ItemApplyFormDetail>("短标题/smallTitle", { it.smallTitle }, { item, value -> item.copy(smallTitle = value) }),
        RowDef<ItemApplyFormDetail>("主图/itemMainPic", { it.itemMainPic }, { item, value -> item.copy(itemMainPic = value) }),
        RowDef<ItemApplyFormDetail>("辅图1/itemExtraPic1", { it.itemExtraPic1 }, { item, value -> item.copy(itemExtraPic1 = value) }),
        RowDef<ItemApplyFormDetail>("辅图2/itemExtraPic2", { it.itemExtraPic2 }, { item, value -> item.copy(itemExtraPic2 = value) }),
        RowDef<ItemApplyFormDetail>("辅图3/itemExtraPic3", { it.itemExtraPic3 }, { item, value -> item.copy(itemExtraPic3 = value) }),
        RowDef<ItemApplyFormDetail>("辅图4/itemExtraPic4", { it.itemExtraPic4 }, { item, value -> item.copy(itemExtraPic4 = value) }),
        RowDef<ItemApplyFormDetail>("无线主图/itemWireMainPic", { it.itemWireMainPic }, { item, value -> item.copy(itemWireMainPic = value) }),
        RowDef<ItemApplyFormDetail>("商品素材图/itemTaobaoAppMaterial", { it.itemTaobaoAppMaterial }, { item, value -> item.copy(itemTaobaoAppMaterial = value) }),
        RowDef<ItemApplyFormDetail>("卖点1/feature1", { it.feature1 }, { item, value -> item.copy(feature1 = value) }),
        RowDef<ItemApplyFormDetail>("描述1/featureDesc1", { it.featureDesc1 }, { item, value -> item.copy(featureDesc1 = value) }),
        RowDef<ItemApplyFormDetail>("卖点2/feature2", { it.feature2 }, { item, value -> item.copy(feature2 = value) }),
        RowDef<ItemApplyFormDetail>("描述2/featureDesc2", { it.featureDesc2 }, { item, value -> item.copy(featureDesc2 = value) }),
        RowDef<ItemApplyFormDetail>("卖点3/feature3", { it.feature3 }, { item, value -> item.copy(feature3 = value) }),
        RowDef<ItemApplyFormDetail>("描述4/featureDesc3", { it.featureDesc3 }, { item, value -> item.copy(featureDesc3 = value) }),
        RowDef<ItemApplyFormDetail>("价格卖点/sellPoint", { it.sellPoint }, { item, value -> item.copy(sellPoint = value) }),
        RowDef<ItemApplyFormDetail>("是否进口商品/isImport", { it.import }, { item, value -> item.copy(import = value) }),
        RowDef<ItemApplyFormDetail>("运费/payPostage", { it.payPostage }, { item, value -> item.copy(payPostage = value) }),
        RowDef<ItemApplyFormDetail>("每个ID限购/limitNum", { it.limitNum }, { item, value -> item.copy(limitNum = value) }),
        RowDef<ItemApplyFormDetail>("宝贝描述/itemDesc", { it.itemDesc }, { item, value -> item.copy(itemDesc = value) }),
        RowDef<ItemApplyFormDetail>("品牌名称/itemBrandName", { it.itemBrandName }, { item, value -> item.copy(itemBrandName = value) }),
        RowDef<ItemApplyFormDetail>("品牌Logo/itemBrandLogo", { it.itemBrandLogo }, { item, value -> item.copy(itemBrandLogo = value) }),
        RowDef<ItemApplyFormDetail>("大促卖点/DC_SPMD", { it.dcSpmd }, { item, value -> item.copy(dcSpmd = value) }),
        RowDef<ItemApplyFormDetail>("大促推荐理由/DC_TJLY", { it.dcTjly }, { item, value -> item.copy(dcTjly = value) }),
        RowDef<ItemApplyFormDetail>("必买理由/bimaiReason", { it.bimaiReason }, { item, value -> item.copy(bimaiReason = value) }),
        RowDef<ItemApplyFormDetail>("尖货卖点/TOP_SELL_POINTS", { it.topSellPoints }, { item, value -> item.copy(topSellPoints = value) })
    )

    fun exportItemApplyFormDetails(request: ExportItemApplyFormDetailsRequest): Mono<XSSFWorkbook> {
        val queryItemsRequestTemplate = QueryItemsRequest(
            cookie2 = request.cookie2,
            tbToken = request.tbToken,
            sg = request.sg,
            activityEnterId = request.activityEnterId,
            itemStatusCode = request.itemStatusCode,
            actionStatus = request.actionStatus,
            currentPage = 1,
            pageSize = 0
        )

        return manager.exportItemApplyFormDetails({
            client.queryItems(queryItemsRequestTemplate).map {
                println("Activity(${request.activityEnterId}) has ${it.totalItem} items")
                it.totalItem
            }
        }, { queryPagedItemsRequest ->
            println("Fetching page ${queryPagedItemsRequest.currentPage}/${queryPagedItemsRequest.pageCount}")

            client.queryItems(queryItemsRequestTemplate.copy(
                currentPage = queryPagedItemsRequest.currentPage,
                pageSize = queryPagedItemsRequest.pageSize)
            ).map { queryItemsResponse ->
                QueryPagedItemsResponse(
                    currentPage = queryPagedItemsRequest.currentPage,
                    itemList = queryItemsResponse.itemList,
                    totalCount = queryItemsResponse.totalItem
                )
            }
        }, { getItemApplyFormDetailRequest ->
            val item = getItemApplyFormDetailRequest.item
            println("Fetching item ${item.juId} (${getItemApplyFormDetailRequest.currentCount}/${getItemApplyFormDetailRequest.totalCount})")
            client.getItemApplyFormDetail(request.cookie2, request.tbToken, request.sg, item.juId)
                .map { GetItemApplyFormDetailResponse(it, true, null) }
                .onErrorResume { e ->
                    println(e.message)
                    Mono.just(GetItemApplyFormDetailResponse(
                        itemApplyFormDetail = ItemApplyFormDetail.empty.copy(juId = item.juId, itemId = item.itemId, shortTitle = item.itemName),
                        isSuccess = false,
                        errorMessage = e.message
                    ))
                }
        }, rowDefs)
    }
}