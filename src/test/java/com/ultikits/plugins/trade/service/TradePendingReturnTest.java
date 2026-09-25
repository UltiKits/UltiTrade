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
    @DisplayName("The next join hands the stake over, metadata intact, and empties the list; a second join delivers nothing")
    void joinDeliversOnceWithMetadata() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);

        service.deliverPendingReturns(away);

        assertThat(count(away, Material.DIAMOND)).isEqualTo(10);
        ItemStack sword = away.getInventory().getItem(away.getInventory().first(Material.DIAMOND_SWORD));
        assertThat(sword).as("name, lore and enchantment survive the round trip").isEqualTo(namedSword());
        assertThat(store.rowsOf(away.getUniqueId())).as("the list is empty afterwards").isEmpty();
        assertThat(away.nextMessage()).contains("returned to you");

        service.deliverPendingReturns(away);

        assertThat(count(away, Material.DIAMOND)).as("a second join delivers nothing").isEqualTo(10);
        assertThat(away.getInventory().all(Material.DIAMOND_SWORD)).hasSize(1);
    }

    @Test
    @DisplayName("The join listener hands the stake over")
    void joinListenerDelivers() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        TradeListener listener = new TradeListener();
        UltiTradeTestHelper.setField(listener, "tradeService", service);

        listener.onPlayerJoin(new PlayerJoinEvent(away, "joined"));

        assertThat(count(away, Material.DIAMOND)).isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("A full inventory at join: what does not fit is dropped at the player's feet, not destroyed")
    void fullInventoryDropsAtFeet() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        // Every slot, not only the 36 storage slots: MockBukkit's addItem also fills armour slots.
        for (int i = 0; i < away.getInventory().getSize(); i++) {
            away.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }

        service.deliverPendingReturns(away);

        int dropped = 0;
        for (Item item : away.getWorld().getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.DIAMOND) {
                dropped += item.getItemStack().getAmount();
            }
        }
        assertThat(dropped).as("the diamonds lie at the player's feet").isEqualTo(10);
        assertThat(store.rowsOf(away.getUniqueId())).isEmpty();
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
    @DisplayName("An entry that cannot be removed is not handed over, and is handed over once at the next join")
    void removeFailureDeliversNothingThenOnce() throws Exception {
        service.completeTrade(sessionWithStakes());
        server.addPlayer(away);
        store.failDelete = true;

        service.deliverPendingReturns(away);

        assertThat(count(away, Material.DIAMOND)).as("nothing handed over while the entry stays").isZero();
        assertThat(store.rowsOf(away.getUniqueId())).hasSize(1);
        verify(logger).error(any(Throwable.class), argThat((String s) -> s.contains("Away")));

        store.failDelete = false;
        service.deliverPendingReturns(away);
        service.deliverPendingReturns(away);

        assertThat(count(away, Material.DIAMOND)).as("delivered exactly once").isEqualTo(10);
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

        service.deliverPendingReturns(away);

        assertThat(count(away, Material.DIAMOND)).isEqualTo(10);
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
    static final class InMemoryPendingReturns implements DataOperator<PendingStakeReturn> {
        final List<PendingStakeReturn> rows = new ArrayList<>();
        boolean failInsert;
        boolean failDelete;
        private int nextId = 1;

        List<PendingStakeReturn> rowsOf(UUID owner) {
            List<PendingStakeReturn> out = new ArrayList<>();
            for (PendingStakeReturn row : rows) {
                if (owner.toString().equals(row.getOwnerUuid())) {
                    out.add(row);
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
            rows.add(entity);
        }

        @Override
        public List<PendingStakeReturn> getAll(WhereCondition... conditions) {
            List<PendingStakeReturn> out = new ArrayList<>(rows);
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
            if (failDelete) {
                throw new IllegalStateException("simulated delete failure");
            }
            rows.removeIf(row -> row.getId().equals(id));
        }

        @Override
        public List<PendingStakeReturn> getAll() {
            return new ArrayList<>(rows);
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

        @Override
        public void update(PendingStakeReturn entity) {
            throw new UnsupportedOperationException();
        }
    }
}
