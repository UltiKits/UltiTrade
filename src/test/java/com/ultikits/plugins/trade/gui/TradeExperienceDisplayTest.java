package com.ultikits.plugins.trade.gui;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the trade window and the confirm page show for an experience offer under a tax
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a> item 4; maintainer decision
 * 2026-10-06 00:48).
 *
 * <p>Player 1 offers 100 points and player 2 offers 50, at a 29% experience tax. The windows must show what
 * the trade does: the tax is {@link TradeService#experienceTax} (29 of 100, 14 of 50), and what the other
 * side receives is the offer less that tax (71 and 36) -- the amounts {@code completeTrade} moves.
 *
 * <p>The shared fixture's item factory gives items no meta, so lore is not observable under it; these
 * tests use the live test server's own factory, whose metas keep lore (as
 * {@code TradeConfirmPageTest#previewsCarryTheMarker} does).
 */
@DisplayName("The trade windows show an experience offer less its tax (UltiTrade#65 item 4)")
class TradeExperienceDisplayTest {

    private TradeService tradeService;
    private TradeSession session;
    private Player player1;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        tradeService = mock(TradeService.class);
        com.ultikits.plugins.trade.i18n.TradeSeams.speak(tradeService, "zh");
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        when(config.getExpTaxRate()).thenReturn(0.29);
        when(config.getTradeTax()).thenReturn(0.0);
        when(tradeService.getConfig()).thenReturn(config);
        when(tradeService.hasEconomy()).thenReturn(true);
        when(tradeService.getTotalExperience(any())).thenReturn(1000);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        player1 = UltiTradeTestHelper.createMockPlayer("Player1", uuid1);
        Player player2 = UltiTradeTestHelper.createMockPlayer("Player2", uuid2);
        when(Bukkit.getServer().getPlayer(uuid1)).thenReturn(player1);
        when(Bukkit.getServer().getPlayer(uuid2)).thenReturn(player2);
        session = new TradeSession(player1, player2);
        session.setExp(uuid1, 100);
        session.setExp(uuid2, 50);

        // Real metas, so the lore written into each item can be read back.
        doCallRealMethod().when(Bukkit.getServer()).getItemFactory();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private String line(String key, String amount) {
        return ChatColor.translateAlternateColorCodes('&', tradeService.i18n(key).replace("{AMOUNT}", amount));
    }

    /** The lore of the item last put into {@code slot}. */
    private static List<String> loreAt(Inventory inventory, int slot) {
        ArgumentCaptor<ItemStack> item = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, atLeastOnce()).setItem(eq(slot), item.capture());
        ItemStack last = item.getValue();
        assertThat(last.getType()).isEqualTo(Material.EXPERIENCE_BOTTLE);
        assertThat(last.getItemMeta()).as("the live factory gives the item a meta").isNotNull();
        return last.getItemMeta().getLore();
    }

    @Test
    @DisplayName("The trade window's own experience item shows the tax (29) and what the other side receives (100 - 29 = 71)")
    void tradeWindowShowsOfferLessTax() {
        Inventory inventory = Bukkit.createInventory(null, 54, "x");
        clearInvocations(inventory);

        new TradeGUI(tradeService, session, player1);

        List<String> lore = loreAt(inventory, TradeGUI.YOUR_EXP_SLOT);
        assertThat(lore).contains(line("gui_tax", "29"), line("gui_other_receives", "71"));
        assertThat(lore).doesNotContain(line("gui_other_receives", "100"), line("gui_other_receives", "72"));
    }

    @Test
    @DisplayName("The confirm page shows what the other side receives of this player's offer (71) and what this player receives of theirs (50 - 14 = 36)")
    void confirmPageShowsOffersLessTax() {
        Inventory inventory = Bukkit.createInventory(null, TradeConfirmPage.SIZE, "x");
        clearInvocations(inventory);

        new TradeConfirmPage(tradeService, session, player1, () -> { }, () -> { });

        assertThat(loreAt(inventory, TradeConfirmPage.YOUR_EXP_SLOT))
                .contains(line("confirm_other_receives_after_tax", "71"));
        assertThat(loreAt(inventory, TradeConfirmPage.THEIR_EXP_SLOT))
                .contains(line("confirm_you_receive_amount", "36"));
    }

    @Test
    @DisplayName("The confirm page's summary lists this player's experience tax (29) and the experience they receive (36)")
    void confirmPageSummaryShowsTaxAndReceived() {
        Inventory inventory = Bukkit.createInventory(null, TradeConfirmPage.SIZE, "x");
        clearInvocations(inventory);

        new TradeConfirmPage(tradeService, session, player1, () -> { }, () -> { });

        ArgumentCaptor<ItemStack> item = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, atLeastOnce()).setItem(eq(TradeConfirmPage.INFO_SLOT), item.capture());
        List<String> lore = item.getValue().getItemMeta().getLore();
        assertThat(lore).contains(line("confirm_tax_line", "29"), line("confirm_exp_line", "36"));
    }

    @Test
    @DisplayName("A player whose total is at the cap (25,000 levels) sees why experience cannot be offered, not a capped number (UltiTrade#65 F1)")
    void tradeWindowShowsTheCapReasonInsteadOfANumber() {
        when(player1.getLevel()).thenReturn(25_000);
        when(player1.getExpToLevel()).thenReturn(9 * 25_000 - 158);
        when(tradeService.getTotalExperience(player1)).thenReturn(Integer.MAX_VALUE);
        Inventory inventory = Bukkit.createInventory(null, 54, "x");
        clearInvocations(inventory);

        new TradeGUI(tradeService, session, player1);

        List<String> lore = loreAt(inventory, TradeGUI.YOUR_EXP_SLOT);
        assertThat(lore).contains(ChatColor.translateAlternateColorCodes('&', tradeService.i18n("exp_total_unreadable")));
        assertThat(lore).as("no capped number presented as the total")
                .doesNotContain(line("gui_your_total_exp", String.valueOf(Integer.MAX_VALUE)));
    }
}
