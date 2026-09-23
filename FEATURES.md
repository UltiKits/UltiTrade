# UltiTrade — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultitrade` here. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. This module has no `gate` rows (0 `@ConditionalOnConfig`
  sites, confirmed below) — the Kind stays in the vocabulary for cross-repository consistency
  even though it does not appear below. Unlike UltiChat and UltiRemoteBag, this module DOES carry
  `placeholder` rows (`TradePlaceholderExpansion`). The four rows under `## Lifecycle Hooks` are
  `event` rows with no `@EventHandler` site behind them: module enable, module unload and
  `/ul reload` are framework-invoked lifecycle steps, not commands this repository maps or config reads, so `event`
  is the closest-fitting Kind.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for,
  not from whether it carries a permission string.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind. `TradeCommand` is class-level `@CmdTarget(PLAYER)`, so every command row below is `player`.
- **Permission:** the literal node string, `none`, or `n/a`. `TradeCommand` carries a single
  class-level `@CmdExecutor(permission = "ultitrade.use")`; none of its 8 `@CmdMapping` sites
  declares its own method-level `permission()`, so every command row below carries the single
  node `ultitrade.use` with no `AND` compounding (unlike UltiRemoteBag's admin sub-commands). The
  shift-click trigger (`ultitrade.request.shift-click`) additionally checks the same
  `ultitrade.use` node in code, by hand, rather than through the validator chain (it is not a
  `@CmdMapping` at all) — recorded in its own Permission cell for that reason.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 24 `config` rows below cite the reading
  member, or the config class's own field declaration when no reading member exists anywhere in
  this module's source.
- **Row order:** by section, then by ID ascending within the section, except where a dependency
  between checklist rows requires departing from it (see `UAT-CHECKLIST.md`'s own Conventions).
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what a config key or the shipped lang
  files describe it as doing, that fact is itself part of "what the feature does" and is stated
  here as a plain, sourced observation, with the filed issue number, never as advice on how to fix
  it.
- **A note on this module's actual language behaviour, read before any other row below:** this
  module ships a complete, accurate `lang/en.yml` and `lang/zh.yml` (62 keys each, faithfully
  paired, measured after `UltiKits/UltiTrade#15`'s lifecycle-hook migration removed the
  `trade_reloaded` key together with the only log line it described). **Six of those keys are
  consulted by real key, and nothing else is:** `TradeCommand`'s replies to `/trade toggle`,
  `/trade block` and `/trade unblock` — `trade_toggle_on`, `trade_toggle_off`, `block_success`,
  `already_blocked`, `unblock_success`, `not_blocked` — come from the catalogue as of
  `UltiKits/UltiTrade#17`, so they follow `language`. Every other player-facing string in
  `TradeCommand`, `TradeService`, `TradeGUI`, `TradeConfirmPage`, and `TradeListener` is a
  hardcoded Simplified Chinese literal; `TradeConfig`'s 7 message keys are likewise Chinese-only
  string defaults with no i18n indirection at all. The only other two
  `i18n(...)` calls in the whole module (`UltiTrade.java`, enable/disable log lines) pass a
  raw CHINESE SENTENCE as the lookup key itself (e.g. a call meaning literally `i18n("UltiTrade enabled!")`, but written with the Chinese text as the key) rather than one
  of the 62 real keys (`trade_enabled`, etc.) — confirmed by reading
  `entities/Language.java#getLocalizedText`: a dictionary miss returns the input string
  unchanged, so these two calls print the same Chinese text under `language: en` as under
  `language: zh`. **Setting `language: en` therefore has no observable effect anywhere in this
  module except those six replies**, despite the framework's default `supported()` scan
  (`interfaces/Localized.java`) finding both `en` and `zh` lang files and letting the module
  resolve to `en` without error. Filed as `UltiKits/UltiTrade#16`. Every other Feature/Expected
  quotation below is therefore given in its real, shipped form — Chinese, in English gloss —
  never in the unused English lang key's text; the six catalogue replies are quoted verbatim from
  `lang/en.yml`, and only the checklist rows that assert them carry a `language: en` precondition
  for this module's own strings, because anywhere else that precondition would produce a false
  expectation (`UAT-CHECKLIST.md`'s `ultitrade.lifecycle.reload` and
  `ultitrade.lifecycle.reload-money-trade` set it only for two framework-owned console lines).

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/`
   and `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently.
   This module is a single-root Maven project, so this trap does not apply, but the robust `find`
   form is used regardless so the same command works unmodified across all 18 repositories.
2. **Git worktrees and build output** — UltiEconomy carries `.worktrees/economy-v2/src/main/java`.
   This module carries no worktree directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) defeats a javadoc mention or a warning-message string literal that
   merely contains the annotation's name as text. This module's naive (unanchored) and line-start
   counts are identical for every annotation kind measured below.

**Positive control:** the line-start form returns `@CmdExecutor` = 1, `@CmdMapping` = 8,
`@EventListener` = 1 (class), `@EventHandler` = 6 (handler methods), `@Scheduled` = 1,
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 1 (class), `@ConfigEntry` = 24 (31 before
`UltiKits/UltiTrade#18` removed `trade-timeout` and `UltiKits/UltiTrade#17` removed six
`messages.*` keys) — confirmed by
reading `TradeCommand.java` directly (8 `@CmdMapping` sites: `<player>`, `accept`, `deny`,
`cancel`, `toggle`, `block <player>`, `unblock <player>`, bare `""`) and `TradeListener.java`
directly (6 `@EventHandler` sites: `onPlayerInteractEntity`, `onInventoryClick`, `onPlayerChat`,
`onInventoryDrag`, `onInventoryClose`, `onPlayerQuit`). `onPlayerChat` (line 261,
`AsyncPlayerChatEvent`, fully-qualified inline rather than imported) is this module's standing
positive control — its fully-qualified event type makes it easy for a naive scan keyed on the
`import` list alone to miss; it is checked by name, not merely by count, below. This document's
command-row count matches the `@CmdMapping` annotation-site count exactly (8 against 8).

**A second annotation-site line, not in the canonical eight, is worth stating explicitly: `@Table`
entities = 2** (`PlayerTradeSettings`, `TradeLogData`), both folded into this document's `## Data
persistence` section rather than given a Kind of their own — see that section's note.

## Trade Requests

`TradeCommand` — class-level `@CmdExecutor(alias = {"trade", "t"}, permission = "ultitrade.use",
description = ...)` (the `description` value in source is a Chinese-only string meaning "Player
Trade System"), `@CmdTarget(PLAYER)`. A trade request is a one-to-one offer between two online
players; `TradeListener#onPlayerInteractEntity` is a second entry point for the same underlying
`TradeService#sendRequest` behaviour.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.request.accept | Accept the sender's own pending incoming trade request, opening `TradeGUI` for both players; refuses if no request is pending or if it has already expired, or if the requester has since gone offline | command | `/trade accept` | ultitrade.use | player | player | brief | TradeCommand#accept, TradeService#acceptRequest |
| ultitrade.request.deny | Decline the sender's own pending incoming trade request; notifies the original requester if still online | command | `/trade deny` | ultitrade.use | player | player | brief | TradeCommand#deny, TradeService#denyRequest |
| ultitrade.request.send | Send a trade request to a named, currently-ONLINE player, subject to both players' trade-toggle state, either player's blocklist, distance/cross-world restrictions, and no already-active trade on either side; on send, notifies the target (clickable accept/deny chat buttons or plain text depending on `enable-clickable-buttons`) and shows a countdown BossBar if `enable-bossbar` is true; if the target had ALREADY sent a request to this same sender, the trade auto-starts immediately instead of queuing a second request | command | `/trade <player>` | ultitrade.use | player | player | brief | TradeCommand#sendRequest, TradeService#sendRequest |
| ultitrade.request.shift-click | Send the same trade request as `.send`, triggered by shift+right-clicking another online player instead of typing a command; independently re-checks `ultitrade.use` in code (this is not a `@CmdMapping`, so the framework's own validator chain never runs for it) | event | shift+right-click another online player while `enable-shift-click` is true | ultitrade.use (checked in code, not via the validator chain) | n/a | player | brief | TradeListener#onPlayerInteractEntity |
| ultitrade.request.timeout-cleanup | Every 10 seconds (`@Scheduled(period = 200)`), expire any pending trade request older than the `request-timeout` in force when that request was sent, removing its BossBar and notifying the intended recipient | scheduled | runs automatically every 200 ticks (10s, fixed) while at least one trade request is pending | n/a | n/a | internal | brief | TradeService#cleanupExpiredRequests |

## Trading Preferences

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.settings.block | Add a named, currently-ONLINE player to the sender's own trade blocklist, refusing to add self or a name that is already blocked. The success reply and the already-blocked refusal are the language catalogue's `block_success` and `already_blocked` entries, so they follow `language`; the second line after a success and the self and offline refusals are still Chinese literals (`UltiKits/UltiTrade#17`, `#16`) | command | `/trade block <player>` | ultitrade.use | player | player | brief | TradeCommand#blockPlayer, TradeLogService#blockPlayer |
| ultitrade.settings.help | Show the command list plus the sender's own current trade-enabled status | command | `/trade` (bare) or `/trade help` (via `handleHelp`) | ultitrade.use | player | player | brief | TradeCommand#help |
| ultitrade.settings.toggle | Flip the sender's own trade-enabled flag; while disabled, other players cannot send this player a trade request (`ultitrade.request.send` above). The reply is the language catalogue's `trade_toggle_on` or `trade_toggle_off` entry, so it follows `language` (`UltiKits/UltiTrade#17`) | command | `/trade toggle` | ultitrade.use | player | player | brief | TradeCommand#toggle, TradeLogService#toggleTrade |
| ultitrade.settings.unblock | Remove a named, currently-ONLINE player from the sender's own trade blocklist, refusing if that name is not currently blocked. The success reply and the not-blocked refusal are the language catalogue's `unblock_success` and `not_blocked` entries, so they follow `language`; the offline refusal is still a Chinese literal (`UltiKits/UltiTrade#17`, `#16`) | command | `/trade unblock <player>` | ultitrade.use | player | player | brief | TradeCommand#unblockPlayer, TradeLogService#unblockPlayer |

## Trade Session

`TradeGUI` (a 6-row raw `InventoryHolder`, not a `BaseInventoryPage`) is the live trading window;
`TradeConfirmPage` (a 5-row raw `InventoryHolder`) is a second-step confirmation window shown only
when a trade's total money or experience meets `confirm-threshold`. `TradeListener` dispatches
every click/drag/close/chat event for both, since neither extends the framework's declarative or
imperative GUI base classes.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.session.cancel | Cancel the sender's own currently active trade (session or pending confirmation), returning both sides' placed items to their original owners (overflow drops on the ground rather than being discarded) and logging the cancellation | command | `/trade cancel` | ultitrade.use | player | player | detailed | TradeCommand#cancel, TradeService#cancelTrade(Player), TradeService#cancelTrade(TradeSession,String) |
| ultitrade.session.chat-input | Capture the next chat line from a player who clicked the money or experience slot, applying it as the new offer amount (or cancelling on the literal text `cancel`), with balance/experience sufficiency checks; the chat message itself is always cancelled (never broadcast) while an input is pending | event | click the money or experience slot in `TradeGUI`, then send any chat message | n/a | n/a | player | detailed | TradeListener#onPlayerChat |
| ultitrade.session.close-cancels | Closing the trade GUI (any means: Escape, another inventory, `/trade cancel` uses its own path) while a trade is still in the `TRADING` state cancels the trade one tick later, UNLESS the close was itself caused by opening the money/experience chat-input prompt | event | close the `TradeGUI` inventory while a trade is active and no chat input is pending | n/a | n/a | player | detailed | TradeListener#onInventoryClose |
| ultitrade.session.confirm-large | For a trade whose total money OR total experience meets `confirm-threshold`, clicking Confirm the first time is INTENDED to open `TradeConfirmPage` instead of confirming immediately. What actually happens: `TradeService#confirmTrade` calls `player.closeInventory()` to close `TradeGUI` BEFORE scheduling the page's construction; that synchronous close fires `TradeListener#onInventoryClose`, which — seeing the session still `TRADING` and no `waitingForInput` exemption — schedules `cancelTrade` for the same 1-tick delay `confirmTrade` uses to open the page. Because the cancel task was registered first (synchronously, before `confirmTrade` reaches its own scheduling call), it runs first: the trade is silently auto-cancelled (items returned to both sides, both GUIs closed, cancellation message shown) before `TradeConfirmPage` ever opens. The page then opens anyway, showing the same items that were just returned (the session's own item map is never cleared by cancellation), operating on a session `TradeService` no longer tracks. Known product defect, `UltiKits/UltiTrade#23` | event | click the Confirm slot in `TradeGUI` when the trade's total money or experience is at least `confirm-threshold` | n/a | n/a | player | detailed | TradeService#confirmTrade, TradeListener#onInventoryClose |
| ultitrade.session.confirm-toggle | Click the Confirm slot to mark the sender's own side as confirmed (or, for a below-threshold trade, complete it immediately once both sides are confirmed); clicking again while already confirmed un-confirms instead | event | click the Confirm slot in `TradeGUI` | n/a | n/a | player | detailed | TradeListener#onInventoryClick, TradeService#confirmTrade, TradeService#cancelConfirmation |
| ultitrade.session.drag-blocked | Dragging an item across any slot of `TradeGUI`'s or `TradeConfirmPage`'s OWN window is cancelled — items can only be staked one click at a time. One window slot among a drag's slots refuses all of it, because a drag is answered as a whole and cannot be applied to only some of the slots it names. A drag whose slots all lie in the viewer's own inventory is NOT cancelled: `InventoryEvent#getInventory()` names the view's top inventory, so such a drag reports this module's holder and used to be refused with the rest, which took the player's own inventory away from them while a trade was open (`UltiKits/UltiTrade#39`) | event | attempt a drag-place across `TradeGUI`'s or `TradeConfirmPage`'s inventory | n/a | n/a | player | none | TradeListener#onInventoryDrag |
| ultitrade.session.item-place | Place an item from the cursor into one of the sender's own 16 item slots, resetting both sides' confirmation and refreshing both open GUIs. Whether the slot is free is read from the offer the trade session holds, not from the material of the item drawn there, so a stained glass pane the sender has offered is not treated as the empty-slot placeholder. Clicking a slot that already holds one of the sender's own offers while holding an item swaps them: the held item takes the slot and the stored offer is handed back through `TradeService#giveOrDrop` (dropped at the sender's feet if the inventory cannot hold it). The whole cursor stack is staked whatever the click type: a right-click stakes all 64 rather than one, and there is no partial place. A click from anyone who is not one of the two traders is refused outright, anywhere in the view including their own inventory half. A click in a trader's OWN inventory is not refused, which is what makes staking possible at all: lifting the stack onto the cursor there is the only gesture that loads it, and while that click was refused nothing could ever be staked by anybody. Three actions stay refused even from an own inventory slot, because they are not confined to the slot clicked and would reach into the window: a shift-click (`MOVE_TO_OTHER_INVENTORY`, which scans the window for somewhere to put the stack), a double-click collect (`COLLECT_TO_CURSOR`, which sweeps every slot of both inventories) and `UNKNOWN` (`UltiKits/UltiTrade#39`) | event | click a "your items" slot in `TradeGUI` while holding an item | n/a | n/a | player | brief | TradeListener#onInventoryClick, TradeService#giveOrDrop |
| ultitrade.session.item-remove | Remove a previously placed item from one of the sender's own item slots back into the sender's own inventory by clicking it with an empty cursor (clicking the same slot while holding an item swaps instead, see `ultitrade.session.item-place`), resetting both sides' confirmation. If the sender's inventory is completely full at that moment, whatever does not fit is dropped at the sender's feet rather than discarded — the same overflow handling `TradeService#cancelTrade`/`#completeTrade` use, since every path in this module that hands an item back to a player goes through `TradeService#giveOrDrop`, which delivers a copy so that a merge cannot rewrite what the session records as staked — with the one exception that a participant the server can no longer resolve is handed nothing at all, `UltiKits/UltiTrade#32` | event | click a "your items" slot in `TradeGUI` that already holds a placed item | n/a | n/a | player | detailed | TradeListener#onInventoryClick, TradeService#giveOrDrop |
| ultitrade.session.quit-cancels | If either participant quits the server while a trade is active, the trade is cancelled immediately and items are returned as in `.cancel` | event | disconnect from the server while in an active trade | n/a | n/a | player | detailed | TradeListener#onPlayerQuit, TradeService#cancelTrade |
| ultitrade.session.confirm-page | The second-step confirmation window for a large trade (5-row raw `InventoryHolder`): an info item summarizing what each side gives/receives after tax, and Confirm/Cancel buttons. Item preview is narrower than it looks: `TradeConfirmPage#displayItems` writes its "N more items..." indicator to `startSlot + displaySlots - 1` — the SAME index as the 3rd preview slot — so a side with 4 or more items shows only 2 real item previews plus the indicator, not 3 previews plus a genuine 4th slot; a side with exactly 3 items shows all 3 with no indicator. Known product defect, `UltiKits/UltiTrade#22`. Cancel returns to `TradeGUI` WITHOUT cancelling the trade itself. Separately, and more seriously: this page's own Confirm button never checks whether both sides have now confirmed and never calls `completeTrade` — unlike the below-threshold direct-confirm path — so a large trade (at or above `confirm-threshold`) can NEVER complete through the normal confirm flow — a defect that is, in current shipped code, masked by the more fundamental `UltiKits/UltiTrade#23` above: by the time this page opens at all, the underlying trade has ALREADY been auto-cancelled, so neither the Cancel nor the Confirm button on this page has any effect on a live trade (Cancel's own `isTrading` check fails, so `TradeGUI` does not reopen; Confirm mutates the orphaned session and still sends the OTHER player a "confirmed the trade" message, which is misleading once the trade is already dead). Known product defects, `UltiKits/UltiTrade#21` and `#23` | gui | opened by `ultitrade.session.confirm-large` | n/a | n/a | player | detailed | TradeConfirmPage#TradeConfirmPage, TradeConfirmPage#initializeGUI, TradeConfirmPage#displayItems, TradeService#confirmTrade |
| ultitrade.session.start | Both players' `TradeGUI` windows open simultaneously once a request is accepted or auto-started. Every click inside `TradeGUI` is cancelled before its action runs, so no display or control item (glass panes, the money and experience slot items, the buttons) can be taken out of the window by any click type, whether money or experience trading is on or off | gui | opened by `ultitrade.request.accept` or the auto-start path of `ultitrade.request.send` | n/a | n/a | player | brief | TradeService#startTrade, TradeGUI#TradeGUI, TradeListener#onInventoryClick |

