# UltiTrade

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.x-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-green)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)

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
   - [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) (必需)
   - [Vault](https://www.spigotmc.org/resources/vault.34315/) (金币交易)
   - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (可选)

2. 将 `UltiTrade.jar` 放入 `plugins/UltiTools/plugins/` 目录

3. 重启服务器或使用 `/ultitools reload`

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

```yaml
# 基础设置
requestTimeout: 30           # 交易请求超时时间（秒）
maxDistance: 50              # 最大交易距离（-1 无限制）
allowCrossWorld: false       # 是否允许跨世界交易

# 交易功能
enableMoneyTrade: true       # 启用金币交易
enableExpTrade: true         # 启用经验交易
enableShiftClick: true       # 启用 Shift+右键发起交易

# 税收设置
tradeTax: 0.0                # 金币交易税率（0-1）
expTaxRate: 0.0              # 经验交易税率（0-1）

# 大额交易确认
confirmThreshold: 10000      # 确认阈值（金币或经验）

# 日志设置
enableTradeLog: true         # 启用交易日志
logRetentionDays: 30         # 日志保留天数
cleanupIntervalHours: 24     # 清理间隔（小时）

# 效果设置
enableSounds: true           # 启用音效
enableParticles: true        # 启用粒子效果
enableBossbar: true          # 启用 BossBar 倒计时
enableClickableButtons: true # 启用聊天可点击按钮
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

### v2.0.0
- 新增经验交易功能
- 新增玩家黑名单系统
- 新增交易开关功能
- 新增 BossBar 请求倒计时
- 新增可点击聊天按钮
- 新增 Shift+右键快捷交易
- 新增大额交易确认机制
- 新增交易日志系统
- 新增 PlaceholderAPI 集成
- 新增音效和粒子特效
- 新增物品详情悬浮预览
- 优化 GUI 界面布局
- 优化代码结构，使用 UltiTools-API 框架

### v1.0.0
- 初始版本
- 基础物品和金币交易功能

## 🔧 技术架构

- **框架**: UltiTools-API 6.2.1+
- **注解驱动**: `@Service`, `@Autowired`, `@CmdMapping`, `@Table`, `@Scheduled`
- **数据持久化**: Query DSL + DataOperator ORM
- **依赖注入**: UltiTools IoC 容器
- **配置验证**: `@Range`, `@NotEmpty` validation annotations

### UltiTools-API 6.2.0 新特性应用

本插件已完全迁移到 UltiTools-API 6.2.0 的现代化模式：

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
  state and can hand over an entry that an upgraded server holds.
- **A trade payment that committed and then failed counts as not made.** If the economy plugin's
  withdrawal or deposit throws an error after its storage had already applied it (the connection was lost
  before the answer arrived), the trade treats it as not moved: money can then be created (a deposit:
  the payer is refunded and the payee keeps the payment) or destroyed (a withdrawal: never refunded).
  The console logs a SEVERE line beginning `Outcome unknown:` that names the player and the exact amount;
  check that player's balance for that amount and correct it by hand (UltiKits/UltiTrade#60). Reading the
  balance again cannot settle it, because other plugins or servers may change it too. This is an
  accepted limitation (maintainer 2026-10-08), unit-pinned by `TradeMoneySettlementTest`.
- **Edit `trade_player_settings` by hand only with the values the module writes.** Every settings change is written
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
  可能发还已被暂扣的条目。
- **已在经济插件中生效、随后才报错的交易付款按未付款处理。** 若经济插件的扣款或付款在其存储已经生效后才抛出错误（应答返回前连接中断），
  交易会按未转移处理：此时可能凭空产生金币（付款：付款方被退款而收款方保留了这笔钱）或使金币消失（扣款：永远不会退还）。
  控制台会记录一条以 `结果未知：` 开头的 SEVERE 日志，写明玩家与精确金额；请检查该玩家的余额是否有这笔金额的变动并手动更正（UltiKits/UltiTrade#60）。
  重新读取余额无法判断，因为其他插件或服务器也可能修改余额。此为已接受的限制（维护者 2026-10-08），由 `TradeMoneySettlementTest` 单元测试固定。
- **手动编辑 `trade_player_settings` 时只写入本模块会写入的取值形式。** 每次设置修改仅在存储行仍为读取时的值时才写入（UltiKits/UltiTrade#54）。在 SQLite 上，
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
