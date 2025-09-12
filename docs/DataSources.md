# Data Sources & Connectors (Spec)

## Exchanges (MVP)
### Binance Spot
- REST: /api/v3/klines, /api/v3/exchangeInfo, /api/v3/order, /api/v3/openOrders
- WS: wss://stream.binance.com:9443 (trade, bookTicker, depth)
- User Data Stream: POST /api/v3/userDataStream → listenKey → WS user events

### Coinbase Advanced Trade
- REST: /api/v3/brokerage/products/{id}/candles, /orders
- WS: wss feed for market data & user channel

## Phase-2 Exchanges
- Bybit v5, OKX v5 (analogous endpoints)

## Wallets
- WalletConnect v2: session pairing, eth_sendTransaction, personal_sign
- Watch-only: JSON-RPC (eth_call, eth_getBalance, multicall), or explorers

## Metadata
- CoinGecko: /coins/list, /coins/markets (seed snapshot offline)

## DEX Aggregators (Optional)
- 0x Swap API, 1inch Quote/Swap endpoints
