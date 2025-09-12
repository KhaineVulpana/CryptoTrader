# CryptoTrader 2.0 — End‑to‑End Project Design (Hand‑off Spec)

**Owner:** Kevin  
**Audience:** Implementing engineers (human or AI)  
**Purpose:** A comprehensive, modular blueprint you can implement in parallel. Every section lists contracts, data sources, deliverables, and acceptance criteria so work can be chunked and verified independently.

> **Execution modes:** `SIM` (offline), `PAPER` (simulated broker on live data), `LIVE` (real exchange). All subsystems must be mode‑aware but share the same pipeline.

---

## Table of Contents
- Part 0 — Handoff Bundle & Checklist
- Part I — Concepts & Architecture
- Part II — Implementation Plan & Phases
0. Glossary
1. Product Requirements & Non‑Goals
2. System Architecture Overview
3. Data Sources & Integrations
4. Domain Model & Schemas
5. Event‑Sourced Ledger
6. Market Data Layer (REST + WebSocket + Caching)
7. Broker Layer (Paper + Live)
8. Execution Coordinator & Conflict Policies
9. Risk, Sizing & Portfolio Engine
10. Strategy Runtime (DSL + Statecharts + VM)
11. Visual Automation Editor (Blocks → JSON → Bytecode)
12. Indicator & Window Engine (TA math)
13. Backtesting & Research
14. UI/UX by Tab (Compose)  
 14.1 Dashboard  
 14.2 Strategies  
 14.3 Automations  
 14.4 Timeline (Activity)  
 14.5 Blotter (Orders/Positions/Fills/Alerts)  
 14.6 Backtests  
 14.7 Settings  
 14.8 Sim Params  
 14.9 Alerts/Notifications
15. Persistence & Migrations (Room)
16. Telemetry & Observability
17. Security, Privacy & Compliance Basics
18. Performance & Resource Budgets
19. Release Management & Feature Flags
20. Work Breakdown Structure (Epics → Milestones → Tasks)
21. Appendix A: Kotlin Interfaces & Pseudocode
22. Appendix B: JSON Schemas
23. Appendix C: Test Plan & Fixtures

---

## 0) Glossary
- **Intent**: Proposed trade before risk/policy (e.g., “BUY 1% notional BTCUSDT”).
- **Order**: Broker request (limit/market/stop) with `clientOrderId`.
- **Fill**: Execution event (partial/complete).
- **Ledger**: Append‑only event log: market, intents, orders, fills, PnL, state changes.
- **Automation**: Visual/DSL program producing intents on events.
- **Strategy**: A prebuilt automation blueprint (e.g., EMA crossover).

---

## Part 0 — Handoff Bundle & Checklist
**Purpose:** Everything the implementing AI/human needs on day 1 without asking questions.

### 0.1 Repository Layout (proposed)
```
root/
  settings.gradle.kts
  gradle/libs.versions.toml        # version catalog (pin deps)
  build.gradle.kts                 # common config
  app/                             # Android launcher module
  contracts/                       # pure interfaces/DTOs/schemas (no deps)
  core/                            # domain models, ledger, policy, risk (pure Kotlin)
  data/                            # exchange + wallet adapters, repos
  runtime/                         # statecharts + VM + indicator engine
  paperbroker/                     # paper broker impl
  ui/                              # Compose screens & viewmodels
  mocks/                           # fake connectors, fixtures
  tools/                           # codegen, schema validators, fixture loader
```

### 0.2 Environment & Toolchain
- **JDK:** 17  
- **Gradle:** Kotlin DSL + Version Catalogs (`libs.versions.toml`).  
- **Android:** minSdk 26, targetSdk current; Jetpack Compose; Hilt DI; Coroutines/Flow; Room; Retrofit/OkHttp; WorkManager.  
- **Static Analysis:** ktlint + detekt; Android Lint baseline.  
- **CI:** GitHub Actions (or GitLab CI) with matrix for unit tests + instrumented tests.