## Placeholders

`TradePlaceholderExpansion` — identifier `%ultitrade_<param>%`, registered only when
PlaceholderAPI is present, `persist()` true. Every value is read from `TradeLogService`'s
persisted `PlayerTradeSettings`, so it reflects the value as of the player's last completed trade
(or the shipped defaults for a player never seen before).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.placeholder.blocked-count | Number of players on the target's blocklist | placeholder | `%ultitrade_blocked_count%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.is-trading | Whether the target is currently inside an active trade session; two aliases (`is_trading`, `in_trade`) return the identical `"true"`/`"false"` string | placeholder | `%ultitrade_is_trading%` or `%ultitrade_in_trade%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.last-trade-ago | Human-readable elapsed time since the target's last completed trade (days/hours/minutes/"just now"), in Chinese regardless of the server's `language` setting (this class calls no i18n indirection at all); a target who has never traded returns the literal Chinese text meaning "never" | placeholder | `%ultitrade_last_trade_ago%` | n/a | n/a | player | brief | TradePlaceholderExpansion#formatTimeAgo |
| ultitrade.placeholder.last-trade-time | The target's last completed-trade timestamp, formatted `yyyy-MM-dd HH:mm`; a target who has never traded returns the literal Chinese text meaning "never traded" | placeholder | `%ultitrade_last_trade_time%` | n/a | n/a | player | brief | TradePlaceholderExpansion#formatTimestamp |
| ultitrade.placeholder.total-exp | Cumulative experience the target has GIVEN across every completed trade; two aliases (`total_exp`, `total_exp_traded`) return the identical value | placeholder | `%ultitrade_total_exp%` or `%ultitrade_total_exp_traded%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.total-money | Cumulative currency the target has GIVEN across every completed trade, formatted to 2 decimal places; two aliases (`total_money`, `total_money_traded`) return the identical value | placeholder | `%ultitrade_total_money%` or `%ultitrade_total_money_traded%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.total-trades | Total number of trades the target has completed | placeholder | `%ultitrade_total_trades%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.trade-enabled | Raw boolean string of the target's own trade-toggle state; two aliases (`trade_enabled`, `enabled`) return the identical `"true"`/`"false"` string | placeholder | `%ultitrade_trade_enabled%` or `%ultitrade_enabled%` | n/a | n/a | player | none | TradePlaceholderExpansion#onRequest |
| ultitrade.placeholder.trade-enabled-display | Localized (Chinese-only, regardless of `language`) two-character Chinese "on"/"off" rendering of the same toggle state as `.trade-enabled`; two aliases (`trade_enabled_display`, `enabled_display`) return the identical value | placeholder | `%ultitrade_trade_enabled_display%` or `%ultitrade_enabled_display%` | n/a | n/a | player | brief | TradePlaceholderExpansion#onRequest |

## Lifecycle Hooks

`UltiTrade#onUnregister()` is the extension-point hook the framework's `final`
`UltiToolsPlugin#unregisterSelf()` invokes as of UltiTools 6.3.0, before the framework's own
command and listener cleanup for this module. Before `UltiKits/UltiTrade#15`'s lifecycle-hook
migration this module overrode `unregisterSelf()`/`reloadSelf()` directly, completely replacing the
framework's own steps; its reload override only logged a line and was deleted rather than renamed.
`/ul reload UltiTrade` runs the framework's own reload steps (config reload, language refresh,
`@ConditionalOnConfig` drift report, and the framework's per-module `Module 'UltiTrade' reloaded.`
INFO line) and then, as of `UltiKits/UltiTrade#26`, this module's `UltiTrade#onReload()` hook, which
re-runs the Vault economy lookup (`TradeService#reloadEconomy`) and reschedules the old-log cleanup
task (`TradeLogService#reloadCleanupTask`), because `enable-money-trade`, `enable-trade-log` and
`cleanup-interval-hours` are applied through state the services set up when they start. A reload
that changes neither `enable-trade-log` nor `cleanup-interval-hours` leaves the running cleanup task
and its countdown untouched; changing either reschedules the task (or, with `enable-trade-log: false`,
stops it), and a rescheduled task first runs one full new interval after the reload. The hook
also voids the confirmations of every open trade and tells both players to confirm again, because a
reload may change the terms they confirmed (`trade-tax`, `exp-tax-rate`, `confirm-threshold`); a
trade that carries experience is cancelled at completion when `enable-exp-trade` is off. It then
redraws every open trade window from the reloaded configuration (title, money and experience
availability, `trade-tax`, `exp-tax-rate`), keeping every offer exactly as the session holds it and
redrawing each player's window in isolation; on a server without `InventoryView#setTitle` only the
title keeps its previous text. An open large-trade confirmation page is replaced with a trade window,
although on current builds that page does not stay open because of `UltiKits/UltiTrade#23`
(`UltiKits/UltiTrade#27`). The two kinds of value are treated differently on purpose: the terms of a
deal (taxes, threshold, which offers are allowed) always follow the reloaded configuration, which is why
open windows are redrawn and confirmations voided, while a pending trade request keeps the
`request-timeout` it was sent with, because that is a promise to the receiving player of how long they
have; a changed timeout applies to requests sent after the reload. An exception from
one reconciliation is logged at SEVERE, naming the keys it could not apply, and the other still runs. Before
any of that, the hook repeats the check `registerSelf()` makes when the module is enabled: it warns
about every key this version no longer reads that is still in the operator's `config/trade.yml`
(`ultitrade.lifecycle.removed-key-warning`, `UltiKits/UltiTrade#17`, `#18`). None of these entry
points is reachable through a command this repository maps itself, so all four rows below are
`event`-Kind, not `command`-Kind.
`ultitrade.lifecycle.reload` records what `/ul reload UltiTrade` now changes for an operator:
`ConfigManager#reloadConfigs` re-initialises, in place, the same `TradeConfig` instance the
container injected into `TradeService`, and `TradeService` reads its getters at call time.
`ultitrade.lifecycle.reload-money-trade` records the start-up-captured case the hook exists for.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.lifecycle.reload | `/ul reload UltiTrade` re-reads `config/trade.yml` into the running module, so an edited value such as `max-distance` applies to the next trade request without a restart; `max-distance` needs no reload work from this module's `onReload()` hook. With `enable-money-trade: true` and a Vault economy provider registered the module prints no reload line of its own apart from `ultitrade.lifecycle.removed-key-warning`'s one WARNING per removed key still in `config/trade.yml` (none on a fresh file, seven on a file an earlier version wrote); with money trading on and Vault or its provider missing, each reload logs the module's warning `Vault not found! Money trading disabled.` or `No Vault economy provider is registered! Money trading disabled.`; with money trading off it performs no lookup and logs nothing. Before `UltiKits/UltiTrade#15` the module's reload override replaced the framework's reload and only logged, so an edit took effect only after a restart | event | `/ul reload UltiTrade` (framework calls `reloadSelf()`, which reloads configuration, refreshes language, reports `@ConditionalOnConfig` drift and logs its own per-module line) | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.lifecycle.reload-money-trade | `/ul reload UltiTrade` applies an edited `enable-money-trade` in both directions: after a `false` to `true` edit the trade GUI offers money trading without a restart (a gold nugget in the money slot, and clicking it opens the chat amount prompt), and after a `true` to `false` edit it shows the disabled barrier instead. `UltiTrade#onReload()` re-runs the Vault economy lookup, or drops the provider when money trading is off; the same hook also reschedules the old-log cleanup task, so `enable-trade-log` and `cleanup-interval-hours` edits apply without a restart too (that half is evidenced by unit tests only, because observing a cleanup run on a server takes at least an hour). A trade that already carries money when money trading becomes unavailable is cancelled when it completes: no money moves, each side gets back its own items, and both players are told money trading is currently unavailable (also unit-test evidenced). Before `UltiKits/UltiTrade#26` the lookup and the task were set up only when the module started, so turning money trading on took effect only after a restart | event | `/ul reload UltiTrade` (framework calls `reloadSelf()`, which reloads configuration first and then invokes this hook) | n/a | n/a | admin | brief | UltiTrade#onReload, TradeService#reloadEconomy, TradeLogService#reloadCleanupTask, TradeService#completeTrade |
| ultitrade.lifecycle.removed-key-warning | When the module is enabled and again on every reload of it (`/ul reload` or `/ul reload UltiTrade`), read the operator's own `config/trade.yml` and, for each key this version no longer reads that is still in it, log one console WARNING naming the file, the key, where the setting went and that the key can be deleted. The seven such keys are `trade-timeout`, removed by `UltiKits/UltiTrade#18` (nothing replaces it; the warning names `request-timeout` as unchanged and feature request `UltiKits/UltiTrade#41`), and `messages.toggle-on`, `messages.toggle-off`, `messages.block-success`, `messages.unblock-success`, `messages.already-blocked` and `messages.not-blocked`, removed by `UltiKits/UltiTrade#17` (each warning names the language catalogue entry that now holds the reply: `trade_toggle_on`, `trade_toggle_off`, `block_success`, `unblock_success`, `already_blocked`, `not_blocked`). The framework writes a declared default only for a missing key and never deletes one, so every server that ran an earlier version still has all seven. The warnings are emitted in that order, once per key present, on every enable and reload until the key is deleted. A missing or unparseable file produces no warning, and an error inside the check itself is logged as one warning and never stops the module enabling or reloading. The file checked is the one `TradeConfig` binds (`TradeConfig#CONFIG_FILE`) | event | module enable (server start, or loading the module) and every reload of it: bare `/ul reload`, which reloads every module, or `/ul reload UltiTrade` | n/a | n/a | admin | brief | UltiTrade#registerSelf, UltiTrade#onReload, RemovedConfigKeys#warnAboutLeftovers |
| ultitrade.lifecycle.unload | When the module is unloaded (for example by `/upm uninstall UltiTrade`), shut down `TradeService` then `TradeLogService`, unregister the `ultitrade` PlaceholderAPI expansion if one was registered and clear the reference so a repeated unload cannot unregister it twice, then log the module's own "disabled" console line (the Chinese literal in `UltiTrade#onUnregister`, printed identically under either `language`, `UltiKits/UltiTrade#16`) | event | unload the `UltiTrade` module at runtime (framework calls `unregisterSelf()`, which invokes this hook before its own command/listener cleanup) | n/a | n/a | admin | brief | UltiTrade#onUnregister |

