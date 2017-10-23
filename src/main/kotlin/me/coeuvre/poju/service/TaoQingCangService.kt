package me.coeuvre.poju.service

import me.coeuvre.poju.manager.GetItemApplyFormDetailResponse
import me.coeuvre.poju.manager.JuLikeFlowManager
import me.coeuvre.poju.manager.QueryPagedItemsResponse
import me.coeuvre.poju.manager.RowDef
import me.coeuvre.poju.thirdparty.taoqingcang.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

data class ExportActivityItemsRequest(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val activityEnterId: Long,
    val itemStatusCode: String,
    val actionStatus: String
)

data class UpdateItemApplyFormDetail(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val workbook: XSSFWorkbook,
    val zipImagesMap: Map<String, ByteArray>?
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
        RowDef("品牌名称/itemBrandName", { it.itemBrandName }, { item, value -> item.copy(itemBrandName = value) }),
        RowDef("品牌Logo/itemBrandLogo", { it.itemBrandLogo }, { item, value -> item.copy(itemBrandLogo = value) })
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
            taoQingCangClient.queryItems(queryItemsRequestTemplate.copy(
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
            taoQingCangClient.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
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
                name.contains("itemMainPic") -> taoQingCangClient.uploadItemMainPic(UploadItemMainPicRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = item.platformId,
                    itemId = item.itemId,
                    pic = uploadImageRequest.image
                ))
                name.contains("itemTaobaoAppMaterial") -> taoQingCangClient.uploadItemTaobaoAppMaterial(UploadItemTaobaoAppMaterialRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = item.platformId,
                    itemId = item.itemId,
                    activityEnterId = item.activityEnterId,
                    pic = uploadImageRequest.image
                ))
                name.contains("itemBrandLogo") -> taoQingCangClient.uploadItemBrandLogo(UploadItemBrandLogoRequest(
                    tbToken = request.tbToken,
                    cookie2 = request.cookie2,
                    sg = request.sg,
                    platformId = item.platformId,
                    itemId = item.itemId,
                    pic = uploadImageRequest.image
                ))
                else -> Mono.error(IllegalArgumentException("字段 $name 不支持上传图片"))
            }
        },
        { updateItemApplyFormDetailRequest ->
            taoQingCangClient.submitItemApplyForm(request.tbToken, request.cookie2, request.sg, updateItemApplyFormDetailRequest.item)
        }
    )
}