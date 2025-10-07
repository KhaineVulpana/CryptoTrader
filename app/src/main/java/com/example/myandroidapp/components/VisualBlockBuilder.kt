package com.example.myandroidapp.components

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myandroidapp.blocks.AutomationSchema
import com.example.myandroidapp.blocks.BlockEditorNode
import com.example.myandroidapp.blocks.BlockEditorState
import com.example.myandroidapp.blocks.BlockSchema
import com.example.myandroidapp.blocks.PropertySpec
import com.example.myandroidapp.blocks.PropertyType
import org.json.JSONObject

@Composable
fun VisualBlockBuilder(
    state: BlockEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.toAutomationDoc()
    val json = remember(doc) { AutomationSchema.encode(doc) }
    val validationErrors = AutomationSchema.validate(doc, state)

    Column(modifier) {
        EditorHeader(state)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.weight(1f)) {
            PaletteColumn(state, Modifier.width(220.dp).fillMaxHeight())
            Spacer(Modifier.width(12.dp))
            WorkspacePreview(state, json, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            PropertiesPanel(state, Modifier.width(280.dp).fillMaxHeight())
        }
        Spacer(Modifier.height(12.dp))
        JsonPreview(json, validationErrors)
    }
}

@Composable
private fun EditorHeader(state: BlockEditorState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.automationId,
            onValueChange = { state.automationId = it },
            label = { Text("Automation ID") },
            modifier = Modifier.weight(1f)
        )
        VersionSelector(state)
        Text("Nodes: ${state.nodes.size}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSelector(state: BlockEditorState) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.version.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Schema Version") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().width(160.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(BlockSchema.VERSION).forEach { version ->
                DropdownMenuItem(text = { Text("v$version") }, onClick = {
                    state.version = version
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun PaletteColumn(state: BlockEditorState, modifier: Modifier = Modifier) {
    val grouped = remember { BlockSchema.specs.groupBy { it.category } }
    val scroll = rememberScrollState()
    Column(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(12.dp)
            .verticalScroll(scroll)
    ) {
        grouped.forEach { (category, blocks) ->
            Text(category.name.uppercase(), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            blocks.forEach { spec ->
                Card(
                    onClick = { state.addNode(spec.type) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                        if (spec.description.isNotBlank()) {
                            Text(spec.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WorkspacePreview(state: BlockEditorState, json: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.border(1.dp, MaterialTheme.colorScheme.outline)) {
        AndroidView(
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    webChromeClient = WebChromeClient()
                    loadDataWithBaseURL(null, BLOCK_WORKSPACE_HTML, "text/html", "utf-8", null)
                }
            },
            update = { view ->
                val script = "window.renderGraph(${JSONObject.quote(json)});"
                view.evaluateJavascript(script, null)
                state.selectedNodeId?.let { sel ->
                    view.evaluateJavascript("window.highlightNode(${JSONObject.quote(sel)});", null)
                }
            },
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        )
    }
}

@Composable
private fun PropertiesPanel(state: BlockEditorState, modifier: Modifier = Modifier) {
    val selected = state.nodes.firstOrNull { it.id == state.selectedNodeId }
    Column(modifier.border(1.dp, MaterialTheme.colorScheme.outline).padding(12.dp)) {
        Text("Properties", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (selected == null) {
            Text("Select a block to edit its properties.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        NodeIdentityEditor(state, selected)
        Spacer(Modifier.height(12.dp))
        PropertyFields(selected)
        Spacer(Modifier.height(16.dp))
        EdgeEditor(state, selected)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { state.removeNode(selected.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
            Spacer(Modifier.width(8.dp))
            Text("Remove Block")
        }
    }
}

@Composable
private fun NodeIdentityEditor(state: BlockEditorState, node: BlockEditorNode) {
    var value by remember(node.id) { mutableStateOf(node.id) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            state.updateNodeId(node, it)
        },
        label = { Text("Block ID") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text("Type: ${node.type}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PropertyFields(node: BlockEditorNode) {
    val spec = BlockSchema.specFor(node.type)
    if (spec == null) {
        Text("Unknown block spec", color = MaterialTheme.colorScheme.error)
        return
    }
    spec.propertySpecs.forEach { prop ->
        Spacer(Modifier.height(8.dp))
        PropertyField(node, prop)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyField(node: BlockEditorNode, spec: PropertySpec) {
    val current = node.props[spec.key] ?: ""
    when (spec.type) {
        PropertyType.ENUM -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = current.ifEmpty { spec.defaultValue.orEmpty() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(spec.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    spec.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            node.props[spec.key] = option
                            expanded = false
                        })
                    }
                }
            }
        }
        else -> {
            val placeholder: (@Composable () -> Unit)? = spec.helper?.let { helper ->
                @Composable { Text(helper) }
            }
            OutlinedTextField(
                value = current,
                onValueChange = { node.props[spec.key] = it },
                label = { Text(spec.label) },
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EdgeEditor(state: BlockEditorState, node: BlockEditorNode) {
    val outgoing = state.edges.filter { it.from == node.id }
    val incoming = state.edges.filter { it.to == node.id }
    Text("Edges", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text("Outgoing: ${if (outgoing.isEmpty()) "None" else outgoing.joinToString { it.to }}", style = MaterialTheme.typography.bodySmall)
    Text("Incoming: ${if (incoming.isEmpty()) "None" else incoming.joinToString { it.from }}", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    var from by remember(node.id) { mutableStateOf(node.id) }
    var to by remember(node.id) { mutableStateOf("") }
    OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Button(onClick = {
        if (from.isNotBlank() && to.isNotBlank()) {
            state.addEdge(from.trim(), to.trim())
            to = ""
        }
    }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Add Edge")
    }
    Spacer(Modifier.height(12.dp))
    outgoing.forEach { edge ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${edge.from} → ${edge.to}", modifier = Modifier.weight(1f))
            TextButton(onClick = { state.removeEdge(edge) }) { Text("Remove") }
        }
    }
}

@Composable
private fun JsonPreview(json: String, errors: List<String>) {
    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).padding(12.dp)) {
        Text("Automation JSON", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFF1E1E1E))
                .padding(12.dp)
        ) {
            Text(json, color = Color.White, fontFamily = FontFamily.Monospace)
        }
        if (errors.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("✓ Schema valid", color = MaterialTheme.colorScheme.primary)
        } else {
            Spacer(Modifier.height(8.dp))
            errors.forEach { err ->
                Text("• $err", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private const val BLOCK_WORKSPACE_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\" />
  <style>
    body { font-family: sans-serif; margin: 0; background: #121212; color: #eee; }
    #workspace { padding: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
    .block { border-radius: 8px; padding: 12px; background: #1f2933; border: 1px solid #334155; min-width: 140px; }
    .block.selected { border-color: #60a5fa; box-shadow: 0 0 8px rgba(96,165,250,0.6); }
    .title { font-weight: 600; margin-bottom: 6px; }
    .props { font-size: 12px; color: #a7b0c0; }
  </style>
</head>
<body>
  <div id=\"workspace\"></div>
  <script>
    function formatProps(props) {
      const keys = Object.keys(props || {});
      if (!keys.length) return 'No props';
      return keys.map(k => `${k}: ${typeof props[k] === 'object' ? JSON.stringify(props[k]) : props[k]}`).join('\n');
    }
    window.renderGraph = function(json) {
      try {
        const doc = JSON.parse(json);
        const ws = document.getElementById('workspace');
        ws.innerHTML = '';
        doc.graph.nodes.forEach(node => {
          const el = document.createElement('div');
          el.className = 'block';
          el.dataset.nodeId = node.id;
          const title = document.createElement('div');
          title.className = 'title';
          title.textContent = `${node.id} (${node.type})`;
          el.appendChild(title);
          const props = document.createElement('pre');
          props.className = 'props';
          props.textContent = formatProps(node.props);
          el.appendChild(props);
          ws.appendChild(el);
        });
      } catch (err) {
        console.error(err);
      }
    };
    window.highlightNode = function(id) {
      document.querySelectorAll('.block').forEach(el => {
        if (el.dataset.nodeId === id) {
          el.classList.add('selected');
        } else {
          el.classList.remove('selected');
        }
      });
    };
  </script>
</body>
</html>
"""
