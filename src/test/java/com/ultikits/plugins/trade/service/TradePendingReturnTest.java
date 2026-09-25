package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PendingStakeReturn;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.listener.TradeListener;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A cancelled trade whose participant the server cannot resolve
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/32">UltiTrade#32</a>).
 *
 * <h2>The defect</h2>
 * {@code cancelTrade} returned each side's stake only inside {@code if (player != null)}, then
 * discarded the session, which is the stake's only record: the stake of a participant
 * {@code Bukkit#getPlayer} could not resolve was destroyed.
 *
 * <h2>The decision this implements (maintainer, 2026-09-24)</h2>
 * Keep the stake in a saved pending-returns list and hand it back when that player next joins,
 * through the module's give-or-drop step; if saving the list fails, drop the items at the player's
 * last known location and log an error naming the player and the items.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * A real MockBukkit server with real player inventories and a real world; the saved list is an
 * in-memory store that keeps exactly what the service inserted, so every assertion reads stored rows,
 * real inventories or item entities in the world. The "unresolvable" participant is a real player
 * object that is simply not on the server, then joins it.
 */
@DisplayName("A cancelled trade keeps the stake of a participant the server cannot find (UltiTrade#32)")
class TradePendingReturnTest {

    private ServerMock server;
    private World world;
    private TradeService service;
    private TradeLogService logService;
    private Economy economy;
    private PluginLogger logger;
    private InMemoryPendingReturns store;

    private PlayerMock present;
    private PlayerMock away;

    @BeforeEach
    void setUp() throws Exception {
        // A real MockBukkit server, not the shared helper's spy: that one stubs the item factory with
        // a mock ItemMeta, and this test must see an item's name, lore and enchantment round-trip.
        UltiTradeTestHelper.tearDown(); // clears any server a previous class left behind
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");

        UltiTrade plugin = mock(UltiTrade.class);
        logger = mock(PluginLogger.class);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        store = new InMemoryPendingReturns();
        lenient().when(plugin.getDataOperator(PendingStakeReturn.class)).thenReturn(store);

        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.isEnableSounds()).thenReturn(false);
        lenient().when(config.isEnableParticles()).thenReturn(false);
        lenient().when(config.isEnableMoneyTrade()).thenReturn(false);
        logService = mock(TradeLogService.class);
        economy = mock(Economy.class);

        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", logService);
        UltiTradeTestHelper.setField(service, "economy", economy);
        UltiTradeTestHelper.setField(service, "pendingReturns", store);

        present = server.addPlayer("Present");
        // A real player object that is NOT on the server: Bukkit#getPlayer cannot resolve it.
        away = new PlayerMock(server, "Away", UUID.randomUUID());
        away.setLocation(world.getSpawnLocation());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private static ItemStack namedSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("Blade of the Away Player");
        meta.setLore(Collections.singletonList("staked, not traded"));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        sword.setItemMeta(meta);
        return sword;
    }

    /** A live session between the present player and the away one, each with a stake. */
    private TradeSession sessionWithStakes() throws Exception {
        TradeSession session = new TradeSession(present, away);
        session.setItem(present.getUniqueId(), 0, new ItemStack(Material.EMERALD, 4));
        session.setItem(away.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 10));
        session.setItem(away.getUniqueId(), 1, namedSword());
        session.setMoney(away.getUniqueId(), 50.0);
        session.setExp(away.getUniqueId(), 30);
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(service, "activeSessions");
        Map<UUID, UUID> bySession = UltiTradeTestHelper.getField(service, "playerSessionMap");
        active.put(session.getSessionId(), session);
        bySession.put(present.getUniqueId(), session.getSessionId());
        bySession.put(away.getUniqueId(), session.getSessionId());
        return session;
    }

    private List<ItemStack> savedStacksOf(PlayerMock owner) {
        List<ItemStack> all = new ArrayList<>();
        for (PendingStakeReturn row : store.rowsOf(owner.getUniqueId())) {
            all.addAll(TradeService.deserializeStacks(row.getItems()));
        }
        return all;
    }

    private int count(PlayerMock player, Material material) {
        int n = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                n += item.getAmount();
            }
        }
        return n;
    }

    @Test
    @DisplayName("Completion offline guard: the away player's stake is saved, the present player's is returned")
    void offlineGuardSavesTheStake() throws Exception {
        TradeSession session = sessionWithStakes();

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(count(present, Material.EMERALD)).as("the present player's own stake came back").isEqualTo(4);
        assertThat(store.rowsOf(away.getUniqueId())).as("one saved entry for the away player").hasSize(1);
        assertThat(savedStacksOf(away))
                .as("exactly the stacks the away player staked")
                .containsExactlyInAnyOrder(new ItemStack(Material.DIAMOND, 10), namedSword());
        assertThat(store.rowsOf(present.getUniqueId())).as("nothing saved for the present player").isEmpty();
        verify(logger).warn(argThat((String s) -> s.contains(away.getUniqueId().toString())
                && s.contains("DIAMOND x10") && s.contains("DIAMOND_SWORD x1")));
    }

    @Test
    @DisplayName("Cancelling moves no money and no experience")
    void cancellingMovesNoMoneyOrExperience() throws Exception {
        present.setTotalExperience(100);
        TradeSession session = sessionWithStakes();

        service.cancelTrade(session, "test");

        verify(economy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        verify(economy, never()).depositPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        assertThat(present.getTotalExperience()).isEqualTo(100);
    }

    @Test
    @DisplayName("Shutdown reaches the same handling")
    void shutdownSavesTheStake() throws Exception {
        sessionWithStakes();

        service.shutdown();

        assertThat(savedStacksOf(away)).containsExactlyInAnyOrder(new ItemStack(Material.DIAMOND, 10), namedSword());
    }

    @Test
    @DisplayName("A quit or /trade cancel by the present player reaches the same handling")
    void cancelByThePresentPlayerSavesTheAwayStake() throws Exception {
        sessionWithStakes();

        service.cancelTrade(present);

        assertThat(count(present, Material.EMERALD)).isEqualTo(4);
        assertThat(savedStacksOf(away)).containsExactlyInAnyOrder(new ItemStack(Material.DIAMOND, 10), namedSword());
    }

    @Test
    @DisplayName("A failed save drops the stake in the world and logs an error naming player and items")
    void failedSaveDropsAndLogs() throws Exception {
        store.failInsert = true;

        service.completeTrade(sessionWithStakes());

        int diamonds = 0;
        int swords = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.DIAMOND) {
                diamonds += item.getItemStack().getAmount();
            }
            if (item.getItemStack().isSimilar(namedSword())) {
                swords++;
            }
        }
        assertThat(diamonds).as("the stake is in the world, not destroyed").isEqualTo(10);
        assertThat(swords).isEqualTo(1);
        assertThat(store.rows).as("nothing was saved").isEmpty();
        verify(logger).error(any(Throwable.class), argThat((String s) -> s.contains(away.getUniqueId().toString())
                && s.contains("DIAMOND x10") && s.contains("DIAMOND_SWORD x1")));
    }

    @Test
    @DisplayName("A failed save drops the stake where the player last was, not at the spawn (gate-1 WR-01)")
    void failedSaveDropsAtTheLastKnownLocation() throws Exception {
        store.failInsert = true;
        // The away player was on the server, far from the spawn, and has since left it.
        server.addPlayer(away);
        org.bukkit.Location lastSeen = new org.bukkit.Location(world, 500.5, 70, -300.5);
        away.teleport(lastSeen);
        away.disconnect();
        assertThat(org.bukkit.Bukkit.getPlayer(away.getUniqueId()))
                .as("precondition: the server can no longer resolve the away player").isNull();

        service.completeTrade(sessionWithStakes());

        List<Item> diamonds = new ArrayList<>();
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.DIAMOND) {
                diamonds.add(item);
            }
        }
        assertThat(diamonds).as("the stake was dropped").isNotEmpty();
        for (Item item : diamonds) {
            assertThat(item.getLocation().distance(lastSeen))
                    .as("dropped where the player was last seen, not at the world spawn %s", world.getSpawnLocation())
                    .isLessThan(2.0);
        }
    }

    @Test
    @DisplayName("A saved stake whose log line then fails is not also dropped (Codex P2 on #45)")
    void aFailingLogAfterTheSaveDoesNotDropTheStake() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("logger unavailable during shutdown"))
                .when(logger).warn(anyString());

        try {
            service.completeTrade(sessionWithStakes());
        } catch (IllegalStateException expected) {
            // Whether the logging failure escapes is not what this test is about.
        }

        assertThat(store.rowsOf(away.getUniqueId())).as("the stake was saved once").hasSize(1);
        int dropped = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.DIAMOND) {
                dropped += item.getItemStack().getAmount();
            }
        }
        assertThat(dropped).as("and not also dropped into the world, which would hand it out twice").isZero();
    }

    @Test
    @DisplayName("A failing log line never leaves the cancelled session open, so a later cancel cannot save or drop the stake again")
    void aFailingLogDoesNotLeaveTheSessionOpen() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("logger unavailable"))
                .when(logger).warn(anyString());
        TradeSession session = sessionWithStakes();

        service.completeTrade(session);

        assertThat(service.getSession(present.getUniqueId()))
                .as("the session is closed, so no later cancel reaches the same stake").isNull();
        assertThat(store.rowsOf(away.getUniqueId())).hasSize(1);
    }

    // ==================== The join-time hand-over (maintainer answers 2026-09-24, amended 2026-09-25) ====================
    //
    // The hand-over touches two stores that cannot be committed together: the pending-returns table
    // and the player's data file. The tests model the data file the way the server writes it: at
    // Player#saveData the inventory and the persistent data are written together, and a crash rolls
    // the player back to the last such write. A crash is a SimulatedCrash (an Error, so no catch in
    // production code can mistake it for a storage failure) thrown from a store or save hook.

    /** A process death at a chosen point; nothing after it runs. */
    static final class SimulatedCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    /** The player's data file as the server last wrote it. */
    private ItemStack[] savedContents;
    private String savedDeliveries;

    private static final org.bukkit.NamespacedKey DELIVERIES =
            org.bukkit.NamespacedKey.fromString("ultitrade:pending_return_deliveries");

    private static String deliveriesOf(org.bukkit.entity.Player player) {
        return player.getPersistentDataContainer().get(DELIVERIES, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** {@code player}, whose saveData writes the file above; the hooks run before and after the write. */
    private PlayerMock saving(PlayerMock player, Runnable beforeSave, Runnable afterSave) {
        PlayerMock spy = org.mockito.Mockito.spy(player);
        org.mockito.Mockito.doAnswer(inv -> {
            beforeSave.run();
            savedContents = copy(spy.getInventory().getContents());
            savedDeliveries = deliveriesOf(spy);
            afterSave.run();
            return null;
        }).when(spy).saveData();
        return spy;
    }

    private PlayerMock saving(PlayerMock player) {
        return saving(player, () -> { }, () -> { });
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = contents[i] == null ? null : contents[i].clone();
        }
        return out;
    }

    private int count(ItemStack[] contents, Material material) {
        int n = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                n += item.getAmount();
            }
        }
        return n;
    }

    private int countListed(Material material, boolean deliveredPerSavedFile) {
        int n = 0;
        for (PendingStakeReturn row : store.rowsOf(away.getUniqueId())) {
            boolean completed = row.getDeliveryToken() != null && savedDeliveries != null
                    && savedDeliveries.contains(row.getDeliveryToken());
            String effective = completed && deliveredPerSavedFile ? row.getAfterDelivery() : row.getItems();
            if (effective == null || effective.isEmpty()) {
                continue;
            }
            for (ItemStack item : TradeService.deserializeStacks(effective)) {
                if (item.getType() == material) {
                    n += item.getAmount();
                }
            }
        }
        return n;
    }

    /** Fill every slot but {@code free} with dirt (MockBukkit's addItem also fills armour slots). */
    private void fillAllBut(PlayerMock player, int... free) {
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (int f : free) {
            keep.add(f);
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (!keep.contains(i)) {
                player.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
            }
        }
    }

    private int droppedInWorld(Material material) {
        int n = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == material) {
                n += item.getItemStack().getAmount();
            }
        }
        return n;
    }

    @Test
    @DisplayName("The next join hands the stake over, metadata intact, and empties the list; a second join delivers nothing")
    void joinDeliversOnceWithMetadata() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        PlayerMock joined = saving(away);

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).isEqualTo(10);
        ItemStack sword = joined.getInventory().getItem(joined.getInventory().first(Material.DIAMOND_SWORD));
        assertThat(sword).as("name, lore and enchantment survive the round trip").isEqualTo(namedSword());
        assertThat(store.rowsOf(away.getUniqueId())).as("the list is empty afterwards").isEmpty();
        assertThat(away.nextMessage()).contains("returned to you");

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("a second join delivers nothing").isEqualTo(10);
        assertThat(joined.getInventory().all(Material.DIAMOND_SWORD)).hasSize(1);
    }

    @Test
    @DisplayName("The join listener hands the stake over")
    void joinListenerDelivers() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        PlayerMock joined = saving(away);
        TradeListener listener = new TradeListener();
        UltiTradeTestHelper.setField(listener, "tradeService", service);

        listener.onPlayerJoin(new PlayerJoinEvent(joined, "joined"));

        assertThat(count(joined, Material.DIAMOND)).isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("A full inventory: nothing is dropped, the whole stake stays in the list, the player is told (amended answer 2026-09-25)")
    void fullInventoryKeepsTheStakeInTheList() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        fillAllBut(away);
        PlayerMock joined = saving(away);

        service.deliverPendingReturns(joined);

        assertThat(droppedInWorld(Material.DIAMOND)).as("nothing is dropped").isZero();
        assertThat(countListed(Material.DIAMOND, false)).as("the diamonds are still listed").isEqualTo(10);
        assertThat(countListed(Material.DIAMOND_SWORD, false)).isEqualTo(1);
        assertThat(away.nextMessage()).contains("11").contains("rejoin");
    }

    @Test
    @DisplayName("Only what fits is handed over; the rest stays listed and arrives at a later join with space")
    void partialDeliveryKeepsTheRest() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        fillAllBut(away, 5);
        away.getInventory().setItem(5, new ItemStack(Material.DIAMOND, 60));
        PlayerMock joined = saving(away);

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("4 diamonds topped the stack up to 64").isEqualTo(64);
        assertThat(droppedInWorld(Material.DIAMOND)).as("nothing is dropped").isZero();
        assertThat(countListed(Material.DIAMOND, false)).as("6 diamonds stay listed").isEqualTo(6);
        assertThat(countListed(Material.DIAMOND_SWORD, false)).isEqualTo(1);
        assertThat(savedContents).as("the player was saved holding what was handed over").isNotNull();
        assertThat(count(savedContents, Material.DIAMOND)).isEqualTo(64);
        assertThat(away.nextMessage()).contains("7");

        for (int i = 9; i < 20; i++) {
            joined.getInventory().setItem(i, null);
        }
        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("the rest arrived, once").isEqualTo(70);
        assertThat(joined.getInventory().all(Material.DIAMOND_SWORD)).hasSize(1);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    /**
     * A crash at each point of a partial hand-over: at the moment of the crash every diamond is either in
     * the saved player file or in the list as the next join will read it, never both and never neither;
     * and the join after the restart, with room, ends with exactly the stake.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "crash {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "before-mark", "after-mark", "before-save", "after-save", "before-complete", "after-complete"})
    @DisplayName("A crash at any point leaves each item in exactly one place")
    void crashAtAnyPointLeavesEachItemInExactlyOnePlace(String point) throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        fillAllBut(away, 0);
        ItemStack[] atJoin = copy(away.getInventory().getContents());
        savedContents = atJoin;
        savedDeliveries = null;
        final int[] updates = {0};
        Runnable crash = () -> {
            throw new SimulatedCrash();
        };
        store.beforeUpdate = () -> {
            updates[0]++;
            if ((updates[0] == 1 && point.equals("before-mark")) || (updates[0] == 2 && point.equals("before-complete"))) {
                crash.run();
            }
        };
        store.afterUpdate = () -> {
            if ((updates[0] == 1 && point.equals("after-mark")) || (updates[0] == 2 && point.equals("after-complete"))) {
                crash.run();
            }
        };
        PlayerMock joined = saving(away,
                () -> { if (point.equals("before-save")) crash.run(); },
                () -> { if (point.equals("after-save")) crash.run(); });

        try {
            service.deliverPendingReturns(joined);
        } catch (SimulatedCrash expected) {
            // the process died here
        }

        // What survives: the table as committed and the player file as last written.
        assertThat(count(savedContents, Material.DIAMOND) + countListed(Material.DIAMOND, true))
                .as("every diamond is in the saved file or in the list, exactly once").isEqualTo(10);

        store.beforeUpdate = () -> { };
        store.afterUpdate = () -> { };
        PlayerMock restarted = new PlayerMock(server, "Away", away.getUniqueId());
        restarted.getInventory().setContents(copy(savedContents));
        if (savedDeliveries != null) {
            restarted.getPersistentDataContainer().set(DELIVERIES, org.bukkit.persistence.PersistentDataType.STRING, savedDeliveries);
        }
        for (int i = 1; i < 20; i++) {
            restarted.getInventory().setItem(i, null);
        }
        PlayerMock rejoined = saving(restarted);

        service.deliverPendingReturns(rejoined);

        assertThat(count(rejoined, Material.DIAMOND)).as("after the restart the stake is handed over exactly once").isEqualTo(10);
        assertThat(rejoined.getInventory().all(Material.DIAMOND_SWORD)).hasSize(1);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("A failing save of the player leaves the entry marked, and the next join settles it from the player's data")
    void failedPlayerSaveIsSettledAtTheNextJoin() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        PlayerMock joined = org.mockito.Mockito.spy(away);
        org.mockito.Mockito.doThrow(new IllegalStateException("disk full")).when(joined).saveData();

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("handed over in memory").isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).as("the entry stays, marked, until the player file holds the stake")
                .hasSize(1);

        // The server writes the player's data later (autosave or quit); the next join then completes it.
        PlayerMock later = saving(away);
        service.deliverPendingReturns(later);

        assertThat(count(later, Material.DIAMOND)).as("not handed over a second time").isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("An entry that cannot be marked is not handed over, and is handed over once at a later join")
    void markFailureDeliversNothingThenOnce() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        PlayerMock joined = saving(away);
        store.failUpdate = true;

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("nothing handed over while the entry cannot be marked").isZero();
        assertThat(countListed(Material.DIAMOND, false)).isEqualTo(10);
        verify(logger).error(any(Throwable.class), argThat((String s) -> s.contains("Away")));

        store.failUpdate = false;
        service.deliverPendingReturns(joined);
        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("delivered exactly once").isEqualTo(10);
    }

    @Test
    @DisplayName("An entry that cannot be completed after the player was saved is completed at the next join, not handed over twice")
    void completeFailureIsSettledWithoutDuplicating() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        PlayerMock joined = saving(away);
        store.failDelete = true;

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).hasSize(1);

        store.failDelete = false;
        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).as("not handed over twice").isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("An unreadable entry is kept and reported; a readable one beside it is still handed over")
    void unreadableEntryIsKept() throws Exception {
        service.completeTrade(sessionWithStakes());
        PendingStakeReturn broken = new PendingStakeReturn();
        broken.setOwnerUuid(away.getUniqueId().toString());
        broken.setItems("items: [");
        store.insert(broken);
        server.addPlayer(away);
        PlayerMock joined = saving(away);

        service.deliverPendingReturns(joined);

        assertThat(count(joined, Material.DIAMOND)).isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).as("only the unreadable entry remains").containsExactly(broken);
        verify(logger).error(any(Throwable.class), argThat((String s) -> s.contains(broken.getId())));
    }

    @Test
    @DisplayName("The stacks column holds more than MySQL's TEXT limit of 65,535 bytes (gate-1 WR-02)")
    void stacksColumnIsLongText() throws Exception {
        com.ultikits.ultitools.annotations.Column column = PendingStakeReturn.class.getDeclaredField("items")
                .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
        // The framework writes the declared type into CREATE TABLE verbatim; a stake of written books
        // or filled shulker boxes serialises well past 64 KiB, and on MySQL a TEXT column would refuse
        // it (strict mode, so the stake falls to the ground) or truncate it (so it can never be read).
        assertThat(column.type()).isEqualTo("LONGTEXT");
    }

    /** Stores exactly what it is given; can be told to fail inserts or deletes. */
    /**
     * Stores copies of what it is given (a caller mutating its own object changes nothing stored until
     * it writes again), like a database; can be told to fail, and runs hooks around updates.
     */
    static final class InMemoryPendingReturns implements DataOperator<PendingStakeReturn> {
        final List<PendingStakeReturn> rows = new ArrayList<>();
        boolean failInsert;
        boolean failDelete;
        boolean failUpdate;
        Runnable beforeUpdate = () -> { };
        Runnable afterUpdate = () -> { };
        private int nextId = 1;

        static PendingStakeReturn copyOf(PendingStakeReturn row) {
            PendingStakeReturn c = new PendingStakeReturn();
            c.setId(row.getId());
            c.setOwnerUuid(row.getOwnerUuid());
            c.setItems(row.getItems());
            c.setStackCount(row.getStackCount());
            c.setCreatedAt(row.getCreatedAt());
            c.setDeliveryToken(row.getDeliveryToken());
            c.setAfterDelivery(row.getAfterDelivery());
            return c;
        }

        List<PendingStakeReturn> rowsOf(UUID owner) {
            List<PendingStakeReturn> out = new ArrayList<>();
            for (PendingStakeReturn row : rows) {
                if (owner.toString().equals(row.getOwnerUuid())) {
                    out.add(copyOf(row));
                }
            }
            return out;
        }

        @Override
        public void insert(PendingStakeReturn entity) {
            if (failInsert) {
                throw new IllegalStateException("simulated storage failure");
            }
            entity.setId(String.valueOf(nextId++));
            rows.add(copyOf(entity));
        }

        @Override
        public List<PendingStakeReturn> getAll(WhereCondition... conditions) {
            List<PendingStakeReturn> out = new ArrayList<>();
            for (PendingStakeReturn row : rows) {
                out.add(copyOf(row));
            }
            for (WhereCondition c : conditions) {
                if (!"owner_uuid".equals(c.getColumn())) {
                    throw new UnsupportedOperationException("unexpected column " + c.getColumn());
                }
                out.removeIf(row -> !String.valueOf(c.getValue()).equals(row.getOwnerUuid()));
            }
            return out;
        }

        @Override
        public void delById(Object id) {
            beforeUpdate.run();
            if (failDelete) {
                throw new IllegalStateException("simulated delete failure");
            }
            rows.removeIf(row -> row.getId().equals(id));
            afterUpdate.run();
        }

        @Override
        public void update(PendingStakeReturn entity) {
            beforeUpdate.run();
            if (failUpdate) {
                throw new IllegalStateException("simulated update failure");
            }
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getId().equals(entity.getId())) {
                    rows.set(i, copyOf(entity));
                }
            }
            afterUpdate.run();
        }

        @Override
        public List<PendingStakeReturn> getAll() {
            return getAll(new WhereCondition[0]);
        }

        @Override
        public boolean exist(PendingStakeReturn entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exist(WhereCondition... conditions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingStakeReturn getById(Object id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingStakeReturn> getLike(String column, String value, LikeType type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingStakeReturn> page(int page, int size, WhereCondition... conditions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void del(WhereCondition... conditions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(String column, Object value, Object id) {
            throw new UnsupportedOperationException();
        }
    }
}
