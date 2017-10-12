package me.coeuvre.poju.thirdparty.taoqingcang

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jsoup.Jsoup
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class QueryItemsRequest(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val activityEnterId: Long,
        val itemStatusCode: String,
        val actionStatus: String,
        val currentPage: Int,
        val pageSize: Int
)

data class QueryItemsResponse(
        val success: Boolean,
        val message: String,
        val pageSize: Int,
        val totalItem: Int,
        val itemList: List<Item>
)

data class Item(
        val juId: Long,
        val itemId: Long,
        val itemName: String
)

data class GetItemApplyFormDetailRequest(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val juId: Long
)

data class ItemApplyFormDetail(
        val juId: Long,
        val itemId: Long,
        val platformId: Long,
        val activityEnterId: Long,
        val activityId: Long,
        val skuType: String,
        val activityPrice: String,
        val priceType: String,
        val inventoryType: String,
        val itemCount: String,
        val shortTitle: String,
        val itemMainPic: String,
        val itemTaobaoAppMaterial: String,
        val itemTqcNewTag: String,
        val itemHiddenSearchTag: String,
        val payPostage: String,
        val limitNum: String
)

data class SubmitItemApplyFormResponse(
        val success: Boolean,
        val errorType: String,
        val errorInfo: String
)

/**
 * 淘清仓 Web 客户端
 *
 * 登录信息由 cookie: _tb_token_, cookie2, sg 决定
 */
@Service
class TaoQingCangClient {

    fun queryItems(request: QueryItemsRequest): Mono<QueryItemsResponse> = WebClient.create()
            .get()
            .uri("https://tqcfreeway.ju.taobao.com/tg/json/queryItems.htm?" +
                    "_tb_token_=${request.tbToken}&" +
                    "_input_charset=UTF-8&" +
                    "activityEnterId=${request.activityEnterId}&" +
                    "itemStatusCode=${request.itemStatusCode}&" +
                    "actionStatus=${request.actionStatus}&" +
                    "currentPage=${request.currentPage}&" +
                    "pageSize=${request.pageSize}")
            .accept(MediaType.APPLICATION_JSON_UTF8)
            .cookie("_tb_token_", request.tbToken)
            .cookie("cookie2", request.cookie2)
            .cookie("sg", request.sg)
            .exchange()
            .flatMap { response ->
                response.bodyToMono(String::class.java).map { body ->
                    val queryItemsResponse = jacksonObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(body, QueryItemsResponse::class.java)

                    if (!queryItemsResponse.success) {
                        throw IllegalStateException(queryItemsResponse.message)
                    }

                    queryItemsResponse
                }
            }

    fun getItemApplyFormDetail(request: GetItemApplyFormDetailRequest): Mono<ItemApplyFormDetail> = WebClient.create()
            .get()
            .uri("https://tqcfreeway.ju.taobao.com/tg/itemApplyFormDetail.htm?juId=${request.juId}")
            .accept(MediaType.APPLICATION_JSON_UTF8)
            .cookie("_tb_token_", request.tbToken)
            .cookie("cookie2", request.cookie2)
            .cookie("sg", request.sg)
            .exchange()
            .flatMap { response ->
                if (response.statusCode().value() != 200) {
                    throw IllegalArgumentException("Invalid item ${request.juId}")
                }

                response.bodyToMono(String::class.java).map { body ->
                    val doc = Jsoup.parse(body)
                    val form = doc.select("#J_DetailForm")

                    ItemApplyFormDetail(
                            juId = form.select("#juId").`val`().toLong(),
                            itemId = form.select("#itemId").`val`().toLong(),
                            platformId = form.select("#platformId").`val`().toLong(),
                            activityEnterId = form.select("#activityEnterId").`val`().toLong(),
                            activityId = form.select("#activityId").`val`().toLong(),
                            skuType = form.select("""input[name="skuType"][checked]""").`val`(),
                            activityPrice = form.select("#activityPrice").`val`(),
                            priceType = form.select("""input[name="priceType"][checked]""").`val`(),
                            inventoryType = form.select("""input[name="inventoryType"][checked]""").`val`(),
                            itemCount = form.select("#itemCount").`val`(),
                            shortTitle = form.select("#shortTitle").`val`(),
                            itemMainPic = form.select("#itemMainPicval").`val`(),
                            itemTaobaoAppMaterial = form.select("#itemTaobaoAppMaterialval").`val`(),
                            itemTqcNewTag = form.select("""input[name="itemTqcNewTag"][checked]""").`val`(),
                            itemHiddenSearchTag = form.select("#itemHiddenSearchTag").`val`(),
                            payPostage = form.select("#payPostage").`val`(),
                            limitNum = form.select("#limitNum").`val`()
                    )
                }
            }

//    fun submitItemApplyForm(itemApplyFormDetail: ItemApplyFormDetail): Mono<SubmitItemApplyFormResponse> {}
}