# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading or reloading this module (`/ul reload UltiTrade`, or disabling the module) now
  actually runs the framework's own reload/unload steps (config reload, language refresh,
  `@ConditionalOnConfig` drift reporting, command/listener cleanup) before this module's own
  cleanup work (trade service shutdown and PlaceholderAPI expansion unregistration) —
  previously these framework steps were silently skipped (UltiKits/UltiTrade#15).
- 卸载或重载本模块（`/ul reload UltiTrade`，或禁用本模块）现在会先真正执行框架自身的重载/卸载步骤
  （配置重载、语言刷新、`@ConditionalOnConfig` 漂移报告、命令/监听器清理），再执行本模块自身的清理工作
  （关闭交易服务、注销 PlaceholderAPI 扩展）——此前这些框架步骤会被静默跳过（UltiKits/UltiTrade#15）。

### Removed

- The module's own reload console line on `/ul reload UltiTrade` (a Chinese sentence meaning
  "UltiTrade config reloaded!", printed in Chinese under either `language` setting), and the
  never-consulted `trade_reloaded` language key that described it. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiTrade' reloaded.`) (UltiKits/UltiTrade#15).
- 移除本模块在 `/ul reload UltiTrade` 时输出的"UltiTrade 配置已重载！"控制台行，以及未被使用的
  `trade_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志（UltiKits/UltiTrade#15）。
