package me.coeuvre.poju.thirdparty.ju

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import me.coeuvre.poju.util.NamedByteArrayResource
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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

data class SubmitItemApplyFormResponse(
    val success: Boolean,
    val errorType: String?,
    val errorInfo: String?
)

data class UploadItemTaobaoAppMaterialRequest(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val platformId: String,
    val itemId: String,
    val activityEnterId: String,
    val pic: HttpEntity<NamedByteArrayResource>
)

data class UploadImageResponse(
    val status: Int,
    val message: String?,
    val url: String?
)

data class PublishItemRequest(
    val tbToken: String,
    val cookie2: String,
    val sg: String,
    val juId: String
)

@Service
class JuClient @Autowired constructor(private val objectMapper: ObjectMapper, private val webClient: WebClient) {

    fun queryItems(request: QueryItemsRequest): Mono<QueryItemsResponse> {
        val builder = UriComponentsBuilder.fromHttpUrl("https://freeway.ju.taobao.com/tg/json/queryItems.htm")
        builder.queryParam("_input_charset", "UTF-8")
        builder.queryParam("_tb_token_", request.tbToken)
        builder.queryParam("activityEnterId", request.activityEnterId)
        builder.queryParam("itemStatusCode", request.itemStatusCode)
        builder.queryParam("actionStatus", request.actionStatus)
        builder.queryParam("currentPage", request.currentPage)
        builder.queryParam("pageSize", request.pageSize)
        return doGetRequest(request.cookie2, request.tbToken, request.sg, builder.build().encode().toUri())
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
        return doGetRequest(cookie2, tbToken, sg, builder.build().encode().toUri())
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

    fun publishItem(request: PublishItemRequest): Mono<Void> {
        val builder = UriComponentsBuilder.fromHttpUrl("https://freeway.ju.taobao.com/tg/ItemPublishError")
        builder.queryParam("juid", request.juId)
        builder.queryParam("_tb_token_", request.tbToken)
        return doGetRequest(request.cookie2, request.tbToken, request.sg, builder.build().encode().toUri())
            .flatMap { response ->
                if (response.statusCode().is3xxRedirection) {
                    val location = response.headers().header("Location")[0]
                    if (location.contains("seller_error")) {
                        doGetRequest(request.cookie2, request.tbToken, request.sg, URI.create(location)).flatMap { r ->
                            r.bodyToMono<String>().map { body ->
                                val doc = Jsoup.parse(body)
                                val message = doc.select(".state-notice").text()
                                throw IllegalArgumentException(message)
                            }
                        }
                    } else {
                        Mono.empty()
                    }
                } else if (response.statusCode().is2xxSuccessful) {
                    response.bodyToMono<String>().map { body ->
                        val doc = Jsoup.parse(body)
                        val rows = doc.select(".exception_main_info").select("tbody").select("tr")
                        val errors = rows.map { row -> row.select("td").first().text() }
                        throw IllegalArgumentException(errors.joinToString(", "))
                    }
                } else {
                    throw IllegalStateException()
                }
            }
            .then()
    }

    fun submitItemApplyForm(cookie2: String, tbToken: String, sg: String, itemApplyFormDetail: ItemApplyFormDetail): Mono<Void> {
        val multipartData = LinkedMultiValueMap<String, String>()
        objectMapper.convertValue<Map<String, String>>(itemApplyFormDetail).forEach { (name, value) ->
            multipartData.add(name, value)
        }
        multipartData.add("action", "/tg/ItemPostAction")
        multipartData.add("event_submit_do_update", "true")
        multipartData.add("_tb_token_", tbToken)

        return webClient.post()
            .uri("https://freeway.ju.taobao.com/tg/json/queryItems.htm?_input_charset=UTF-8")
            .cookie("_tb_token_", tbToken)
            .cookie("cookie2", cookie2)
            .cookie("sg", sg)
            .body(BodyInserters.fromMultipartData(multipartData))
            .exchange()
            .flatMap { response ->
                response.bodyToMono<String>().map { body ->
                    if (!response.statusCode().is2xxSuccessful) {
                        throw IllegalStateException(body)
                    }

                    val submitItemApplyFormResponse = objectMapper.readValue<SubmitItemApplyFormResponse>(body)
                    if (!submitItemApplyFormResponse.success) {
                        throw IllegalStateException(submitItemApplyFormResponse.errorInfo)
                    }
                }
            }
            .then()
    }

    private fun doGetRequest(cookie2: String, tbToken: String, sg: String, uri: URI): Mono<ClientResponse> {
        return webClient.get()
            .uri(uri)
            .cookie("_tb_token_", tbToken)
            .cookie("cookie2", cookie2)
            .cookie("sg", sg)
            .exchange()

    }

    fun uploadItemTaobaoAppMaterial(request: UploadItemTaobaoAppMaterialRequest): Mono<String> {
        val multipartData = LinkedMultiValueMap<String, Any>()
        multipartData.add("wise", "hyalineImgPic_${request.platformId}_${request.itemId}_${request.activityEnterId}_0_0_0")
        multipartData.add("itemPicFile", request.pic)
        return uploadImage(request.tbToken, request.cookie2, request.sg, multipartData)
    }

    private fun uploadImage(tbToken: String, cookie2: String, sg: String, multipartData: MultiValueMap<String, *>): Mono<String> =
        webClient.post()
            .uri("https://freeway.ju.taobao.com/tg/json/uploadImageLocal.do?_input_charset=utf-8")
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
