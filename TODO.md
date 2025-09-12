# CryptoTrader 2.0 — Overarching TODO

Global Definition of Done
- [ ] Unit tests for pure logic ≥85% coverage; golden fixtures locked
- [ ] All user actions emit typed Ledger events; errors mapped to sealed types
- [ ] CI passes: `./gradlew ktlintCheck detekt test`

PR Sequence
- [x] PR #1 — Bootstrap (modules, contracts, CI/linters, README)
- [x] PR #2 — Indicator & Window Engine (Track A)
- [x] PR #3 — Statechart VM core (Track B)
- [ ] PR #4 — Policy/Risk (Track C)
- [ ] PR #5 — PaperBroker (Track D)
- [ ] PR #6 — Backtester (Track E)
- [ ] PR #7 — Visual Editor + Compiler JSON→bytecode (Track F)
- [ ] PR #8 — Portfolio aggregator + Funding planner (Track G)
- [ ] PR #9 — UI tabs (Compose) driven by ledger streams (Track H)

Track A — Indicators (Core)
- [x] O(1) windows: sums, sumSq, mono-queues
- [x] EMA (SMA-seeded), RSI (Wilder), MACD, BB, Donchian, ATR (Wilder), Z-score
- [x] Parity tests over `fixtures/ohlcv/*`

Track B — Statechart VM
- [x] Bytecode schema (Block Schema v1 subset)
- [x] Guards: oncePerBar, crosses, thresholds
- [x] Actions: emit; Timers: delay; Quotas
- [x] Deterministic replay tests

Track C — Policy/Risk
- [ ] Netting engine: priorities, portfolio-target, vote + sizing
- [ ] Risk sizer maps NetPlan → Orders
- [ ] Error taxonomy (sealed types) and tests

Track D — PaperBroker
- [ ] Deterministic fills with latency/slippage/fees/partials
- [ ] Stream broker events; typed errors

Track E — Backtester
- [ ] Event-driven runner; metrics and reports

Track F — Visual Editor
- [ ] Blockly/WebView UI for Block Schema v1
- [ ] Compiler JSON→bytecode with parity tests to runtime

Track G — Portfolio
- [ ] Aggregator + Funding planner (mock connectors)

Track H — UI
- [ ] Compose tabs driven by ledger streams; Hilt wiring

Housekeeping
- [ ] Detekt/Ktlint strict mode on all modules
- [ ] Code coverage reporting in CI
