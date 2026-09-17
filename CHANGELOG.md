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
  restart (UltiKits/UltiTrade#26).
- `/upm uninstall UltiTrade` now runs this module's own cleanup (trade service shutdown,
  PlaceholderAPI expansion unregistration) first, then the framework's command unregistration, then
  its listener unregistration, so after the uninstall the module's commands are really removed and
  its listeners stop firing. Previously this module's unload method replaced the framework's, so both
  its commands and its listeners stayed active until the server restarted (UltiKits/UltiTrade#15).
- 重载本模块（`/ul reload UltiTrade`）现在会重新读取 `config/trade.yml` 并刷新语言文件，修改后的
  `max-distance` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出一行日志，
  这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志（UltiKits/UltiTrade#15）。
- `/ul reload UltiTrade` 现在还会应用 `enable-money-trade`、`enable-trade-log` 和 `cleanup-interval-hours`。
  本模块新增的重载钩子会重新查找 Vault 经济提供者，使开启或关闭金币交易无需重启即可生效；并重新调度旧日志
  清理任务，使关闭 `enable-trade-log` 后该任务停止、开启后启动一个任务、修改间隔后以新间隔替换。此前经济
  提供者查找与清理任务只在模块启动时设置，因此开启金币交易以及对清理任务的任何修改都要重启后才生效
  （UltiKits/UltiTrade#26）。
- `/upm uninstall UltiTrade` 现在会先执行本模块自身的清理（关闭交易服务、注销 PlaceholderAPI 扩展），
  再由框架注销命令，最后注销监听器，因此卸载后本模块的命令会被真正移除，其监听器也不再触发。此前本模块
  的卸载方法替换了框架的卸载方法，因此其命令和监听器都会一直保持生效，直到服务器重启
  （UltiKits/UltiTrade#15）。

### Removed

- The module's own reload console line on `/ul reload UltiTrade` (a Chinese sentence meaning
  "UltiTrade config reloaded!", printed in Chinese under either `language` setting), and the
  never-consulted `trade_reloaded` language key that described it. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiTrade' reloaded.`) (UltiKits/UltiTrade#15).
- 移除本模块在 `/ul reload UltiTrade` 时输出的"UltiTrade 配置已重载！"控制台行，以及未被使用的
  `trade_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志（UltiKits/UltiTrade#15）。
