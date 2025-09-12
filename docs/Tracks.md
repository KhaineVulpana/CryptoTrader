# Tracks A–I (Parallel Work)

## A — Indicator & Window Engine
- Implement EMA/RSI/MACD/BB/DC/ATR/Z-Score with rolling windows
- O(1) per new bar; parity vs fixtures

## B — Statechart VM (Runtime)
- Bytecode interpreter, guards, actions, timers, quotas
- Deterministic clock; persistent state; replay tests

## C — Policy & Risk
- Priority/netting/portfolio-target/vote; fixed%, ATR, vol targeting; stops
- Pure functions + unit tests

## D — Paper Broker
- L2-aware fills; latency/slippage/fees/partials; deterministic

## E — Backtester
- Event-driven; integrates A–D; metrics (CAGR, Sharpe, MaxDD, MAR, win rate)

## F — Visual Automation Editor
- Blockly/WebView; blocks per schema; JSON→IR→bytecode compiler; parity vs DSL

## G — Portfolio Aggregator & Planner
- Multi-account balances; venue-agnostic acquire(); Funding planner (greedy baseline)

## H — Timeline / Blotter / Backtests UI
- Compose tabs; feeds from ledger; filters

## I — QA & CI
- Fixtures, golden files, coverage, CI pipeline
