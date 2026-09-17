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
- Unloading this module (`/upm uninstall UltiTrade`, or server shutdown) now runs this module's own
  cleanup (trade service shutdown, PlaceholderAPI expansion unregistration) first, then the
  framework's command unregistration, then its listener unregistration. Previously this module's
  unload method replaced the framework's, so its commands were never unregistered on any unload
  path, and its listeners were not unregistered on `/upm uninstall` (UltiKits/UltiTrade#15).
- 重载本模块（`/ul reload UltiTrade`）现在会重新读取 `config/trade.yml` 并刷新语言文件，修改后的
  `max-distance` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出一行日志，
  这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志（UltiKits/UltiTrade#15）。
- 卸载本模块（`/upm uninstall UltiTrade` 或关闭服务器）现在会先执行本模块自身的清理（关闭交易服务、
  注销 PlaceholderAPI 扩展），再由框架注销命令，最后注销监听器。此前本模块的卸载方法替换了框架的
  卸载方法，因此任何卸载途径都不会注销其命令，`/upm uninstall` 也不会注销其监听器
  （UltiKits/UltiTrade#15）。

### Removed

- The module's own reload console line on `/ul reload UltiTrade` (a Chinese sentence meaning
  "UltiTrade config reloaded!", printed in Chinese under either `language` setting), and the
  never-consulted `trade_reloaded` language key that described it. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiTrade' reloaded.`) (UltiKits/UltiTrade#15).
- 移除本模块在 `/ul reload UltiTrade` 时输出的"UltiTrade 配置已重载！"控制台行，以及未被使用的
  `trade_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志（UltiKits/UltiTrade#15）。
