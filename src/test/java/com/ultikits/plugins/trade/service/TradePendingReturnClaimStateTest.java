package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PendingStakeReturn;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The database records each pending-return hand-over's state, and the operator resolves what a crash
 * left behind with a command (maintainer decision of 2026-10-04, "UltiTrade pending-return crash
 * reconciliation": 数据库记状态，用命令交给服主; UltiKits/UltiTrade#55).
 *
 * <h2>The decision this implements</h2>
 * Before handing over, the row is marked CLAIMED with a conditional write; after the player's data save
 * returns, the row is removed. A row left CLAIMED by a crash anywhere in between is never handed over
 * again by a join; start-up and reload warn with the count, and an operator command lists such rows and
 * resolves each one: redeliver or void. A partial hand-over leaves the part that did not fit as an
 * unclaimed row.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * The table is the framework's real SQLite operator with a real transaction manager; a "restart" is a
 * new service and a player object rebuilt from the contents the last {@code saveData} wrote; every
 * assertion reads the table, that saved file, or a live inventory.
 */
@DisplayName("A hand-over's state is in the database; a crash leaves a held row the operator resolves (UltiTrade#55)")
class TradePendingReturnClaimStateTest {

    @TempDir
    Path dir;

    private ServerMock server;
    private World world;
    private SharedSqliteDatabase database;
    private DataOperator<PendingStakeReturn> table;
    private UUID awayId;
    private final List<PluginLogger> loggers = new ArrayList<>();

    /** The player's data file as the last saveData wrote it. */
    private ItemStack[] savedFile;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.tearDown();
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PendingStakeReturn.class);
        awayId = UUID.randomUUID();
        savedFile = new ItemStack[0];
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** A process death at a chosen point; nothing after it runs. */
    static final class SimulatedCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    private TradeService serverWith(DataOperator<PendingStakeReturn> operator) throws Exception {
        UltiTrade plugin = mock(UltiTrade.class);
        PluginLogger logger = mock(PluginLogger.class);
        loggers.add(logger);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        lenient().when(plugin.getDataOperator(PendingStakeReturn.class)).thenReturn(operator);
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.isEnableSounds()).thenReturn(false);
        lenient().when(config.isEnableParticles()).thenReturn(false);
        lenient().when(config.isEnableMoneyTrade()).thenReturn(false);
        TradeService service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", mock(TradeLogService.class));
        UltiTradeTestHelper.setField(service, "economy", mock(Economy.class));
        UltiTradeTestHelper.setField(service, "pendingReturns", operator);
        return service;
    }

    private TradeService server() throws Exception {
        return serverWith(database.openAs(PendingStakeReturn.class));
    }

    /** A pending return for the away player: 10 diamonds and 1 emerald. */
    private void stakeIsPending(TradeService service) throws Exception {
        PlayerMock present = server.addPlayer("Present");
        PlayerMock away = new PlayerMock(server, "Away", awayId);
        away.setLocation(world.getSpawnLocation());
        server.getPlayerList().addOfflinePlayer(away); // has played here before: its name is known
        TradeSession session = new TradeSession(present, away);
        session.setItem(awayId, 0, new ItemStack(Material.DIAMOND, 10));
        session.setItem(awayId, 1, new ItemStack(Material.EMERALD, 1));
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(service, "activeSessions");
        active.put(session.getSessionId(), session);
        service.cancelTrade(session, "test");
        assertThat(rows()).hasSize(1);
    }

    /** The away player as a join sees them: built from the saved file; saveData writes the file, with hooks around it. */
    private PlayerMock joining(Runnable beforeSave, Runnable afterSave) {
        PlayerMock player = org.mockito.Mockito.spy(new PlayerMock(server, "Away", awayId));
        player.getInventory().setContents(copy(savedFile.length == 0 ? player.getInventory().getContents() : savedFile));
        org.mockito.Mockito.doAnswer(inv -> {
            beforeSave.run();
            savedFile = copy(player.getInventory().getContents());
            afterSave.run();
            return null;
        }).when(player).saveData();
        return player;
    }

    private PlayerMock joining() {
        return joining(() -> { }, () -> { });
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = contents[i] == null ? null : contents[i].clone();
        }
        return out;
    }

    private static int count(ItemStack[] contents, Material material) {
        int n = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                n += item.getAmount();
            }
        }
        return n;
    }

    private List<PendingStakeReturn> rows() {
        return table.getAll(WhereCondition.builder().column("owner_uuid").value(awayId.toString()).build());
    }

    private static void fillAllBut(PlayerMock player, int... free) {
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

    /** {@code operator}, with {@code before}/{@code after} run around the named method's first call. */
    @SuppressWarnings("unchecked")
    private static DataOperator<PendingStakeReturn> around(DataOperator<PendingStakeReturn> operator, String methodName,
                                                          Runnable before, Runnable after) {
        boolean[] ran = {false};
        return (DataOperator<PendingStakeReturn>) java.lang.reflect.Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    boolean hook = method.getName().equals(methodName) && !ran[0];
                    if (hook) {
                        ran[0] = true;
                        before.run();
                    }
                    Object result;
                    try {
                        result = method.invoke(operator, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (hook) {
                        after.run();
                    }
                    return result;
                });
    }

    private static final Runnable NOTHING = () -> { };
    private static final Runnable CRASH = () -> {
        throw new SimulatedCrash();
    };

    /** Run the join until the simulated process death, if any. */
    private static void untilCrash(Runnable join) {
        try {
            join.run();
        } catch (SimulatedCrash expected) {
            // the process died here
        }
    }

    // ==================== Crash points ====================

    @Test
    @DisplayName("Crash before the claim: nothing is held, and the join after the restart hands the stake over once")
    void crashBeforeTheClaim() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "transaction", CRASH, NOTHING));
        stakeIsPending(server());
        untilCrash(() -> serverA.deliverPendingReturns(joining()));

        TradeService restarted = server();
        assertThat(restarted.heldClaims()).isEmpty();
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        restarted.deliverPendingReturns(rejoined);

        assertThat(count(savedFile, Material.DIAMOND)).isEqualTo(10);
        assertThat(count(savedFile, Material.EMERALD)).isEqualTo(1);
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("Crash after the claim committed, before the items reached the player's file: the row is held, a later join hands nothing over, and the start-up warning counts it")
    void crashAfterTheClaimBeforeTheSave() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "transaction", NOTHING, CRASH));
        stakeIsPending(server());
        untilCrash(() -> serverA.deliverPendingReturns(joining()));

        TradeService restarted = server();
        assertThat(restarted.warnAboutHeldClaims()).as("start-up warning count").isEqualTo(1);
        verify(loggers.get(loggers.size() - 1)).warn(argThat((String line) -> line.contains("1") && line.contains("/trade pending")));
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);

        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).as("a held row is never handed over by a join").isZero();
        List<TradeService.HeldClaim> held = restarted.heldClaims();
        assertThat(held).hasSize(1);
        assertThat(held.get(0).getOwnerUuid()).isEqualTo(awayId.toString());
        assertThat(held.get(0).getOwnerName()).isEqualTo("Away");
        assertThat(held.get(0).getItems()).contains("DIAMOND x10").contains("EMERALD x1");
        assertThat(held.get(0).getClaimedAt()).isPositive();
        assertThat(held.get(0).getServer()).isNotBlank();
    }

    @Test
    @DisplayName("Crash inside the player save, before the file is written: the row is held, the file holds nothing")
    void crashInsideTheSave() throws Exception {
        TradeService serverA = server();
        stakeIsPending(serverA);
        untilCrash(() -> serverA.deliverPendingReturns(joining(CRASH, NOTHING)));

        assertThat(count(savedFile, Material.DIAMOND)).isZero();
        TradeService restarted = server();
        restarted.deliverPendingReturns(joining());
        assertThat(count(savedFile, Material.DIAMOND)).as("not handed over by a join").isZero();
        assertThat(restarted.heldClaims()).hasSize(1);
    }

    @Test
    @DisplayName("Crash after the player save, before the row is removed: the row is held and the file holds the items, so a join hands nothing over again")
    void crashAfterTheSaveBeforeTheConfirm() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "delById", CRASH, NOTHING));
        stakeIsPending(server());
        untilCrash(() -> serverA.deliverPendingReturns(joining()));

        assertThat(count(savedFile, Material.DIAMOND)).isEqualTo(10);
        TradeService restarted = server();
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).as("exactly once").isEqualTo(10);
        assertThat(restarted.heldClaims()).hasSize(1);
    }

    @Test
    @DisplayName("Crash after the row was removed: nothing is held and nothing is handed over again")
    void crashAfterTheConfirm() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "delById", NOTHING, CRASH));
        stakeIsPending(server());
        untilCrash(() -> serverA.deliverPendingReturns(joining()));

        TradeService restarted = server();
        assertThat(restarted.heldClaims()).isEmpty();
        assertThat(restarted.warnAboutHeldClaims()).isZero();
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).isEqualTo(10);
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("Partial hand-over with a crash after the claim: only the handed part is held; the part that did not fit stays an unclaimed row a later join delivers")
    void partialHandOverHoldsOnlyTheHandedPart() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "transaction", NOTHING, CRASH));
        stakeIsPending(server());
        untilCrash(() -> {
            PlayerMock player = joining();
            fillAllBut(player, 0); // the diamonds fit, the emerald does not
            serverA.deliverPendingReturns(player);
        });

        TradeService restarted = server();
        List<TradeService.HeldClaim> held = restarted.heldClaims();
        assertThat(held).hasSize(1);
        assertThat(held.get(0).getItems()).contains("DIAMOND x10").doesNotContain("EMERALD");
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        assertThat(count(rejoined.getInventory().getContents(), Material.EMERALD)).as("the remainder arrives").isEqualTo(1);
        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).as("the held part does not").isZero();
    }

    @Test
    @DisplayName("A claim whose call committed and then threw is recognised as this join's own claim: the hand-over completes, nothing is held")
    void aClaimThatCommittedThenThrewCompletes() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "transaction", NOTHING,
                () -> { throw new IllegalStateException("connection lost after the commit"); }));
        stakeIsPending(server());
        PlayerMock player = joining();

        serverA.deliverPendingReturns(player);

        assertThat(count(player.getInventory().getContents(), Material.DIAMOND)).isEqualTo(10);
        assertThat(rows()).isEmpty();
        assertThat(serverA.heldClaims()).isEmpty();
    }

    // ==================== Resolving a held row ====================

    private String crashLeavesAHeldRow() throws Exception {
        TradeService serverA = serverWith(around(database.openAs(PendingStakeReturn.class), "transaction", NOTHING, CRASH));
        stakeIsPending(server());
        untilCrash(() -> serverA.deliverPendingReturns(joining()));
        return server().heldClaims().get(0).getId();
    }

    @Test
    @DisplayName("Redeliver: the held row becomes unclaimed with its items, and the next join hands it over once")
    void redeliverReleasesTheRow() throws Exception {
        String id = crashLeavesAHeldRow();
        TradeService restarted = server();

        assertThat(restarted.redeliverHeld(id)).isEqualTo(TradeService.HeldResolution.DONE);

        assertThat(restarted.heldClaims()).isEmpty();
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        restarted.deliverPendingReturns(rejoined);
        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).isEqualTo(10);
        assertThat(count(rejoined.getInventory().getContents(), Material.EMERALD)).isEqualTo(1);
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("Void: the held row is removed and logged, and nothing is handed over")
    void voidRemovesTheRow() throws Exception {
        String id = crashLeavesAHeldRow();
        TradeService restarted = server();

        assertThat(restarted.voidHeld(id)).isEqualTo(TradeService.HeldResolution.DONE);

        assertThat(rows()).isEmpty();
        verify(loggers.get(loggers.size() - 1)).warn(argThat((String line) -> line.contains(id) && line.contains("DIAMOND x10")));
        PlayerMock rejoined = joining();
        restarted.deliverPendingReturns(rejoined);
        assertThat(count(rejoined.getInventory().getContents(), Material.DIAMOND)).isZero();
    }

    @Test
    @DisplayName("Resolving a row that is not held, or resolving it twice from two servers, changes nothing the second time")
    void resolvingTwiceIsRefused() throws Exception {
        String id = crashLeavesAHeldRow();
        TradeService first = server();
        TradeService second = server();

        assertThat(first.redeliverHeld(id)).isEqualTo(TradeService.HeldResolution.DONE);
        assertThat(second.voidHeld(id)).as("no longer held: the void is refused").isEqualTo(TradeService.HeldResolution.NOT_HELD);
        assertThat(rows()).as("the released row is still there for the next join").hasSize(1);
        assertThat(first.redeliverHeld("no-such-id")).isEqualTo(TradeService.HeldResolution.NOT_HELD);
    }

    @Test
    @DisplayName("No held rows: start-up writes no warning")
    void noHeldRowsNoWarning() throws Exception {
        TradeService serverA = server();
        stakeIsPending(serverA);

        assertThat(serverA.warnAboutHeldClaims()).isZero();
        verify(loggers.get(0), never()).warn(argThat((String line) -> line.contains("/trade pending")));
    }
}
