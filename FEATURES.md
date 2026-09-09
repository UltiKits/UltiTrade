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
  `placeholder` rows (`TradePlaceholderExpansion`).
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
  feature — for every Kind, `config` included: all 31 `config` rows below cite the reading
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
  module ships a complete, accurate `lang/en.yml` and `lang/zh.yml` (73 keys each, faithfully
  paired) that are **never consulted** by real key anywhere in this module's source. Every
  player-facing string in `TradeCommand`, `TradeService`, `TradeGUI`, `TradeConfirmPage`, and
  `TradeListener` is a hardcoded Simplified Chinese literal; `TradeConfig`'s 13 message keys are
  likewise Chinese-only string defaults with no i18n indirection at all. The only three
  `i18n(...)` calls in the whole module (`UltiTrade.java`, enable/disable/reload log lines) pass a
  raw CHINESE SENTENCE as the lookup key itself (e.g. a call meaning literally `i18n("UltiTrade enabled!")`, but written with the Chinese text as the key) rather than one
  of the 73 real keys (`trade_enabled`, etc.) — confirmed by reading
  `entities/Language.java#getLocalizedText`: a dictionary miss returns the input string
  unchanged, so these three calls print the same Chinese text under `language: en` as under
  `language: zh`. **Setting `language: en` therefore has zero observable effect anywhere in this
  module**, despite the framework's default `supported()` scan (`interfaces/Localized.java`)
  finding both `en` and `zh` lang files and letting the module resolve to `en` without error.
  Filed as `UltiKits/UltiTrade#16`. Every Feature/Expected quotation below is therefore given in
  its real, shipped form — Chinese, in English gloss — never in the unused English lang key's
  text, and no row below carries a `language: en` precondition, because that precondition would
  produce a false expectation.

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
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 1 (class), `@ConfigEntry` = 31 — confirmed by
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
| ultitrade.request.timeout-cleanup | Every 10 seconds (`@Scheduled(period = 200)`), expire any pending trade request older than `request-timeout` seconds, removing its BossBar and notifying the intended recipient | scheduled | runs automatically every 200 ticks (10s, fixed) while at least one trade request is pending | n/a | n/a | internal | brief | TradeService#cleanupExpiredRequests |

