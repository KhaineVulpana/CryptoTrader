# Portfolio Aggregator & Planner (Spec)

## Models
- Account(id, kind: CEX|WALLET, name, venueId, networks[])
- Holding(accountId, asset, network?, free, locked, valuationUsd)
- Position(accountId, symbol, qty, avgPrice, realizedPnl, unrealizedPnl)
- AddressBook(accountId, asset, network, address, memo?)

## Aggregation API
- getNetHoldings(asset[, network]) -> AggregatedHolding
- getNetExposure(symbol) -> AggregatedPosition
- portfolioValue(base=USD) -> Double

## Acquire Flow
- Input: asset, amount, optional target account
- Planner computes all-in cost per venue (price + trading fee + withdrawal fee + network fee + time)
- Produces TransferPlan of steps (buy/withdraw/swap/transfer)
- Safety rails: dry-run, allowlist, two-tap confirm
