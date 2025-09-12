# CryptoTrader 2.0 — Handoff Design (Part 0, Part I, Part II)

## Part 0 — Handoff Bundle & Checklist
- Repo layout, toolchain, secrets, fixtures, DoD, ADRs
- Block Schema Freeze (v1)
- Contracts module stubs
- CI cheat sheet

### Block Schema Freeze (v1)
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

## Part I — Concepts & Architecture

### Product Requirements
- Unified SIM/PAPER/LIVE, identical semantics
- Visual Automations compile to bytecode VM (statecharts)
- Backtester with realistic costs
- Conflict policy engine (priority/netting/portfolio-target/vote)
- Portfolio aggregator across exchanges & wallets with **Buy Anywhere**

### System Architecture
```
MarketData → Indicators → Runtime(Automations/Strategies) → Intents
   → Risk/Sizing → Policy → ExecutionCoordinator → Broker(Paper/Live) → Fills
   → Ledger (event-sourced) → Derived State → UI
```

### Data Sources & Integrations
- **Exchanges (MVP):** Binance Spot, Coinbase Advanced Trade
- **Phase-2:** Bybit, OKX
- **Wallets:** WalletConnect v2 (EVM), watch-only RPCs, deep links for BTC-like
- **Metadata:** CoinGecko snapshot + local overrides
- **Optional:** 0x / 1inch DEX aggregation

**Adapter contracts (high-level):**
- MarketData: `fetchOhlcv`, `streamTicker`, `streamTrades`, `streamBook`
- Trading: `place`, `cancel`, `userEvents`, `fetchOpenOrders`
- Funding (CEX): `getDepositAddress`, `withdraw`, `transferInternal`
- Wallets: sign/send tx via WalletConnect; watch balances

### Funding / Deposits / Withdrawals / Transfers
- `FundingService.planTransfer()` produces `TransferPlan` across venues & networks
- Safety: dry-run, whitelists, two-tap confirm, rollback on failure

### Accounts & Portfolio Aggregator
- Aggregates balances/positions across CEX+wallets
- Provides `acquire(asset, amount)` → uses Funding planner to choose venue
- UI: Portfolio tab with **Buy Anywhere** flow

### Ledger
- Append-only events: MD_*, INTENT_*, ORDER_*, FILL_*, STOP_*, PNL_*, HEALTH
- Replaying reproduces state exactly

### Runtime (Statecharts + Bytecode VM)
- Typed events, guards (oncePerBar, crosses, thresholds), actions (emit, stops)
- Deterministic clock; quotas

### Execution Coordinator & Policies
- Dedup intents; apply policy; pass to RiskSizer; place orders

### Risk & Sizing
- Fixed %, notional, ATR scaling, volatility targeting; global caps
- Stops: ATR, trailing, time-based

### Indicator Engine
- EMA/SMA/WMA, RSI, MACD, BB, Donchian, ATR, ROC, Z-score, Chandelier
- O(1) incremental updates; memoized per (symbol, tf, params)

### UI Tabs
- Dashboard, Strategies, Automations (visual editor), Timeline, Blotter, Backtests, Settings, Sim Params, **Portfolio**

## Part II — Implementation Plan & Phases
- Epics A–F with milestones and acceptance criteria
- Tracks A–I for parallel execution

See **docs/Tracks.md** for detailed track breakdown and **docs/DataSources.md** for adapter specifics.
