package me.coeuvre.poju.thirdparty.ju

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.net.URI

data class ItemApplyFormDetail(
    val juId: String,
    val itemId: String,
    val platformId: String,
    val activityEnterId: String,
    val skuType: String,
    val activityPrice: String,
    val priceType: String,
    val inventoryType: String,
    val itemCount: String,
    val currentCount: String,
    val shortTitle: String,
    val smallTitle: String,
    val itemMainPic: String,
    val itemExtraPic1: String,
    val itemExtraPic2: String,
    val itemExtraPic3: String,
    val itemExtraPic4: String,
    val itemWireMainPic: String,
    val itemTaobaoAppMaterial: String,
    val feature1: String,
    val featureDesc1: String,
    val feature2: String,
    val featureDesc2: String,
    val feature3: String,
    val featureDesc3: String,
    val sellPoint: String,
    @JsonProperty("isImport")
    val import: String,
    val payPostage: String,
    val limitNum: String,
    val itemDesc: String,
    val itemBrandName: String,
    val itemBrandLogo: String,
    @JsonProperty("DC_SPMD")
    val dcSpmd: String,
    @JsonProperty("DC_TJLY")
    val dcTjly: String,
    val bimaiReason: String,
    @JsonProperty("TOP_SELL_POINTS")
    val topSellPoints: String
) {
    companion object {
        val empty: ItemApplyFormDetail = ItemApplyFormDetail(
            juId = "",
            itemId = "",
            platformId = "",
            activityEnterId = "",
            skuType = "",
            activityPrice = "",
            priceType = "",
            inventoryType = "",
            itemCount = "",
            currentCount = "",
            shortTitle = "",
            smallTitle = "",
            itemMainPic = "",
            itemExtraPic1 = "",
            itemExtraPic2 = "",
            itemExtraPic3 = "",
            itemExtraPic4 = "",
            itemWireMainPic = "",
            itemTaobaoAppMaterial = "",
            feature1 = "",
            featureDesc1 = "",
            feature2 = "",
            featureDesc2 = "",
            feature3 = "",
            featureDesc3 = "",
            sellPoint = "",
            import = "",
            payPostage = "",
            limitNum = "",
            itemDesc = "",
            itemBrandName = "",
            itemBrandLogo = "",
            dcSpmd = "",
            dcTjly = "",
            bimaiReason = "",
            topSellPoints = ""
        )
    }
}

data class QueryItemsRequest(
    val cookie2: String,
    val tbToken: String,
    val sg: String,
    val activityEnterId: String,
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
    val itemList: List<QueryItemsResponseItem>
)

data class QueryItemsResponseItem(
    val juId: String,
    val itemId: String,
    val itemName: String
)

@Service
class JuClient(@Autowired private val objectMapper: ObjectMapper) {

    fun queryItems(request: QueryItemsRequest): Mono<QueryItemsResponse> {
        val builder = UriComponentsBuilder.fromHttpUrl("https://freeway.ju.taobao.com/tg/json/queryItems.htm")
        builder.queryParam("_input_charset", "UTF-8")
        builder.queryParam("_tb_token_", request.tbToken)
        builder.queryParam("activityEnterId", request.activityEnterId)
        builder.queryParam("itemStatusCode", request.itemStatusCode)
        builder.queryParam("actionStatus", request.actionStatus)
        builder.queryParam("currentPage", request.currentPage)
        builder.queryParam("pageSize", request.pageSize)
        return doRequest(request.cookie2, request.tbToken, request.sg, builder.build().encode().toUri())
            .flatMap { response ->
                response.bodyToMono(String::class.java)
                    .map { body ->
                        if (!response.statusCode().is2xxSuccessful) {
                            throw IllegalStateException(body)
                        }
                        val queryItemsResponse: QueryItemsResponse
                        try {
                            queryItemsResponse = objectMapper.readValue(body, QueryItemsResponse::class.java)
                        } catch (e: Exception) {
                            throw IllegalStateException(e.message)
                        }

                        if (!queryItemsResponse.success) {
                            throw IllegalStateException(queryItemsResponse.message)
                        }

                        queryItemsResponse
                    }
            }
    }

    fun getItemApplyFormDetail(cookie2: String, tbToken: String, sg: String, juId: String): Mono<ItemApplyFormDetail> {
        val builder = UriComponentsBuilder.fromHttpUrl("https://freeway.ju.taobao.com/tg/itemApplyFormDetail.htm")
        builder.queryParam("_input_charset", "UTF-8")
        builder.queryParam("juId", juId)
        return doRequest(cookie2, tbToken, sg, builder.build().encode().toUri())
            .flatMap { response ->
                if (response.statusCode().value() != 200) {
                    throw IllegalArgumentException("Invalid item " + juId)
                }

                response.bodyToMono(String::class.java)
                    .map { body ->
                        val document = Jsoup.parse(body)
                        val form = document.select("#J_DetailForm")
                        var itemApplyFormDetail = ItemApplyFormDetail.empty
                        val keys = objectMapper.convertValue<Map<String, String>>(itemApplyFormDetail).keys
                        val map = keys.map { name ->
                            val checkedInput = form.select("input[name=\"$name\"][checked]")
                            val value = when (name) {
                                "currentCount" -> form.select("input[name=\"itemCount\"]").next().select(".c-primary").text()
                                "itemDesc" -> form.select("textarea[name=\"itemDesc\"]").text()
                                else -> {
                                    if (!checkedInput.isEmpty()) {
                                        checkedInput.`val`()
                                    } else {
                                        form.select("input[name=\"$name\"]").`val`()
                                    }
                                }
                            }
                            Pair(name, value)
                        }.toMap()
                        itemApplyFormDetail = objectMapper.convertValue(map)
                        itemApplyFormDetail
                    }
            }
    }

    private fun doRequest(cookie2: String, tbToken: String, sg: String, uri: URI): Mono<ClientResponse> {
        return WebClient.create()
            .get()
            .uri(uri)
            .cookie("_tb_token_", tbToken)
            .cookie("cookie2", cookie2)
            .cookie("sg", sg)
            .exchange()

    }
}
