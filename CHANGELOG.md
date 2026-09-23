# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Added

- A console warning for each removed setting still in `config/trade.yml`. The seven settings this
  version removes (`trade-timeout`, and `messages.toggle-on`, `messages.toggle-off`,
  `messages.block-success`, `messages.unblock-success`, `messages.already-blocked`,
  `messages.not-blocked`; see `### Removed`) stay in the file of a server upgraded from an earlier
  version, because the framework never deletes a key from an operator's file. While any of them is
  there, the module logs one warning per key when it is enabled and on every reload of it
  (`/ul reload` or `/ul reload UltiTrade`), naming the file and the key and saying where the setting
  went; the key can simply be deleted (UltiKits/UltiTrade#17, UltiKits/UltiTrade#18).
- 为 `config/trade.yml` 中仍保留的每个已移除设置项输出一条控制台警告。本版本移除的七个设置项（`trade-timeout`，
  以及 `messages.toggle-on`、`messages.toggle-off`、`messages.block-success`、`messages.unblock-success`、
  `messages.already-blocked`、`messages.not-blocked`，见 `### Removed`）会保留在从旧版本升级的服务器的文件中，
  因为框架从不删除运维文件中的键。只要其中任何一个还在，本模块会在启用时以及每次重载本模块时
  （`/ul reload` 或 `/ul reload UltiTrade`）为每个键记录一条警告，指出文件、键名以及该设置的去向；直接删除该键即可
  （UltiKits/UltiTrade#17、UltiKits/UltiTrade#18）。

### Changed

- The replies to `/trade toggle`, `/trade block` and `/trade unblock` — trading turned on or off, a
  player added to or removed from your trade blacklist, and the refusals for a player who already is
  or is not on it — now come from this module's language file, `lang/<language>.yml` beside the
  `config` folder (entries `trade_toggle_on`, `trade_toggle_off`, `block_success`, `unblock_success`,
  `already_blocked`, `not_blocked`), so they follow the server's `language` setting: English under
  `language: en`, Chinese under `language: zh`. Previously each was a fixed Chinese sentence under
  either setting. The wording is now the language file's, so the toggle replies no longer add a second
  sentence about whether other players can send you trade requests. Only these six replies change:
  the grey line after a successful `/trade block` ("this player will not be able to send you trade
  requests"), the refusals for blocking yourself or naming an offline player, and the command's other
  text are still Chinese under either setting (UltiKits/UltiTrade#16) (UltiKits/UltiTrade#17).
- `/trade toggle`、`/trade block` 和 `/trade unblock` 的回复——开启或关闭交易、把玩家加入或移出交易黑名单，
  以及对方已在或不在黑名单时的拒绝提示——现在来自本模块的语言文件，即 `config` 文件夹旁的
  `lang/<语言>.yml`（条目 `trade_toggle_on`、`trade_toggle_off`、`block_success`、`unblock_success`、
  `already_blocked`、`not_blocked`），因此会跟随服务器的 `language` 设置：`language: en` 下为英文，
  `language: zh` 下为中文。此前无论哪种设置，这些回复都是固定的中文句子。措辞现以语言文件为准，因此开关交易的
  回复不再附带第二句关于其他玩家能否向你发送交易请求的说明。只有这六条回复发生变化：`/trade block` 成功后的
  灰色提示行（"该玩家将无法向你发送交易请求"）、拉黑自己或指定离线玩家时的拒绝提示，以及该命令的其余文本，
  在两种设置下仍为中文（UltiKits/UltiTrade#16）（UltiKits/UltiTrade#17）。

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
- A stained glass pane that a player placed into one of their own trade slots is no longer destroyed
  when another item is placed over it, and can now be taken back out at all. Which action a click on
  an own item slot performs is now read from the offer the trade session holds, not from the material
  of the item drawn in that slot, so a pane the player offered is no longer mistaken for the
  empty-slot placeholder. Clicking an own slot that already holds an offer while holding an item now
  swaps them: the held item takes the slot and the stored offer returns to the player, dropping at
  their feet if it does not fit. Previously that click destroyed a stored pane outright, and for any
  other stored item it took the item back while leaving the held item on the cursor
  (UltiKits/UltiTrade#31).
- Stopping the server, or uninstalling this module, no longer destroys the items staked in trades that
  are still open. Every stake is now returned — to the player's inventory, or at their feet if it does
  not fit — before anything else happens, and each open trade is closed independently of the others.
  Previously the first thing a cancellation did was schedule the trade-log write, which a stopping
  server refuses; that refusal ended the cancellation before a single item had been returned and
  skipped every remaining trade, and it was reported on the console as this module failing to
  unregister rather than as items being lost. The cancellation is still recorded: when no background
  task can be scheduled, the log entry is written directly instead of being dropped
  (UltiKits/UltiTrade#34).
- The trade log now records the amounts that were actually staked. A stack delivered into an inventory
  that already held a partial stack of the same material was rewritten by that merge, so a trade of 64
  diamonds into an inventory holding 60 was logged as 60 (UltiKits/UltiTrade#37).
- A click in a trade window from anyone other than the two traders is refused. It was handled as though
  it came from the second trader, so a third player who had been shown the window by another plugin
  received that trader's staked item (UltiKits/UltiTrade#38).
- An item can be staked in a trade at all. Every click whose slot belonged to the acting player's own
  inventory was refused, and a click there is the only way a player can lift an item onto the cursor
  while the trade window is open — which is what the slot the item goes into reads. No item could ever
  be offered, by any player, since this module's first release. A drag confined to the player's own
  inventory was refused for the same reason one layer over, taking their own inventory away from them
  while a trade was open. The trade window and the confirmation preview now govern their own slots only.
  Three actions stay refused from an own inventory slot, because they are not confined to the slot
  clicked and would reach into the window: a shift-click, a double-click collect, and an action the
  server reports as unknown (UltiKits/UltiTrade#39).
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
- 玩家放入自己交易格的染色玻璃板，在其上放置其他物品时不再被销毁，并且现在可以被取回。点击自己的物品格时执行
  哪种操作，现在依据交易会话中记录的出价判断，而不再依据该格所绘制物品的材质，因此玩家出价的玻璃板不会再被误认
  为空格占位符。手持物品点击自己已有出价的格子现在会交换：手持物品放入该格，原有出价返还给玩家，装不下则掉落在
  其脚下。此前这一点击会直接销毁存放的玻璃板；对其他物品则是取回该物品、而手持物品仍留在光标上
  （UltiKits/UltiTrade#31）。
- 关闭服务器或卸载本模块，不再销毁仍在进行中的交易里已放入的物品。现在每份出价都会先返还给玩家（放不下则掉落
  在其脚下），之后才执行其他步骤，并且每笔进行中的交易互相独立地关闭。此前取消交易的第一步是调度交易日志写入，
  而正在关闭的服务器会拒绝该调度：这一异常使取消在任何物品返还之前就结束，并跳过其余所有交易，而控制台只报告
  本模块注销失败，而非物品丢失。取消记录仍会保留：在无法调度后台任务时，日志条目会直接写入而不是被丢弃
  （UltiKits/UltiTrade#34）。
- 交易日志现在记录实际放入的数量。此前将一组物品交付到已有同材质零散堆叠的背包时，该合并会改写这组物品本身，
  因此向已有 60 个钻石的背包交易 64 个钻石会被记录为 60（UltiKits/UltiTrade#37）。
- 交易界面中来自两名交易者以外任何人的点击都会被拒绝。此前这类点击会被当作第二名交易者的点击处理，因此被其他
  插件展示了该界面的第三名玩家会拿到该交易者已放入的物品（UltiKits/UltiTrade#38）。
- 现在终于可以把物品放入交易。此前所有落在操作玩家自身背包内的点击都会被拒绝，而在交易界面打开时，点击自身背包是
  玩家把物品提到光标上的唯一途径，物品格恰恰只读取光标。因此自本模块首个版本起，任何玩家都无法放入任何物品。仅在
  玩家自身背包内进行的拖拽也因同样的原因（只是层级不同）被拒绝，使玩家在交易期间无法整理自己的背包。交易界面与确认
  预览界面现在只管辖各自窗口内的格位。以下三种操作从自身背包格发起时仍会被拒绝，因为它们并不局限于所点击的那一格，
  会伸进窗口内：Shift 点击、双击收集，以及服务器报告为未知的操作（UltiKits/UltiTrade#39）。

### Removed

- The module's own reload console line on `/ul reload UltiTrade` (a Chinese sentence meaning
  "UltiTrade config reloaded!", printed in Chinese under either `language` setting), and the
  never-consulted `trade_reloaded` language key that described it. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiTrade' reloaded.`) (UltiKits/UltiTrade#15).
- The `trade-timeout` setting in `config/trade.yml`. It never took effect in any version: nothing
  read it, and an open trade window has no time limit at all — it ends only when a player cancels,
  both players confirm, a player quits or a player closes the window. A pending trade *request*
  still expires after `request-timeout` seconds; that setting is unchanged. Removing the setting
  does not reject the feature it described: a time limit for an open trade window is kept on
  record as a feature request, UltiKits/UltiTrade#41 (UltiKits/UltiTrade#18).
- The six settings `messages.toggle-on`, `messages.toggle-off`, `messages.block-success`,
  `messages.unblock-success`, `messages.already-blocked` and `messages.not-blocked` in
  `config/trade.yml`. None of them ever took effect in any version: the commands they describe sent
  fixed text of their own, so editing them never changed what a player saw. Those replies now come
  from the language file (see `### Changed`), which is where their text is changed; removing the
  settings moves that ability to the file that already held the text in both languages rather than
  taking it away. The seven other `messages.*` settings are read and are unchanged
  (UltiKits/UltiTrade#17).
- 移除本模块在 `/ul reload UltiTrade` 时输出的"UltiTrade 配置已重载！"控制台行，以及未被使用的
  `trade_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志（UltiKits/UltiTrade#15）。
- 移除 `config/trade.yml` 中的 `trade-timeout` 设置项。它在任何版本中都从未生效：没有任何代码读取它，而已打开的
  交易窗口根本没有时间限制——只有玩家取消、双方确认、一方退出或关闭窗口时才会结束。待处理的交易*请求*仍会在
  `request-timeout` 秒后过期，该设置项不变。移除该设置项并不代表否决它所描述的功能：为已打开的交易窗口设置时间
  限制已作为功能请求 UltiKits/UltiTrade#41 留档（UltiKits/UltiTrade#18）。
- 移除 `config/trade.yml` 中的六个设置项 `messages.toggle-on`、`messages.toggle-off`、`messages.block-success`、
  `messages.unblock-success`、`messages.already-blocked` 和 `messages.not-blocked`。它们在任何版本中都从未生效：
  它们所描述的命令一直发送自己固定的文本，修改这些设置项从未改变玩家看到的内容。这些回复现在来自语言文件
  （见 `### Changed`），应在那里修改其文本；移除这些设置项并不是取消修改文本的能力，而是把它移到了早已以两种语言
  保存这些文本的文件中。其余七个 `messages.*` 设置项会被读取，保持不变（UltiKits/UltiTrade#17）。
