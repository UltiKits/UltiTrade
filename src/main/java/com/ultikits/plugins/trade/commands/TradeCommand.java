package com.ultikits.plugins.trade.commands;

import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.ultikits.plugins.trade.util.Placeholders;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;
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
    description = "command_description"
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
     * A reply from this module's language file ({@code lang/<language>.yml}), with {@code {PLAYER}}
     * filled in and {@code &} colour codes applied, so it follows the server's {@code language}
     * setting (UltiKits/UltiTrade#16, #17).
     *
     * @param languageText the language file's text, as {@code plugin.i18n(key)} returns it
     * @param playerName   the name substituted for {@code {PLAYER}}
     * @return the text to send
     */
    private static String withPlayer(String languageText, String playerName) {
        return ChatColor.translateAlternateColorCodes('&', languageText.replace("{PLAYER}", playerName));
    }

    /** The language file's text, {@code &} colour codes applied. */
    private static String text(String languageText) {
        return ChatColor.translateAlternateColorCodes('&', languageText);
    }
    
    @CmdMapping(format = "<player>")
    public void sendRequest(@CmdSender Player sender, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(withPlayer(plugin.i18n("player_not_found"), targetName));
            return;
        }
        
        if (target.equals(sender)) {
            sender.sendMessage(text(plugin.i18n("cannot_trade_self")));
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
            player.sendMessage(text(plugin.i18n("not_trading")));
            return;
        }
        tradeService.cancelTrade(player);
    }
    
    @CmdMapping(format = "toggle")
    public void toggle(@CmdSender Player player) {
        boolean newState = logService.toggleTrade(player);
        player.sendMessage(withPlayer(newState
            ? plugin.i18n("trade_toggle_on")
            : plugin.i18n("trade_toggle_off"), player.getName()));
    }
    
    @CmdMapping(format = "block <player>")
    public void blockPlayer(@CmdSender Player player, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Try to block by name even if offline
            player.sendMessage(withPlayer(plugin.i18n("block_player_offline"), targetName));
            return;
        }
        
        if (target.equals(player)) {
            player.sendMessage(text(plugin.i18n("cannot_block_self")));
            return;
        }
        
        if (logService.isBlocked(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(withPlayer(plugin.i18n("already_blocked"), target.getName()));
            return;
        }
        
        logService.blockPlayer(player, target.getUniqueId());
        player.sendMessage(withPlayer(plugin.i18n("block_success"), target.getName()));
        player.sendMessage(text(plugin.i18n("block_success_hint")));
    }
    
    @CmdMapping(format = "unblock <player>")
    public void unblockPlayer(@CmdSender Player player, @CmdParam("player") String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(withPlayer(plugin.i18n("unblock_player_offline"), targetName));
            return;
        }
        
        if (!logService.isBlocked(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(withPlayer(plugin.i18n("not_blocked"), target.getName()));
            return;
        }
        
        logService.unblockPlayer(player, target.getUniqueId());
        player.sendMessage(withPlayer(plugin.i18n("unblock_success"), target.getName()));
    }
    
    /**
     * Lists every hand-over of a saved trade stake that was claimed and never confirmed -- the rows a crash
     * or a failed save left held, which no join hands over (maintainer decision of 2026-10-04,
     * UltiKits/UltiTrade#55). Console-usable; needs {@code ultitrade.admin}.
     */
    @CmdTarget(CmdTarget.CmdTargetType.BOTH)
    @CmdMapping(format = "pending list", permission = "ultitrade.admin")
    public void pendingList(@CmdSender CommandSender sender) {
        List<TradeService.HeldClaim> held = tradeService.heldClaims();
        if (held.isEmpty()) {
            sender.sendMessage(text(plugin.i18n("pending_list_empty")));
            return;
        }
        sender.sendMessage(text(plugin.i18n("pending_list_header").replace("{COUNT}", String.valueOf(held.size()))));
        SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (TradeService.HeldClaim claim : held) {
            sender.sendMessage(text(Placeholders.fill(plugin.i18n("pending_list_entry"),
                "{ID}", claim.getId(),
                "{PLAYER}", claim.getOwnerName(),
                "{UUID}", claim.getOwnerUuid(),
                "{TIME}", time.format(new Date(claim.getClaimedAt())),
                "{SERVER}", claim.getServer(),
                "{ITEMS}", claim.getItems())));
        }
    }

    /** Releases a held hand-over so the owner's next join hands it over (now, if they are online here). */
    @CmdTarget(CmdTarget.CmdTargetType.BOTH)
    @CmdMapping(format = "pending redeliver <id>", permission = "ultitrade.admin")
    public void pendingRedeliver(@CmdSender CommandSender sender, @CmdParam("id") String id) {
        sender.sendMessage(resolution(tradeService.redeliverHeld(id), plugin.i18n("pending_redelivered"), id));
    }

    /** Voids a held hand-over: the entry is removed and its items are not handed over again. */
    @CmdTarget(CmdTarget.CmdTargetType.BOTH)
    @CmdMapping(format = "pending void <id>", permission = "ultitrade.admin")
    public void pendingVoid(@CmdSender CommandSender sender, @CmdParam("id") String id) {
        sender.sendMessage(resolution(tradeService.voidHeld(id), plugin.i18n("pending_voided"), id));
    }

    private String resolution(TradeService.HeldResolution outcome, String doneText, String id) {
        String line = doneText;
        if (outcome == TradeService.HeldResolution.NOT_HELD) {
            line = plugin.i18n("pending_not_held");
        } else if (outcome == TradeService.HeldResolution.FAILED) {
            line = plugin.i18n("pending_resolve_failed");
        }
        return text(line.replace("{ID}", id));
    }

    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        player.sendMessage(text(plugin.i18n("help_header")));
        player.sendMessage(text(plugin.i18n("help_request")));
        player.sendMessage(text(plugin.i18n("help_accept")));
        player.sendMessage(text(plugin.i18n("help_deny")));
        player.sendMessage(text(plugin.i18n("help_cancel")));
        player.sendMessage(text(plugin.i18n("help_toggle")));
        player.sendMessage(text(plugin.i18n("help_block")));
        player.sendMessage(text(plugin.i18n("help_unblock")));
        player.sendMessage("");
        
        // Show current status
        boolean tradeEnabled = logService.isTradeEnabled(player.getUniqueId());
        String status = tradeEnabled ? plugin.i18n("help_status_on") : plugin.i18n("help_status_off");
        player.sendMessage(text(plugin.i18n("help_status").replace("{STATUS}", status)));
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        if (sender instanceof Player) {
            help((Player) sender);
        }
    }
}
