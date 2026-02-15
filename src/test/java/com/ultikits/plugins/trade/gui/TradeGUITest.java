package com.ultikits.plugins.trade.gui;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TradeGUI Tests")
class TradeGUITest {

    private TradeService tradeService;
    private TradeConfig config;
    private TradeSession session;
    private Player player1;
    private Player player2;
    private UUID uuid1;
    private UUID uuid2;
    private TradeGUI gui;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();

        tradeService = mock(TradeService.class);
        config = UltiTradeTestHelper.createDefaultConfig();
        when(tradeService.getConfig()).thenReturn(config);
        when(tradeService.hasEconomy()).thenReturn(true);

        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        player1 = UltiTradeTestHelper.createMockPlayer("Player1", uuid1);
        player2 = UltiTradeTestHelper.createMockPlayer("Player2", uuid2);

        session = new TradeSession(player1, player2);

        org.bukkit.Server server = Bukkit.getServer();
        when(server.getPlayer(uuid1)).thenReturn(player1);
        when(server.getPlayer(uuid2)).thenReturn(player2);

        gui = new TradeGUI(tradeService, session, player1);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create GUI instance")
        void createInstance() {
            assertThat(gui).isNotNull();
        }

        @Test
        @DisplayName("Should set session")
        void setSession() {
            assertThat(gui.getSession()).isSameAs(session);
        }

        @Test
        @DisplayName("Should set viewer")
        void setViewer() {
            assertThat(gui.getViewer()).isSameAs(player1);
        }

        @Test
        @DisplayName("Should set trade service")
        void setTradeService() {
            assertThat(gui.getTradeService()).isSameAs(tradeService);
        }

        @Test
        @DisplayName("Should create inventory")
        void createInventory() {
            assertThat(gui.getInventory()).isNotNull();
        }

