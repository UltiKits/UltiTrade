package com.ultikits.plugins.trade.commands;

import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Trade command executor with blacklist and toggle support.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"trade", "t"},
    permission = "ultitrade.use",
    description = "玩家交易系统"
)
public class TradeCommand extends BaseCommandExecutor {
    
    private final UltiToolsPlugin plugin;
    private final TradeService tradeService;
    private final TradeLogService logService;

    public TradeCommand(UltiToolsPlugin plugin, TradeService tradeService, TradeLogService logService) {
        this.plugin = plugin;
        this.tradeService = tradeService;
        this.logService = logService;
    }

    /**
     * A reply from this module's language catalogue ({@code lang/<language>.yml}), with
     * {@code {PLAYER}} filled in and {@code &} colour codes applied. The toggle and blocklist replies
     * come from here so that they follow the server's {@code language} setting; the
     * {@code config/trade.yml} keys that once described them were never read and are removed
     * (UltiKits/UltiTrade#17).
     *
     * @param key        catalogue key
     * @param playerName the name substituted for {@code {PLAYER}}
     * @return the text to send
     */
    private String catalogueMessage(String key, String playerName) {
        return ChatColor.translateAlternateColorCodes('&', plugin.i18n(key).replace("{PLAYER}", playerName));
    }
    
    @CmdMapping(format = "<player>")
    public void sendRequest(@CmdSender Player sender, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "玩家 " + targetName + " 不在线！");
            return;
        }
        
        if (target.equals(sender)) {
            sender.sendMessage(ChatColor.RED + "不能和自己交易！");
            return;
        }
        
        tradeService.sendRequest(sender, target);
    }
    
    @CmdMapping(format = "accept")
    public void accept(@CmdSender Player player) {
        tradeService.acceptRequest(player);
    }
    
    @CmdMapping(format = "deny")
    public void deny(@CmdSender Player player) {
        tradeService.denyRequest(player);
    }
    
    @CmdMapping(format = "cancel")
    public void cancel(@CmdSender Player player) {
        if (!tradeService.isTrading(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你当前没有在交易！");
            return;
        }
        tradeService.cancelTrade(player);
    }
    
    @CmdMapping(format = "toggle")
    public void toggle(@CmdSender Player player) {
        boolean newState = logService.toggleTrade(player);
        player.sendMessage(catalogueMessage(newState ? "trade_toggle_on" : "trade_toggle_off", player.getName()));
    }
    
    @CmdMapping(format = "block <player>")
    public void blockPlayer(@CmdSender Player player, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Try to block by name even if offline
            player.sendMessage(ChatColor.RED + "玩家 " + targetName + " 不在线！无法添加到黑名单。");
            return;
        }
        
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "不能将自己添加到黑名单！");
            return;
        }
        
        if (logService.isBlocked(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(catalogueMessage("already_blocked", target.getName()));
            return;
        }
        
        logService.blockPlayer(player, target.getUniqueId());
        player.sendMessage(catalogueMessage("block_success", target.getName()));
        player.sendMessage(ChatColor.GRAY + "该玩家将无法向你发送交易请求。");
    }
    
    @CmdMapping(format = "unblock <player>")
    public void unblockPlayer(@CmdSender Player player, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "玩家 " + targetName + " 不在线！无法从黑名单移除。");
            return;
        }
        
        if (!logService.isBlocked(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(catalogueMessage("not_blocked", target.getName()));
            return;
        }
        
        logService.unblockPlayer(player, target.getUniqueId());
        player.sendMessage(catalogueMessage("unblock_success", target.getName()));
    }
    
    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        player.sendMessage(ChatColor.GOLD + "=== UltiTrade 帮助 ===");
        player.sendMessage(ChatColor.YELLOW + "/trade <玩家>" + ChatColor.WHITE + " - 发起交易请求");
        player.sendMessage(ChatColor.YELLOW + "/trade accept" + ChatColor.WHITE + " - 接受交易请求");
        player.sendMessage(ChatColor.YELLOW + "/trade deny" + ChatColor.WHITE + " - 拒绝交易请求");
        player.sendMessage(ChatColor.YELLOW + "/trade cancel" + ChatColor.WHITE + " - 取消当前交易");
        player.sendMessage(ChatColor.YELLOW + "/trade toggle" + ChatColor.WHITE + " - 开启/关闭交易功能");
        player.sendMessage(ChatColor.YELLOW + "/trade block <玩家>" + ChatColor.WHITE + " - 屏蔽指定玩家");
        player.sendMessage(ChatColor.YELLOW + "/trade unblock <玩家>" + ChatColor.WHITE + " - 取消屏蔽玩家");
        player.sendMessage("");
        
        // Show current status
        boolean tradeEnabled = logService.isTradeEnabled(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "交易状态: " + 
            (tradeEnabled ? ChatColor.GREEN + "已开启" : ChatColor.RED + "已关闭"));
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        if (sender instanceof Player) {
            help((Player) sender);
        }
    }
}
