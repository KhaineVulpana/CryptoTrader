package com.example.myandroidapp.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class BlockEditorNode(
    initialId: String,
    val type: String,
    initialProps: Map<String, String> = emptyMap(),
) {
    var id: String by mutableStateOf(initialId)
    val props: SnapshotStateMap<String, String> = mutableStateMapOf<String, String>().apply {
        putAll(initialProps)
    }
}

data class BlockEdge(val from: String, val to: String)

class BlockEditorState internal constructor(
    initialId: String,
    initialVersion: Int = BlockSchema.VERSION,
) {
    var automationId: String by mutableStateOf(initialId)
    var version: Int by mutableStateOf(initialVersion)
    var selectedNodeId: String? by mutableStateOf(null)
    val nodes: SnapshotStateList<BlockEditorNode> = mutableStateListOf()
    val edges: SnapshotStateList<BlockEdge> = mutableStateListOf()

    private val counters = mutableMapOf<String, Int>()

    fun addNode(type: String): BlockEditorNode {
        val spec = BlockSchema.specFor(type) ?: error("Unknown block type: $type")
        val node = BlockEditorNode(nextId(type), type, spec.defaultProps())
        nodes += node
        selectedNodeId = node.id
        return node
    }

    fun removeNode(nodeId: String) {
        val idx = nodes.indexOfFirst { it.id == nodeId }
        if (idx >= 0) {
            nodes.removeAt(idx)
            val iter = edges.iterator()
            while (iter.hasNext()) {
                val edge = iter.next()
                if (edge.from == nodeId || edge.to == nodeId) iter.remove()
            }
            if (selectedNodeId == nodeId) selectedNodeId = null
        }
    }

    fun addEdge(from: String, to: String) {
        if (from == to) return
        if (edges.any { it.from == from && it.to == to }) return
        edges += BlockEdge(from, to)
    }

    fun removeEdge(edge: BlockEdge) {
        edges.remove(edge)
    }

    fun selectNode(nodeId: String?) {
        selectedNodeId = nodeId
    }

    fun updateNodeId(node: BlockEditorNode, newId: String): Boolean {
        if (newId.isBlank() || nodes.any { it !== node && it.id == newId }) return false
        val currentId = node.id
        if (currentId == newId) return true
        node.id = newId
        edges.replaceAll { edge ->
            when (currentId) {
                edge.from -> edge.copy(from = newId)
                edge.to -> edge.copy(to = newId)
                else -> edge
            }
        }
        if (selectedNodeId == currentId) selectedNodeId = newId
        return true
    }

    private fun nextId(type: String): String {
        val base = type.replace(Regex("[^A-Za-z0-9]"), "")
            .replaceFirstChar { if (it.isLowerCase()) it.toString() else it.lowercase() }
        val idx = (counters[base] ?: 0) + 1
        counters[base] = idx
        return "$base$idx"
    }

    fun toAutomationDoc(): AutomationDoc {
        val nodesJson = nodes.map { node ->
            val spec = BlockSchema.specFor(node.type)
            val jsonProps = node.props.mapNotNull { (key, value) ->
                val propSpec = spec?.propertySpecs?.firstOrNull { it.key == key }
                val element = valueToJson(propSpec, value)
                if (element != null) key to element else null
            }.toMap()
            AutomationNode(id = node.id, type = node.type, props = jsonProps)
        }
        val edgesJson = edges.map { listOf(it.from, it.to) }
        return AutomationDoc(
            v = version,
            id = automationId,
            graph = AutomationGraph(nodesJson, edgesJson)
        )
    }

    companion object {
        private fun valueToJson(spec: PropertySpec?, raw: String): JsonElement? {
            if (raw.isEmpty()) return spec?.defaultValue?.let { valueToJson(spec, it) }
            return when (spec?.type) {
                PropertyType.NUMBER -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) }
                PropertyType.BOOLEAN -> raw.toBooleanStrictOrNull()?.let { JsonPrimitive(it) }
                PropertyType.LIST -> {
                    val items = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    JsonArray(items.map { JsonPrimitive(it) })
                }
                else -> JsonPrimitive(raw)
            }
        }
    }
}

@Composable
fun rememberBlockEditorState(initialId: String = "automation"): BlockEditorState {
    return remember { BlockEditorState(initialId) }
}

@Serializable
data class AutomationDoc(val v: Int, val id: String, val graph: AutomationGraph)

@Serializable
data class AutomationGraph(val nodes: List<AutomationNode>, val edges: List<List<String>>)

@Serializable
data class AutomationNode(val id: String, val type: String, val props: Map<String, JsonElement> = emptyMap())

object AutomationSchema {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun encode(doc: AutomationDoc): String = json.encodeToString(doc)

    fun validate(doc: AutomationDoc, state: BlockEditorState? = null): List<String> {
        val errors = mutableListOf<String>()
        if (doc.v != BlockSchema.VERSION) {
            errors += "Version must be ${BlockSchema.VERSION}"
        }
        if (doc.id.isBlank()) {
            errors += "Automation id is required"
        }
        val seen = mutableSetOf<String>()
        doc.graph.nodes.forEach { node ->
            if (!seen.add(node.id)) {
                errors += "Duplicate node id: ${node.id}"
            }
            val spec = BlockSchema.specFor(node.type)
            if (spec == null) {
                errors += "Unknown block type: ${node.type}"
                return@forEach
            }
            val rawProps = state?.nodes?.firstOrNull { it.id == node.id }?.props ?: emptyMap()
            spec.propertySpecs.forEach { prop ->
                val rawValue = rawProps[prop.key]?.trim().orEmpty()
                val effectiveValue = if (rawValue.isEmpty()) prop.defaultValue?.trim().orEmpty() else rawValue
                if (prop.required && effectiveValue.isEmpty()) {
                    errors += "${node.id}: ${prop.label} is required"
                    return@forEach
                }
                if (effectiveValue.isNotEmpty()) {
                    when (prop.type) {
                        PropertyType.NUMBER -> if (effectiveValue.toDoubleOrNull() == null) {
                            errors += "${node.id}: ${prop.label} must be a number"
                        }
                        PropertyType.ENUM -> if (prop.options.isNotEmpty() && effectiveValue !in prop.options) {
                            errors += "${node.id}: ${prop.label} must be one of ${prop.options.joinToString()}"
                        }
                        PropertyType.LIST -> {
                            val items = effectiveValue.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            if (prop.required && items.isEmpty()) {
                                errors += "${node.id}: ${prop.label} requires at least one entry"
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        val nodeIds = doc.graph.nodes.map { it.id }.toSet()
        doc.graph.edges.forEach { edge ->
            if (edge.size != 2) {
                errors += "Invalid edge: $edge"
            } else {
                val (from, to) = edge
                if (from !in nodeIds) errors += "Edge references missing source $from"
                if (to !in nodeIds) errors += "Edge references missing target $to"
            }
        }
        return errors
    }
}

private fun String.toBooleanStrictOrNull(): Boolean? = when (this.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}
