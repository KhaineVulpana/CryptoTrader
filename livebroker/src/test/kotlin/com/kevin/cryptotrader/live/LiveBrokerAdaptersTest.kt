package com.kevin.cryptotrader.live

import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.TIF
import com.kevin.cryptotrader.live.binance.BinanceExchangeAdapter
import com.kevin.cryptotrader.live.coinbase.CoinbaseExchangeAdapter
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveBrokerAdaptersTest {
    private val clock = object : Clock() {
        private val instant = Instant.parse("2024-05-01T00:00:00Z")
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun `binance adapter signs requests and parses balances`() = runTest {
        val rest = RecordingExecutor(
            response = Json.parseToJsonElement(
                """
                {"orderId":12345,"clientOrderId":"my-id","status":"NEW"}
                """.trimIndent()
            ).jsonObject
        )
        val events = UserEventSource { emptyFlow() }
        val adapter = BinanceExchangeAdapter(
            apiKey = "key",
            secretKey = "secret",
            rest = rest,
            events = events,
            clock = clock,
            recvWindowMs = 5000
        )
        val order = Order(
            clientOrderId = "my-id",
            symbol = "BTCUSDT",
            side = Side.BUY,
            type = OrderType.LIMIT,
            qty = 0.5,
            price = 25_000.0,
            stopPrice = null,
            tif = TIF.GTC,
            ts = 0L
        )
        val id = adapter.place(order)
        assertEquals("my-id", id)
        val request = rest.lastRequest
        assertEquals("/api/v3/order", request?.path)
        assertTrue(request?.query?.containsKey("signature") == true)
        assertEquals("key", request?.headers?.get("X-MBX-APIKEY"))

        rest.response = Json.parseToJsonElement(
            """
            {"balances":[{"asset":"USDT","free":"100.0","locked":"0"},{"asset":"BTC","free":"0.1","locked":"0"}]}
            """.trimIndent()
        ).jsonObject
        val snapshot = adapter.account()
        assertEquals(100.0, snapshot.equityUsd)
        assertEquals(2, snapshot.balances.size)
    }

    @Test
    fun `coinbase adapter builds JSON payload`() = runTest {
        val rest = RecordingExecutor(
            response = Json.parseToJsonElement("""{"success":true}""").jsonObject
        )
        val adapter = CoinbaseExchangeAdapter(
            apiKey = "key",
            secretKey = Base64.getEncoder().encodeToString("secret".toByteArray()),
            passphrase = "pass",
            rest = rest,
            events = UserEventSource { emptyFlow() },
            clock = clock,
            json = Json
        )
        val order = Order(
            clientOrderId = "",
            symbol = "ETH-USD",
            side = Side.SELL,
            type = OrderType.MARKET,
            qty = 1.5,
            price = null,
            stopPrice = null,
            tif = TIF.IOC,
            ts = 0L
        )
        val id = adapter.place(order)
        assertTrue(id.startsWith("cb_eth-usd_"))
        val request = rest.lastRequest
        assertEquals("/api/v3/brokerage/orders", request?.path)
        assertEquals("POST", request?.method?.uppercase())
        assertEquals("application/json", request?.contentType)
        assertTrue(request?.headers?.containsKey("CB-ACCESS-SIGN") == true)

        rest.response = Json.parseToJsonElement(
            """
            {"accounts":[{"currency":"USD","available_balance":{"value":"120.0"}}]}
            """.trimIndent()
        ).jsonObject
        val account = adapter.account()
        assertEquals(120.0, account.equityUsd)
        assertEquals(120.0, account.balances["USD"])
    }

    private class RecordingExecutor(var response: kotlinx.serialization.json.JsonObject) : RestExecutor {
        var lastRequest: RestRequest? = null
        override suspend fun execute(request: RestRequest): kotlinx.serialization.json.JsonObject {
            lastRequest = request
            return response
        }
    }
}
