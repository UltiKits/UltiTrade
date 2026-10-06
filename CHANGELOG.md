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
  there with a value, the module logs one warning per key when it is enabled and on every reload of it
  (`/ul reload` or `/ul reload UltiTrade`), naming the file and the key and saying where the setting
  went; the key can simply be deleted. A key left with no value (`trade-timeout:` or `~`) is not
  reported: Bukkit drops it when reading the file, so it reads back as absent
  (UltiKits/UltiTrade#17, UltiKits/UltiTrade#18).
- 为 `config/trade.yml` 中仍保留的每个已移除设置项输出一条控制台警告。本版本移除的七个设置项（`trade-timeout`，
  以及 `messages.toggle-on`、`messages.toggle-off`、`messages.block-success`、`messages.unblock-success`、
  `messages.already-blocked`、`messages.not-blocked`，见 `### Removed`）会保留在从旧版本升级的服务器的文件中，
  因为框架从不删除运维文件中的键。只要其中任何一个还在且带有值，本模块会在启用时以及每次重载本模块时
  （`/ul reload` 或 `/ul reload UltiTrade`）为每个键记录一条警告，指出文件、键名以及该设置的去向；直接删除该键即可。
  没有值的键（`trade-timeout:` 或 `~`）不会被报告：Bukkit 读取文件时会丢弃它，读回时与不存在相同
  （UltiKits/UltiTrade#17、UltiKits/UltiTrade#18）。

### Changed

- Message and title settings in `config/trade.yml` — the trade-window title (`gui-title`) and the seven
  messages (`messages.request-sent`, `request-received`, `request-timeout`, `trade-complete`,
  `trade-cancelled`, `trade-disabled`, `player-blocked`) — are written in the server's language when the
  module starts, and the file is what the module shows (for example `messages.trade-complete: '&aTrade
  completed!'` under `language: en`); previously they were fixed Chinese text, so `language: en` had no
  effect on them. A setting that is still built-in text — in any language, or a default an earlier
  version shipped — follows `language`: it is rewritten when the module starts or after `/ul reload`. A
  setting you edited is kept. To keep a built-in text but stop it following `language`, change at least
  one character. Under `language: zh` every one of them keeps exactly the Chinese text earlier versions
  shipped. The language entries `request_sent`, `request_received`, `trade_disabled_target` and
  `player_blocked`, which no code reads any more, are removed; the texts written into `trade.yml` come
  from `message_request_sent`, `message_request_received`, `message_trade_disabled` and
  `message_player_blocked` (UltiKits/UltiTrade#16). The text written is this module's built-in text: edit these settings in `config/trade.yml`; an edit of the extracted
  language file does not change them (earlier versions never read them from the language file either).
- `config/trade.yml` 中的消息与标题设置——交易界面标题（`gui-title`）与七条消息（`messages.request-sent`、
  `request-received`、`request-timeout`、`trade-complete`、`trade-cancelled`、`trade-disabled`、`player-blocked`）——
  在模块启动时按服务器语言写入，文件内容即模块显示的内容；此前它们是写死的中文，`language: en` 对它们不起作用。
  仍为内置文本（任一语言的内置文本，或旧版本的出厂默认值）的设置会跟随 `language`：模块启动或执行 `/ul reload`
  后改写为当前语言的文本。你改过的设置保持不变。若想保留内置文本又不让它跟随语言，请至少改动一个字符。在
  `language: zh` 下它们都与旧版本出厂的中文文本完全相同。不再有代码读取的语言条目 `request_sent`、`request_received`、
  `trade_disabled_target`、`player_blocked` 已删除；写入 `trade.yml` 的文本来自 `message_request_sent`、
  `message_request_received`、`message_trade_disabled`、`message_player_blocked`（UltiKits/UltiTrade#16）。写入的是本模块的内置文本：请在 `config/trade.yml` 中修改这些设置；修改已解压的语言文件不会改变它们（旧版本同样从不从语言文件读取它们）。

- The replies to `/trade toggle`, `/trade block` and `/trade unblock` — trading turned on or off, a
  player added to or removed from your trade blacklist, and the refusals for a player who already is
  or is not on it — now come from this module's language file, `lang/<language>.yml` beside the
  `config` folder (entries `trade_toggle_on`, `trade_toggle_off`, `block_success`, `unblock_success`,
  `already_blocked`, `not_blocked`), so they follow the server's `language` setting: English under
  `language: en`, Chinese under `language: zh`. Previously each was a fixed Chinese sentence under
  either setting. The wording is now the language file's, which changes five of the six Chinese
  replies as well: the two toggle replies no longer add a second sentence about whether other players
  can send you trade requests; the already-blocked and not-blocked refusals now say "already in the
  blacklist" and "not in the blacklist" instead of "in your trade blacklist"; and the unblock reply is
  reworded slightly ("removed from the trade blacklist"). Only the block-success reply reads exactly as
  before. In English the already-blocked and not-blocked refusals say "your blacklist". The rest of the
  command's text follows `language` as well; see `### Fixed` (UltiKits/UltiTrade#17).
- `/trade toggle`、`/trade block` 和 `/trade unblock` 的回复——开启或关闭交易、把玩家加入或移出交易黑名单，
  以及对方已在或不在黑名单时的拒绝提示——现在来自本模块的语言文件，即 `config` 文件夹旁的
  `lang/<语言>.yml`（条目 `trade_toggle_on`、`trade_toggle_off`、`block_success`、`unblock_success`、
  `already_blocked`、`not_blocked`），因此会跟随服务器的 `language` 设置：`language: en` 下为英文，
  `language: zh` 下为中文。此前无论哪种设置，这些回复都是固定的中文句子。措辞现以语言文件为准，六条中文回复中有五条
  的措辞也随之改变：开关交易的两条回复不再附带第二句关于其他玩家能否向你发送交易请求的说明；"已经在你的交易黑名单中"
  改为"已在黑名单中"，"不在你的交易黑名单中"改为"不在黑名单中"；"从交易黑名单中移除"改为"从交易黑名单移除"。
  只有拉黑成功的回复与之前完全相同。该命令的其余文本同样跟随 `language`，见 `### Fixed`（UltiKits/UltiTrade#17）。

### Fixed

- A trade's money now moves only when the economy confirms it. The trade checked each payer's balance, then withdrew and
  paid without reading whether the withdrawal succeeded, so a withdrawal refused after the check (the balance changed,
  for example on another server sharing the economy's database) still paid the other player, and the items still moved.
  Now both payers are withdrawn first; if either withdrawal is refused or fails, whatever was withdrawn is refunded and
  the whole trade is cancelled: no money, experience or item moves, no tax is taken, and both players are told the
  balance changed and nothing was transferred. A payment that is refused or fails cancels the trade the same way, and a
  refund that fails is logged at SEVERE naming both players, their UUIDs, the amount and the currency, and the players are
  then told a balance could not be restored and an operator was notified. Every balance and
  experience check now also runs before anything moves: too little experience used to cancel the trade after the money
  had already moved (UltiKits/UltiTrade#58).
- 交易中的金币现在只在经济插件确认后才转移。此前交易先检查余额，再扣款和付款，却不读取扣款是否成功，因此检查之后余额发生变化（例如共享经济数据库的另一台服务器上的操作）
  导致扣款被拒绝时，对方仍会收到金币，物品也照常转移。现在先从双方扣款；任一方扣款被拒绝或出错时，已扣除的金额会退还，整笔交易取消：金币、经验与物品都不转移，
  不收税，并告知双方余额已变化、未转移任何东西。付款被拒绝或出错时同样取消交易；退款失败时记录一条 SEVERE，写明双方玩家、其 UUID、金额与货币，并告知玩家有余额未能恢复、已通知管理员。
  所有余额与经验检查现在也都在任何转移之前进行：此前经验不足会在金币已经转移之后才取消交易（UltiKits/UltiTrade#58）。

- On servers sharing one database, a stake waiting to be returned to a player is handed over once, not again at every
  server hop. A join used to decide whether an earlier hand-over had reached the player by looking in the player's
  data on its own server, which another server cannot see, so a player who moved between servers was given the same
  items on each. The table now records each hand-over's state: a join first marks the entry CLAIMED with one
  conditional write (only one server can), and the entry is removed once the player's data save returns; the part
  that did not fit becomes a new, unclaimed entry in the same transaction. An entry left CLAIMED by a crash or a failed
  save is never handed over again automatically: the console warns at start-up and on every reload with the number of
  such entries, and the new `/trade pending list`, `/trade pending redeliver <id>` and `/trade pending void <id>`
  commands (permission `ultitrade.admin`, console-usable) list each one (player name and UUID, items, claim time,
  server) and let the operator, after checking the player, hand it over again or discard it; meanwhile the player is
  told at each join that a return is waiting for an administrator's check. All servers sharing the
  database must be upgraded together (see the README's known limitations). An entry that an earlier build had marked
  for hand-over when the server was upgraded is settled from the player's data first, so a single server neither hands
  it over again nor loses it (UltiKits/UltiTrade#55).
- 多台服务器共享数据库时，待返还给玩家的物品只会发还一次，不再在每次换服时重复发还。此前进服时通过本服务器上的玩家数据判断之前的发还是否已送达，
  而另一台服务器看不到这份数据，因此在服务器之间移动的玩家会在每台服务器上再次收到同样的物品。现在进服会先用一次条件写入在 `trade_pending_returns`
  中记录每次发还的状态：进服先以一次条件写入将条目标记为「已占用」（只有一台服务器能成功），玩家数据保存完成后再删除条目；装不下的部分在同一事务中成为新的未占用条目。
  因崩溃或保存失败而停留在「已占用」状态的条目不会再自动发还：服务器启动及每次重载时控制台会提示此类条目的数量，新增的 `/trade pending list`、
  `/trade pending redeliver <id>` 与 `/trade pending void <id>` 命令（权限 `ultitrade.admin`，可在控制台使用）会列出每个条目（玩家名与 UUID、物品、占用时间、服务器），
  供服主检查玩家后选择重新发还或作废；在此期间玩家每次进服都会收到正在等待管理员核对的提示。共享数据库的所有服务器必须一起升级（见 README 的已知限制）。升级时，旧版本已标记为发还中的条目会先依据玩家数据结算，单台服务器上既不会重复发还也不会丢失（UltiKits/UltiTrade#55）。

- A stake waiting to be returned to a player is no longer handed over when its stored entry was deleted after the
  player's join read it (by an administrator, or by another server sharing the database). The write for the entry
  matched no row and wrote nothing, but the items were given anyway and the entry was treated as updated. The join's
  claim of the entry now does not apply to a deleted row: the hand-over is undone and a line names the player and the
  entry (UltiKits/UltiTrade#53).
- 待返还给玩家的物品条目若在玩家加入时被读取后、写入前被删除（管理员删除，或共享数据库的另一台服务器删除），不再照常交付。此前对该条目的写入没有命中任何行、
  什么也没写，物品却仍交给玩家，条目也被当作已更新。现在进服对条目的占用不会作用于已删除的行：撤销本次交付，并记录一条写明玩家与条目的日志（UltiKits/UltiTrade#53）。

- A save of a player's trade settings whose stored row no longer exists is now reported as failed on every storage
  type. The module keeps each player's settings in memory for the server's lifetime, so if their
  `trade_player_settings` row was deleted while the server ran, the next toggle, block, unblock, post-trade
  statistics update or the save at shutdown wrote nothing and passed as saved on SQLite and MySQL (only the JSON
  backend reported it). A change that reaches no row is now logged as `Failed to save player settings: <player>`,
  the same line a failed write logs; there is no save at shutdown any more
  (UltiKits/UltiTrade#52, UltiKits/UltiTrade#54).
- 玩家交易设置的存储行已不存在时，保存现在在所有存储类型上都报告为失败。模块在服务器运行期间一直缓存每位玩家的设置，因此若其 `trade_player_settings`
  行在运行中被删除，之后的开关交易、拉黑、取消拉黑、交易后统计更新或关服时的保存在 SQLite 与 MySQL 上什么也没写却当作已保存（只有 JSON 后端会报告）。
  现在未命中任何行的修改会记录 `保存玩家设置失败：<玩家>`，与写入出错时的日志行相同；关服时已不再保存（UltiKits/UltiTrade#52、UltiKits/UltiTrade#54）。

- A change to a player's trade settings is no longer lost at the next restart when their stored row was deleted
  while the server ran. Such a save matched no row and wrote nothing (since UltiKits/UltiTrade#52 it was at least
  logged as failed), while the player's chat confirmed the change. The next change now re-creates the row with the
  current settings (those this server last held for the player while they are online here) and logs a WARNING naming
  the player. If another server sharing the database has already created a row
  for that player, the settings are written onto it instead; a player's settings row now takes the player's UUID as
  its id, so two servers creating or re-creating it at once leave one row, not two (UltiKits/UltiTrade#57).
- 玩家的交易设置存储行在服务器运行期间被删除后，其设置修改不再在下次重启时丢失。此前这种保存匹配不到任何行、什么也没写（自 UltiKits/UltiTrade#52
  起至少会记录为失败），玩家聊天栏却显示修改成功。现在下一次修改会用当前设置（该玩家在线于本服期间本服最后持有的设置）重建该行，并记录一条指名该玩家的 WARNING。若共享数据库的另一台服务器已为该玩家创建了行，
  则写入那一行；玩家设置行现在以玩家 UUID 作为 id，因此两台服务器同时创建或重建时只会留下一行（UltiKits/UltiTrade#57）。

- On servers sharing one database, a player's trade settings and trade statistics changed on another server are no
  longer reverted or lost. The module kept each player's settings in memory for the server's lifetime and wrote that
  whole copy back on every change and again at shutdown, so a setting another server had changed in between went back
  to the old value, and a trade completed on another server was lost from `total_trades` and the money and experience
  totals. Now a player's settings are cached only while the player is online on this server (loaded on join, dropped on
  quit) and never written back: nothing is written at quit or at shutdown. Every change — `/trade toggle`,
  `/trade block`, `/trade unblock`, a new player name at join, and the statistics of a completed trade — reads the
  stored settings again, applies only that change and writes it only if the stored settings still hold what was read;
  otherwise it reads them again and re-applies the change, up to three times. A toggle flips the stored state, so two
  toggles on two servers both count. If another server keeps changing the settings, the module logs
  `Failed to save player settings: <player>: the stored row kept changing …` and tells the player to try again
  (`settings_busy`); a change the storage refused is no longer confirmed — the player gets `settings_not_saved` and the
  console line names the player and the error (UltiKits/UltiTrade#54).
- 多台服务器共享同一数据库时，另一台服务器上修改的交易设置与交易统计不再被还原或丢失。此前模块在服务器运行期间一直在内存中保留每位玩家的设置，并在每次修改和关服时整份写回，
  因此另一台服务器期间所做的设置修改会被改回旧值，另一台服务器上完成的交易也会从 `total_trades` 及金币、经验累计中丢失。现在玩家设置只在该玩家在线于本服期间缓存
  （进服时加载、退出时丢弃），且从不写回：退出与关服时都不写入。每次修改——`/trade toggle`、`/trade block`、`/trade unblock`、进服时的新玩家名，以及交易完成后的统计——
  都会重新读取存储的设置、只应用这一项修改，并且仅当存储的设置仍是读取时的值时才写入；否则重新读取并再次应用，最多三次。开关交易翻转的是存储中的状态，
  因此两台服务器上的两次开关都会生效。若另一台服务器持续修改，模块会记录 `保存玩家设置失败：<玩家>：重试写入期间存储行一直在变化……` 并提示玩家重试（`settings_busy`）；
  存储拒绝的修改不再提示成功——玩家会收到 `settings_not_saved`，控制台日志写明玩家与错误（UltiKits/UltiTrade#54）。

- A trade withdrawal or deposit whose economy call threw now logs a SEVERE line an operator can act on:
  `Outcome unknown: taking <amount> from <player> for a trade failed with an error, so the trade treats it as not
  taken, but the economy may have taken it. Check <player>'s balance for <amount>` (and the same for paying), with
  the amount in full (`12345678.9`, not `1.23456789E7`). It was a WARNING that said only "it counts as refused". The
  trade still counts such a call as refused; a call that committed and then threw is documented as a known
  limitation. A refund or take-back that throws now logs only this line, not also the certain "give it back by hand" /
  "take it back by hand" line, which would have had an operator correct a call that went through a second time; a
  refused refund or take-back keeps that line (UltiKits/UltiTrade#60).
- 交易的扣款或付款在经济插件中抛出异常时，现在记录一条服主可据以处理的 SEVERE 日志：`结果未知：交易扣除 <金额>（来自 <玩家>）时出错，交易按未扣除处理，
  但经济插件可能已经扣除。请检查 <玩家> 的余额是否有 <金额> 的变动`（付款同理），金额完整显示（`12345678.9`，而非 `1.23456789E7`）。此前是一条只说明
  「按拒绝处理」的 WARNING。交易仍将此类调用视为被拒绝；已生效后才抛出异常的调用作为已知限制记录在文档中。退款或收回付款抛出异常时，现在只记录这一条，
  不再同时记录确定性的「请手动退还 / 手动收回」日志，以免服主对实际已生效的调用再更正一次；被拒绝的退款或收回仍保留那条日志（UltiKits/UltiTrade#60）。

- A player at a very high level no longer reads a negative experience total. From 15,466 levels (for example after
  `/xp set <player> 16000 levels`) the total overflowed, so the experience prompt and the trade window showed a negative
  number and every experience offer was refused as insufficient. The total is now exact up to 21,863 levels. Above
  that it exceeds 2,147,483,647 points and cannot be counted exactly, so such a player cannot offer experience: the
  experience slot and the amount prompt answer `exp_total_unreadable` ("Your experience is too high to be counted
  exactly …"), the trade window shows that line instead of a total, and a trade whose offer was set before the player
  reached such a level is cancelled before anything moves (`cancel_reason_exp_unreadable`). Taking an offer off the
  capped total would have rebuilt the player hundreds of millions of points below their real total. Paper's own
  experience arithmetic drifting above about 411,616 points is documented as a known limitation (UltiKits/UltiTrade#65).
- 等级极高的玩家不再读到负数的经验总量。此前从 15,466 级起（例如执行 `/xp set <玩家> 16000 levels` 后）经验总量会溢出，经验输入提示和交易界面显示负数，
  所有经验报价都被判为经验不足而拒绝。现在经验总量在 21,863 级以内精确计算；更高等级的总量超过 2,147,483,647 点，无法精确计算，因此这类玩家不能出价经验：
  点击经验栏位与经验数量输入都会提示 `exp_total_unreadable`，交易界面显示该提示而非总量；若出价在玩家达到此等级之前已设置，交易会在任何东西转移之前取消
  （`cancel_reason_exp_unreadable`）。若从封顶后的数值中扣除报价，会使玩家的经验比实际少数亿点。Paper 自身经验计算在约 411,616 点以上的偏差
  作为已知限制记录在文档中（UltiKits/UltiTrade#65）。

- `exp-tax-rate: .nan` no longer breaks experience trades: a rate that is not greater than zero, NaN included, takes no
  experience tax. NaN used to throw while the tax was computed — in a completing trade after the money had already
  moved, and in both trade windows (UltiKits/UltiTrade#65).
- `exp-tax-rate: .nan` 不再导致经验交易出错：任何不大于零的税率（包括 NaN）都不收取经验税。此前 NaN 会在计算税额时抛出异常——在完成交易时发生于金币已转移之后，
  两个交易界面也会出错（UltiKits/UltiTrade#65）。

- `/ul reload UltiTrade` (and a bare `/ul reload`) no longer reports a plain success when part of this module's
  reload failed. If rescheduling the trade-log cleanup task, applying `enable-money-trade`, or voiding open trades'
  confirmations and redrawing their windows fails, or one player's open trade window cannot be redrawn, the module
  still logs that error and runs the rest, and the reply and the framework's reload log line now say the reload was
  partial and name each step that did not reload, with its cause, or the players whose window still shows the
  previous terms (UltiKits/UltiTrade#50).
- 本模块的重载有部分失败时，`/ul reload UltiTrade`（以及不带参数的 `/ul reload`）不再回复单纯的成功。若重新安排交易日志清理任务、应用 `enable-money-trade`、
  或作废进行中交易的确认并重绘其窗口失败，或某位玩家打开的交易窗口未能重绘，模块仍记录该错误并继续执行其余部分，同时回复与框架的重载日志行会说明重载不完整，并列出未完成的每个步骤及其原因，或窗口仍显示旧条款的玩家（UltiKits/UltiTrade#50）。

- `config/trade.yml` now writes its comments in the server's language. All twenty-four comments (every setting
  in the file) used to be Chinese-only, so a fresh install under `language: en` got a file with Chinese comments.
  Each is now a language-file key that the framework resolves in the server's `language` every time it writes
  the file, with an English and a Chinese entry in `lang/en.yml` and `lang/zh.yml`. On an existing server the
  comments the framework wrote on these settings, the Chinese ones earlier versions wrote included, switch to
  the server's language at the next start, and after you change `language` and run a bare `/ul reload`; values are
  untouched, and a comment you wrote yourself is kept as you wrote it (UltiKits/UltiTools-Reborn#611)
  (UltiKits/UltiTrade#51).
- `config/trade.yml` 的注释现在跟随服务器语言。此前文件中全部二十四条注释（每个设置一条）只有中文，`language: en` 的全新安装得到的文件注释是中文。
  现在每条注释都是一个语言文件键，框架每次写入文件时按服务器的 `language` 解析，`lang/en.yml` 与 `lang/zh.yml` 各有英文和中文条目。
  已有服务器上框架在这些设置上写下的注释（包括旧版本写下的中文注释）会在下次启动时、以及你修改 `language` 并执行不带参数的 `/ul reload` 后
  切换为服务器语言；设置值不受影响，你自己写的注释保持原样（UltiKits/UltiTools-Reborn#611）（UltiKits/UltiTrade#51）。

- An unrelated plugin refusing the large-trade confirmation page's own `InventoryOpenEvent` no longer
  leaves the player with no trade UI at all while the trade keeps running with both stakes locked. The
  trade window this page was meant to replace is already closed by the time it tries to open, and
  `HumanEntity#openInventory` returns `null` for a refused open rather than throwing, so the failure
  went unnoticed before. The player is now sent back to the trade window instead, exactly as clicking
  the page's own Cancel would -- the trade is not cancelled outright, since nothing about the player's
  offer changed (UltiKits/UltiTrade#47 review).
- 现在有其他插件拒绝大额交易确认页面自身的 `InventoryOpenEvent` 时，不会再让玩家完全没有交易界面，同时交易仍在进行、
  双方出价被一直占用。该页面原本要替换的交易窗口此时已经关闭，而 `HumanEntity#openInventory` 在打开被拒绝时会返回
  `null` 而不是抛出异常，因此此前这一失败不会被察觉。现在会像点击该页面自身的取消按钮一样把玩家送回交易窗口——不会
  直接取消交易，因为玩家的出价并未发生任何变化（UltiKits/UltiTrade#47 复查）。

- A reload that fails to build or open the replacement trade window for an open large-trade
  confirmation page now cancels that trade and returns both stakes, instead of leaving the page
  dismissed and inert (its buttons and Esc now do nothing) while the trade keeps running with both
  stakes locked, recoverable only by someone noticing and cancelling it by hand. The reload's own
  per-player error log for the failed redraw is unchanged (UltiKits/UltiTrade#47 review).
- 现在重载时若未能为已打开的大额交易确认页面构建或打开替换窗口，会取消该笔交易并返还双方出价，而不是让该页面
  保持已失效状态（其按钮和 Esc 此时均不再产生任何效果），同时交易仍在进行、双方出价被一直占用，只能靠有人发现
  后手动取消。重载原有的、按玩家记录的重绘失败错误日志保持不变（UltiKits/UltiTrade#47 复查）。

- An unrelated plugin closing a player's large-trade confirmation page, or opening its own window over
  it, no longer leaves the trade running with both stakes locked and no window until someone cancels
  it by hand. Paper reports the identical `PLUGIN`/`OPEN_NEW` reason whether this module's own close
  (a button click, completing or cancelling the trade, a reload redraw) caused it or an unrelated
  plugin did, so those two could not be told apart by the reason alone; every one of this module's own
  closes now marks the page first, and a `PLUGIN`/`OPEN_NEW` close that arrives unmarked is treated as
  terminal, the same as death or disconnect already were (UltiKits/UltiTrade#47 review).
- 现在其他插件关闭玩家的大额交易确认页面，或在其上打开自己的窗口时，不会再让交易悄悄保持进行、双方都没有窗口，
  直到有人手动取消。此前无论是本模块自身的关闭（点击按钮、完成或取消交易、重载重绘）还是其他插件造成的关闭，
  Paper 报告的都是同一个 `PLUGIN`/`OPEN_NEW` 原因，仅凭原因无法区分两者；现在本模块自身的每一次关闭都会先标记
  该页面，一个未被标记就到达的 `PLUGIN`/`OPEN_NEW` 关闭会被当作终止性原因处理，与死亡、断线的处理方式一致
  （UltiKits/UltiTrade#47 复查）。

- A money or experience amount a player just typed can no longer be discarded by cancelling their
  trade a tick later. Typing an answer claims it instantly (removed from the pending-prompt map) but
  applies it only afterward, on the server thread; the close-guard's own deferred check -- scheduled
  the instant the prompt itself closed the trade window, before the prompt was even registered -- could
  run in that gap and, seeing no pending prompt, cancel the trade as though the player had never
  answered. The claimed-but-not-yet-applied answer is now tracked separately so the close guard still
  recognises it (UltiKits/UltiTrade#47 review).
- 玩家刚输入的金额或经验数量，现在不会再因一个 tick 后交易被取消而丢失。输入答案会立即被认领（从待处理提示映射中移除），
  但只会稍后在服务器线程上应用；而关闭守卫自身的延迟检查——在提示本身关闭交易窗口的那一刻就已排定，早于提示被登记之前——
  可能恰好在这段间隙运行，看到没有待处理的提示，就会像玩家从未回答一样取消交易。现在会单独跟踪这个「已认领但尚未应用」
  的答案，使关闭守卫仍能识别它（UltiKits/UltiTrade#47 复查）。

- Two console lines name a file path or a world exactly as they are: the warning about a setting this
  version no longer reads, and the error for staked items that could not be saved and were dropped. A path
  or world name containing `{KEY}`, `{REASON}` or `{ITEMS}` used to be rewritten, because it was inserted
  before those placeholders were filled. Every placeholder of the line is now filled in one pass (the same
  fix as UltiKits/UltiMail#37).
- 两条控制台日志现在按原样给出文件路径或世界名：已不再读取的配置项警告，以及无法保存而掉落的交易物品的错误。此前路径或世界名先于
  `{KEY}`、`{REASON}`、`{ITEMS}` 占位符插入，含这些占位符时会被改写。现在同一行的所有占位符一次性替换（与 UltiKits/UltiMail#37 相同的修复）。

- When `/ul reload UltiTrade` makes money trading unavailable (money trading turned off, or no economy
  provider found), the money offered in every open trade is withdrawn and both players are told, so the
  rest of the trade can still complete; experience offers are withdrawn the same way when experience trading
  is turned off. While either is unavailable, its chat prompt accepts `0` to withdraw an offer. Before, such
  an offer could not be withdrawn and the trade could only be cancelled (UltiKits/UltiTrade#28).
- 当 `/ul reload UltiTrade` 使金币交易不可用（关闭金币交易或找不到经济提供者）时，所有进行中交易里出价的金币会被撤回并通知双方，交易的其余内容仍可完成；
  关闭经验交易时，经验出价也同样撤回。不可用期间，对应的聊天提示接受 `0` 以撤回出价。此前这样的出价无法撤回，只能取消交易（UltiKits/UltiTrade#28）。

- A money or experience prompt left over from a cancelled trade no longer affects a trade the player opens
  afterwards: its 10-second timeout no longer reopens the old trade's window (which cancelled the new trade
  and told both players so), no longer closes a prompt opened in the new trade, no longer stops closing the
  new trade's window from cancelling it, and an amount typed for it is answered with `The trade has ended!`
  instead of being applied to the new trade (UltiKits/UltiTrade#40).
- 已取消交易遗留的金币或经验输入提示不再影响玩家之后开启的交易：其 10 秒超时不再重新打开旧交易的窗口（此前会导致新交易被取消并通知双方），
  也不再关闭在新交易中打开的提示，也不再阻止关闭新交易窗口时取消新交易；为它输入的数额会提示「交易已结束」，不会被应用到新交易（UltiKits/UltiTrade#40）。

- A trade whose money or experience meets `confirm-threshold` completes once both players have confirmed
  through the confirmation page, exactly as a smaller trade does. Before, the page only recorded the
  confirmation, so such a trade could never complete (UltiKits/UltiTrade#21).
- 金币或经验达到 `confirm-threshold` 的交易，在双方都通过确认页面确认后即完成，与较小的交易一致。此前确认页面只记录确认，这样的交易永远无法完成（UltiKits/UltiTrade#21）。

- Clicking Confirm on a trade whose money or experience meets `confirm-threshold` now opens the
  confirmation page and leaves the trade running. Before, closing the trade window to open the page
  cancelled the trade first, returning every item, and the page then opened on a trade that no longer
  existed (UltiKits/UltiTrade#23). The page's Cancel button, or closing the page with Esc, returns to the
  trade window; a page closed for the player (the trade cancelled or completed, a reload, another window
  opened over it) is not treated as Cancel, and only the player the page was shown to can answer it. Its
  Confirm button confirms only the offer the page showed — if either player changed an
  offer while it was open, nothing is confirmed and the player is told to check the trade again. An amount
  typed at the money or experience prompt is applied on the server thread, so it cannot land between
  that check and the confirmation.
- 当交易的金币或经验达到 `confirm-threshold` 时，点击确认现在会打开确认页面，交易继续进行。此前为打开该页面而关闭交易窗口时会先取消交易并退回全部物品，
  页面随后打开的是已不存在的交易（UltiKits/UltiTrade#23）。确认页面的取消按钮或按 Esc 关闭页面会回到交易窗口；
  因交易取消或完成、重载或其他窗口覆盖而被关闭的确认页不算作取消；只有该页面所属的玩家能作答。确认按钮只确认页面所显示的出价——
  页面打开期间任一方改动了出价，则不会确认，并提示玩家重新检查交易。金币、经验输入提示中输入的数额在服务器主线程上生效，
  不会插进这一检查与确认之间。

- In the trade window, clicking one of your own empty slots with nothing in hand, or opening the money or
  experience prompt, no longer clears both players' confirmations. Confirmations are cleared only when an
  offer actually changes, and both windows are then redrawn; re-entering the amount already offered at
  a prompt is not a change. Before, such a click cleared them without redrawing, so a player still looked
  confirmed while the trade could not complete (UltiKits/UltiTrade#36).
- 在交易窗口中，空手点击自己的空格子，或打开金币、经验输入提示，不再清除双方的确认。只有出价真正变化时才清除确认，并同时刷新双方窗口；
  在输入提示中再次输入已出价的数额不算变化。此前这样的点击会清除确认却不刷新，玩家看起来仍已确认，交易却无法完成（UltiKits/UltiTrade#36）。

- The money and experience chat prompts refuse `NaN` and `Infinity` with `Invalid amount!`. Before,
  `NaN` was accepted as the money offer; the money transfer was then skipped while the items still moved,
  and the other player saw `NaN` (UltiKits/UltiTrade#29).
- 金币和经验的聊天输入提示会以「无效的数额」拒绝 `NaN` 和 `Infinity`。此前 `NaN` 会被接受为金币出价，完成交易时金币转账被跳过而物品照常交换，
  对方看到的是 `NaN`（UltiKits/UltiTrade#29）。

- On the large-trade confirmation page, a side offering four or more items shows three item previews
  and the "N more items" count in a slot of its own; the count used to cover the third preview, so only
  two items were visible (UltiKits/UltiTrade#22).
- 大额交易确认页面上，一方放入四个及以上物品时，会显示三个物品预览，并在单独的格子中显示「还有 N 个物品」；
  此前该提示覆盖了第三个预览，只能看到两个物品（UltiKits/UltiTrade#22）。

- The large-trade confirmation page's item previews now carry the `---Trade item---` marker line in
  their lore; it was built and then discarded (UltiKits/UltiTrade#43).
- 大额交易确认页面中预览的物品现在在描述中带有「交易物品」标记行；此前该行被生成后又被丢弃（UltiKits/UltiTrade#43）。

- A trade window always shows what both players have staked. Five of the seven ways a trade window
  opens — after the money or experience prompt times out, after a `cancel` reply to the prompt, and on
  the two large-trade confirmation paths — showed an empty trade while both stakes were still in it,
  so a player could confirm a trade whose contents they could not see (UltiKits/UltiTrade#35).
- 交易窗口始终显示双方已放入的内容。此前七种打开交易窗口的方式中有五种——金币或经验输入提示超时后、对提示回复 `cancel`
  后，以及大额交易确认的两条路径——会显示空的交易，而双方的物品实际仍在交易中，玩家可能在看不到内容的情况下确认交易（UltiKits/UltiTrade#35）。

- If a trade is cancelled while one participant cannot be found on the server, that participant's
  staked items are kept and handed back when they next join, instead of being destroyed. They are
  saved in a new table, `trade_pending_returns`; if they cannot be saved they are dropped at the
  player's last known location, and the console names the player and the items in both cases.
  At the join only what fits in the inventory is handed over; the rest stays saved, the player is
  told how many items are still kept and to free some space and rejoin, and nothing is dropped. With
  SQLite or MySQL storage a server crash during the hand-over never duplicates an item; the hand-over is
  held for the operator (see the UltiKits/UltiTrade#55 entry above); with JSON storage the list
  reaches the disk only at the next flush (`datasource.flushRate`), so a crash within that window can
  hand a stake over a second time (UltiKits/UltiTrade#32).
- 修复：交易取消时若服务器找不到一方，其押入的物品会被保存，并在其下次进服时发还，不再被销毁。物品保存在新表
  `trade_pending_returns` 中；无法保存时掉落在该玩家最后所在的位置，两种情况下控制台都会写明玩家与物品。
  进服发还时只发背包装得下的部分，其余继续保存，并告知玩家还有多少物品保留、腾出空间后重新进服领取，不会掉落在地。
  使用 SQLite 或 MySQL 存储时，发还过程中服务器崩溃不会重复物品，该次发还会被暂扣交由服主处理（见上文 UltiKits/UltiTrade#55 条目）；使用 JSON 存储时列表要到下次写盘
  （`datasource.flushRate`）才落盘，在此窗口内崩溃可能导致重复发还（UltiKits/UltiTrade#32）。
- `language: en` now applies to everything this module shows or logs: every `/trade` reply and its
  help, the trade request (clickable buttons, their hover text and the countdown BossBar), the
  request, accept, deny and cancel replies and every cancellation reason, the trade window and the
  large-trade confirmation page (titles, item names, lore, buttons, tax lines), the money and
  experience input prompts and replies, the PlaceholderAPI display values (`enabled_display`,
  `last_trade_time`, `last_trade_ago`), the command description, and the console lines. Most of this
  was fixed Chinese text in every language, although the language files already held English text for
  much of it that no code read; the console lines were fixed English text and now follow
  `language: zh` too (UltiKits/UltiTrade#16). Language keys were renamed: the few lines that used a
  Chinese sentence as their language key now use ASCII keys. If you customised this module's messages in your own language file -- a copy of an official file
  whose name starts with that file's language code and a hyphen (for example `lang/zh-myserver.yml`),
  selected with `language: zh-myserver` in `plugins/UltiTools/config.yml` -- re-apply those edits to the new
  keys; until then each renamed line shows the text of the official language the name starts with. A copy
  whose name does not start with an official language code and a hyphen is still read, but every message
  it lacks then shows in English, with one warning. An edit made directly in an official language file (`lang/en.yml`, `lang/zh.yml`) is not
  kept: the framework restores the official files at every start and on every module reload and keeps the edited file as `.bak`
  (UltiKits/UltiTools-Reborn#616).
- `language: en` 现在对本模块显示或记录的全部内容生效：`/trade` 的所有回复与帮助，交易请求（可点击按钮、按钮的悬停
  提示与倒计时 BossBar），请求、接受、拒绝与取消的回复及每一种取消原因，交易界面与大额交易确认页（标题、物品名称、
  说明、按钮、税费行），金币与经验的输入提示及回复，PlaceholderAPI 的显示值（`enabled_display`、`last_trade_time`、
  `last_trade_ago`），命令描述以及控制台日志。其中大部分原先在任何语言下都是写死的中文，而语言文件中其实已有其中许多
  内容的无人读取的英文文本；控制台日志原先写死为英文，现在也跟随 `language: zh`（UltiKits/UltiTrade#16）。语言键已改名：
  少数以中文句子作为语言键的行现在改用 ASCII 键。如果你在自己的语言文件中自定义过本模块的消息——即把官方文件复制一份，文件名以该文件的语言代码加连字符开头
  （例如 `lang/zh-myserver.yml`），并在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver` 选择它——请把这些修改重新应用到新键上；
  在此之前，改名的行显示文件名开头那种官方语言的文本。文件名不以官方语言代码加连字符开头的副本仍会被读取，但其中缺少的消息
  都显示英文，并记录一条警告。
  直接在官方语言文件（`lang/en.yml`、`lang/zh.yml`）中做的修改不会保留：框架会在每次启动以及每次模块重载时恢复官方文件，并把修改过的文件保留为
  `.bak`（UltiKits/UltiTools-Reborn#616）。

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
  kept as it was. An open large-trade confirmation page is replaced with a trade window and the trade
  keeps running; the player confirms again from that window (UltiKits/UltiTrade#27).
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
  与交易实际收取的一致；所有出价保持不变。已打开的大额交易确认页会被替换为交易界面，交易继续进行，
  玩家需在该界面重新确认（UltiKits/UltiTrade#27）。
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
- This module now loads on a server without PlaceholderAPI installed. The module main class carried
  a field typed directly as `TradePlaceholderExpansion` (a PlaceholderAPI type), so the framework's
  container threw `NoClassDefFoundError` while autowiring the module and the whole module failed to
  load — trading, not just placeholders, was unavailable, present since the module's initial commit
  (UltiKits/UltiTrade#48).
- 本模块现在可以在没有安装 PlaceholderAPI 的服务器上加载。此前模块主类的一个字段直接以 `TradePlaceholderExpansion`
  （一个 PlaceholderAPI 类型）声明，框架容器在自动装配本模块时会抛出 `NoClassDefFoundError`，导致整个模块加载失败——
  不仅是变量功能，交易功能本身也一并不可用；此缺陷自本模块首个提交起就存在（UltiKits/UltiTrade#48）。
- This module now loads on a server without Vault installed. `TradeService`'s economy field and
  `getEconomy()`'s return type were typed directly as `net.milkbowl.vault.economy.Economy`, and this
  module never declared Vault as a dependency (not even a soft one) in `plugin.yml`, so nothing
  guaranteed Vault's absence was handled — the same crash shape as #48, for a different soft
  dependency, found by a sweep for the same defect class (UltiKits/UltiTrade#49).
- 本模块现在可以在没有安装 Vault 的服务器上加载。此前 `TradeService` 的经济字段与 `getEconomy()` 的返回值类型都直接
  声明为 `net.milkbowl.vault.economy.Economy`，而本模块的 `plugin.yml` 从未把 Vault 声明为依赖（甚至连软依赖都没有），
  因此没有任何机制保证缺少 Vault 时的行为——与 #48 崩溃形状相同，只是软依赖不同，由针对同一缺陷类的普查发现
  （UltiKits/UltiTrade#49）。
- Dying while viewing the large-trade confirmation page now cancels the trade, the same as closing
  it any other way. Paper closes the page with `InventoryCloseEvent.Reason.DEATH`, which used to be
  treated the same as the module's own close (reload, completion, cancellation) and silently left
  the trade running with no window for either player, holding both stakes until someone ran the
  cancel command by hand.
- 现在在查看大额交易确认页面时死亡也会取消交易，与其他方式关闭该页面一致。此前 Paper 以 `InventoryCloseEvent.Reason.DEATH`
  关闭该页面时，会被当作模块自身的关闭（重载、完成、取消）处理，交易会悄悄保持进行、双方都没有窗口，两方的出价也一直
  被占用，直到有人手动运行取消命令。
- Every other reason the server, not this module, can close the large-trade confirmation page for now
  cancels the trade the same way `DEATH` already did: disconnecting, a teleport (on a server old enough
  to still send it — deprecated since Paper 1.21.10, which no longer fires it for a normal teleport, but
  this module's `plugin.yml` still declares `api-version: '1.19'`), the chunk unloading, the server
  otherwise revoking access, or an unrecognised reason. Before this, only `DEATH` cancelled the trade;
  every one of these left it running with both stakes locked and no window for either participant, until
  someone ran the cancel command by hand. A close this module or a reload causes itself (`PLUGIN`,
  `OPEN_NEW`) is unchanged — it never cancels the trade, since the code that caused it already owns the
  session's next state.
- 现在服务器（而非本模块）出于其他任何原因关闭大额交易确认页面时，都会像 `DEATH` 一样取消交易：断线、传送（仅限仍会
  发送该原因的旧版本服务端——Paper 自 1.21.10 起已弃用该原因，普通传送不再触发关闭，但本模块 `plugin.yml` 仍声明
  `api-version: '1.19'`）、所在区块被卸载、服务器出于其他原因收回了访问权限，或是一个未识别的原因。此前只有
  `DEATH` 会取消交易；以上任何一种都会让交易悄悄保持进行、双方都没有窗口、两方出价被一直占用，直到有人手动运行
  取消命令。本模块自身或重载引发的关闭（`PLUGIN`、`OPEN_NEW`）行为不变——它们从不取消交易，因为引发关闭的代码
  本身已经掌管了会话的下一个状态。
- Every other place this module opens a trade window now recovers the same way the large-trade
  confirmation page already did: a refused `InventoryOpenEvent` returns `null` from
  `HumanEntity#openInventory` rather than throwing, and none of the six other call sites checked for
  it, so an unrelated plugin could leave a player with no window at all while their session kept
  running and, in three of the six, before either side had staked anything, with no way back short
  of a manual cancel. Starting a trade whose first player's own window is refused now cancels that
  still-empty session immediately, without ever showing the second player a window for a trade the
  first never really joined; a refused reopen after a timeout, after answering an amount prompt, or
  after a session-move both-windows redraw all now fall back to cancelling that trade the same way
  (UltiKits/UltiTrade#47 review).
- 本模块中打开交易窗口的其余每一处，现在都采用大额交易确认页面已经使用的同一种恢复方式：`InventoryOpenEvent`
  被拒绝时，`HumanEntity#openInventory` 返回的是 `null` 而不是抛出异常，此前其余六处调用均未检查这一点，导致其他
  插件可能让玩家完全没有窗口，而其会话仍在继续运行——六处中有三处发生在双方都尚未压上任何出价时，且都只能靠手动
  取消才能恢复。现在，若开始一笔交易时第一位玩家自己的窗口被拒绝，会立即取消这个仍为空的会话，不会再让第二位
  玩家看到一个对方从未真正加入的交易窗口；超时后的重新打开、回答金额提示后的重新打开，以及会话转移后双方窗口的
  重绘，若被拒绝也都会同样地取消该笔交易（UltiKits/UltiTrade#47 复查）。

- `plugin.yml` now truthfully declares `softdepend: [ PlaceholderAPI, Vault ]` -- both are real, optional
  integrations this module already handles the absence of at runtime (`UltiKits/UltiTrade#48`, `#49`),
  but neither was ever declared. The framework is changing how it logs a module failing to load because
  a plugin it declares as optional is missing (debug instead of SEVERE, maintainer decision of
  2026-09-29); this module's PlaceholderAPI expansion would otherwise still trigger a misleadingly loud
  SEVERE line under that change, for a dependency this module's own `plugin.yml` never admitted to
  wanting in the first place.
- `plugin.yml` 现在如实声明 `softdepend: [ PlaceholderAPI, Vault ]`——两者都是本模块已经在运行时妥善处理其缺失的真实、
  可选集成（`UltiKits/UltiTrade#48`、`#49`），但此前都从未在文件中声明过。框架即将改变对「模块因其在 `plugin.yml`
  中声明为可选依赖的插件缺失而加载失败」这一情形的日志方式（改为调试级而非 SEVERE，维护者 2026-09-29 的决定）；
  若不声明，本模块的 PlaceholderAPI 扩展在该改动后仍会触发一条误导性的高调 SEVERE 日志——而这本是本模块自己的
  `plugin.yml` 从未承认需要的依赖。

### Removed

- Seven language entries no code ever read: `confirm_required`, `confirmed`, `confirmation_cancelled`,
  `request_expired`, `trade_started`, `gui_status_confirmed`, `gui_status_pending`. Removing them changes
  nothing players see.
- 移除七个从未有代码读取的语言条目：`confirm_required`、`confirmed`、`confirmation_cancelled`、`request_expired`、
  `trade_started`、`gui_status_confirmed`、`gui_status_pending`。移除它们不会改变玩家看到的任何内容。

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
  from the language file (see `### Changed`). To customise them, copy the official language file to one
  whose name starts with its language code and a hyphen (for example `lang/en-myserver.yml`), edit the
  replies there and set `language: en-myserver` in `plugins/UltiTools/config.yml`; an edit made in the
  official file itself is restored at the next start or module reload (UltiKits/UltiTools-Reborn#616). The seven other `messages.*` settings are read and are unchanged
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
  （见 `### Changed`）。要自定义这些回复，请把官方语言文件复制为以其语言代码加连字符开头的文件（例如 `lang/zh-myserver.yml`），
  在副本中修改，并在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`；直接修改官方文件的改动会在下次启动或模块重载时被恢复
  （UltiKits/UltiTools-Reborn#616）。其余七个 `messages.*` 设置项会被读取，保持不变（UltiKits/UltiTrade#17）。
