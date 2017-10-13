package me.coeuvre.poju.thirdparty.taoqingcang

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class TaoQingCangClientTest {

    @Autowired
    lateinit var client: TaoQingCangClient

    @Test
    fun queryItemsTest() {
        val queryItemsResponse = client.queryItems(QueryItemsRequest(
                tbToken = "fdea5e59b8578",
                cookie2 = "760e6f0cf1b29c3fc128b941260f9d3b",
                sg = "%E5%BA%9708",
                activityEnterId = 27847640,
                itemStatusCode = "0",
                actionStatus = "0",
                currentPage = 1,
                pageSize = 10
        ))
        queryItemsResponse.map { response ->
            println(response)
        }.block()
    }

    @Test
    fun getItemApplyFormDetailTest() {
        val itemApplyFormDetailMono = client.getItemApplyFormDetail(GetItemApplyFormDetailRequest(
                tbToken = "fdea5e59b8578",
                cookie2 = "760e6f0cf1b29c3fc128b941260f9d3b",
                sg = "%E5%BA%9708",
                juId = 10000058474400
        ))
        itemApplyFormDetailMono.map { response ->
            println(response)
        }.block()
    }
}