## Data persistence

Two `@Table` entities back this module's persistence: `PlayerTradeSettings` (`trade_player_settings`
— trade-toggle flag, JSON blocklist, running trade statistics) and `TradeLogData` (`trade_logs` —
one row per completed OR cancelled trade, full item/money/experience detail). Neither is given its
own `gui`/`command`/`event` row; both are folded into the two persistence guarantees below, which
is what an operator actually observes.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.persistence.log-retention | A `trade_logs` row older than `log-retention-days` is deleted by a background task; this task itself is scheduled manually via `Bukkit.getScheduler().runTaskTimerAsynchronously` at `cleanup-interval-hours`, NOT via the framework's `@Scheduled` annotation — the reconciliation table's `@Scheduled` line therefore reads 1 site against 2 `scheduled`-Kind rows in this document (see this row and `ultitrade.request.timeout-cleanup`), with this row as the stated reason | scheduled | runs automatically every `cleanup-interval-hours` hours (default 24) while `enable-trade-log` is true | n/a | n/a | internal | detailed | TradeLogService#cleanupOldLogs, TradeLogService#init |
| ultitrade.persistence.settings-survive-restart | A player's trade-toggle flag and blocklist, once changed via `.toggle`/`.block`/`.unblock`, survive a full server restart — `TradeLogService#saveSettings` writes the `trade_player_settings` row asynchronously on every change, and `TradeLogService#getSettings`/`getOrCreateSettings` re-query it by `player_uuid` on first access after restart | persistence | change trade-toggle or blocklist state, then restart the server and re-check the state | n/a | n/a | player | detailed | TradeLogService#saveSettings, TradeLogService#getSettings |
| ultitrade.persistence.trade-log | A completed or cancelled trade is written to `trade_logs` asynchronously, capturing both players' items (as JSON), money, experience, tax collected, and outcome status — this is the module's audit trail, not player-visible through any command in this module | persistence | complete or cancel a trade with `enable-trade-log: true` (shipped default) | n/a | n/a | admin | detailed | TradeLogService#logCompletedTrade, TradeLogService#logCancelledTrade |

