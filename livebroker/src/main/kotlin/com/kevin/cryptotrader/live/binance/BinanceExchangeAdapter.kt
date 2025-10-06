package com.kevin.cryptotrader.live.binance

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
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BinanceExchangeAdapter(
    private val apiKey: String,
    private val secretKey: String,
    private val rest: RestExecutor,
    private val events: UserEventSource,
    private val clock: Clock = Clock.systemUTC(),
    private val recvWindowMs: Long = 5_000
) : ExchangeAdapter {
    private val orderToSymbol = ConcurrentHashMap<String, String>()

    override suspend fun place(order: Order): String {
        val params = linkedMapOf(
            "symbol" to order.symbol,
            "side" to order.side.name,
            "type" to order.type.name,
            "timestamp" to clock.millis().toString(),
            "recvWindow" to recvWindowMs.toString()
        )
        if (order.clientOrderId.isNotEmpty()) {
            params["newClientOrderId"] = order.clientOrderId
        }
        when (order.type) {
            OrderType.MARKET -> params["quantity"] = format(order.qty)
            OrderType.LIMIT -> {
                params["quantity"] = format(order.qty)
                params["price"] = format(order.price ?: error("Limit order requires price"))
                params["timeInForce"] = mapTif(order.tif)
            }
            OrderType.STOP, OrderType.STOP_LIMIT -> {
                params["quantity"] = format(order.qty)
                params["stopPrice"] = format(order.stopPrice ?: error("Stop price required"))
                if (order.type == OrderType.STOP_LIMIT) {
                    params["price"] = format(order.price ?: error("Stop limit requires limit price"))
                    params["timeInForce"] = mapTif(order.tif)
                }
                params["type"] = if (order.type == OrderType.STOP) "STOP_LOSS" else "STOP_LOSS_LIMIT"
            }
        }
        sign(params)
        val response = rest.execute(
            RestRequest(
                method = "POST",
                path = "/api/v3/order",
                query = params,
                headers = mapOf("X-MBX-APIKEY" to apiKey)
            )
        )
        val orderId = response["clientOrderId"]?.jsonPrimitive?.content
            ?: response["orderId"]?.jsonPrimitive?.content
            ?: order.clientOrderId.ifEmpty { error("Binance response missing orderId") }
        orderToSymbol[orderId] = order.symbol
        return orderId
    }

    override suspend fun cancel(orderId: String): Boolean {
        val symbol = orderToSymbol[orderId] ?: return false
        val params = linkedMapOf(
            "symbol" to symbol,
            "origClientOrderId" to orderId,
            "timestamp" to clock.millis().toString(),
            "recvWindow" to recvWindowMs.toString()
        )
        sign(params)
        val response = rest.execute(
            RestRequest(
                method = "DELETE",
                path = "/api/v3/order",
                query = params,
                headers = mapOf("X-MBX-APIKEY" to apiKey)
            )
        )
        val status = response["status"]?.jsonPrimitive?.content
        val canceled = status == null || status == "CANCELED"
        if (canceled) {
            orderToSymbol.remove(orderId)
        }
        return canceled
    }

    override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> = events.stream(accounts)

    override suspend fun account(): AccountSnapshot {
        val params = linkedMapOf(
            "timestamp" to clock.millis().toString(),
            "recvWindow" to recvWindowMs.toString()
        )
        sign(params)
        val response = rest.execute(
            RestRequest(
                method = "GET",
                path = "/api/v3/account",
                query = params,
                headers = mapOf("X-MBX-APIKEY" to apiKey)
            )
        )
        val balances = parseBalances(response)
        val equity = balances.filterKeys { it in STABLE_COINS }.values.sum()
        return AccountSnapshot(equity, balances)
    }

    private fun parseBalances(response: JsonObject): Map<String, Double> {
        val balancesNode = response["balances"] ?: return emptyMap()
        val array = balancesNode.jsonArray
        return array.associate { element ->
            val obj = element.jsonObject
            val asset = obj["asset"]!!.jsonPrimitive.content
            val free = obj["free"]!!.jsonPrimitive.content.toDouble()
            val locked = obj["locked"]?.jsonPrimitive?.content?.toDouble() ?: 0.0
            asset to (free + locked)
        }.filterValues { it > 0.0 }
    }

    private fun mapTif(tif: TIF): String = when (tif) {
        TIF.GTC, TIF.DAY -> "GTC"
        TIF.IOC -> "IOC"
        TIF.FOK -> "FOK"
    }

    private fun sign(params: LinkedHashMap<String, String>) {
        val canonical = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte)
        }
        params["signature"] = signature
    }

    private fun format(value: Double): String =
        BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.DOWN).stripTrailingZeros().toPlainString()

    companion object {
        private val STABLE_COINS = setOf("USDT", "USDC", "BUSD", "TUSD", "USD")
        private const val SCALE = 12
    }
}