## Trading Preferences

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.settings.block | Add a named, currently-ONLINE player to the sender's own trade blocklist, refusing to add self or a name that is already blocked | command | `/trade block <player>` | ultitrade.use | player | player | brief | TradeCommand#blockPlayer, TradeLogService#blockPlayer |
| ultitrade.settings.help | Show the command list plus the sender's own current trade-enabled status | command | `/trade` (bare) or `/trade help` (via `handleHelp`) | ultitrade.use | player | player | brief | TradeCommand#help |
| ultitrade.settings.toggle | Flip the sender's own trade-enabled flag; while disabled, other players cannot send this player a trade request (`ultitrade.trade-disabled-target` below) | command | `/trade toggle` | ultitrade.use | player | player | brief | TradeCommand#toggle, TradeLogService#toggleTrade |
| ultitrade.settings.unblock | Remove a named, currently-ONLINE player from the sender's own trade blocklist, refusing if that name is not currently blocked | command | `/trade unblock <player>` | ultitrade.use | player | player | brief | TradeCommand#unblockPlayer, TradeLogService#unblockPlayer |

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
| ultitrade.session.drag-blocked | Dragging an item across any slot of `TradeGUI` or `TradeConfirmPage` is always cancelled — items can only be placed one click at a time | event | attempt a drag-place across `TradeGUI`'s or `TradeConfirmPage`'s inventory | n/a | n/a | player | none | TradeListener#onInventoryDrag |
| ultitrade.session.item-place | Place an item from the cursor into one of the sender's own 16 item slots (over a glass-pane placeholder), resetting both sides' confirmation and refreshing both open GUIs | event | click a "your items" slot in `TradeGUI` while holding an item | n/a | n/a | player | brief | TradeListener#onInventoryClick |
| ultitrade.session.item-remove | Remove a previously placed item from one of the sender's own item slots back into the sender's own inventory, resetting both sides' confirmation. If the sender's inventory is completely full at that moment, the item is LOST — `TradeListener#onInventoryClick`'s remove-item branch discards `Inventory#addItem`'s overflow return value instead of dropping it, unlike `TradeService#cancelTrade`/`#completeTrade`, which both drop overflow at the player's feet. Known product defect, `UltiKits/UltiTrade#20` | event | click a "your items" slot in `TradeGUI` that already holds a placed item | n/a | n/a | player | detailed | TradeListener#onInventoryClick |
| ultitrade.session.quit-cancels | If either participant quits the server while a trade is active, the trade is cancelled immediately and items are returned as in `.cancel` | event | disconnect from the server while in an active trade | n/a | n/a | player | detailed | TradeListener#onPlayerQuit, TradeService#cancelTrade |
| ultitrade.session.confirm-page | The second-step confirmation window for a large trade (5-row raw `InventoryHolder`): an info item summarizing what each side gives/receives after tax, and Confirm/Cancel buttons. Item preview is narrower than it looks: `TradeConfirmPage#displayItems` writes its "N more items..." indicator to `startSlot + displaySlots - 1` — the SAME index as the 3rd preview slot — so a side with 4 or more items shows only 2 real item previews plus the indicator, not 3 previews plus a genuine 4th slot; a side with exactly 3 items shows all 3 with no indicator. Known product defect, `UltiKits/UltiTrade#22`. Cancel returns to `TradeGUI` WITHOUT cancelling the trade itself. Separately, and more seriously: this page's own Confirm button never checks whether both sides have now confirmed and never calls `completeTrade` — unlike the below-threshold direct-confirm path — so a large trade (at or above `confirm-threshold`) can NEVER complete through the normal confirm flow — a defect that is, in current shipped code, masked by the more fundamental `UltiKits/UltiTrade#23` above: by the time this page opens at all, the underlying trade has ALREADY been auto-cancelled, so neither the Cancel nor the Confirm button on this page has any effect on a live trade (Cancel's own `isTrading` check fails, so `TradeGUI` does not reopen; Confirm mutates the orphaned session and still sends the OTHER player a "confirmed the trade" message, which is misleading once the trade is already dead). Known product defects, `UltiKits/UltiTrade#21` and `#23` | gui | opened by `ultitrade.session.confirm-large` | n/a | n/a | player | detailed | TradeConfirmPage#TradeConfirmPage, TradeConfirmPage#initializeGUI, TradeConfirmPage#displayItems, TradeService#confirmTrade |
| ultitrade.session.start | Both players' `TradeGUI` windows open simultaneously once a request is accepted or auto-started | gui | opened by `ultitrade.request.accept` or the auto-start path of `ultitrade.request.send` | n/a | n/a | player | brief | TradeService#startTrade, TradeGUI#TradeGUI |

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
(`config/trade.yml`, 31 keys total — matching the reconciliation table's own `@ConfigEntry` count
of 31 exactly).

