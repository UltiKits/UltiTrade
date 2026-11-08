# UltiTrade

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)

UltiTrade 是一个功能完整的 Minecraft 玩家间交易系统插件，基于 UltiTools-API 框架开发。支持物品、金币、经验交易，具有完善的安全机制和丰富的用户体验功能。

## ✨ 功能特性

### 核心交易功能
- 🔄 **安全物品交易** - 双方确认机制，防止欺诈
- 💰 **金币交易** - 集成 Vault 经济系统，支持税收
- ⭐ **经验交易** - 支持经验值交换，可配置税率
- ⏱️ **超时机制** - 可配置的交易请求超时时间

### 用户体验
- 🎯 **BossBar 倒计时** - 直观显示交易请求剩余时间
- 🖱️ **可点击按钮** - 聊天消息中的 [接受]/[拒绝] 按钮
- 👆 **Shift+右键交易** - 快捷发起交易请求
- 🔊 **音效反馈** - 交易成功/失败/物品放置等音效
- ✨ **粒子特效** - 交易完成时的视觉效果
- 📋 **物品详情预览** - 悬浮显示附魔、耐久度等信息

### 安全与管理

- 🚫 **玩家黑名单** - 屏蔽不想交易的玩家
- 🔐 **交易开关** - 玩家可自主开关交易功能
- ⚠️ **大额交易确认** - 超过阈值自动弹出确认界面
- 📊 **交易日志** - 记录所有交易，支持自动清理
- 📈 **PlaceholderAPI** - 交易统计变量支持

## 📦 安装