## Configuration

Every `@ConfigEntry`-annotated field on this module's one `@ConfigEntity` class, `TradeConfig`
(`config/trade.yml`, 24 keys total — matching the reconciliation table's own `@ConfigEntry` count
of 24 exactly). `trade-timeout` is no longer among them: it was declared as the trade window's
timeout, but no open-window timer exists, so `UltiKits/UltiTrade#18` removed it (feature request
`UltiKits/UltiTrade#41`). Nor are the six `messages.*` keys `toggle-on`, `toggle-off`,
`block-success`, `unblock-success`, `already-blocked` and `not-blocked`: nothing ever read them, and
the replies they described now come from the language catalogue (`ultitrade.settings.toggle`,
`.block`, `.unblock` above), so `UltiKits/UltiTrade#17` removed them. A copy of any of the seven
left in an operator's file has no effect. Every key below is read.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.config.trade.allow-cross-world | Whether a trade request may be sent across two different worlds. Only actually checked when `max-distance` is above 0 — the whole distance/cross-world block is nested inside `if (config.getMaxDistance() > 0)`, so setting `max-distance: 0` (which disables the distance check entirely, per that key's own row) ALSO silently disables this key's own cross-world restriction, allowing cross-world trades regardless of its value | config | `config/trade.yml: allow-cross-world (default: false, only enforced while max-distance is above 0)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.cleanup-interval-hours | Interval, in hours, at which `ultitrade.persistence.log-retention`'s background task runs | config | `config/trade.yml: cleanup-interval-hours (default: 24)` | n/a | n/a | admin | brief | TradeLogService#init, TradeLogService#reloadCleanupTask |
| ultitrade.config.trade.confirm-threshold | Money-or-experience amount at or above which `ultitrade.session.confirm-large`'s second confirmation step is required | config | `config/trade.yml: confirm-threshold (default: 10000)` | n/a | n/a | admin | brief | TradeService#confirmTrade |
| ultitrade.config.trade.enable-bossbar | Whether a countdown BossBar is shown to the recipient of a pending trade request | config | `config/trade.yml: enable-bossbar (default: true)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.enable-clickable-buttons | Whether an incoming trade-request notification uses clickable chat buttons (Accept/Deny running the commands directly) rather than plain instructional text | config | `config/trade.yml: enable-clickable-buttons (default: true)` | n/a | n/a | admin | brief | TradeService#notifyTradeRequest |
| ultitrade.config.trade.enable-exp-trade | Whether experience may be included in a trade at all; when false, both sides' experience slots show a disabled/barrier item instead | config | `config/trade.yml: enable-exp-trade (default: true)` | n/a | n/a | admin | brief | TradeGUI#updateExpDisplay, TradeService#completeTrade |
| ultitrade.config.trade.enable-money-trade | Whether Vault-backed money trading is set up at all; when false, `hasEconomy()` is always false regardless of whether Vault is installed | config | `config/trade.yml: enable-money-trade (default: true)` | n/a | n/a | admin | brief | TradeService#init, TradeService#reloadEconomy, TradeService#hasEconomy |
| ultitrade.config.trade.enable-particles | Whether success/failure particle effects play at the end of a trade | config | `config/trade.yml: enable-particles (default: true)` | n/a | n/a | admin | none | TradeService#playSuccessEffects, TradeService#playFailEffects |
| ultitrade.config.trade.enable-shift-click | Whether `ultitrade.request.shift-click` is active at all | config | `config/trade.yml: enable-shift-click (default: true)` | n/a | n/a | admin | brief | TradeListener#onPlayerInteractEntity |
| ultitrade.config.trade.enable-sounds | Master switch for every sound effect this module plays | config | `config/trade.yml: enable-sounds (default: true)` | n/a | n/a | admin | brief | TradeService#playSound |
| ultitrade.config.trade.enable-trade-log | Whether a completed or cancelled trade is written to `trade_logs`, and whether the retention-cleanup background task runs at all | config | `config/trade.yml: enable-trade-log (default: true)` | n/a | n/a | admin | brief | TradeLogService#init, TradeLogService#reloadCleanupTask, TradeLogService#logCompletedTrade |
| ultitrade.config.trade.exp-tax-rate | Fraction of offered experience deducted as tax on a completed trade (0 disables) | config | `config/trade.yml: exp-tax-rate (default: 0.0)` | n/a | n/a | admin | brief | TradeService#completeTrade |
| ultitrade.config.trade.gui-title | The `TradeGUI` inventory title template, with a `{PLAYER}` placeholder for the other participant's name | config | `config/trade.yml: gui-title (default: a Chinese-language template meaning "Trading with {PLAYER}")` | n/a | n/a | admin | brief | TradeGUI#TradeGUI |
| ultitrade.config.trade.log-retention-days | Age, in days, past which a `trade_logs` row is deleted by the retention-cleanup task | config | `config/trade.yml: log-retention-days (default: 30)` | n/a | n/a | admin | brief | TradeLogService#cleanupOldLogs |
| ultitrade.config.trade.max-distance | Maximum block distance between two players for a trade request to be sendable; `0` disables the distance check entirely | config | `config/trade.yml: max-distance (default: 50)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.player-blocked | Message shown to a sender whose target has blocked them | config | `config/trade.yml: messages.player-blocked (default: a Chinese-language message meaning "The other party has blocked you!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.request-received | Message shown to the recipient of a trade request, only when `enable-clickable-buttons` is false | config | `config/trade.yml: messages.request-received (default: a Chinese-language template meaning "{PLAYER} wants to trade with you! Type /trade accept to accept")` | n/a | n/a | admin | brief | TradeService#notifyTradeRequest |
| ultitrade.config.trade.messages.request-sent | Message shown to the sender immediately after a trade request is sent | config | `config/trade.yml: messages.request-sent (default: a Chinese-language template meaning "Trade request sent to {PLAYER}!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.request-timeout | Message shown to the recipient when their pending request expires unanswered | config | `config/trade.yml: messages.request-timeout (default: a Chinese-language message meaning "Trade request has timed out!")` | n/a | n/a | admin | brief | TradeService#cleanupExpiredRequests |
| ultitrade.config.trade.messages.trade-cancelled | Message shown to both sides when a trade is cancelled, with an optional reason appended in parentheses | config | `config/trade.yml: messages.trade-cancelled (default: a Chinese-language message meaning "Trade cancelled!")` | n/a | n/a | admin | brief | TradeService#cancelTrade |
| ultitrade.config.trade.messages.trade-complete | Message shown to both sides on a successfully completed trade | config | `config/trade.yml: messages.trade-complete (default: a Chinese-language message meaning "Trade complete!")` | n/a | n/a | admin | brief | TradeService#completeTrade |
| ultitrade.config.trade.messages.trade-disabled | Message shown to a sender whose target currently has trading toggled off | config | `config/trade.yml: messages.trade-disabled (default: a Chinese-language message meaning "The other party has disabled trading!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.request-timeout | Seconds a sent trade request remains valid before `ultitrade.request.timeout-cleanup` expires it; also the countdown length shown on the recipient's BossBar | config | `config/trade.yml: request-timeout (default: 30)` | n/a | n/a | admin | brief | TradeService#sendRequest, TradeRequest#isExpired, TradeService#showRequestBossBar, TradeService#cleanupExpiredRequests |
| ultitrade.config.trade.trade-tax | Fraction of offered money deducted as tax on a completed trade (0 disables) | config | `config/trade.yml: trade-tax (default: 0.0)` | n/a | n/a | admin | brief | TradeService#completeTrade |