### 0.3 Secrets & Config
- `app/src/main/assets/config.example.json` → copy to `config.json` at first run.  
- `.env.example` for local dev keys (never commit real keys).  
- Keys stored via **Android Keystore**; encrypt prefs with **Tink**/**EncryptedSharedPreferences**.

### 0.4 Fixtures (checked in)
- `fixtures/ohlcv/BTCUSDT_1h_2y.csv`, `ETHUSDT_1h_2y.csv`  
- `fixtures/ws/BTC_trades_15m.jsonl`  
- `fixtures/policies/*.json` (conflict scenarios)  
- `fixtures/automations/*.json` (EMA/RSI/Rotation)  
- `fixtures/accounts/*.json` (mock balances across venues)

### 0.5 Definition of Done (global)
- Unit tests for module ≥ 85% line coverage for pure logic.  
- Public APIs KDoc’d; non‑trivial algos have design notes.  
- All user actions emit ledger events; errors mapped to typed failures.  
- No module directly accesses another module’s tables—always via repos.

### 0.6 ADRs (Architecture Decision Records)
Template in `docs/adr/000-template.md`. Create ADRs for: **VM bytecode design**, **Conflict policy math**, **Indicator numeric tolerances**, **Funding planner cost model**.

### 0.7 Handoff Packet (give this to the other AI)
1) This document (canvas export as PDF/markdown).  
2) **Tracks A–I** list with acceptance criteria (Part II §20).  
3) **Data sources & connectors** summary (Part I §3).  
4) **Block schema freeze** (below).  
5) **Contracts module** (stubs below) + **mocks** (fixtures) so they can run tests immediately.  
6) **CI config** file and run command cheat‑sheet.  
7) **Style guide** (ktlint/detekt config) and **error taxonomy**.

### 0.8 Block Schema Freeze (v1)
```json
{
  "events": ["onCandle","onTick","onSchedule","onFill","onPnL"],
  "data": ["indicator","window","stateGet","stateSet","pairFeed","universe"],
  "logic": ["if","else","math","compare","crossesAbove","crossesBelow","cooldown","oncePerBar","forEach","rank"],
  "risk": ["riskSize","atrStop","trailStop"],
  "actions": ["emitOrder","emitSpread","log","notify","abort"],
  "version": 1
}
```

### 0.9 Contracts Module — Key Stubs
> Implementations go in `data/`, but interfaces live here so other tracks can code now.

**Market Data**
```kotlin
interface MarketDataFeed {
  suspend fun fetchOhlcv(symbol: String, tf: Interval, start: Long? = null, end: Long? = null, limit: Int? = null): List<Candle>
  fun streamTicker(symbols: Set<String>): Flow<Ticker>
  fun streamTrades(symbols: Set<String>): Flow<Trade>
  fun streamBook(symbols: Set<String>): Flow<OrderBookDelta>
}
```

**Broker** (Paper/Live share)
```kotlin
interface Broker { suspend fun place(order: Order): String; suspend fun cancel(orderId: String): Boolean; fun streamEvents(symbols: Set<String>): Flow<BrokerEvent>; suspend fun account(): AccountSnapshot }
```

**Funding Service**
```kotlin
interface FundingService {
  suspend fun planTransfer(from: AccountId?, to: AccountId, asset: String, amount: Amount, preferences: PlanPrefs = PlanPrefs()): TransferPlan
  suspend fun execute(plan: TransferPlan): PlanExecutionId
  fun track(executionId: PlanExecutionId): Flow<PlanStatus>
}
```

**Portfolio Aggregator**
```kotlin
interface PortfolioService {
  fun accounts(): Flow<List<Account>>
  fun holdings(): Flow<List<AggregatedHolding>>
  fun positions(): Flow<List<AggregatedPosition>>
  suspend fun acquire(asset: String, amount: Amount, target: AccountId? = null): TransferPlan
}
```

**Runtime/VM**
```kotlin
interface AutomationRuntime { fun load(def: AutomationDef): LoadedProgram; fun run(env: RuntimeEnv): Flow<Intent> }
```

### 0.10 CI Cheatsheet
- `./gradlew ktlintCheck detekt test`  
- `./gradlew :runtime:test --tests "*Backtest*"`  
- `./gradlew :ui:connectedDebugAndroidTest`

---

## Part I — Concepts & Architecture

## 1) Product Requirements & Non‑Goals
### Requirements
- Unified pipeline for SIM/PAPER/LIVE with identical semantics.
- Strategy *and* Automation engines feed the same execution path.
- Visual Block Editor to author automations; compile to DSL/bytecode.
- On‑device backtesting with realistic costs, slippage, and latency.
- Conflict policy: priority, netting, portfolio‑target, vote.
- Paper broker with L2‑aware fill simulation and latency/slippage models.
- Offline‑tolerant: cache market data, resume streams, backfill gaps.
- Observability: timeline, health, PnL, equity, error budgets.

### Non‑Goals (v1)
- Options/derivatives trading.
- Cross‑exchange smart order routing.
- Custody/PII heavy workflows beyond exchange keys.

---

## 2) System Architecture Overview
**Layers**
1. **Market Data** (REST/WS adapters, normalizer, cache)
2. **Strategy/Automation Runtime** (statecharts + bytecode VM)
3. **Risk & Policy** (sizing, limits, conflict resolution)
4. **Execution** (Coordinator → Broker: Paper/Live)
5. **Ledger** (event‑sourced truth → derived state)
6. **Persistence** (Room) & **Telemetry**
7. **UI** (Compose): tabs driven by `StateFlow<UiState>`

**Event Flow**
```
MarketData → Indicators → Automations/Strategies → Intents → Risk & Policy
→ ExecutionCoordinator → Broker(Paper/Live) → Fills → Ledger → UI
                                  ↑                             ↓
                         Backtester/Replay  ←———————  Derived State
```

---

## 3) Data Sources & Integrations
> This section is **source-of-truth** for where data comes from and how connectors behave. All adapters are swap‑in via a `SourceRegistry` and **must** conform to the contracts below. (URLs shown as examples; fetch from build‑time constants.)

### 3.1 Exchange Market Data (Spot)
**Exchanges (MVP):** Binance Spot, Coinbase Advanced Trade; **Phase‑2:** Bybit, OKX.  
**Data types:** OHLCV (agg trades), Ticker, Trades, Order Book (L2), Exchange Info (symbols, filters), Funding limits.

**Common interface (Retrofit):**
- `suspend fun fetchOhlcv(symbol: String, interval: Interval, start: Long?, end: Long?, limit: Int?): List<Candle>`
- `suspend fun fetchExchangeInfo(): ExchangeInfo`
- `fun streamTicker(symbols: Set<String>): Flow<Ticker>`
- `fun streamTrades(symbols: Set<String>): Flow<Trade>`
- `fun streamBook(symbols: Set<String>): Flow<OrderBookDelta>`

**Normalization**
- Symbol canonical form: `BASEQUOTE` (e.g., `BTCUSDT`).  
- Timeframes: enum `{ M1, M5, M15, M30, H1, H4, D1 }` → map to per‑exchange strings.
- Price/qty precisions from exchange filters; clamp UI/placement accordingly.

**Rate limiting & resilience**
- Token bucket per host; WS heartbeats + backoff; REST 429 retry with jitter.  
- Gap detection: when WS reconnects, backfill missing candles via REST.

### 3.2 Trading (Live)
**Order APIs** (per exchange adapter):
- `place(order: OrderDraft): PlaceOrderResult`
- `cancel(id: String): Boolean`
- `fetchOpenOrders(symbol?: String): List<Order>`
- `fetchPositions(symbol?: String): List<Position>` (if supported by exchange)
- `userEvents(): Flow<UserEvent>` (fills, order updates, account updates)

**Auth & keys**
- Secure storage: hardware‑backed Keystore; secrets only in memory when needed.  
- Per‑exchange signing: HMAC‑SHA256 with timestamp drift guard.

### 3.3 Wallets (On‑chain)
**Objective:** allow portfolio aggregation and optional on‑chain transfers without custody.  
**Connectors:**
- **WalletConnect v2** (EVM chains) → initiate transfers from user‑approved wallet.  
- **Deep Links / URI Schemes** for BTC/other non‑EVM wallets (e.g., `bitcoin:<addr>?amount=...`).  
- **Watch‑only** support: track balances via public RPC/block‑explorer endpoints.

**On‑chain data**
- EVM: ERC‑20 balances via JSON‑RPC `eth_call` batched; optional public providers (Alchemy/Infura/Ankr) configured via keys.  
- BTC/LTC: block explorer APIs (e.g., mempool.space or Blockstream) for UTXO & tx status (watch‑only).

### 3.4 Reference & Metadata
- **Assets catalog** (icons, names, decimals, tags) → seed from CoinGecko snapshot + local overrides.  
- **Networks registry**: map `USDT` across `ETH/ARB/BSC/TRX` with chain‑specific contract addresses and withdrawal tags/memos if CEX.

### 3.5 Optional DEX Aggregators (Phase‑2)
- **0x Swap API** / **1inch** for best‑price on EVM pairs via WalletConnect execution.

### 3.6 Source Registry
- `SourceRegistry` maps `sourceId → adapter` (e.g., `binance`, `coinbase`, `wallet:evm:0x…`).  
- Feature flags enable/disable connectors; UI reflects availability.

---

## 4) Domain Model & Schemas Domain Model & Schemas
Core types (Room entities + network DTO mappers):
- **Candle**: `ts, open, high, low, close, volume, interval, symbol, source`.
- **Tick**: `ts, price, qty, side`.
- **OrderBookSnap/Delta** (optional for L2 sim): `bids[], asks[]`.
- **Intent**: `id, sourceId, kind(Strategy|Automation), symbol, side, qty|notional, priceHint, meta`.
- **Order**: `id, brokerId?, symbol, side, type, qty, price?, stopPrice?, tif, status, feesEst`.
- **Fill**: `orderId, ts, price, qty, fee`.
- **Position**: `symbol, qty, avgPrice, realized, unrealized, risk, stops`.
- **AutomationDef**: versioned JSON (visual blocks/DSL) + compiled bytecode.
- **SimParams**: `latencyMs, slippageBps, feeModel, partialFillRatio`.
- **Policy**: conflict resolution configuration.

JSON Schemas in **Appendix B**.

---

## 5) Event‑Sourced Ledger
**Why**: Single source of truth; backtest/paper/live share identical code.

**Event types**
- `MD_CANDLE`, `MD_TICK`, `INTENT_CREATED`, `ORDER_PLACED`, `ORDER_ACCEPTED`, `ORDER_REJECTED`, `ORDER_CANCELED`, `ORDER_FILLED`, `STOP_SET/UPDATE/HIT`, `PNL_UPDATE`, `HEALTH`.

**API**
- `append(event: LedgerEvent)`
- `stream(fromTs): Flow<LedgerEvent>`
- `materialize(snapshotSpec): DerivedState` (positions, PnL, equity, stats)

**Acceptance**
- Replaying ledger from empty reproduces the same positions/PnL (bit‑for‑bit) across SIM/PAPER/LIVE for identical inputs.

---

## 6) Market Data Layer (REST + WS + Cache)
**Responsibilities**
- Normalize symbols/timeframes; forward‑fill partial candles; gap backfill.
- Unified interface: `fetchOHLCV(symbol, interval, limit)`, `subscribe(symbols, streams)`.
- Local cache (Room) with pruning & indices.

**Key classes**
- `MarketDataFeed` (facade)
- `ExchangeAdapter` (per exchange)
- `OhlcvCache` (Room DAO)
- `StreamSupervisor` (WS lifecycle: backoff, heartbeats)

**Edge Cases**
- Exchange maintenance; symbol delist; time drift; partial candles; WS reconnect with missed trades → backfill.

**Acceptance**
- 99th‑pctl WS reconnect <3s; no duplicate candles; monotonic candle timestamps.

---

## 7) Broker Layer (Paper + Live)
**Interfaces**
- `Broker.place(order): String` → broker orderId
- `Broker.cancel(orderId): Boolean`
- `Broker.streamEvents(symbols): Flow<BrokerEvent>`
- `Broker.account(): AccountSnapshot`

**Paper Broker (Sim)**
- Inputs: market stream (best bid/ask, optional L2), `SimParams`.
- Model: latency (ms), slippage (bps), maker/taker fees, partial fills.
- Outputs: `Accepted`, `PartialFill`, `Filled`, `Canceled`, `Rejected`.
- Deterministic given same inputs (injectable `Clock`).

**Live Broker**
- Signed REST for orders; WS user‑data for fills; idempotent `clientOrderId`.

**Acceptance**
- Paper fills within configured latency ± jitter; slippage applied correctly; idempotent place/cancel.

---

## 7A) Funding, Deposits, Withdrawals & Transfers
**Goal:** unified, behind‑the‑scenes movement of assets across **exchanges** and **wallets**, with safety rails.

### 7A.1 Concepts
- **Account** (CEX or Wallet): has balances, network capabilities, fees, limits, whitelisted addresses.
- **TransferPlan**: concrete steps to move asset A from account X → Y (may include buy/sell steps and on‑chain hops).
- **AddressBook**: per asset+network deposit addresses (with memo/tag support).

### 7A.2 CEX Funding APIs
- **Deposit addresses**: create/fetch for `asset+network` (e.g., USDT+TRX).  
- **Withdraw**: request withdrawal to `address+network(+memo)`; poll status; respect 2FA/whitelist modes.  
- **Internal transfer**: sub‑account or product (spot <→ funding) moves where supported.

### 7A.3 Wallet Transfers (On‑chain)
- **EVM**: build transaction (to, data, value, gas limit), present via WalletConnect; await tx hash & confirmations.
- **UTXO chains (BTC)**: app opens wallet deep‑link URI; confirmation is out‑of‑band → poll explorer until confirmed.

### 7A.4 Planner (Acquisition/Settlement)
**Use‑cases:**
1. **Acquire coin X anywhere**: choose venue that minimizes **all‑in cost** (price + trading fee + withdrawal fee + network fee + time).  
2. **Consolidate balances**: sweep dust to a target account.  
3. **Fulfill automation**: when automation targets a symbol on a specific venue, but inventory sits elsewhere.

**Algorithm (greedy baseline):**
- Get live quotes across venues; compute all‑in cost per unit to target account.  
- Respect constraints: min withdrawal size, network availability, whitelists, risk caps.  
- Produce **TransferPlan**: `[BUY on Binance] → [WITHDRAW USDT(TRX) to Wallet] → [SWAP via 0x to target token]` (if DEX enabled), or direct `[BUY on Coinbase] → [HOLD]`.

### 7A.5 Safety Rails
- Dry‑run + cost breakdown shown to user.  
- Two‑tap confirm for withdrawals; enforce allow‑list.  
- Timeouts/rollback: if a step fails, stop subsequent legs and notify.

**Deliverables**
- `FundingService` with: `planTransfer()`, `execute(plan)`, `track(statusId)`; CEX adapters implementing deposit/withdraw, wallet connectors for on‑chain.

**Acceptance**
- Simulated end‑to‑end plan with mocks; real deposit address fetch; paper execution paths logged to Ledger.

---

## 8) Execution Coordinator & Conflict Policies
**Purpose**
- Consume `Intent`s from multiple sources; apply risk & policy; emit orders.

**Core flow**
1. Deduplicate by `(sourceId, barTs/hash)`.
2. Enforce per‑source cooldown/idempotency.
3. Apply **Policy**:
   - **Priority**: rank sources; higher wins.
   - **Netting**: offset opposing intents on same symbol.
   - **Portfolio‑target**: convert intents to target weights (e.g., rotation) and compute net orders.
   - **Vote**: majority direction across sources.
4. Pass to Risk Sizer → Orders.

**Acceptance**
- Given conflicting intents, output matches policy math; proof in ledger audit.

---

## 9) Risk, Sizing & Portfolio Engine
**Features**
- Fixed fraction, fixed notional, ATR‑scaled sizing, volatility targeting.
- Global caps: max risk % of equity, per‑symbol caps.
- Stops: ATR, trailing, time‑based; take‑profit.
- Correlation guard: block overlapping beta across strategies (optional).

**Deliverables**
- `RiskProfile` config; `size(intent, account, market): OrderDraft[]`.

**Acceptance**
- Unit tests on canonical fixtures (see Appendix C) for position sizing & stop placement.

---

## 9A) Accounts & Portfolio Aggregator (Multi‑Exchange, Multi‑Wallet)
**Objective:** provide a unified **Portfolio** that aggregates balances and positions across CEX accounts and on‑chain wallets, and supports **venue‑agnostic acquisition**.

### 9A.1 Account Model
```text
Account(id, kind: CEX|WALLET, name, venueId, networks[], feeSchedules, limits)
Holding(accountId, asset, network?, free, locked, valuationUsd)
Position(accountId, symbol, qty, avgPrice, realizedPnl, unrealizedPnl)
AddressBook(accountId, asset, network, address, memo?)
```

### 9A.2 Repositories & Polling
- `AccountsRepo`: add/remove accounts; secrets live only in secure store.  
- `BalancesRepo`: incremental sync via WS/user‑events (CEX) + periodic REST; on‑chain via RPC/explorers.

### 9A.3 Aggregation API
- `getNetHoldings(asset[, network]) → AggregatedHolding`  
- `getNetExposure(symbol) → AggregatedPosition`  
- `portfolioValue(base=USD) → Double`

### 9A.4 Venue‑agnostic Buy/Sell
- `acquire(asset, amount, targetAccount?)` → uses **Planner** (7A.4) to pick venue & route.  
- Supports: direct CEX order; CEX + withdrawal; wallet + DEX swap (EVM) if enabled.

### 9A.5 UI — Portfolio Tab
- Accounts list (CEX & Wallets) with per‑account balances.  
- Aggregated view by Asset & by Symbol.  
- **Buy Anywhere** flow with cost breakdown & plan preview.  
- Transfer/Withdraw/Deposit actions per asset with network picker & safety checks.

**Acceptance**
- With two linked CEX accounts and one EVM wallet (watch‑only), the app shows a single USD total, per‑asset merged balances, and can plan a route to acquire `SOL` even if only `USDT` exists on the other exchange.

---

## 10) Strategy Runtime (DSL + Statecharts + VM) Strategy Runtime (DSL + Statecharts + VM)
**Model**
- Each strategy/automation is a **statechart** compiled to **bytecode** executed by a small VM.
- Events: `CANDLE(tf)`, `TICK`, `SCHEDULE`, `FILL`, `PNL`, `TIMEOUT`.
- Guards: crossings, thresholds, cooldown, once‑per‑bar.
- Actions: set state, set stops, emit intents, notify.

**Artifacts**
- DSL (Kotlin) for built‑in strategies.
- JSON (visual) → compiler → bytecode.

**Acceptance**
- Deterministic replays; op budget per automation; stall‑free.

---

## 11) Visual Automation Editor (Blocks → JSON → Bytecode)
**Blocks**
- **Events**: OnCandle(tf), OnTick, OnSchedule(cron), OnFill, OnPnL
- **Data/State**: Indicator(name, params), Window(N), State(key, initial)
- **Logic**: If/Else, CrossesAbove/Below, Compare, Math, Cooldown, OncePerBar
- **Universe/Rank**: Universe(filter), Rank(metric, topK), ForEach
- **Risk/Action**: RiskSize(%/vol), ATRStop, TrailStop, EmitOrder, EmitSpread
- **Util**: Log, Notify, Abort

**Flow**
- Canvas JSON (versioned) → Type/Static checks → IR → Bytecode.

**Acceptance**
- Every built‑in strategy can be authored in blocks with identical backtest results to the DSL version ±0.5% PnL tolerance.

---

## 12) Indicator & Window Engine (TA math)
**Indicators**: EMA/SMA/WMA, RSI, MACD, Bollinger, Donchian, ATR, Stdev, ROC, Z‑Score, Chandelier Exit.
**Windowing**: Rolling series with O(1) incremental updates; memoized per `(symbol, tf, params)`.

**Acceptance**
- Numerical parity with TA4J/NumPy within tolerance in fixtures; no recompute of full history on each tick.

---

## 13) Backtesting & Research
**Engine**
- Event‑driven: feed candles → runtime → intents → risk → broker‑sim.
- Costs: bps, spreads, latency; slippage model.
- Walk‑forward: train/test split; parameter grid.
- Metrics: CAGR, Sharpe, Sortino, MaxDD, MAR, Win rate, Avg R, Exposure.

**Acceptance**
- Backtest BTCUSDT 1h 2y in <30s on device (baseline target), results persist with equity curve & trades overlay.

---

## 14) UI/UX by Tab (Compose)
> All screens are fed by `UiState` slices derived from Ledger + Repos.

### 14.1 Dashboard
**Widgets**: Mode switch (Sim/Paper/Live), equity curve, daily P&L, risk meter, health (WS lag, error budget), kill switch.
**Actions**: Toggle mode, open Sim Params, export diagnostics.
**State**: `dashboard: equity[], pnlToday, mode, simParams, health`.
**Acceptance**: Mode switch recolors theme; kill switch cancels all orders and returns flat state within 1s.

### 14.2 Strategies
**List**: Cards with on/off, preset, last signal, micro‑sparkline.
**Details**: Params, backtest vs paper comparison, logs.
**Actions**: Enable/disable, edit params, “Promote to Paper”.

### 14.3 Automations
**List**: Cards with trigger (cron/price/event), next run, last outcome.
**Editor**: Visual canvas + properties panel + JSON tab + Validate/Compile.
**Actions**: Dry‑run toggle; Backtest on canvas; Save versions.

### 14.4 Timeline (Activity)
**Feed**: Intents → Orders → Fills; filters: source, symbol, status, mode.
**Conflict banner**: Show conflicting sources on same symbol; CTA: apply policy / resolve now.

### 14.5 Blotter
**Segments**: Orders, Positions, Fills, Alerts. Paper‑only columns: slippage bps, sim latency.

### 14.6 Backtests
**Compare**: Backtest vs Paper equity & stats; parameter report; export CSV.

### 14.7 Settings
**Sections**: Risk caps, Policies, Exchange keys (Live), Notifications, Data retention.

### 14.8 Sim Params (Paper)
**Controls**: latency, slippage, fees, partial fills; reset presets.

### 14.9 Alerts/Notifications
**Config**: sources, channels; test notification.

### 14.10 Portfolio (Aggregate Accounts)
**Views**
- **Overview**: total equity, P&L, allocation by asset & venue; search/filter by asset/network.  
- **Accounts**: tiles for each account (CEX/Wallet) with balances and quick actions (Deposit/Withdraw/Transfer).  
- **Asset Details**: per‑asset holdings across venues, cost basis, recent activity, **Buy Anywhere** button → Plan preview.

**Actions**
- Link CEX account (API keys); Link Wallet (WalletConnect or watch‑only).  
- Deposit (show address + QR), Withdraw (venue‑aware form with memo/tag), Transfer (plan selection), Reconcile (refresh, rescan).

**State**
- `portfolio: accounts[], holdings[], positions[], valuation, planDraft?`

**Acceptance**
- Aggregated balances update within 10s of a new fill or on‑chain confirmation; plan preview shows all‑in costs and ETA.
**Config**: sources, channels; test notification.

---

## 15) Persistence & Migrations (Room)
**Entities**: `candle`, `intent`, `order`, `fill`, `position`, `automation_def`, `automation_state`, `sim_params`, `policy`, `ledger_event`.
**Indices**: composite `(symbol, interval, ts)`; `(orderId)`, `(sourceId, ts)`.
**Migrations**: versioned SQL; backfill derived tables from ledger on upgrade.

**Acceptance**
- Cold start with empty DB works; migrations preserve strategy/automation configs and history.

---

## 16) Telemetry & Observability
**Logs**: structured JSON per module (tag, ts, level, fields).
**Health**: WS latency, reconnects, data gaps, backoff counts; displayed on Dashboard.
**User‑opt analytics**: feature usage counts (no PII), crash/ANR monitoring.

---

## 17) Security, Privacy & Compliance Basics
- Exchange keys in hardware‑backed keystore; encrypted prefs for metadata.
- BiometricPrompt before viewing keys.
- Data minimization; explicit “not financial advice” footprint.

---

## 18) Performance & Resource Budgets
- Backtest target: 2y 1h BTC in <30s; CPU < 2 cores sustained.
- Live: end‑to‑end intent→fill (paper) median <250ms; 99p <800ms.
- Memory: ≤ 250MB steady with 3 symbols streaming, 10 automations active.
- Battery: background polling ≤ 2%/hr cap; use WorkManager for schedules.

---

## 19) Release Management & Feature Flags
- Flags: Visual Editor, Rotation, Pairs, Premium feeds, Live Trading.
- Staged rollout; crash/ANR guardrails; remote disable for risky features.

---

## Part II — Implementation Plan & Phases

## 20) Work Breakdown Structure (Epics → Milestones → Tasks)

### Epic A — Foundations (2–3 weeks)
1. **A1 Core Models & Room**  
   Deliverables: entities, DAOs, migrations v1.  
   Acceptance: CRUD + sample fixtures load.
2. **A2 Market Data (Binance + Cache)**  
   Deliverables: REST/WS adapters, normalizer, cache.  
   Acceptance: Stream + backfill without gaps.
3. **A3 Ledger & Derived State**  
   Deliverables: append/stream/materialize.  
   Acceptance: replay parity.

### Epic B — Paper Execution (2 weeks)
1. **B1 Broker Interface & Paper Broker**  
   Deliverables: fills, latency/slippage/fees models.  
   Acceptance: deterministic simulations.
2. **B2 Execution Coordinator & Policies**  
   Deliverables: priority/netting/portfolio‑target/vote.  
   Acceptance: conflict scenarios pass.
3. **B3 Risk & Sizing**  
   Deliverables: fixed/ATR/vol targeting, stops.  
   Acceptance: fixture parity.

### Epic C — Runtime & Indicators (2–3 weeks)
1. **C1 Indicator Engine + Windows**  
   Deliverables: EMA/RSI/MACD/BB/DC/ATR/etc.  
   Acceptance: numeric parity.
2. **C2 Statechart Runtime + VM**  
   Deliverables: bytecode, guards, actions, timeouts.  
   Acceptance: deterministic replay & quotas.
3. **C3 Built‑in Strategies (10)**  
   Deliverables: EMA, RSI, BB, MACD, Donchian, Rotation, Z‑Score, Grid, DCA, Pair Ratio.  
   Acceptance: backtest each with defaults.

### Epic D — Visual Editor (2 weeks)
1. **D1 Canvas + Blocks + JSON**  
   Deliverables: palette, properties panel, serializer.  
   Acceptance: save/load/validate.
2. **D2 Compiler (JSON → Bytecode)**  
   Deliverables: type checks, IR, codegen.  
   Acceptance: EMA/RSI parity vs DSL.

### Epic E — UI Tabs (2 weeks)
- Dashboard, Strategies, Automations, Timeline, Blotter, Backtests, Settings, Sim Params, Alerts.  
  Acceptance: Navigation, state restoration, dark/light themes.

### Epic F — Polishing (1–2 weeks)
- Perf, battery, error handling, empty/error states, diagnostics exporter.

---

## 21) Appendix A — Kotlin Interfaces & Pseudocode

### A.1 Broker & Events
```kotlin
interface Broker {
  suspend fun place(order: Order): String
  suspend fun cancel(orderId: String): Boolean
  fun streamEvents(symbols: Set<String>): Flow<BrokerEvent>
  suspend fun account(): AccountSnapshot
}
sealed class BrokerEvent {
  data class Accepted(val orderId: String, val order: Order): BrokerEvent()
  data class PartialFill(val orderId: String, val fill: Fill): BrokerEvent()
  data class Filled(val orderId: String, val fill: Fill): BrokerEvent()
  data class Canceled(val orderId: String): BrokerEvent()
  data class Rejected(val orderId: String, val reason: String): BrokerEvent()
}
```

### A.2 Execution Coordinator (pseudocode)
```kotlin
fun onIntent(intent: Intent) {
  if (!idempotency.ok(intent)) return
  queue.add(intent)
  val batch = queue.collectWindow(200.ms) // micro-batch for policy
  val plan = policyEngine.net(batch, positionsRepo.snapshot())
  val orders = riskSizer.size(plan)
  orders.forEach { placeOrder(it) }
}
```

### A.3 Statechart Runtime & VM (ops sketch)
```
OPS: LOAD, GET_STATE, SET_STATE, IND(name,len), WINDOW(n), CROSS_ABOVE, CROSS_BELOW,
     CMP_LT, CMP_GT, ABS, THRESH, ONCE_PER_BAR, COOLDOWN(ms), EMIT(side, qty|notional,
     type), EMIT_SPREAD, SET_STOP_ATR, TRAIL_STOP, LOG, NOTIFY, WAIT(ms), HALT
```

### A.4 Indicator Cache
```kotlin
class IndicatorCache {
  private val m = ConcurrentHashMap<Key, Indicator<Number>>()
  fun <T: Number> get(symbol: String, tf: TF, name: String, params: Map<String,Any>): Indicator<T> { /* memoized */ }
}
```

### A.5 Ledger
```kotlin
interface Ledger {
  suspend fun append(ev: LedgerEvent)
  fun stream(from: Long = 0L): Flow<LedgerEvent>
}
```

---

## 22) Appendix B — JSON Schemas (abridged)

### B.1 AutomationDef (visual JSON)
```json
{
  "$schema": "https://json-schema.org/draft-07/schema#",
  "title": "AutomationDef",
  "type": "object",
  "properties": {
    "v": {"type": "integer"},
    "id": {"type": "string"},
    "graph": {
      "type": "object",
      "properties": {
        "nodes": {"type": "array", "items": {"type": "object", "properties": {
          "id": {"type": "string"},
          "type": {"type": "string"},
          "props": {"type": "object"}
        }, "required": ["id","type"]}},
        "edges": {"type": "array", "items": {"type": "array", "items": {"type": "string"}, "minItems": 2, "maxItems": 2}}
      },
      "required": ["nodes","edges"]
    }
  },
  "required": ["v","id","graph"]
}
```

### B.2 PolicyConfig
```json
{
  "type": "object",
  "properties": {
    "mode": {"enum": ["priority","netting","portfolio_target","vote"]},
    "priority": {"type": "array", "items": {"type": "string"}},
    "voteThreshold": {"type": "number"}
  },
  "required": ["mode"]
}
```

---

## 23) Appendix C — Test Plan & Fixtures

### C.1 Unit Tests
- Indicators: known sequences → expected outputs.
- Risk: given equity, ATR, risk% → expected size/stops.
- Policy: conflicting intents produce correct net plan.
- Paper fills: latency/slippage/partials exactness.

### C.2 Integration Tests
- End‑to‑end backtest: EMA cross BTCUSDT 1h with fixture data → metrics snapshot.
- Visual automation parity: JSON blocks vs DSL yield identical signals on fixture bars.

### C.3 Replay Tests
- Recorded live WS stream → paper broker replay → same fills and PnL.

**Fixtures**
- `BTCUSDT_1h_2y.csv`, `ETHUSDT_1h_2y.csv` (OHLCV)
- `WS_trades_BTC_15m.jsonl` (replay)

---

## “AI Handoff” Notes (per‑module contracts)
Each task in WBS includes:
- **Inputs/Outputs**
- **Interfaces to implement**
- **Edge cases**
- **Acceptance tests** (from Appendix C)
- **Artifacts** to produce (code, sample JSON, screenshots if UI)

> **Rule**: No module may read another module’s DB tables directly; always go through its repository/service to preserve testability.

---

### Final Notes
- Prefer **Kotlin Coroutines/Flow**, **Jetpack Compose**, **Room**, **Hilt**, **WorkManager**, **OkHttp/Retrofit**.
- Use **feature flags** to stage risky features (Rotation, Pairs, Live Trading).
- Keep **bytecode VM** small and well‑tested; it’s the keystone for determinism.

This spec is intentionally modular so you can assign Epics/Tasks to independent workers (or agents) without stepping on each other.