1. 安装前置插件：
   - [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) 6.3.0 或更高版本 (必需)。本模块声明 `api-version: 630`，更早的框架会拒绝加载它
   - [Vault](https://www.spigotmc.org/resources/vault.34315/) (金币交易)
   - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (可选)

2. 将 `UltiTrade.jar` 放入 `plugins/UltiTools/plugins/` 目录

3. 重启服务器（`/ul reload` 只重载配置，不会加载新的模块 JAR）

## 🎮 命令

| 命令 | 权限 | 描述 |
|------|------|------|
| `/trade <玩家>` | `ultitrade.use` | 向玩家发起交易请求 |
| `/trade accept` | `ultitrade.use` | 接受交易请求 |
| `/trade deny` | `ultitrade.use` | 拒绝交易请求 |
| `/trade cancel` | `ultitrade.use` | 取消当前交易 |
| `/trade toggle` | `ultitrade.use` | 开启/关闭交易功能 |
| `/trade block <玩家>` | `ultitrade.use` | 屏蔽指定玩家 |
| `/trade unblock <玩家>` | `ultitrade.use` | 取消屏蔽玩家 |

**命令别名:** `/t`

## ⚙️ 配置

配置文件位于 `plugins/UltiTools/pluginConfig/UltiTrade/config/trade.yml`：

`allow-cross-world: false` refuses trade requests between different worlds whatever `max-distance` is,
including `max-distance: 0`. Zero removes only the same-world distance limit; it does not enable
cross-world trading. With `allow-cross-world: true`, requests between worlds are allowed and no
cross-world distance comparison is made.

The same policy is checked again when a pending request is accepted, using both players' current
locations and the settings then in force. This includes `/trade accept`, its clickable chat button,
and reverse-request auto-accept. A refused acceptance tells the accepting player "Cross-world trading
is disabled!" or "Too far away to trade!", removes the pending request and its BossBar, and starts no
trade. Reload does not invalidate pending requests immediately: they are judged against the reloaded
policy when accepted, while their original request timeout remains unchanged. This is not continuous
world/distance enforcement after a trade has already started.

`allow-cross-world: false` 始终拒绝不同世界之间的交易请求，即使 `max-distance: 0` 也一样。
`0` 只取消同世界的距离限制，不会开启跨世界交易；只有 `allow-cross-world: true` 才允许跨世界请求，
此时不计算不同世界之间的距离。
接受待处理请求时，会按双方当时的位置和当前生效的配置再次检查同一策略，包括命令、聊天接受按钮和反向请求自动接受。
拒绝时会告知接受者原因，移除请求及其 BossBar，且不开始交易。重载不会立即清空待处理请求；接受时按重载后的策略判断，
原请求的超时时间不变。交易开始后不会持续检查世界或距离。

### Optional open-window time limit / 可选交易窗口时限

Set `window-timeout: 120` to close an open trade after two minutes. The shipped value is `0`
(unlimited), and valid values are `0` through `600` seconds. This is separate from `request-timeout`:
the window clock starts when the trade opens, not when its request is sent. Changing an offer,
confirming, opening a large-trade confirmation page or answering a money/experience prompt does not
restart it. Both players receive the duration in chat when the windows open, then one warning in the
last ten seconds (with a limit of ten seconds or less, the opening notice and the warning arrive together). At expiry both see the configured cancellation message with the reason
"trade window time limit reached; nothing was transferred" under `language: en`.

Expiry uses the same return path as `/trade cancel`: each player gets their own items back, overflow
drops at the owner's feet, and an unavailable player's items are kept in the existing pending-return
system. Money and experience have not been taken, so neither changes. A confirmation after the
deadline cannot complete the trade while waiting for the next timer check. The check runs on the
server thread every 20 ticks; a lagging server acts on the next available check, not on an async
inventory thread. A trade whose settlement started before the deadline is allowed to finish.

After editing the setting, run `ul reload UltiTrade` from the console. Only newly opened trades use
the new duration; existing trades keep the duration announced at opening, including unlimited ones.
The legacy `trade-timeout` key is still ignored and warned about, never copied or automatically
removed: explicitly set `window-timeout` to opt in. `enable-bossbar` still controls request bars only.

设置 `window-timeout: 120` 可让已打开的交易在两分钟后取消，默认 `0` 不限时，有效范围为 0-600 秒。
它与请求超时分别计时；修改物品、金币、经验或确认都不会重置。双方开窗时获知总时长，最后十秒收到一次提醒（时限不超过十秒时，开窗通知与提醒同时到达），
到期经原取消流程各自退回物品、放不下则掉落在原主脚下，离线物品沿用待返还机制，金币和经验不变。
重载只影响之后打开的新交易，旧交易保留原时限；旧键 `trade-timeout` 仍无效，必须明确设置新键来启用。

```yaml
# 基础设置
request-timeout: 30           # 交易请求超时时间（秒，5-600）
window-timeout: 0             # 交易窗口固定时限（秒，0-600；0为不限时；重载只影响新交易）
max-distance: 50              # 最大交易距离（格，0 无限制，最大 1000）
allow-cross-world: false      # 是否允许跨世界交易

# 交易功能
enable-money-trade: true      # 启用金币交易
enable-exp-trade: true        # 启用经验交易
enable-shift-click: true      # 启用 Shift+右键发起交易

# 税收设置
trade-tax: 0.0                # 金币交易税率（0-1）
exp-tax-rate: 0.0             # 经验交易税率（0-1）

# 大额交易确认
confirm-threshold: 10000      # 确认阈值（金币或经验）

# 日志设置
enable-trade-log: true        # 启用交易日志
log-retention-days: 30        # 日志保留天数（1-365）
cleanup-interval-hours: 24    # 清理间隔（小时，1-168）

# 效果设置
enable-sounds: true           # 启用音效
enable-particles: true        # 启用粒子效果
enable-bossbar: true          # 启用 BossBar 倒计时
enable-clickable-buttons: true # 启用聊天可点击按钮
```

## 📊 PlaceholderAPI 变量

| 变量 | 描述 |
|------|------|
| `%ultitrade_total_trades%` | 玩家总交易次数 |
| `%ultitrade_total_money%` | 玩家总交易金额 |
| `%ultitrade_total_exp%` | 玩家总交易经验 |
| `%ultitrade_trade_enabled%` | 交易是否开启 |
| `%ultitrade_is_trading%` | 是否正在交易中 |
| `%ultitrade_last_trade_time%` | 上次交易时间 |
| `%ultitrade_blocked_count%` | 黑名单玩家数量 |

## 🎨 GUI 界面

交易界面采用 54 格大箱子布局：

```
[你的物品区 4x4] | [分隔线] | [对方物品区 4x4]
[金币] [状态] [经验] |   | [经验] [状态] [金币]
[取消] [...] [...] [确认] [...] [...] [...]
```

### 物品详情预览

悬浮在对方物品上可查看详细信息：

- 附魔列表及等级
- 耐久度百分比
- 物品标志
- 自定义模型数据
- 无法破坏标记

## 📝 更新日志

### 未发布 / Unreleased

- Added an opt-in fixed trade-window lifetime (`window-timeout`), with localized notices and safe stake return on expiry (#41).
- 需要 UltiTools-API 6.3.0 或更高版本（`api-version: 630`）

### v1.0.0 (2026-02-18)

- 初始版本
- 基础物品和金币交易功能
- 经验交易功能
- 玩家黑名单系统
- 交易开关功能
- BossBar 请求倒计时
- 可点击聊天按钮
- Shift+右键快捷交易
- 大额交易确认机制
- 交易日志系统
- PlaceholderAPI 集成
- 音效和粒子特效
- 物品详情悬浮预览
- 基于 UltiTools-API 框架

## 🔧 技术架构

- **框架**: UltiTools-API 6.3.0+
- **注解驱动**: `@Service`, `@Autowired`, `@CmdMapping`, `@Table`, `@Scheduled`
- **数据持久化**: Query DSL + DataOperator ORM
- **依赖注入**: UltiTools IoC 容器
- **配置验证**: `@Range`, `@NotEmpty` validation annotations

### UltiTools-API 6.2.0 起的新特性应用

本插件使用 UltiTools-API 6.2.0 引入的现代化模式（当前要求 6.3.0 或更高版本）：

#### 1. Query DSL (查询构建器)

```java
// 旧模式
List<PlayerTradeSettings> settings = settingsOperator.getAll(
    WhereCondition.builder().column("player_uuid").value(uuid).build()
);

// 新模式 - 流式 API
List<PlayerTradeSettings> settings = settingsOperator.query()
    .where("player_uuid").eq(uuid)
    .list();
```

#### 2. @Scheduled 任务系统

```java
// 自动注册的定时任务
@Scheduled(period = 200, async = false)
public void cleanupExpiredRequests() {
    // 每10秒执行一次，自动管理生命周期
}
```

#### 3. 配置验证

```java
@Range(min = 5, max = 600)
@ConfigEntry(path = "request-timeout")
private int requestTimeout = 30;

@NotEmpty
@ConfigEntry(path = "gui-title")
private String guiTitle = "&6与 {PLAYER} 交易";
```

#### 4. 基类迁移

- `AbstractCommendExecutor` → `BaseCommandExecutor`
- `AbstractDataEntity` → `BaseDataEntity<String>`

### 主要类说明

| 类 | 说明 |
|---|---|
| `TradeService` | 核心交易逻辑服务 |
| `TradeLogService` | 日志记录和玩家设置管理 |
| `TradeGUI` | 交易界面实现 |
| `TradeConfirmPage` | 大额交易确认页面 |
| `TradeListener` | 事件监听处理 |
| `TradeCommand` | 命令执行器 |
| `TradePlaceholderExpansion` | PlaceholderAPI 扩展 |

### 数据实体

| 实体 | 用途 |
|---|---|
| `TradeLogData` | 交易日志记录 |
| `PlayerTradeSettings` | 玩家交易设置和统计 |
| `SerializedItemStack` | 物品序列化 (JSON) |
| `TradeSession` | 活跃交易会话 |

## ⚠️ Known limitations / 已知限制

- **The settings lock budget is not a database timeout.** A `/trade block`, `/trade unblock` or trade request
  (including shift+right-click) shares about 50 ms across its settings-lock attempts, rather than waiting 50 ms
  again at each check. A free lock can still be acquired after that budget expires. Database calls themselves
  are not timed out by this budget; a slow database can still delay the server thread.
- **Completed-trade statistics can be skipped silently in a disabled-core fallback.** Normal completed-trade
  logging runs in the background and waits for the player's settings write. If the UltiTools core is no longer
  enabled, logging runs inline instead; if that is on the server thread while an earlier same-player write is
  still holding the lock beyond its allowance, that statistics increment is skipped without a warning.
  Do not initiate trades while disabling the core; finish active trades before stopping or unloading it.
- **Another plugin must not interrupt the server thread.** While its interrupt flag is set, settings-lock
  attempts immediately fail: settings commands may answer the busy reply, joins skip caching, and uncached
  reads do not fill the cache. UltiTrade preserves that flag instead of clearing another plugin's signal.
  Remove or repair the plugin causing the interrupt and restart the server; retrying the command alone does
  not clear the flag.
- **A crash while a saved stake is being returned holds that return for the operator.** When a trade
  is cancelled while one participant cannot be found, their staked items are kept in
  `trade_pending_returns` and handed back at their next join. The table records each hand-over's state:
  before handing over, a join marks the entry CLAIMED (only one server can), and once the player's data
  save returns the entry is removed (UltiKits/UltiTrade#55). An entry left CLAIMED by a crash (or a failed
  save) is never handed over again automatically, because whether its items reached the player cannot be
  known from the table. At start-up and on every reload the console warns with the number of such
  entries. Check the player's inventory and ender chest, then resolve each entry with
  `/trade pending list`, `/trade pending redeliver <id>` (handed over at the player's next join, or at once
  if they are online on that server) or `/trade pending void <id>` (removed, not handed over). The
  commands need `ultitrade.admin` and work from the console. While an entry is held, the player is told
  at each join that a return of items is waiting for an administrator's check (with the count).
- **All servers sharing one database must run the same UltiTrade version.** Stop them all, upgrade, then
  start them: a server still running a build from before UltiKits/UltiTrade#55 does not know the CLAIMED
  state. It clears the held entry's marker and saved hand-over items, leaving an empty row; an upgraded
  server deletes that row at the next join. This erases the recovery record rather than redelivering it.
- **Resolve only interrupted hand-overs, not ones still running.** Before using `/trade pending redeliver`
  or `/trade pending void`, stop hand-overs for that player on every server sharing the database (for
  example, stop those servers, then start only the server used for recovery without letting the player
  join). Check the claiming server's saved player inventory and ender chest first. A redeliver during a
  live hand-over can leave the player with the items and queue them again; the warning that an operator
  resolved the entry meanwhile means you must re-check for a possible duplicate before another join.
  New claims show `<server folder>@<bind IP>:<port>` (for example `survival@*:25565`); when the folder
  cannot be resolved they show only the address. This is a diagnostic label, not a globally unique ID:
  keep distinct server folder names or an external mapping when backends share the same label. Existing
  claims keep the label recorded when they were created.
- **JSON storage has a crash window.** Pending-return changes reach disk at the framework's next flush
  (`datasource.flushRate` seconds, and at shutdown). A crash before that flush can lose a saved return or
  hand the items over twice, even if the player data was saved. SQLite and MySQL write the row at once.
- **An empty-row cleanup failure is not a held return.** Its warning says the row lists no items and
  removal will be retried at a later join; it does not appear in `/trade pending list`. A confirmation
  failure still leaves a held return for the administrator to check (UltiKits/UltiTrade#62).
- **A very long server folder name breaks pending-return claims on MySQL.** The claim marker stored in
  `trade_pending_returns.delivery_token` (`VARCHAR(255)`) includes the server folder name, so a folder name longer
  than about 140 characters makes every claim on that server fail on MySQL. Keep server folder names short
  (UltiKits/UltiTrade#95).
- **A cancel that throws part-way can be retried by the window time limit.** With `window-timeout` set, the expiry
  check retries an expired trade every 20 ticks until it is cancelled. If the cancel ever throws after returning the
  first player's items but before closing the trade (a runtime error inside a Bukkit inventory, world or sound call,
  not ordinary use), each retry returns that player's items again. The console shows an exception; stop
  the server and remove the duplicated staked items from that player (UltiKits/UltiTrade#97).
- **A trade payment that committed and then failed counts as not made.** If the economy plugin's
  withdrawal or deposit throws an error after its storage had already applied it (the connection was lost
  before the answer arrived), the trade treats it as not moved: money can then be created (a deposit:
  the payer is refunded and the payee keeps the payment) or destroyed (a withdrawal: never refunded).
  The console logs a SEVERE line beginning `Outcome unknown:` that names the player and the exact amount;
  check that player's balance for that amount and correct it by hand (UltiKits/UltiTrade#60). Reading the
  balance again cannot settle it, because other plugins or servers may change it too. This is an
  accepted limitation (maintainer 2026-10-08), unit-pinned by `TradeMoneySettlementTest`.
- **Edit `trade_player_settings` by hand only with all sharing servers stopped and with the values the module writes.** Every settings change is written
  only if the stored row still holds what was read (UltiKits/UltiTrade#54). On SQLite a hand-written value in another
  form — `trade_enabled = 'false'` instead of `0`, `total_money_traded = '1.50'` instead of `1.5` — never compares
  equal, so every later change for that player answers "Your trade settings are being saved right now; please try again in a moment." and their
  trade statistics are not counted, until the cell is rewritten as `0`/`1` or a plain number. MySQL compares numbers
  numerically and is not affected.
- **On Paper a failed player-data save is not reported to the module.** Paper's `Player#saveData()` logs
  its own `Failed to save player data for <name>` and returns normally, so the module cannot tell a stake
  hand-over's player save failed (UltiKits/UltiTrade#60).
- **Above about 411,616 experience points (about level 320), Paper's own experience arithmetic drifts.** After a
  trade the sender's remaining experience is rebuilt with `Player#giveExp`, which on Paper can store a few to about
  100 points more or less than given at those totals; the server's `/xp` command behaves the same way. The
  module reads a player's total exactly (UltiKits/UltiTrade#65).
- **服务器在发还保存的押入物品时崩溃，这次发还会被暂扣，交由服主处理。** 交易取消时若服务器找不到一方，其押入物品保存在
  `trade_pending_returns` 中，并在其下次进服时发还。该表记录每次发还的状态：发还前进服会将条目标记为「已占用」（只有一台服务器能成功），
  玩家数据保存完成后再删除条目（UltiKits/UltiTrade#55）。因崩溃（或保存失败）而停留在「已占用」状态的条目不会再自动发还，因为仅凭该表无法判断物品是否已送达。
  服务器启动及每次重载时，控制台会提示此类条目的数量。请先检查玩家背包与末影箱，再用 `/trade pending list`、`/trade pending redeliver <id>`
  （在玩家下次进服时发还，若其正在该服在线则立即发还）或 `/trade pending void <id>`（删除，不发还）逐条处理。这些命令需要 `ultitrade.admin` 权限，可在控制台使用。
  条目被暂扣期间，玩家每次进服都会收到一条提示：有待返还物品正在等待管理员核对（含笔数）。
- **共享同一数据库的所有服务器必须运行同一版本的 UltiTrade。** 请全部停服、升级后再启动：仍运行 UltiKits/UltiTrade#55 之前版本的服务器不认识「已占用」状态，
  会清除暂扣条目的标记与已交付物品记录，只留下空行；升级后的服务器会在玩家下次进服时删除该行。这会抹去恢复记录，而不是重新发还。
- **只处理已中断的发还，不要处理仍在进行的发还。** 使用 `/trade pending redeliver` 或 `/trade pending void` 前，先在所有共享数据库的服务器上停止该玩家的发还
  （例如全部停服，再只启动恢复所用的服务器，期间不让玩家进服），核对占用服务器上保存的玩家背包与末影箱。在发还进行中重新发还，可能让玩家已收到物品又排队再领一次；
  日志提示服主在发还过程中处理了条目时，应在玩家再次进服前复核是否可能重复。新占用记录显示 `<服务器目录名>@<绑定IP>:<端口>`（如 `survival@*:25565`），
  无法解析目录时仅显示地址。这只是诊断标签，不保证全局唯一；请使用不同的服务器目录名，或维护同名后端的外部对照。已有记录保留创建时的标签。
- **JSON 存储存在崩溃窗口。** 待返还记录在框架下次写盘时才落盘（间隔 `datasource.flushRate` 秒，及正常关服时）。此前崩溃可能丢失待返还记录，
  或在玩家数据已保存后再次发还同样的物品；SQLite 与 MySQL 会立即写入行。
- **空条目删除失败不代表暂扣。** 此时警告说明该行不含物品，将在玩家下次进服时重试删除；它不会出现在 `/trade pending list`。确认失败仍会保留暂扣条目，
  需要管理员核对（UltiKits/UltiTrade#62）。
- **服务器目录名过长会使 MySQL 上的待返还占用失败。** 写入 `trade_pending_returns.delivery_token`（`VARCHAR(255)`）的占用标记
  包含服务器目录名；目录名超过约 140 个字符时，该服务器在 MySQL 上的每次占用都会失败。请保持服务器目录名简短（UltiKits/UltiTrade#95）。
- **取消过程中途抛错时，交易窗口时限可能重复执行取消。** 设置 `window-timeout` 后，到期检查每 20 tick 重试一次已到期的交易，直到其被取消。
  若取消在退回第一位玩家的物品之后、关闭交易之前抛出异常（Bukkit 背包、世界或音效调用中的运行时错误，正常使用不会出现），
  每次重试都会再次退回该玩家的物品。控制台会显示异常；请停服并移除该玩家身上重复的交易物品（UltiKits/UltiTrade#97）。
- **已在经济插件中生效、随后才报错的交易付款按未付款处理。** 若经济插件的扣款或付款在其存储已经生效后才抛出错误（应答返回前连接中断），
  交易会按未转移处理：此时可能凭空产生金币（付款：付款方被退款而收款方保留了这笔钱）或使金币消失（扣款：永远不会退还）。
  控制台会记录一条以 `结果未知：` 开头的 SEVERE 日志，写明玩家与精确金额；请检查该玩家的余额是否有这笔金额的变动并手动更正（UltiKits/UltiTrade#60）。
  重新读取余额无法判断，因为其他插件或服务器也可能修改余额。此为已接受的限制（维护者 2026-10-08），由 `TradeMoneySettlementTest` 单元测试固定。
- **手动编辑 `trade_player_settings` 时先停止所有共享数据库的服务器，且只写入本模块会写入的取值形式。** 每次设置修改仅在存储行仍为读取时的值时才写入（UltiKits/UltiTrade#54）。在 SQLite 上，
  以其他形式手写的值（如用 `trade_enabled = 'false'` 代替 `0`，用 `total_money_traded = '1.50'` 代替 `1.5`）永远不会比较相等，该玩家之后的每次修改都会提示
  「你的交易设置正在保存中，请稍后重试」，其交易统计也不会被计入，直到该单元格改写为 `0`/`1` 或普通数字为止。MySQL 按数值比较数字，不受影响。
- **在 Paper 上，玩家数据保存失败不会通知本模块。** Paper 的 `Player#saveData()` 只记录其自身的 `Failed to save player data for <name>` 并正常返回，
  因此本模块无法得知押入物品发还时的玩家数据保存失败（UltiKits/UltiTrade#60）。
- **经验超过约 411,616 点（约 320 级）时，Paper 自身的经验计算存在偏差。** 交易后发送方剩余的经验通过 `Player#giveExp` 重建，
  在这一数量级上 Paper 实际保存的数值可能比给予的多或少数点至约 100 点；服务器自带的 `/xp` 命令也是如此。本模块读取玩家经验总量是精确的（UltiKits/UltiTrade#65）。

## 📜 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献

欢迎提交 Pull Request 或创建 Issue！

## 🔗 相关链接

- [UltiTools-API 文档](https://doc.dev.ultikits.com/)
- [UltiKits 官网](https://www.ultikits.com/)
- [SpigotMC](https://www.spigotmc.org/)