        @Test
        @DisplayName("Should handle null other player name")
        void handleNullOtherPlayer() {
            org.bukkit.Server server = Bukkit.getServer();
            when(server.getPlayer(uuid2)).thenReturn(null);

            TradeGUI guiNull = new TradeGUI(tradeService, session, player1);
            assertThat(guiNull).isNotNull();
        }
    }

    @Nested
    @DisplayName("Slot Identification")
    class SlotIdentification {

        @Test
        @DisplayName("isYourSlot should return true for your slots")
        void isYourSlotTrue() {
            for (int slot : TradeGUI.YOUR_SLOTS) {
                assertThat(gui.isYourSlot(slot)).isTrue();
            }
        }

        @Test
        @DisplayName("isYourSlot should return false for their slots")
        void isYourSlotFalseForTheirs() {
            for (int slot : TradeGUI.THEIR_SLOTS) {
                assertThat(gui.isYourSlot(slot)).isFalse();
            }
        }

        @Test
        @DisplayName("isYourSlot should return false for separator slots")
        void isYourSlotFalseForSeparator() {
            for (int slot : TradeGUI.SEPARATOR_SLOTS) {
                assertThat(gui.isYourSlot(slot)).isFalse();
            }
        }

        @Test
        @DisplayName("isYourSlot should return false for button slots")
        void isYourSlotFalseForButtons() {
            assertThat(gui.isYourSlot(TradeGUI.CONFIRM_SLOT)).isFalse();
            assertThat(gui.isYourSlot(TradeGUI.CANCEL_SLOT)).isFalse();
        }

        @Test
        @DisplayName("isMoneySlot should return true for money slot")
        void isMoneySlotTrue() {
            assertThat(gui.isMoneySlot(TradeGUI.YOUR_MONEY_SLOT)).isTrue();
        }

        @Test
        @DisplayName("isMoneySlot should return false for other slots")
        void isMoneySlotFalse() {
            assertThat(gui.isMoneySlot(0)).isFalse();
            assertThat(gui.isMoneySlot(TradeGUI.THEIR_MONEY_SLOT)).isFalse();
            assertThat(gui.isMoneySlot(TradeGUI.YOUR_EXP_SLOT)).isFalse();
        }

        @Test
        @DisplayName("isExpSlot should return true for exp slot")
        void isExpSlotTrue() {
            assertThat(gui.isExpSlot(TradeGUI.YOUR_EXP_SLOT)).isTrue();
        }

        @Test
        @DisplayName("isExpSlot should return false for other slots")
        void isExpSlotFalse() {
            assertThat(gui.isExpSlot(0)).isFalse();
            assertThat(gui.isExpSlot(TradeGUI.THEIR_EXP_SLOT)).isFalse();
            assertThat(gui.isExpSlot(TradeGUI.YOUR_MONEY_SLOT)).isFalse();
        }
    }

    @Nested
    @DisplayName("Item Index Mapping")
    class ItemIndexMapping {

        @Test
        @DisplayName("getItemIndex should return correct index for your slots")
        void getItemIndex() {
            for (int i = 0; i < TradeGUI.YOUR_SLOTS.length; i++) {
                assertThat(gui.getItemIndex(TradeGUI.YOUR_SLOTS[i])).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("getItemIndex should return -1 for non-your slot")
        void getItemIndexInvalid() {
            assertThat(gui.getItemIndex(TradeGUI.THEIR_SLOTS[0])).isEqualTo(-1);
            assertThat(gui.getItemIndex(TradeGUI.CONFIRM_SLOT)).isEqualTo(-1);
            assertThat(gui.getItemIndex(99)).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("Sound Effects")
    class SoundEffects {

        @Test
        @DisplayName("playItemSound should play pling sound")
        void playItemSound() {
            when(config.isEnableSounds()).thenReturn(true);

            gui.playItemSound();

            verify(tradeService).playSound(player1, Sound.BLOCK_NOTE_BLOCK_PLING);
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("update should not throw for empty session")
        void updateEmpty() {
            // Should not throw
            gui.update();
        }

        @Test
        @DisplayName("update should not throw with items in session")
        void updateWithItems() {
            session.setItem(uuid1, 0, new ItemStack(Material.DIAMOND, 10));
            session.setItem(uuid2, 0, new ItemStack(Material.GOLD_INGOT, 5));

            // Should not throw
            gui.update();
        }

        @Test
        @DisplayName("update should not throw with money set")
        void updateWithMoney() {
            session.setMoney(uuid1, 100.0);
            session.setMoney(uuid2, 200.0);

            gui.update();
        }

        @Test
        @DisplayName("update should not throw with exp set")
        void updateWithExp() {
            when(tradeService.getTotalExperience(player1)).thenReturn(500);
            session.setExp(uuid1, 100);
            session.setExp(uuid2, 200);

            gui.update();
        }

        @Test
        @DisplayName("update should handle economy disabled")
        void updateEconomyDisabled() {
            when(tradeService.hasEconomy()).thenReturn(false);

            gui.update();
        }

        @Test
        @DisplayName("update should handle exp trade disabled")
        void updateExpDisabled() {
            when(config.isEnableExpTrade()).thenReturn(false);

            gui.update();
        }

        @Test
        @DisplayName("update should handle confirmed state")
        void updateWithConfirmation() {
            session.setConfirmed(uuid1, true);

            gui.update();
        }

        @Test
        @DisplayName("update should handle both confirmed")
        void updateBothConfirmed() {
            session.setConfirmed(uuid1, true);
            session.setConfirmed(uuid2, true);

            gui.update();
        }

        @Test
        @DisplayName("update should handle tax display")
        void updateWithTax() {
            when(config.getTradeTax()).thenReturn(0.1);
            when(config.getExpTaxRate()).thenReturn(0.05);
            session.setMoney(uuid1, 100.0);
            session.setExp(uuid1, 50);

            gui.update();
        }
    }

    @Nested
    @DisplayName("Static Constants")
    class StaticConstants {

        @Test
        @DisplayName("YOUR_SLOTS should have 16 slots")
        void yourSlotsLength() {
            assertThat(TradeGUI.YOUR_SLOTS).hasSize(16);
        }

        @Test
        @DisplayName("THEIR_SLOTS should have 16 slots")
        void theirSlotsLength() {
            assertThat(TradeGUI.THEIR_SLOTS).hasSize(16);
        }

        @Test
        @DisplayName("SEPARATOR_SLOTS should have 5 slots")
        void separatorSlotsLength() {
            assertThat(TradeGUI.SEPARATOR_SLOTS).hasSize(5);
        }

        @Test
        @DisplayName("Slot constants should have expected values")
        void slotConstants() {
            assertThat(TradeGUI.CONFIRM_SLOT).isEqualTo(49);
            assertThat(TradeGUI.CANCEL_SLOT).isEqualTo(45);
            assertThat(TradeGUI.YOUR_MONEY_SLOT).isEqualTo(36);
            assertThat(TradeGUI.THEIR_MONEY_SLOT).isEqualTo(44);
            assertThat(TradeGUI.YOUR_EXP_SLOT).isEqualTo(38);
            assertThat(TradeGUI.THEIR_EXP_SLOT).isEqualTo(42);
            assertThat(TradeGUI.YOUR_STATUS_SLOT).isEqualTo(37);
            assertThat(TradeGUI.THEIR_STATUS_SLOT).isEqualTo(43);
        }
    }

    @Nested
    @DisplayName("InventoryHolder Implementation")
    class InventoryHolderImpl {

        @Test
        @DisplayName("getInventory should return non-null inventory")
        void getInventory() {
            assertThat(gui.getInventory()).isNotNull();
        }

        @Test
        @DisplayName("GUI should implement InventoryHolder")
        void implementsInventoryHolder() {
            assertThat(gui).isInstanceOf(org.bukkit.inventory.InventoryHolder.class);
        }
    }
}
