package me.coeuvre.poju.thirdparty.ju

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class JuClientTest {
    @Autowired
    lateinit var client: JuClient

    val cookie2 = "1c3c4563d598ffb0d0893ccf7f5f1c95"
    val tbToken = "e885884bee071"
    val sg = "%E5%BA%9708"

    @Test
    fun queryItemsTest() {
        val response = client.queryItems(QueryItemsRequest(
            cookie2, tbToken, sg, "27988322", "0", "0",
            1, 10)).block()

        println(response)

        Assert.assertTrue(response != null && response.success)
    }

    @Test
    fun getItemApplyFormDetailTest() {
        val itemApplyFormDetail = client.getItemApplyFormDetail(cookie2, tbToken, sg, "10000060034400").block()
        println(itemApplyFormDetail)
    }
}