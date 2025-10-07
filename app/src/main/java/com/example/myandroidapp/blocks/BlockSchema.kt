package com.example.myandroidapp.blocks

enum class BlockCategory { Event, Data, Logic, Risk, Action, Universe }

enum class PropertyType { TEXT, NUMBER, ENUM, LIST, BOOLEAN }

data class PropertySpec(
    val key: String,
    val label: String,
    val type: PropertyType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
    val helper: String? = null,
)

data class BlockSpec(
    val type: String,
    val category: BlockCategory,
    val displayName: String,
    val description: String,
    val propertySpecs: List<PropertySpec> = emptyList(),
) {
    val requiredKeys: Set<String> = propertySpecs.filter { it.required }.map { it.key }.toSet()

    fun defaultProps(): Map<String, String> = buildMap {
        propertySpecs.forEach { spec ->
            spec.defaultValue?.let { put(spec.key, it) }
        }
    }
}

object BlockSchema {
    const val VERSION: Int = 1

    val specs: List<BlockSpec> = listOf(
        BlockSpec(
            type = "onCandle",
            category = BlockCategory.Event,
            displayName = "On Candle",
            description = "Runs whenever a candle closes for a timeframe.",
            propertySpecs = listOf(
                PropertySpec(
                    key = "tf",
                    label = "Interval",
                    type = PropertyType.ENUM,
                    required = true,
                    options = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1"),
                    defaultValue = "H1",
                )
            )
        ),
        BlockSpec(
            type = "onTick",
            category = BlockCategory.Event,
            displayName = "On Tick",
            description = "Runs on every trade tick for the selected symbol.",
        ),
        BlockSpec(
            type = "onSchedule",
            category = BlockCategory.Event,
            displayName = "On Schedule",
            description = "Runs on a fixed schedule using cron-like notation.",
            propertySpecs = listOf(
                PropertySpec("cron", "Cron", PropertyType.TEXT, required = true, helper = "e.g. 0 * * * *"),
                PropertySpec("timezone", "Timezone", PropertyType.TEXT, defaultValue = "UTC"),
            )
        ),
        BlockSpec(
            type = "onFill",
            category = BlockCategory.Event,
            displayName = "On Fill",
            description = "Runs when an order fill is observed.",
            propertySpecs = listOf(
                PropertySpec("symbol", "Symbol", PropertyType.TEXT, helper = "Optional instrument filter"),
            )
        ),
        BlockSpec(
            type = "onPnL",
            category = BlockCategory.Event,
            displayName = "On PnL",
            description = "Runs when PnL reaches a threshold.",
            propertySpecs = listOf(
                PropertySpec("threshold", "Threshold %", PropertyType.NUMBER, required = true, defaultValue = "5.0"),
            )
        ),
        BlockSpec(
            type = "indicator",
            category = BlockCategory.Data,
            displayName = "Indicator",
            description = "Computes a technical indicator series.",
            propertySpecs = listOf(
                PropertySpec("name", "Indicator", PropertyType.ENUM, required = true, options = listOf("ema", "rsi", "macd", "bollinger", "atr", "zscore"), defaultValue = "ema"),
                PropertySpec("len", "Length", PropertyType.NUMBER, helper = "Primary lookback length"),
                PropertySpec("fast", "Fast Length", PropertyType.NUMBER),
                PropertySpec("slow", "Slow Length", PropertyType.NUMBER),
                PropertySpec("signal", "Signal Length", PropertyType.NUMBER),
                PropertySpec("mult", "StdDev Mult", PropertyType.NUMBER),
            )
        ),
        BlockSpec(
            type = "window",
            category = BlockCategory.Data,
            displayName = "Window",
            description = "Maintains a rolling window of values.",
            propertySpecs = listOf(
                PropertySpec("len", "Length", PropertyType.NUMBER, required = true, defaultValue = "20"),
            )
        ),
        BlockSpec(
            type = "stateGet",
            category = BlockCategory.Data,
            displayName = "State Get",
            description = "Reads a value from persistent state.",
            propertySpecs = listOf(
                PropertySpec("key", "Key", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "stateSet",
            category = BlockCategory.Data,
            displayName = "State Set",
            description = "Writes a value into persistent state.",
            propertySpecs = listOf(
                PropertySpec("key", "Key", PropertyType.TEXT, required = true),
                PropertySpec("value", "Value", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "pairFeed",
            category = BlockCategory.Data,
            displayName = "Pair Feed",
            description = "Loads data for a related trading pair.",
            propertySpecs = listOf(
                PropertySpec("symbol", "Symbol", PropertyType.TEXT, required = true),
                PropertySpec("interval", "Interval", PropertyType.ENUM, options = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1"), defaultValue = "H1"),
            )
        ),
        BlockSpec(
            type = "universe",
            category = BlockCategory.Universe,
            displayName = "Universe",
            description = "Defines the tradable universe for downstream ranking.",
            propertySpecs = listOf(
                PropertySpec("symbols", "Symbols", PropertyType.LIST, required = true, helper = "Comma separated tickers", defaultValue = "BTCUSDT,ETHUSDT"),
            )
        ),
        BlockSpec(
            type = "if",
            category = BlockCategory.Logic,
            displayName = "If",
            description = "Branches when the condition is true.",
            propertySpecs = listOf(
                PropertySpec("condition", "Condition", PropertyType.TEXT, helper = "Optional label"),
            )
        ),
        BlockSpec(
            type = "else",
            category = BlockCategory.Logic,
            displayName = "Else",
            description = "Branches when the prior condition is false.",
        ),
        BlockSpec(
            type = "math",
            category = BlockCategory.Logic,
            displayName = "Math",
            description = "Performs arithmetic on inputs.",
            propertySpecs = listOf(
                PropertySpec("expr", "Expression", PropertyType.TEXT, required = true, helper = "e.g. (a - b) / b"),
            )
        ),
        BlockSpec(
            type = "compare",
            category = BlockCategory.Logic,
            displayName = "Compare",
            description = "Compares two values.",
            propertySpecs = listOf(
                PropertySpec("left", "Left", PropertyType.TEXT, required = true),
                PropertySpec("op", "Operator", PropertyType.ENUM, required = true, options = listOf("GT", "GTE", "LT", "LTE", "EQ"), defaultValue = "GT"),
                PropertySpec("right", "Right", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "crossesAbove",
            category = BlockCategory.Logic,
            displayName = "Crosses Above",
            description = "Triggers when the first series crosses above the second.",
        ),
        BlockSpec(
            type = "crossesBelow",
            category = BlockCategory.Logic,
            displayName = "Crosses Below",
            description = "Triggers when the first series crosses below the second.",
        ),
        BlockSpec(
            type = "cooldown",
            category = BlockCategory.Logic,
            displayName = "Cooldown",
            description = "Delays downstream actions for a period after firing.",
            propertySpecs = listOf(
                PropertySpec("ms", "Cooldown (ms)", PropertyType.NUMBER, required = true, defaultValue = "60000"),
            )
        ),
        BlockSpec(
            type = "oncePerBar",
            category = BlockCategory.Logic,
            displayName = "Once Per Bar",
            description = "Ensures downstream actions run at most once per bar.",
        ),
        BlockSpec(
            type = "forEach",
            category = BlockCategory.Logic,
            displayName = "For Each",
            description = "Iterates over items in a collection (e.g. universe symbols).",
            propertySpecs = listOf(
                PropertySpec("source", "Source", PropertyType.TEXT, required = true, helper = "Node id or context key"),
            )
        ),
        BlockSpec(
            type = "rank",
            category = BlockCategory.Logic,
            displayName = "Rank",
            description = "Sorts items by a metric and keeps the top N.",
            propertySpecs = listOf(
                PropertySpec("metric", "Metric", PropertyType.TEXT, required = true),
                PropertySpec("limit", "Top N", PropertyType.NUMBER, required = true, defaultValue = "5"),
            )
        ),
        BlockSpec(
            type = "riskSize",
            category = BlockCategory.Risk,
            displayName = "Risk Size",
            description = "Defines risk sizing parameters.",
            propertySpecs = listOf(
                PropertySpec("riskPct", "Risk %", PropertyType.NUMBER, helper = "Percent of equity", defaultValue = "1.0"),
                PropertySpec("notional", "Notional USD", PropertyType.NUMBER),
            )
        ),
        BlockSpec(
            type = "atrStop",
            category = BlockCategory.Risk,
            displayName = "ATR Stop",
            description = "Configures an ATR-based protective stop.",
            propertySpecs = listOf(
                PropertySpec("atrMult", "ATR Multiplier", PropertyType.NUMBER, required = true, defaultValue = "2.0"),
            )
        ),
        BlockSpec(
            type = "trailStop",
            category = BlockCategory.Risk,
            displayName = "Trailing Stop",
            description = "Sets a trailing stop percentage.",
            propertySpecs = listOf(
                PropertySpec("trailingPct", "Trailing %", PropertyType.NUMBER, required = true, defaultValue = "3.0"),
            )
        ),
        BlockSpec(
            type = "emitOrder",
            category = BlockCategory.Action,
            displayName = "Emit Order",
            description = "Emits a trade intent.",
            propertySpecs = listOf(
                PropertySpec("symbol", "Symbol", PropertyType.TEXT, required = true, defaultValue = "BTCUSDT"),
                PropertySpec("side", "Side", PropertyType.ENUM, required = true, options = listOf("BUY", "SELL"), defaultValue = "BUY"),
                PropertySpec("type", "Order Type", PropertyType.ENUM, options = listOf("MARKET", "LIMIT"), defaultValue = "MARKET"),
            )
        ),
        BlockSpec(
            type = "emitSpread",
            category = BlockCategory.Action,
            displayName = "Emit Spread",
            description = "Emits a paired spread intent.",
            propertySpecs = listOf(
                PropertySpec("long", "Long Symbol", PropertyType.TEXT, required = true),
                PropertySpec("short", "Short Symbol", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "log",
            category = BlockCategory.Action,
            displayName = "Log",
            description = "Writes to the automation log.",
            propertySpecs = listOf(
                PropertySpec("message", "Message", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "notify",
            category = BlockCategory.Action,
            displayName = "Notify",
            description = "Sends a notification.",
            propertySpecs = listOf(
                PropertySpec("channel", "Channel", PropertyType.TEXT, required = true, defaultValue = "push"),
                PropertySpec("message", "Message", PropertyType.TEXT, required = true),
            )
        ),
        BlockSpec(
            type = "abort",
            category = BlockCategory.Action,
            displayName = "Abort",
            description = "Stops execution with an error.",
            propertySpecs = listOf(
                PropertySpec("reason", "Reason", PropertyType.TEXT, required = true),
            )
        ),
    )

    private val specMap = specs.associateBy { it.type }

    fun specFor(type: String): BlockSpec? = specMap[type]

    fun categoryFor(type: String): BlockCategory? = specMap[type]?.category
}
