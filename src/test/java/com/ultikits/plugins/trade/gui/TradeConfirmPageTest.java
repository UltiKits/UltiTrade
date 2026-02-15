package com.ultikits.plugins.trade.gui;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TradeConfirmPage Tests")
class TradeConfirmPageTest {

    private TradeService tradeService;
    private TradeConfig config;
    private TradeSession session;
    private Player player1;
    private Player player2;
    private UUID uuid1;
    private UUID uuid2;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();

        tradeService = mock(TradeService.class);
        config = UltiTradeTestHelper.createDefaultConfig();
        when(tradeService.getConfig()).thenReturn(config);

        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        player1 = UltiTradeTestHelper.createMockPlayer("Player1", uuid1);
        player2 = UltiTradeTestHelper.createMockPlayer("Player2", uuid2);

        session = new TradeSession(player1, player2);

        org.bukkit.Server server = Bukkit.getServer();
        when(server.getPlayer(uuid1)).thenReturn(player1);
        when(server.getPlayer(uuid2)).thenReturn(player2);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create confirm page instance")
        void createInstance() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should create inventory")
        void createInventory() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page.getInventory()).isNotNull();
        }

        @Test
        @DisplayName("Should set viewer")
        void setViewer() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page.getViewer()).isSameAs(player1);
        }

        @Test
        @DisplayName("Should handle null other player")
        void handleNullOtherPlayer() {
            org.bukkit.Server server = Bukkit.getServer();
            when(server.getPlayer(uuid2)).thenReturn(null);

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should initialize with money and exp data")
        void initWithTradeData() {
            session.setMoney(uuid1, 500.0);
            session.setMoney(uuid2, 300.0);
            session.setExp(uuid1, 100);
            session.setExp(uuid2, 200);

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should initialize with tax rates")
        void initWithTax() {
            when(config.getTradeTax()).thenReturn(0.1);
            when(config.getExpTaxRate()).thenReturn(0.05);
            session.setMoney(uuid1, 1000.0);
            session.setExp(uuid1, 500);

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should initialize with items")
        void initWithItems() {
            session.setItem(uuid1, 0, new ItemStack(Material.DIAMOND, 10));
            session.setItem(uuid1, 1, new ItemStack(Material.GOLD_INGOT, 5));
            session.setItem(uuid1, 2, new ItemStack(Material.EMERALD, 3));
            session.setItem(uuid2, 0, new ItemStack(Material.IRON_INGOT, 20));

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should handle more than 3 items in display")
        void initWithManyItems() {
            session.setItem(uuid1, 0, new ItemStack(Material.DIAMOND, 10));
            session.setItem(uuid1, 1, new ItemStack(Material.GOLD_INGOT, 5));
            session.setItem(uuid1, 2, new ItemStack(Material.EMERALD, 3));
            session.setItem(uuid1, 3, new ItemStack(Material.IRON_INGOT, 20));

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should handle zero tax on money")
        void initZeroTaxMoney() {
            when(config.getTradeTax()).thenReturn(0.0);
            session.setMoney(uuid1, 500.0);

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }

        @Test
        @DisplayName("Should handle zero tax on exp")
        void initZeroTaxExp() {
            when(config.getExpTaxRate()).thenReturn(0.0);
            session.setExp(uuid1, 500);

            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1, () -> {}, () -> {});
            assertThat(page).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleClick")
    class HandleClick {

        @Test
        @DisplayName("Should run onConfirm on confirm slot click")
        void confirmSlotClick() {
            AtomicBoolean confirmed = new AtomicBoolean(false);
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> confirmed.set(true), () -> {});

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(TradeConfirmPage.CONFIRM_SLOT);

            page.handleClick(event);

            verify(event).setCancelled(true);
            verify(player1).closeInventory();
            assertThat(confirmed.get()).isTrue();
        }

        @Test
        @DisplayName("Should run onCancel on cancel slot click")
        void cancelSlotClick() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> cancelled.set(true));

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(TradeConfirmPage.CANCEL_SLOT);

            page.handleClick(event);

            verify(event).setCancelled(true);
            verify(player1).closeInventory();
            assertThat(cancelled.get()).isTrue();
        }

        @Test
        @DisplayName("Should cancel event for non-button slot click")
        void otherSlotClick() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> {});

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(0); // Background slot

            page.handleClick(event);

            verify(event).setCancelled(true);
            verify(player1, never()).closeInventory();
        }

        @Test
        @DisplayName("Should handle null onConfirm callback")
        void nullOnConfirm() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    null, () -> {});

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(TradeConfirmPage.CONFIRM_SLOT);

            // Should not throw
            page.handleClick(event);

            verify(player1).closeInventory();
        }

        @Test
        @DisplayName("Should handle null onCancel callback")
        void nullOnCancel() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, null);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(TradeConfirmPage.CANCEL_SLOT);

            // Should not throw
            page.handleClick(event);

            verify(player1).closeInventory();
        }

        @Test
        @DisplayName("Should handle info slot click")
        void infoSlotClick() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> {});

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(TradeConfirmPage.INFO_SLOT);

            page.handleClick(event);

            verify(event).setCancelled(true);
            verify(player1, never()).closeInventory();
        }
    }

    @Nested
    @DisplayName("open")
    class Open {

        @Test
        @DisplayName("Should open inventory for viewer")
        void openInventory() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> {});

            page.open();

            verify(player1).openInventory(page.getInventory());
        }
    }

    @Nested
    @DisplayName("Static Constants")
    class StaticConstants {

        @Test
        @DisplayName("Should have correct size")
        void size() {
            assertThat(TradeConfirmPage.SIZE).isEqualTo(TradeConfirmPage.ROWS * 9);
            assertThat(TradeConfirmPage.ROWS).isEqualTo(5);
        }

        @Test
        @DisplayName("Should have correct slot positions")
        void slotPositions() {
            assertThat(TradeConfirmPage.CONFIRM_SLOT).isEqualTo(38);
            assertThat(TradeConfirmPage.CANCEL_SLOT).isEqualTo(42);
            assertThat(TradeConfirmPage.INFO_SLOT).isEqualTo(13);
            assertThat(TradeConfirmPage.YOUR_ITEMS_START).isEqualTo(10);
            assertThat(TradeConfirmPage.THEIR_ITEMS_START).isEqualTo(14);
            assertThat(TradeConfirmPage.YOUR_MONEY_SLOT).isEqualTo(28);
            assertThat(TradeConfirmPage.YOUR_EXP_SLOT).isEqualTo(29);
            assertThat(TradeConfirmPage.THEIR_MONEY_SLOT).isEqualTo(32);
            assertThat(TradeConfirmPage.THEIR_EXP_SLOT).isEqualTo(33);
        }
    }

    @Nested
    @DisplayName("InventoryHolder Implementation")
    class InventoryHolderImpl {

        @Test
        @DisplayName("getInventory should return non-null inventory")
        void getInventory() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> {});
            assertThat(page.getInventory()).isNotNull();
        }

        @Test
        @DisplayName("Should implement InventoryHolder")
        void implementsInventoryHolder() {
            TradeConfirmPage page = new TradeConfirmPage(tradeService, session, player1,
                    () -> {}, () -> {});
            assertThat(page).isInstanceOf(org.bukkit.inventory.InventoryHolder.class);
        }
    }
}