**Six of the thirty-one keys are declared and validated but never read by any production code in
this module — each is called out in its own row below with the filed issue number
(`UltiKits/UltiTrade#17`) rather than a claim that flipping it changes anything.** Confirmed for
each by `grep -rn <getterName> src/main/java`, returning no hit outside `TradeConfig` itself.
Unlike UltiRemoteBag's equivalent dead keys, these six are messages `TradeCommand` genuinely needs
for the exact behaviour they name (toggle-on, toggle-off, block-success, unblock-success,
already-blocked, not-blocked) — the command hardcodes a DIFFERENT, Chinese-only literal at each
site instead of reading the configured (also Chinese-only) message.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultitrade.config.trade.allow-cross-world | Whether a trade request may be sent across two different worlds. Only actually checked when `max-distance` is above 0 — the whole distance/cross-world block is nested inside `if (config.getMaxDistance() > 0)`, so setting `max-distance: 0` (which disables the distance check entirely, per that key's own row) ALSO silently disables this key's own cross-world restriction, allowing cross-world trades regardless of its value | config | `config/trade.yml: allow-cross-world (default: false, only enforced while max-distance is above 0)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.cleanup-interval-hours | Interval, in hours, at which `ultitrade.persistence.log-retention`'s background task runs | config | `config/trade.yml: cleanup-interval-hours (default: 24)` | n/a | n/a | admin | brief | TradeLogService#init |
| ultitrade.config.trade.confirm-threshold | Money-or-experience amount at or above which `ultitrade.session.confirm-large`'s second confirmation step is required | config | `config/trade.yml: confirm-threshold (default: 10000)` | n/a | n/a | admin | brief | TradeService#confirmTrade |
| ultitrade.config.trade.enable-bossbar | Whether a countdown BossBar is shown to the recipient of a pending trade request | config | `config/trade.yml: enable-bossbar (default: true)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.enable-clickable-buttons | Whether an incoming trade-request notification uses clickable chat buttons (Accept/Deny running the commands directly) rather than plain instructional text | config | `config/trade.yml: enable-clickable-buttons (default: true)` | n/a | n/a | admin | brief | TradeService#notifyTradeRequest |
| ultitrade.config.trade.enable-exp-trade | Whether experience may be included in a trade at all; when false, both sides' experience slots show a disabled/barrier item instead | config | `config/trade.yml: enable-exp-trade (default: true)` | n/a | n/a | admin | brief | TradeGUI#updateExpDisplay, TradeService#completeTrade |
| ultitrade.config.trade.enable-money-trade | Whether Vault-backed money trading is set up at all; when false, `hasEconomy()` is always false regardless of whether Vault is installed | config | `config/trade.yml: enable-money-trade (default: true)` | n/a | n/a | admin | brief | TradeService#init, TradeService#hasEconomy |
| ultitrade.config.trade.enable-particles | Whether success/failure particle effects play at the end of a trade | config | `config/trade.yml: enable-particles (default: true)` | n/a | n/a | admin | none | TradeService#playSuccessEffects, TradeService#playFailEffects |
| ultitrade.config.trade.enable-shift-click | Whether `ultitrade.request.shift-click` is active at all | config | `config/trade.yml: enable-shift-click (default: true)` | n/a | n/a | admin | brief | TradeListener#onPlayerInteractEntity |
| ultitrade.config.trade.enable-sounds | Master switch for every sound effect this module plays | config | `config/trade.yml: enable-sounds (default: true)` | n/a | n/a | admin | brief | TradeService#playSound |
| ultitrade.config.trade.enable-trade-log | Whether a completed or cancelled trade is written to `trade_logs`, and whether the retention-cleanup background task runs at all | config | `config/trade.yml: enable-trade-log (default: true)` | n/a | n/a | admin | brief | TradeLogService#init, TradeLogService#logCompletedTrade |
| ultitrade.config.trade.exp-tax-rate | Fraction of offered experience deducted as tax on a completed trade (0 disables) | config | `config/trade.yml: exp-tax-rate (default: 0.0)` | n/a | n/a | admin | brief | TradeService#completeTrade |
| ultitrade.config.trade.gui-title | The `TradeGUI` inventory title template, with a `{PLAYER}` placeholder for the other participant's name | config | `config/trade.yml: gui-title (default: a Chinese-language template meaning "Trading with {PLAYER}")` | n/a | n/a | admin | brief | TradeGUI#TradeGUI |
| ultitrade.config.trade.log-retention-days | Age, in days, past which a `trade_logs` row is deleted by the retention-cleanup task | config | `config/trade.yml: log-retention-days (default: 30)` | n/a | n/a | admin | brief | TradeLogService#cleanupOldLogs |
| ultitrade.config.trade.max-distance | Maximum block distance between two players for a trade request to be sendable; `0` disables the distance check entirely | config | `config/trade.yml: max-distance (default: 50)` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.already-blocked | Declared as the "already blocked" refusal message; `/trade block` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.already-blocked (default: a Chinese-language message meaning "{PLAYER} is already in your blacklist!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#alreadyBlockedMessage (declared, never read outside this class) |
| ultitrade.config.trade.messages.block-success | Declared as the block-success confirmation message; `/trade block` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.block-success (default: a Chinese-language message meaning "Added {PLAYER} to your trade blacklist!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#blockSuccessMessage (declared, never read outside this class) |
| ultitrade.config.trade.messages.not-blocked | Declared as the "not blocked" refusal message; `/trade unblock` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.not-blocked (default: a Chinese-language message meaning "{PLAYER} is not in your blacklist!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#notBlockedMessage (declared, never read outside this class) |
| ultitrade.config.trade.messages.player-blocked | Message shown to a sender whose target has blocked them | config | `config/trade.yml: messages.player-blocked (default: a Chinese-language message meaning "The other party has blocked you!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.request-received | Message shown to the recipient of a trade request, only when `enable-clickable-buttons` is false | config | `config/trade.yml: messages.request-received (default: a Chinese-language template meaning "{PLAYER} wants to trade with you! Type /trade accept to accept")` | n/a | n/a | admin | brief | TradeService#notifyTradeRequest |
| ultitrade.config.trade.messages.request-sent | Message shown to the sender immediately after a trade request is sent | config | `config/trade.yml: messages.request-sent (default: a Chinese-language template meaning "Trade request sent to {PLAYER}!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.request-timeout | Message shown to the recipient when their pending request expires unanswered | config | `config/trade.yml: messages.request-timeout (default: a Chinese-language message meaning "Trade request has timed out!")` | n/a | n/a | admin | brief | TradeService#cleanupExpiredRequests |
| ultitrade.config.trade.messages.toggle-off | Declared as the trade-disabled confirmation message; `/trade toggle` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.toggle-off (default: a Chinese-language message meaning "You have disabled trading!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#toggleOffMessage (declared, never read outside this class) |
| ultitrade.config.trade.messages.toggle-on | Declared as the trade-enabled confirmation message; `/trade toggle` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.toggle-on (default: a Chinese-language message meaning "You have enabled trading!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#toggleOnMessage (declared, never read outside this class) |
| ultitrade.config.trade.messages.trade-cancelled | Message shown to both sides when a trade is cancelled, with an optional reason appended in parentheses | config | `config/trade.yml: messages.trade-cancelled (default: a Chinese-language message meaning "Trade cancelled!")` | n/a | n/a | admin | brief | TradeService#cancelTrade |
| ultitrade.config.trade.messages.trade-complete | Message shown to both sides on a successfully completed trade | config | `config/trade.yml: messages.trade-complete (default: a Chinese-language message meaning "Trade complete!")` | n/a | n/a | admin | brief | TradeService#completeTrade |
| ultitrade.config.trade.messages.trade-disabled | Message shown to a sender whose target currently has trading toggled off | config | `config/trade.yml: messages.trade-disabled (default: a Chinese-language message meaning "The other party has disabled trading!")` | n/a | n/a | admin | brief | TradeService#sendRequest |
| ultitrade.config.trade.messages.unblock-success | Declared as the unblock-success confirmation message; `/trade unblock` actually sends a different, hardcoded Chinese literal instead of reading this key. Known product defect, `UltiKits/UltiTrade#17` | config | `config/trade.yml: messages.unblock-success (default: a Chinese-language message meaning "Removed {PLAYER} from your trade blacklist!", has no effect, see UltiKits/UltiTrade#17)` | n/a | n/a | admin | brief | TradeConfig#unblockSuccessMessage (declared, never read outside this class) |
| ultitrade.config.trade.request-timeout | Seconds a sent trade request remains valid before `ultitrade.request.timeout-cleanup` expires it; also the countdown length shown on the recipient's BossBar | config | `config/trade.yml: request-timeout (default: 30)` | n/a | n/a | admin | brief | TradeService#sendRequest, TradeService#showRequestBossBar, TradeService#cleanupExpiredRequests |
| ultitrade.config.trade.trade-tax | Fraction of offered money deducted as tax on a completed trade (0 disables) | config | `config/trade.yml: trade-tax (default: 0.0)` | n/a | n/a | admin | brief | TradeService#completeTrade |
| ultitrade.config.trade.trade-timeout | Declared as the trade-window timeout in seconds; no scheduled task or check in this module's source reads it to actually expire an open `TradeGUI` session by elapsed time — a trade only ends via explicit cancel/complete/quit/GUI-close, never by this timer. Known product defect, `UltiKits/UltiTrade#18` | config | `config/trade.yml: trade-timeout (default: 120, has no effect, see UltiKits/UltiTrade#18)` | n/a | n/a | admin | brief | TradeConfig#tradeTimeout (declared, never read outside this class) |
