package com.kevin.cryptotrader.data.portfolio

import com.kevin.cryptotrader.contracts.Account
import com.kevin.cryptotrader.contracts.Kind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class AccountJson(val id: String, val kind: String, val name: String, val venueId: String, val networks: List<String>)

object FixturesLoader {
  private val json = Json { ignoreUnknownKeys = true }

  fun loadAccounts(path: String = "fixtures/accounts/accounts.json"): List<Account> {
    val file = resolve(path)
    val text = file.readText()
    val arr = json.decodeFromString<List<AccountJson>>(text)
    return arr.map { a ->
      Account(
        id = a.id,
        kind = if (a.kind.equals("WALLET", true)) Kind.WALLET else Kind.CEX,
        name = a.name,
        venueId = a.venueId,
        networks = a.networks,
      )
    }
  }

  private fun resolve(path: String): File {
    val candidates = listOf(
      File(path), File("../$path"), File("../../$path"), File("../../../$path")
    )
    return candidates.firstOrNull { it.exists() } ?: error("Fixture not found: $path")
  }
}

