package me.coeuvre.poju.thirdparty.taoqingcang

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import me.coeuvre.poju.util.NamedByteArrayResource
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
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
        val limitNum: String,
        val itemBrandName: String
) {
    companion object {
        val empty: ItemApplyFormDetail = ItemApplyFormDetail(
                juId = 0,
                itemId = 0,
                platformId = 0,
                activityEnterId = 0,
                activityId = 0,
                skuType = "",
                activityPrice = "",
                priceType = "",
                inventoryType = "",
                itemCount = "",
                shortTitle = "",
                itemMainPic = "",
                itemTaobaoAppMaterial = "",
                itemTqcNewTag = "",
                itemHiddenSearchTag = "",
                payPostage = "",
                limitNum = "",
                itemBrandName = ""
        )
    }
}

data class SubmitItemApplyFormResponse(
        val success: Boolean,
        val errorType: String?,
        val errorInfo: String?
)

data class UploadItemMainPicRequest(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val platformId: Long,
        val itemId: Long,
        val pic: HttpEntity<NamedByteArrayResource>
)

data class UploadItemTaobaoAppMaterialRequest(
        val tbToken: String,
        val cookie2: String,
        val sg: String,
        val platformId: Long,
        val itemId: Long,
        val activityEnterId: Long,
        val pic: HttpEntity<NamedByteArrayResource>
)

data class UploadImageResponse(
        val status: Int,
        val message: String?,
        val url: String?
)

/**
 * 淘清仓 Web 客户端
 *
 * 登录信息由 cookie: _tb_token_, cookie2, sg 决定
 */
@Service
class TaoQingCangClient(@Autowired val objectMapper: ObjectMapper) {

    fun queryItems(request: QueryItemsRequest): Mono<QueryItemsResponse> {
        return WebClient.create()
                .get()
                .uri("https://tqcfreeway.ju.taobao.com/tg/json/queryItems.htm?" +
                        "_tb_token_=${request.tbToken}&" +
                        "_input_charset=UTF-8&" +
                        "activityEnterId=${request.activityEnterId}&" +
                        "itemStatusCode=${request.itemStatusCode}&" +
                        "actionStatus=${request.actionStatus}&" +
                        "currentPage=${request.currentPage}&" +
                        "pageSize=${request.pageSize}")
                .cookie("_tb_token_", request.tbToken)
                .cookie("cookie2", request.cookie2)
                .cookie("sg", request.sg)
                .retrieve()
                .bodyToMono(String::class.java)
                .map { body ->
                    val queryItemsResponse = objectMapper.readValue(body, QueryItemsResponse::class.java)
                    if (!queryItemsResponse.success) {
                        throw IllegalStateException(queryItemsResponse.message)
                    }
                    queryItemsResponse
                }
    }

    fun getItemApplyFormDetail(request: GetItemApplyFormDetailRequest): Mono<ItemApplyFormDetail> {
        return WebClient.create()
                .get()
                .uri("https://tqcfreeway.ju.taobao.com/tg/itemApplyFormDetail.htm?juId=${request.juId}")
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
                                limitNum = form.select("#limitNum").`val`(),
                                itemBrandName = form.select("#itemBrandName").`val`()
                        )
                    }
                }
    }

    fun submitItemApplyForm(tbToken: String, cookie2: String, sg: String, itemApplyFormDetail: ItemApplyFormDetail): Mono<Void> {
        val params = objectMapper.convertValue<Map<String, String>>(itemApplyFormDetail).filter { it.value.isNotBlank() }
        val additionParams = LinkedMultiValueMap<String, String>()
        additionParams.add("action", "/tg/ItemPostAction")
        additionParams.add("event_submit_do_update", "true")
        additionParams.add("itemApplyResult", "//tqcfreeway.ju.taobao.com/tg/itemApplyResult.htm")
        additionParams.add("_tb_token_", tbToken)

        val multipartData = LinkedMultiValueMap<String, String>()
        params.forEach { (key, value) -> multipartData.add(key, value) }
        multipartData.addAll(additionParams)

        return WebClient.create()
                .post()
                .uri("https://tqcfreeway.ju.taobao.com/tg/itemApplyResult.htm?action=/tg/ItemPostAction&event_submit_do_update=true&_input_charset=UTF-8")
                .cookie("_tb_token_", tbToken)
                .cookie("cookie2", cookie2)
                .cookie("sg", sg)
                .body(BodyInserters.fromMultipartData(multipartData))
                .retrieve()
                .bodyToMono(String::class.java)
                .map { body ->
                    val response = objectMapper.readValue(body, SubmitItemApplyFormResponse::class.java)
                    if (!response.success) {
                        throw IllegalStateException(response.errorInfo)
                    }
                }
                .then()
    }

    fun uploadItemMainPic(request: UploadItemMainPicRequest): Mono<String> {
        val multipartData = LinkedMultiValueMap<String, Any>()
        multipartData.add("wise", "mainPic_${request.platformId}_${request.itemId}")
        multipartData.add("itemPicFile", request.pic)
        return uploadImage(request.tbToken, request.cookie2, request.sg, multipartData)
    }

    fun uploadItemTaobaoAppMaterial(request: UploadItemTaobaoAppMaterialRequest): Mono<String> {
        val multipartData = LinkedMultiValueMap<String, Any>()
        multipartData.add("wise", "hyalineImgPic_${request.platformId}_${request.itemId}_${request.activityEnterId}_0_0_0")
        multipartData.add("itemPicFile", request.pic)
        return uploadImage(request.tbToken, request.cookie2, request.sg, multipartData)
    }

    private fun uploadImage(tbToken: String, cookie2: String, sg: String, multipartData: MultiValueMap<String, *>): Mono<String> =
            WebClient.create()
                    .post()
                    .uri("https://tqcfreeway.ju.taobao.com/tg/json/uploadImageLocal.do?_input_charset=utf-8")
                    .cookie("_tb_token_", tbToken)
                    .cookie("cookie2", cookie2)
                    .cookie("sg", sg)
                    .body(BodyInserters.fromMultipartData(multipartData))
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .map { body ->
                        val response = objectMapper.readValue(body, UploadImageResponse::class.java)
                        if (response.status != 1) {
                            throw IllegalStateException(response.message)
                        }
                        response.url!!
                    }
}