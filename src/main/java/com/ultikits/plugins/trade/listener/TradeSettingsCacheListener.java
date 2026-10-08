package com.ultikits.plugins.trade.listener;

import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Scopes the trade-settings cache to a player's time on this server (UltiKits/UltiTrade#54; maintainer
 * decision of 2026-10-06 00:04): a join loads the player's stored settings, a quit drops them and
 * writes nothing.
 *
 * @since 1.0.0
 */
@EventListener
public class TradeSettingsCacheListener implements Listener {

    @Autowired
    private TradeLogService logService;

    /** Load the joining player's stored settings into the cache. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        logService.playerJoined(event.getPlayer());
    }

    /**
     * Drop the leaving player from the cache. Runs last, after every other quit handler of this module
     * (a trade cancelled on quit reads nothing from the cache, but nothing should see the entry
     * missing before the player is really gone).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        logService.playerQuit(event.getPlayer().getUniqueId());
    }
}
