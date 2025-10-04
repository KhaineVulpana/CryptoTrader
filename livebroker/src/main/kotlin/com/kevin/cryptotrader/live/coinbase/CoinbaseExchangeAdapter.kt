package com.kevin.cryptotrader.live.coinbase

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.TIF
import com.kevin.cryptotrader.live.ExchangeAdapter
import com.kevin.cryptotrader.live.RestExecutor
import com.kevin.cryptotrader.live.RestRequest
import com.kevin.cryptotrader.live.UserEventSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

class CoinbaseExchangeAdapter(
    private val apiKey: String,
    private val secretKey: String,
    private val passphrase: String,
    private val rest: RestExecutor,
    private val events: UserEventSource,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ExchangeAdapter {
    private val openOrders = ConcurrentHashMap<String, String>()
    private val secretBytes = Base64.getDecoder().decode(secretKey)

    override suspend fun place(order: Order): String {
        val clientOrderId = order.clientOrderId.ifEmpty { orderId(order) }
        val payload = buildJsonObject {
            put("client_order_id", clientOrderId)
            put("product_id", order.symbol)
            put("side", order.side.name.lowercase())
            put("order_configuration", orderConfig(order))
        }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val headers = authHeaders("POST", "/api/v3/brokerage/orders", body)
        val response = rest.execute(
            RestRequest(
                method = "POST",
                path = "/api/v3/brokerage/orders",
                body = body,
                headers = headers,
                contentType = "application/json"
            )
        )
        val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!success) {
            val reason = response["failure_reason"]?.jsonPrimitive?.content
            throw IllegalStateException("Coinbase rejected order: $reason")
        }
        openOrders[clientOrderId] = order.symbol
        return clientOrderId
    }

    override suspend fun cancel(orderId: String): Boolean {
        if (!openOrders.containsKey(orderId)) return false
        val payload = buildJsonObject {
            put("client_order_ids", buildJsonArray { add(orderId) })
        }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val headers = authHeaders("POST", "/api/v3/brokerage/orders/batch_cancel", body)
        val response = rest.execute(
            RestRequest(
                method = "POST",
                path = "/api/v3/brokerage/orders/batch_cancel",
                body = body,
                headers = headers,
                contentType = "application/json"
            )
        )
        val results = response["results"]?.jsonArray ?: return false
        val canceled = results.any { result ->
            val obj = result.jsonObject
            obj["success"]?.jsonPrimitive?.booleanOrNull == true &&
                obj["client_order_id"]?.jsonPrimitive?.content == orderId
        }
        if (canceled) {
            openOrders.remove(orderId)
        }
        return canceled
    }

    override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> = events.stream(accounts)

    override suspend fun account(): AccountSnapshot {
        val headers = authHeaders("GET", "/api/v3/brokerage/accounts", "")
        val response = rest.execute(
            RestRequest(
                method = "GET",
                path = "/api/v3/brokerage/accounts",
                headers = headers
            )
        )
        val balances = parseAccounts(response)
        val equity = balances.filterKeys { it in STABLE_COINS }.values.sum()
        return AccountSnapshot(equity, balances)
    }

    private fun parseAccounts(response: JsonObject): Map<String, Double> {
        val accounts = response["accounts"]?.jsonArray ?: return emptyMap()
        return accounts.associate { element ->
            val obj = element.jsonObject
            val asset = obj["currency"]!!.jsonPrimitive.content
            val available = obj["available_balance"]?.jsonObject
            val value = available?.get("value")?.jsonPrimitive?.content?.toDouble() ?: 0.0
            asset to value
        }.filterValues { it > 0.0 }
    }

    private fun orderConfig(order: Order): JsonObject = when (order.type) {
        OrderType.MARKET -> buildJsonObject {
            put(
                "market_market_ioc",
                buildJsonObject {
                    put("base_size", format(order.qty))
                }
            )
        }
        OrderType.LIMIT -> buildJsonObject {
            put(
                "limit_limit_gtc",
                buildJsonObject {
                    put("base_size", format(order.qty))
                    put("limit_price", format(order.price ?: error("Limit price required")))
                    put("post_only", order.tif == TIF.GTC)
                }
            )
        }
        OrderType.STOP, OrderType.STOP_LIMIT -> buildJsonObject {
            put(
                "stop_limit_stop_limit_gtc",
                buildJsonObject {
                    put("base_size", format(order.qty))
                    put("limit_price", format(order.price ?: order.stopPrice ?: error("Price required")))
                    put("stop_price", format(order.stopPrice ?: error("Stop required")))
                }
            )
        }
    }

    private fun authHeaders(method: String, path: String, body: String): Map<String, String> {
        val timestamp = clock.instant().epochSecond.toString()
        val prehash = timestamp + method.uppercase() + path + body
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val signature = mac.doFinal(prehash.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(signature)
        return mapOf(
            "CB-ACCESS-KEY" to apiKey,
            "CB-ACCESS-PASSPHRASE" to passphrase,
            "CB-ACCESS-TIMESTAMP" to timestamp,
            "CB-ACCESS-SIGN" to encoded,
            "Content-Type" to "application/json"
        )
    }

    private fun format(value: Double): String =
        BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.DOWN).stripTrailingZeros().toPlainString()

    private fun orderId(order: Order): String = "cb_${order.symbol.lowercase()}_${clock.millis()}"

    companion object {
        private const val SCALE = 12
        private val STABLE_COINS = setOf("USD", "USDC", "USDT")
    }
}
