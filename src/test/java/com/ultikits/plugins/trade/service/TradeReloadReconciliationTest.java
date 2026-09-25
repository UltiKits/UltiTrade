package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeRequest;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UltiKits/UltiTrade#26: {@code /ul reload UltiTrade} re-initialises the same {@link TradeConfig}
 * instance in place and then calls the module's {@code onReload()} hook. Three keys are captured
 * into service state when the services start ({@code enable-money-trade} through the Vault
 * economy lookup, {@code enable-trade-log} and {@code cleanup-interval-hours} through the cleanup
 * task), so the hook must reconcile both services with the reloaded values.
 * <p>
 * Each test edits {@code config/trade.yml}, re-runs {@code TradeConfig#init} on the same instance
 * (what the framework's config reload does), then invokes the module's {@code onReload()} through
 * {@link UltiToolsPlugin}, the way the framework's {@code reloadSelf()} does.
 */
@DisplayName("onReload reconciles init-captured configuration (UltiKits/UltiTrade#26)")
class TradeReloadReconciliationTest {

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiTrade plugin;
    private TradeConfig config;
    private TradeService tradeService;
    private TradeLogService logService;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        configFile = moduleFolder.resolve("config").resolve("trade.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();

        plugin = mock(UltiTrade.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());
        config = new TradeConfig();
        // No language is loaded and no framework instance runs in a unit test: answer i18n from the
        // Chinese file this module ships, and hand the module its own configuration, as the framework
        // does (UltiKits/UltiTrade#16)
        doAnswer(com.ultikits.plugins.trade.i18n.CatalogueText.answer("zh")).when(plugin).i18n(anyString());
        doReturn(config).when(plugin).getConfig(TradeConfig.class);

        tradeService = new TradeService();
        UltiTradeTestHelper.setField(tradeService, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(tradeService, "config", config);

        logService = new TradeLogService();
        UltiTradeTestHelper.setField(logService, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(logService, "config", config);
        // TradeLogService#init always resolves this before any reload can reach the service; without it
        // the service looks like one belonging to a disabled plugin, which production code now treats as
        // the shutdown path (UltiKits/UltiTrade#34).
        UltiTradeTestHelper.setField(logService, "bukkitPlugin",
                org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

        SimpleContainer context = mock(SimpleContainer.class);
        when(context.getBean(TradeService.class)).thenReturn(tradeService);
        when(context.getBean(TradeLogService.class)).thenReturn(logService);
        doReturn(context).when(plugin).getContext();
        doReturn(UltiTradeTestHelper.getMockLogger()).when(plugin).getLogger();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("enable-money-trade")
    class MoneyTrade {

        private Economy vaultEconomy;
        private Server server;

        @BeforeEach
        void registerVaultProvider() {
            server = Bukkit.getServer();
            vaultEconomy = mock(Economy.class);
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            stubProvider(server, vaultEconomy);
        }

        @Test
        @DisplayName("false at startup, then true and reload: money trading becomes available without a restart")
        void falseToTrueEnablesMoneyTrade() throws Exception {
            loadConfig("enable-money-trade: false\n");
            tradeService.init();
            // Precondition is verifiably false at boot: no lookup happened and money trade is off.
            verify(server.getServicesManager(), never()).getRegistration(any());
            assertThat(tradeService.hasEconomy()).isFalse();

            reload("enable-money-trade: true\n");

            assertThat(tradeService.hasEconomy()).isTrue();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
        }

        @Test
        @DisplayName("true at startup, then false and reload: money trading stops and the provider is dropped")
        void trueToFalseDisablesMoneyTrade() throws Exception {
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.hasEconomy()).isTrue();

            reload("enable-money-trade: false\n");

            assertThat(tradeService.hasEconomy()).isFalse();
            assertThat(tradeService.getEconomy()).isNull();
        }

        @Test
        @DisplayName("false, true, false, true across reloads ends with the provider registered at the last lookup")
        void repeatedTogglesStayConsistent() throws Exception {
            Economy secondProvider = mock(Economy.class);
            loadConfig("enable-money-trade: false\n");
            tradeService.init();

            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
            reload("enable-money-trade: false\n");
            assertThat(tradeService.hasEconomy()).isFalse();
            stubProvider(server, secondProvider);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.hasEconomy()).isTrue();
            assertThat(tradeService.getEconomy()).isSameAs(secondProvider);
        }

        @Test
        @DisplayName("true stays true while the provider is replaced: the reload adopts the new provider")
        void unchangedKeyAdoptsReplacedProvider() throws Exception {
            Economy replacement = mock(Economy.class);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            stubProvider(server, replacement);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.getEconomy()).isSameAs(replacement);
        }

        @Test
        @DisplayName("provider present, then absent, then a different one: each reload holds exactly what the lookup found")
        void providerPresentAbsentDifferent() throws Exception {
            Economy different = mock(Economy.class);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            when(server.getServicesManager().getRegistration(Economy.class)).thenReturn(null);
            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isNull();
            assertThat(tradeService.hasEconomy()).isFalse();

            stubProvider(server, different);
            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isSameAs(different);
            assertThat(tradeService.hasEconomy()).isTrue();
        }

        @Test
        @DisplayName("Vault removed while money trading stays on: the held provider is dropped and the warning is logged")
        void vaultGoneDropsProvider() throws Exception {
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            when(server.getPluginManager().getPlugin("Vault")).thenReturn(null);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.getEconomy()).isNull();
            verify(UltiTradeTestHelper.getMockLogger()).warn(zhLine("log_vault_missing"));
        }

        @Test
        @DisplayName("money trading on and Vault present without a provider: the reload logs that money trading is disabled")
        void noProviderIsReported() throws Exception {
            when(server.getServicesManager().getRegistration(Economy.class)).thenReturn(null);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();

            reload("enable-money-trade: true\n");

            verify(UltiTradeTestHelper.getMockLogger(), times(2))
                    .warn(zhLine("log_no_economy_provider"));
        }

        @Test
        @DisplayName("money trading off: a reload performs no lookup and logs nothing, whether or not Vault exists")
        void moneyTradeOffLogsNothing() throws Exception {
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(null);
            loadConfig("enable-money-trade: false\n");
            tradeService.init();

            reload("enable-money-trade: false\n");

            verify(server.getServicesManager(), never()).getRegistration(any());
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(anyString());
        }
    }

    @Nested
    @DisplayName("a pending trade request keeps the timeout it was sent with across a reload (UltiKits/UltiTrade#26)")
    class PendingRequestTimeout {

        private Server server;
        private Player sender;
        private Player receiver;
        private BossBar bar;
        private BukkitScheduler scheduler;

        @BeforeEach
        void players() throws Exception {
            server = Bukkit.getServer();
            TradeLogService requestLog = mock(TradeLogService.class);
            when(requestLog.isTradeEnabled(any())).thenReturn(true);
            when(requestLog.isBlocked(any(), any())).thenReturn(false);
            UltiTradeTestHelper.setField(tradeService, "logService", requestLog);
            sender = UltiTradeTestHelper.createMockPlayer("Sender", UUID.randomUUID());
            receiver = UltiTradeTestHelper.createMockPlayer("Receiver", UUID.randomUUID());
            UUID senderId = sender.getUniqueId();
            UUID receiverId = receiver.getUniqueId();
            doReturn(sender).when(server).getPlayer(senderId);
            doReturn(receiver).when(server).getPlayer(receiverId);
            bar = mock(BossBar.class);
            doReturn(bar).when(server).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class));
            scheduler = server.getScheduler();
        }

        private Runnable sendRequestWithTimeout(int seconds) throws Exception {
            loadConfig("max-distance: 0\nrequest-timeout: " + seconds + "\n");
            assertThat(tradeService.sendRequest(sender, receiver)).isTrue();
            ArgumentCaptor<Runnable> countdown = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(any(), countdown.capture(), anyLong(), anyLong());
            return countdown.getValue();
        }

        @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // ages the request without waiting in real time
        private void ageRequest(long millis) throws Exception {
            Map<UUID, TradeRequest> pending = UltiTradeTestHelper.getField(tradeService, "pendingRequests");
            TradeRequest request = pending.get(receiver.getUniqueId());
            Field timestamp = TradeRequest.class.getDeclaredField("timestamp");
            timestamp.setAccessible(true);
            timestamp.set(request, System.currentTimeMillis() - millis);
        }

        @Test
        @DisplayName("request-timeout 30 reloaded to 5: the bar keeps counting down from 30 and never gets a progress above 1.0")
        void barKeepsOriginalTimeoutWhenLowered() throws Exception {
            Runnable countdown = sendRequestWithTimeout(30);

            reload("max-distance: 0\nrequest-timeout: 5\n");
            countdown.run();

            ArgumentCaptor<Double> progress = ArgumentCaptor.forClass(Double.class);
            verify(bar, atLeastOnce()).setProgress(progress.capture());
            assertThat(progress.getAllValues()).allSatisfy(value -> assertThat(value).isBetween(0.0, 1.0));
            assertThat(progress.getValue()).isEqualTo(29.0 / 30.0);
        }

        @Test
        @DisplayName("request-timeout 5 reloaded to 30: the bar counts down from 5 and is removed at the original deadline")
        void barKeepsOriginalTimeoutWhenRaised() throws Exception {
            Runnable countdown = sendRequestWithTimeout(5);

            reload("max-distance: 0\nrequest-timeout: 30\n");
            countdown.run();

            verify(bar).setProgress(4.0 / 5.0);
            for (int tick = 0; tick < 4; tick++) {
                countdown.run();
            }
            verify(bar).removeAll();
        }

        @Test
        @DisplayName("request-timeout 30 reloaded to 5: a request 10 seconds old can still be accepted, as promised when it was sent")
        void acceptUsesOriginalTimeoutWhenLowered() throws Exception {
            sendRequestWithTimeout(30);
            reload("max-distance: 0\nrequest-timeout: 5\n");
            ageRequest(10_000L);

            assertThat(tradeService.acceptRequest(receiver)).isTrue();
            assertThat(tradeService.isTrading(receiver.getUniqueId())).isTrue();
        }

        @Test
        @DisplayName("request-timeout 5 reloaded to 30: a request 10 seconds old has expired at its original deadline and cannot be accepted")
        void acceptUsesOriginalTimeoutWhenRaised() throws Exception {
            sendRequestWithTimeout(5);
            reload("max-distance: 0\nrequest-timeout: 30\n");
            ageRequest(10_000L);

            assertThat(tradeService.acceptRequest(receiver)).isFalse();
            assertThat(tradeService.isTrading(receiver.getUniqueId())).isFalse();
        }

        @Test
        @DisplayName("the cleanup task expires a request at the timeout it was sent with, not the reloaded one")
        void cleanupUsesOriginalTimeout() throws Exception {
            sendRequestWithTimeout(5);
            reload("max-distance: 0\nrequest-timeout: 30\n");
            ageRequest(10_000L);

            tradeService.cleanupExpiredRequests();

            Map<UUID, TradeRequest> pending = UltiTradeTestHelper.getField(tradeService, "pendingRequests");
            assertThat(pending).doesNotContainKey(receiver.getUniqueId());
        }

        @Test
        @DisplayName("the cleanup task keeps a request that is still within the timeout it was sent with after the timeout is lowered")
        void cleanupKeepsRequestWithinOriginalTimeout() throws Exception {
            sendRequestWithTimeout(30);
            reload("max-distance: 0\nrequest-timeout: 5\n");
            ageRequest(10_000L);

            tradeService.cleanupExpiredRequests();

            Map<UUID, TradeRequest> pending = UltiTradeTestHelper.getField(tradeService, "pendingRequests");
            assertThat(pending).containsKey(receiver.getUniqueId());
        }
    }

    @Nested
    @DisplayName("open trade windows and confirmation pages across a reload (UltiKits/UltiTrade#27)")
    class OpenTradeWindows {

        private Economy vaultEconomy;
        private Server server;
        private Player offerer;
        private Player counterparty;
        private PlayerInventory offererInventory;
        private PlayerInventory counterpartyInventory;
        private InventoryView offererView;
        private InventoryView counterpartyView;
        private Inventory offererTop;
        private Inventory counterpartyTop;
        private ItemStack diamond;
        private ItemStack emerald;
        private TradeSession session;

        @BeforeEach
        void openTradeWindowsAtZeroTax() throws Exception {
            server = Bukkit.getServer();
            vaultEconomy = UltiTradeTestHelper.createMockEconomy();
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            stubProvider(server, vaultEconomy);
            TradeLogService sessionLog = mock(TradeLogService.class);
            UltiTradeTestHelper.setField(tradeService, "logService", sessionLog);

            offerer = UltiTradeTestHelper.createMockPlayer("Offerer", UUID.randomUUID());
            counterparty = UltiTradeTestHelper.createMockPlayer("Counterparty", UUID.randomUUID());
            UUID offererId = offerer.getUniqueId();
            UUID counterpartyId = counterparty.getUniqueId();
            doReturn(offerer).when(server).getPlayer(offererId);
            doReturn(counterparty).when(server).getPlayer(counterpartyId);
            offererInventory = offerer.getInventory();
            counterpartyInventory = counterparty.getInventory();
            when(offererInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
            when(counterpartyInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            loadConfig("enable-money-trade: true\ntrade-tax: 0.0\n");
            tradeService.init();

            session = new TradeSession(offerer, counterparty);
            diamond = new ItemStack(Material.DIAMOND, 3);
            emerald = new ItemStack(Material.EMERALD, 5);
            session.setItem(offererId, 0, diamond);
            session.setItem(counterpartyId, 0, emerald);
            session.setMoney(offererId, 1000.0);
            Map<UUID, TradeSession> activeSessions = UltiTradeTestHelper.getField(tradeService, "activeSessions");
            Map<UUID, UUID> playerSessionMap = UltiTradeTestHelper.getField(tradeService, "playerSessionMap");
            activeSessions.put(session.getSessionId(), session);
            playerSessionMap.put(offererId, session.getSessionId());
            playerSessionMap.put(counterpartyId, session.getSessionId());

            offererView = mock(InventoryView.class);
            counterpartyView = mock(InventoryView.class);
            offererTop = mock(Inventory.class);
            counterpartyTop = mock(Inventory.class);
            when(offererView.getTopInventory()).thenReturn(offererTop);
            when(counterpartyView.getTopInventory()).thenReturn(counterpartyTop);
            when(offerer.getOpenInventory()).thenReturn(offererView);
            when(counterparty.getOpenInventory()).thenReturn(counterpartyView);
            // Keep item meta (and so the rendered lore) when the window builds its items.
            ItemFactory itemFactory = server.getItemFactory();
            when(itemFactory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
            when(itemFactory.asMetaFor(any(ItemMeta.class), any(ItemStack.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        private java.util.List<String> plainLore(ItemStack item) {
            java.util.List<String> plain = new java.util.ArrayList<>();
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    plain.add(ChatColor.stripColor(line));
                }
            }
            return plain;
        }

        private TradeGUI openWindow(Player viewer, Inventory top) {
            TradeGUI gui = new TradeGUI(tradeService, session, viewer);
            gui.update();
            when(top.getHolder()).thenReturn(gui);
            return gui;
        }

        @Test
        @DisplayName("a reload changes trade-tax while both windows are open: both are redrawn with the new tax, and the tax charged equals the tax shown")
        void reloadRedrawsOpenWindowsWithNewTax() throws Exception {
            TradeGUI offererWindow = openWindow(offerer, offererTop);
            openWindow(counterparty, counterpartyTop);
            Inventory windowContents = offererWindow.getInventory();
            clearInvocations(windowContents, offererView, counterpartyView);

            reload("enable-money-trade: true\ntrade-tax: 1.0\ngui-title: '&6Reloaded {PLAYER}'\n");

            ArgumentCaptor<ItemStack> moneySlot = ArgumentCaptor.forClass(ItemStack.class);
            verify(windowContents, atLeastOnce()).setItem(eq(TradeGUI.YOUR_MONEY_SLOT), moneySlot.capture());
            assertThat(moneySlot.getAllValues()).anySatisfy(item -> assertThat(plainLore(item))
                    .contains("\u7A0E\u7387: 100.0%", "\u7A0E\u91D1: 1000.00")); // "税率: 100.0%", "税金: 1000.00"
            verify(offererView).setTitle(contains("Reloaded Counterparty"));
            verify(counterpartyView).setTitle(contains("Reloaded Offerer"));

            // Both confirm against the redrawn windows: the charged tax is the displayed 1000.00.
            session.setConfirmed(offerer.getUniqueId(), true);
            tradeService.confirmTrade(counterparty);
            verify(vaultEconomy).withdrawPlayer(offerer, 1000.0);
            verify(vaultEconomy).depositPlayer(counterparty, 0.0);
        }

        @Test
        @DisplayName("a reload replaces an open large-trade confirmation page with a trade window built from the reloaded configuration")
        void reloadReplacesOpenConfirmationPage() throws Exception {
            TradeConfirmPage page = mock(TradeConfirmPage.class);
            when(offererTop.getHolder()).thenReturn(page);
            openWindow(counterparty, counterpartyTop);

            Inventory windowContents = server.createInventory(null, 54, "probe");
            clearInvocations(server, windowContents);

            reload("enable-money-trade: true\ntrade-tax: 0.5\n");

            // The replacement window shows the offer the session holds, not empty placeholders.
            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], diamond);
            verify(server, atLeastOnce()).createInventory(
                    argThat(holder -> holder instanceof TradeGUI && ((TradeGUI) holder).getViewer() == offerer),
                    eq(54), anyString());
            verify(offerer).openInventory(any(Inventory.class));
            verify(counterparty, never()).openInventory(any(Inventory.class));
            assertThat(tradeService.isTrading(offerer.getUniqueId())).isTrue();
        }

        @Test
        @DisplayName("redrawing the windows neither loses nor duplicates anything offered")
        void redrawKeepsOffersIntact() throws Exception {
            TradeGUI offererWindow = openWindow(offerer, offererTop);
            openWindow(counterparty, counterpartyTop);
            Inventory windowContents = offererWindow.getInventory();
            clearInvocations(windowContents);

            reload("enable-money-trade: true\ntrade-tax: 0.25\n");

            // The windows were redrawn from the reloaded tax (stale before: no tax lore at all) ...
            ArgumentCaptor<ItemStack> moneySlot = ArgumentCaptor.forClass(ItemStack.class);
            verify(windowContents, atLeastOnce()).setItem(eq(TradeGUI.YOUR_MONEY_SLOT), moneySlot.capture());
            assertThat(moneySlot.getAllValues()).anySatisfy(item -> assertThat(plainLore(item))
                    .contains("\u7A0E\u7387: 25.0%")); // "税率: 25.0%"
            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], diamond);
            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], emerald);
            // ... and the redraw neither lost nor duplicated an offer.
            assertThat(session.getPlayerItems(offerer.getUniqueId())).containsExactly(entry(0, diamond));
            assertThat(session.getPlayerItems(counterparty.getUniqueId())).containsExactly(entry(0, emerald));
            assertThat(session.getPlayerMoney(offerer.getUniqueId())).isEqualTo(1000.0);
            verify(offererInventory, never()).addItem(any(ItemStack.class));
            verify(counterpartyInventory, never()).addItem(any(ItemStack.class));
            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.TRADING);
        }

        @Test
        @DisplayName("a server without InventoryView#setTitle: the reload still completes and every window is still redrawn")
        void missingSetTitleDoesNotAbortReload() throws Exception {
            openWindow(offerer, offererTop);
            TradeGUI counterpartyWindow = openWindow(counterparty, counterpartyTop);
            doThrow(new NoSuchMethodError("org.bukkit.inventory.InventoryView.setTitle(Ljava/lang/String;)V"))
                    .when(offererView).setTitle(anyString());
            doThrow(new NoSuchMethodError("org.bukkit.inventory.InventoryView.setTitle(Ljava/lang/String;)V"))
                    .when(counterpartyView).setTitle(anyString());
            Inventory windowContents = counterpartyWindow.getInventory();
            clearInvocations(windowContents);

            reload("enable-money-trade: true\ntrade-tax: 0.5\n");

            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], diamond);
            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], emerald);
            // An unsupported title change is expected on such a server, not a failure to report.
            verify(UltiTradeTestHelper.getMockLogger(), never()).error(any(Throwable.class), anyString());
        }

        @Test
        @DisplayName("one player's window failing to redraw is logged at SEVERE with that player's name, and the other player's window is still redrawn")
        void onePlayersRedrawFailureDoesNotStopTheOthers() throws Exception {
            TradeGUI counterpartyWindow = openWindow(counterparty, counterpartyTop);
            RuntimeException failure = new IllegalStateException("view unavailable");
            when(offererView.getTopInventory()).thenThrow(failure);
            Inventory windowContents = counterpartyWindow.getInventory();
            clearInvocations(windowContents);

            reload("enable-money-trade: true\ntrade-tax: 0.5\n");

            verify(windowContents).setItem(TradeGUI.YOUR_SLOTS[0], emerald);
            verify(UltiTradeTestHelper.getMockLogger()).error(eq(failure), contains("Offerer"));
        }
    }

    @Nested
    @DisplayName("a trade that is open while a reload turns money trading off")
    class MidTradeDisable {

        private Economy vaultEconomy;
        private Server server;
        private TradeLogService sessionLog;
        private Player offerer;
        private Player counterparty;
        private PlayerInventory offererInventory;
        private PlayerInventory counterpartyInventory;
        private ItemStack offererItem;
        private ItemStack counterpartyItem;
        private TradeSession session;

        @BeforeEach
        void openTradeWithMoneyOffered() throws Exception {
            server = Bukkit.getServer();
            vaultEconomy = UltiTradeTestHelper.createMockEconomy();
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            stubProvider(server, vaultEconomy);

            sessionLog = mock(TradeLogService.class);
            UltiTradeTestHelper.setField(tradeService, "logService", sessionLog);

            offerer = UltiTradeTestHelper.createMockPlayer("Offerer", UUID.randomUUID());
            counterparty = UltiTradeTestHelper.createMockPlayer("Counterparty", UUID.randomUUID());
            UUID offererId = offerer.getUniqueId();
            UUID counterpartyId = counterparty.getUniqueId();
            doReturn(offerer).when(server).getPlayer(offererId);
            doReturn(counterparty).when(server).getPlayer(counterpartyId);
            offererInventory = offerer.getInventory();
            counterpartyInventory = counterparty.getInventory();
            when(offererInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
            when(counterpartyInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.hasEconomy()).isTrue();

            session = new TradeSession(offerer, counterparty);
            // Real stacks of two distinguishable materials, not mocks: the delivery path copies the
            // stack it hands over (UltiKits/UltiTrade#37) and a Mockito mock's clone() is null, while the
            // verifications below match by equality and so stay exactly as discriminating as before.
            offererItem = new ItemStack(Material.DIAMOND, 4);
            counterpartyItem = new ItemStack(Material.GOLD_INGOT, 6);
            session.setItem(offerer.getUniqueId(), 0, offererItem);
            session.setItem(counterparty.getUniqueId(), 0, counterpartyItem);
            session.setMoney(offerer.getUniqueId(), 1000.0);
            Map<UUID, TradeSession> activeSessions = UltiTradeTestHelper.getField(tradeService, "activeSessions");
            Map<UUID, UUID> playerSessionMap = UltiTradeTestHelper.getField(tradeService, "playerSessionMap");
            activeSessions.put(session.getSessionId(), session);
            playerSessionMap.put(offerer.getUniqueId(), session.getSessionId());
            playerSessionMap.put(counterparty.getUniqueId(), session.getSessionId());
        }

        @Test
        @DisplayName("A offers money, a reload turns money trading off, both confirm again: the trade is cancelled, nothing moves, no completed trade is logged")
        void confirmAfterDisableCancelsTrade() throws Exception {
            reload("enable-money-trade: false\n");
            // A reload voids earlier confirmations, so both players confirm again after it.
            session.setConfirmed(offerer.getUniqueId(), true);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            // Balances unchanged: nothing was withdrawn or deposited.
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
            // No item crossed sides; each side got back exactly its own offer.
            verify(counterpartyInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(offererInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(counterpartyItem));
            verify(offererInventory, times(1)).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(counterpartyInventory, times(1)).addItem(UltiTradeTestHelper.deliveredCopyOf(counterpartyItem));
            // No completed-trade log entry and therefore no money statistic.
            verify(sessionLog, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyInt());
            // Both players are told why.
            verify(offerer).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
            verify(counterparty).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
            assertThat(tradeService.isTrading(offerer.getUniqueId())).isFalse();
        }

        @Test
        @DisplayName("the accepting player offered the money: a reload that turns money trading off still cancels the trade")
        void counterpartyMoneyAfterDisableCancelsTrade() throws Exception {
            session.setMoney(offerer.getUniqueId(), 0.0);
            session.setMoney(counterparty.getUniqueId(), 1000.0);

            reload("enable-money-trade: false\n");
            session.setConfirmed(offerer.getUniqueId(), true);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
            verify(counterpartyInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(offererInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(counterpartyItem));
            verify(sessionLog, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyInt());
            verify(counterparty).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
        }

        @Test
        @DisplayName("provider still held but enable-money-trade already false in memory (hook not reached): the money trade is cancelled")
        void heldProviderWithMoneyTradeOffCancelsTrade() throws Exception {
            session.setConfirmed(offerer.getUniqueId(), true);

            // Only the configuration changes: onReload() is not run, so the provider stays held.
            loadConfig("enable-money-trade: false\n");
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
        }

        @Test
        @DisplayName("one player confirmed, then a reload changes trade-tax: the trade does not complete on the other player's confirmation and both must confirm again")
        void reloadResetsConfirmationsBeforeChangedTermsApply() throws Exception {
            loadConfig("enable-money-trade: true\ntrade-tax: 0.0\n");
            session.setConfirmed(offerer.getUniqueId(), true);

            reload("enable-money-trade: true\ntrade-tax: 1.0\n");
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.TRADING);
            assertThat(session.isConfirmed(offerer.getUniqueId())).isFalse();
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(counterpartyInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(offerer).sendMessage(contains(RECONFIRM_AFTER_RELOAD));
        }

        @Test
        @DisplayName("experience offered, then a reload turns enable-exp-trade off: the trade is cancelled instead of moving items without the experience")
        void expOfferAfterExpTradeDisabledCancelsTrade() throws Exception {
            session.setMoney(offerer.getUniqueId(), 0.0);
            session.setExp(offerer.getUniqueId(), 100);

            reload("enable-money-trade: true\nenable-exp-trade: false\n");
            session.setConfirmed(offerer.getUniqueId(), true);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            verify(counterparty, never()).giveExp(anyInt());
            verify(offerer, never()).setExp(anyFloat());
            verify(counterpartyInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(offererInventory, never()).addItem(UltiTradeTestHelper.deliveredCopyOf(counterpartyItem));
            verify(sessionLog, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyInt());
            verify(offerer).sendMessage(contains(EXP_UNAVAILABLE_REASON));
        }

        @Test
        @DisplayName("no money offered: a reload that turns money trading off does not stop an item-only trade")
        void itemOnlyTradeStillCompletes() throws Exception {
            session.setMoney(offerer.getUniqueId(), 0.0);

            reload("enable-money-trade: false\n");
            session.setConfirmed(offerer.getUniqueId(), true);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
            verify(counterpartyInventory).addItem(UltiTradeTestHelper.deliveredCopyOf(offererItem));
            verify(offererInventory).addItem(UltiTradeTestHelper.deliveredCopyOf(counterpartyItem));
        }
    }

    @Nested
    @DisplayName("enable-trade-log and cleanup-interval-hours")
    class CleanupTask {

        private BukkitScheduler scheduler;
        private BukkitTask first;
        private BukkitTask second;
        private BukkitTask third;

        @BeforeEach
        void distinctTasks() {
            scheduler = Bukkit.getServer().getScheduler();
            first = mock(BukkitTask.class);
            second = mock(BukkitTask.class);
            third = mock(BukkitTask.class);
            when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(first, second, third);
        }

        @Test
        @DisplayName("true at startup, then false and reload: the running cleanup task is cancelled and none is started")
        void trueToFalseCancelsCleanupTask() throws Exception {
            loadConfig("enable-trade-log: true\n");
            logService.init();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());

            reload("enable-trade-log: false\n");

            verify(first, times(1)).cancel();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isNull();
        }

        @Test
        @DisplayName("false at startup, then true and reload: exactly one cleanup task is started with the configured interval")
        void falseToTrueStartsCleanupTask() throws Exception {
            loadConfig("enable-trade-log: false\ncleanup-interval-hours: 6\n");
            logService.init();
            verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());

            reload("enable-trade-log: true\ncleanup-interval-hours: 6\n");

            long ticks = 6L * 60L * 60L * 20L;
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), eq(ticks), eq(ticks));
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }

        @Test
        @DisplayName("changing cleanup-interval-hours replaces the task once; a second reload with the same interval keeps it")
        void intervalChangeReschedulesWithoutAccumulating() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();

            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");
            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");

            long twelveHours = 12L * 60L * 60L * 20L;
            verify(scheduler, times(1)).runTaskTimerAsynchronously(
                    any(), any(Runnable.class), eq(twelveHours), eq(twelveHours));
            verify(scheduler, times(2)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            verify(first, times(1)).cancel();
            verify(second, never()).cancel();
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(second);
        }

        @Test
        @DisplayName("a reload that changes neither cleanup key keeps the running task and its countdown")
        void unrelatedReloadKeepsRunningTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\nmax-distance: 50\n");
            logService.init();

            reload("enable-trade-log: true\ncleanup-interval-hours: 24\nmax-distance: 10\n");

            verify(first, never()).cancel();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }

        @Test
        @DisplayName("logging on, off, on again with the same interval: a cleanup task is scheduled again")
        void onOffOnSameIntervalRestartsTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();

            reload("enable-trade-log: false\ncleanup-interval-hours: 24\n");
            verify(first, times(1)).cancel();
            reload("enable-trade-log: true\ncleanup-interval-hours: 24\n");

            long day = 24L * 60L * 60L * 20L;
            verify(scheduler, times(2)).runTaskTimerAsynchronously(any(), any(Runnable.class), eq(day), eq(day));
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(second);
        }

        @Test
        @DisplayName("logging off at startup and still off after a reload: nothing is scheduled")
        void offStaysOffSchedulesNothing() throws Exception {
            loadConfig("enable-trade-log: false\n");
            logService.init();

            reload("enable-trade-log: false\ncleanup-interval-hours: 6\n");

            verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("if scheduling the replacement task fails, the previous task keeps running")
        void failedRescheduleKeepsPreviousTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();
            when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenThrow(new IllegalStateException("scheduler unavailable"));

            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 12\n");
            assertThatThrownBy(() -> logService.reloadCleanupTask()).isInstanceOf(IllegalStateException.class);

            verify(first, never()).cancel();
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }
    }

    private static final String EXP_UNAVAILABLE_REASON = "\u7ECF\u9A8C\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528"; // "经验交易当前不可用"
    private static final String RECONFIRM_AFTER_RELOAD = "\u8BF7\u91CD\u65B0\u786E\u8BA4"; // "请重新确认"
    private static final String MONEY_UNAVAILABLE_REASON = "\u91D1\u5E01\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528"; // "金币交易当前不可用"

    /** Make the services manager return a registration for {@code provider}, built before the stub starts. */
    private static void stubProvider(Server server, Economy provider) {
        ServicesManager servicesManager = server.getServicesManager();
        RegisteredServiceProvider<Economy> registration = registrationOf(provider);
        doReturn(registration).when(servicesManager).getRegistration(Economy.class);
    }

    private static RegisteredServiceProvider<Economy> registrationOf(Economy provider) {
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(provider);
        return registration;
    }

    private void loadConfig(String yaml) throws Exception {
        Files.write(configFile.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        config.init(plugin);
    }

    /** Edit the file, re-initialise the same config instance, then run the module's reload hook. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // onReload() is protected on the framework base class
    private void reload(String yaml) throws Exception {
        loadConfig(yaml);
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload");
        hook.setAccessible(true);
        hook.invoke(plugin);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }

    /** The Chinese catalogue's console line for {@code key}, or a marker naming the missing key (UltiKits/UltiTrade#16). */
    private static String zhLine(String key) {
        return com.ultikits.plugins.trade.i18n.CatalogueText.entries("zh")
                .getOrDefault(key, "<lang/zh has no " + key + ">");
    }
}
