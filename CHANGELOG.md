# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Reloading this module (`/ul reload UltiTrade`) now re-reads `config/trade.yml` and refreshes the
  language files, so an edited value such as `max-distance` applies without a restart. Previously
  this module's reload method replaced the framework's and only logged a line, so neither step ran.
  UltiTools 6.3.0 also reports `@ConditionalOnConfig` drift and logs its own per-module reload line
  at this point (UltiKits/UltiTrade#15).
- `/ul reload UltiTrade` now also applies `enable-money-trade`, `enable-trade-log` and
  `cleanup-interval-hours`. The module's new reload hook re-runs the Vault economy lookup, so turning
  money trading on or off takes effect without a restart, and reschedules the old-log cleanup task, so
  turning `enable-trade-log` off stops that task, turning it on starts one, and a changed interval
  replaces it. Previously the economy lookup and the cleanup task were set up only when the module
  started, so turning money trading on, and any change to the cleanup task, took effect only after a
  restart. A trade request that is already pending keeps the timeout it was sent with; a changed
  `request-timeout` applies to requests sent after the reload (UltiKits/UltiTrade#26).
- A trade in which either player offered money is now cancelled if money trading is not available at
  the moment the trade completes, for example after `/ul reload UltiTrade` turned `enable-money-trade`
  off while the trade was open, or after a reload found no Vault economy provider. No money moves, each
  player gets back the items they offered, and both players are told that money trading is currently
  unavailable, instead of the items and experience being exchanged without the offered money. While
  money trading is unavailable, the chat amount prompt also refuses a money amount instead of accepting
  it (UltiKits/UltiTrade#26).
- `/ul reload UltiTrade` now voids the confirmations of every open trade that had one and tells both
  players to confirm again, so a trade no longer completes on terms such as `trade-tax`, `exp-tax-rate`
  or `confirm-threshold` that the reload changed after a player confirmed. A trade in which either
  player offered experience is likewise cancelled if `enable-exp-trade` is off when it completes,
  instead of the items being exchanged without the experience, and the chat amount prompt refuses an
  experience amount while `enable-exp-trade` is off (UltiKits/UltiTrade#26).
- `/ul reload UltiTrade` now redraws every open trade window from the reloaded configuration, so its
  title, money and experience availability and taxes match what the trade will charge. Every offer is
  kept as it was. An open large-trade confirmation page is replaced with a trade window, although on
  current builds that page does not stay open because of UltiKits/UltiTrade#23
  (UltiKits/UltiTrade#27).
- Players can no longer take the display item out of the money slot of the trade window while money
  trading is off, out of the experience slot while experience trading is off, or the glass pane out of
  an empty item slot, by clicking it. Every click in the trade window is now cancelled before its action
  runs (UltiKits/UltiTrade#25).
- `/upm uninstall UltiTrade` now runs this module's own cleanup (trade service shutdown,
  PlaceholderAPI expansion unregistration) first, then the framework's command unregistration, then
  its listener unregistration, so after the uninstall the module's commands are really removed and
  its listeners stop firing. Previously this module's unload method replaced the framework's, so both
  its commands and its listeners stayed active until the server restarted (UltiKits/UltiTrade#15).
- Taking a placed item back out of the trade window no longer destroys it when the acting player's
  inventory is completely full: whatever does not fit is now dropped at that player's feet, the same
  overflow handling a cancelled or completed trade already uses when it returns items. Previously the
  item was silently destroyed while the slot reverted to its glass-pane placeholder in both trade
  windows as if the removal had succeeded (UltiKits/UltiTrade#20).
- 重载本模块（`/ul reload UltiTrade`）现在会重新读取 `config/trade.yml` 并刷新语言文件，修改后的
  `max-distance` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出一行日志，
  这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志（UltiKits/UltiTrade#15）。
- `/ul reload UltiTrade` 现在还会应用 `enable-money-trade`、`enable-trade-log` 和 `cleanup-interval-hours`。
  本模块新增的重载钩子会重新查找 Vault 经济提供者，使开启或关闭金币交易无需重启即可生效；并重新调度旧日志
  清理任务，使关闭 `enable-trade-log` 后该任务停止、开启后启动一个任务、修改间隔后以新间隔替换。此前经济
  提供者查找与清理任务只在模块启动时设置，因此开启金币交易以及对清理任务的任何修改都要重启后才生效。已发出的
  待处理交易请求保持其发出时的超时时间；修改后的 `request-timeout` 只对重载之后发出的请求生效（UltiKits/UltiTrade#26）。
- 若交易中任一方出价了金币，而在交易完成的那一刻金币交易不可用（例如交易进行中执行 `/ul reload UltiTrade`
  关闭了 `enable-money-trade`，或重载时未找到 Vault 经济提供者），该交易现在会被取消：不转移任何金币，双方各自
  取回自己放入的物品，并被告知金币交易当前不可用，而不是在不支付所出金币的情况下交换物品与经验。金币交易
  不可用时，聊天输入金额提示也会拒绝金币数额，而不是接受它（UltiKits/UltiTrade#26）。
- `/ul reload UltiTrade` 现在会清除所有已有确认的进行中交易的确认状态，并提示双方重新确认，因此交易不会再按
  玩家确认后被重载修改的条款（如 `trade-tax`、`exp-tax-rate`、`confirm-threshold`）完成。同样，若任一方出价了
  经验，而交易完成时 `enable-exp-trade` 已关闭，该交易会被取消，而不是在不转移经验的情况下交换物品；
  `enable-exp-trade` 关闭时，聊天输入提示也会拒绝经验数额（UltiKits/UltiTrade#26）。
- `/ul reload UltiTrade` 现在会按重载后的配置重绘所有已打开的交易界面，使其标题、金币与经验交易的可用状态和税率
  与交易实际收取的一致；所有出价保持不变。已打开的大额交易确认页会被替换为交易界面，但在当前版本中该确认页因
  UltiKits/UltiTrade#23 不会保持打开（UltiKits/UltiTrade#27）。
- 玩家不再能通过点击，在金币交易关闭时从交易界面的金币栏、在经验交易关闭时从经验栏取走展示物品，或从空物品栏
  取走玻璃板。交易界面中的每次点击现在都会先被取消，再执行对应操作（UltiKits/UltiTrade#25）。
- `/upm uninstall UltiTrade` 现在会先执行本模块自身的清理（关闭交易服务、注销 PlaceholderAPI 扩展），
  再由框架注销命令，最后注销监听器，因此卸载后本模块的命令会被真正移除，其监听器也不再触发。此前本模块
  的卸载方法替换了框架的卸载方法，因此其命令和监听器都会一直保持生效，直到服务器重启
  （UltiKits/UltiTrade#15）。
- 从交易界面取回已放入的物品时，若操作玩家的背包已完全装满，该物品不再被销毁：装不下的部分现在会掉落在该玩家
  脚下，与取消或完成交易时返还物品所用的溢出处理一致。此前该物品会被静默销毁，而双方交易界面中的该格子仍会恢复
  为玻璃板占位符，仿佛取回成功（UltiKits/UltiTrade#20）。

### Removed

- The module's own reload console line on `/ul reload UltiTrade` (a Chinese sentence meaning
  "UltiTrade config reloaded!", printed in Chinese under either `language` setting), and the
  never-consulted `trade_reloaded` language key that described it. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiTrade' reloaded.`) (UltiKits/UltiTrade#15).
- 移除本模块在 `/ul reload UltiTrade` 时输出的"UltiTrade 配置已重载！"控制台行，以及未被使用的
  `trade_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志（UltiKits/UltiTrade#15）。
