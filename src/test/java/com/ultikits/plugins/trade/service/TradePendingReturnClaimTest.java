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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A pending stake return on servers sharing one database is handed over by exactly one server
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/55">UltiTrade#55</a>).
 *
 * <h2>The defect</h2>
 * A join decided whether an earlier hand-over "reached disk" by looking for its token in the joining
 * player's persistent data. That data lives in each server's own player file, while the
 * {@code trade_pending_returns} table is shared: a player who moved to another server was handed the
 * same stake again there, once per server hop.
 *
 * <h2>The decision this implements (maintainer, 2026-10-04)</h2>
 * Claim before handing over: a join first claims the row with a conditional write, and only the server
 * whose claim succeeds hands the items over. Accepted cost: a crash between the claim and the hand-over
 * reaching the player's saved data leaves that return undelivered, with a log line naming the player,
 * the items and the row that an operator can act on.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Each server is a real {@link TradeService} over the framework's real SQLite operator, both on one
 * database file, so a claim is the framework's own single-statement conditional write. Each server's
 * player is a separate player object with its own inventory and persistent data, as each server keeps
 * its own player file. The assertions count real items in real inventories and read the table itself.
 */
@DisplayName("A pending stake return is handed over by exactly one server sharing the database (UltiTrade#55)")
class TradePendingReturnClaimTest {

    @TempDir
    Path dir;

    private ServerMock server;
    private World world;
    private SharedSqliteDatabase database;
    private DataOperator<PendingStakeReturn> table;
    private UUID awayId;
    private final List<PluginLogger> loggers = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.tearDown();
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PendingStakeReturn.class);
        awayId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** One server: its own service, logger and operator on the shared database file. */
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

    private PluginLogger loggerOf(int serverIndex) {
        return loggers.get(serverIndex);
    }

    /** A pending return for the away player: 10 diamonds and 1 emerald, saved by a cancelled trade on server A. */
    private void stakeIsPending(TradeService serverA) throws Exception {
        PlayerMock present = server.addPlayer("Present");
        PlayerMock away = new PlayerMock(server, "Away", awayId);
        away.setLocation(world.getSpawnLocation());
        TradeSession session = new TradeSession(present, away);
        session.setItem(awayId, 0, new ItemStack(Material.DIAMOND, 10));
        session.setItem(awayId, 1, new ItemStack(Material.EMERALD, 1));
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(serverA, "activeSessions");
        active.put(session.getSessionId(), session);
        serverA.cancelTrade(session, "test");
        assertThat(rows()).as("precondition: one pending row").hasSize(1);
    }

    /** The away player as one server sees them: its own player object, inventory and saved data. */
    private PlayerMock awayOn() {
        PlayerMock player = org.mockito.Mockito.spy(new PlayerMock(server, "Away", awayId));
        org.mockito.Mockito.doNothing().when(player).saveData();
        return player;
    }

    private List<PendingStakeReturn> rows() {
        return table.getAll(WhereCondition.builder().column("owner_uuid").value(awayId.toString()).build());
    }

    private static int count(PlayerMock player, Material material) {
        int n = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                n += item.getAmount();
            }
        }
        return n;
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

    @Test
    @DisplayName("Server hop: a stake handed over on one server is not handed over again when the player joins another")
    void aServerHopDoesNotHandTheStakeOverAgain() throws Exception {
        TradeService serverA = server();
        TradeService serverB = server();
        stakeIsPending(serverA);
        PlayerMock onB = awayOn();
        PlayerMock onA = awayOn();

        serverB.deliverPendingReturns(onB);
        serverA.deliverPendingReturns(onA); // the player moved to A, whose player file B's hand-over never reached
        serverB.deliverPendingReturns(onB); // and back to B

        assertThat(count(onB, Material.DIAMOND) + count(onA, Material.DIAMOND))
                .as("10 diamonds were staked; exactly 10 handed over across both servers").isEqualTo(10);
        assertThat(count(onB, Material.EMERALD) + count(onA, Material.EMERALD)).isEqualTo(1);
        assertThat(rows()).as("nothing is listed any more").isEmpty();
    }

    @Test
    @DisplayName("Server hop after a partial hand-over: the other server hands over only the part that did not fit")
    void aServerHopAfterAPartialHandOverHandsOverOnlyTheRest() throws Exception {
        TradeService serverA = server();
        TradeService serverB = server();
        stakeIsPending(serverA);
        PlayerMock onB = awayOn();
        fillAllBut(onB, 0); // room for one stack: the diamonds fit, the emerald does not
        PlayerMock onA = awayOn();

        serverB.deliverPendingReturns(onB);
        assertThat(count(onB, Material.DIAMOND)).isEqualTo(10);
        serverA.deliverPendingReturns(onA);
        serverA.deliverPendingReturns(onA);

        assertThat(count(onB, Material.DIAMOND) + count(onA, Material.DIAMOND)).as("diamonds handed over once").isEqualTo(10);
        assertThat(count(onB, Material.EMERALD) + count(onA, Material.EMERALD)).as("the emerald arrives on A, once").isEqualTo(1);
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("Two servers claiming one row at once: the items are handed over exactly once, and the server that lost hands over nothing")
    void twoClaimersOnOneRowHandOverOnce() throws Exception {
        TradeService serverB = server();
        PlayerMock onB = awayOn();
        // Server A reads the row, and server B's whole join runs between A's read and A's claim.
        DataOperator<PendingStakeReturn> aOperator = beforeFirstWrite(database.openAs(PendingStakeReturn.class),
                () -> serverB.deliverPendingReturns(onB));
        TradeService serverA = serverWith(aOperator);
        stakeIsPending(serverB);
        PlayerMock onA = awayOn();

        serverA.deliverPendingReturns(onA);

        assertThat(count(onB, Material.DIAMOND)).as("B claimed first and handed everything over").isEqualTo(10);
        assertThat(count(onB, Material.EMERALD)).isEqualTo(1);
        assertThat(count(onA, Material.DIAMOND)).as("A's claim lost: nothing handed over on A").isZero();
        assertThat(count(onA, Material.EMERALD)).isZero();
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("A hand-over is logged at WARNING naming the player, the items and the row before the items can reach the player's saved data")
    void theWinningClaimIsLoggedForTheOperator() throws Exception {
        TradeService serverA = server();
        stakeIsPending(serverA);
        String rowId = rows().get(0).getId();
        PlayerMock onA = org.mockito.Mockito.spy(new PlayerMock(server, "Away", awayId));
        // The process dies while saving the player: the claim is committed, the hand-over never reached disk.
        org.mockito.Mockito.doThrow(new SimulatedCrash()).when(onA).saveData();

        try {
            serverA.deliverPendingReturns(onA);
        } catch (SimulatedCrash expected) {
            // the server died here
        }

        verify(loggerOf(0), atLeastOnce()).warn(argThat((String line) -> line.contains("Away") && line.contains(rowId)
                && line.contains("DIAMOND x10") && line.contains("EMERALD x1")));
        assertThat(rows()).as("accepted cost: the claimed stake is no longer listed, so it is never handed over twice").isEmpty();
    }

    @Test
    @DisplayName("A claim that lost: the hand-over line it wrote is followed by one saying nothing from that entry was handed over")
    void aLostClaimIsLoggedAsNothingHandedOver() throws Exception {
        TradeService serverB = server();
        PlayerMock onB = awayOn();
        DataOperator<PendingStakeReturn> aOperator = beforeFirstWrite(database.openAs(PendingStakeReturn.class),
                () -> serverB.deliverPendingReturns(onB));
        TradeService serverA = serverWith(aOperator);
        stakeIsPending(serverB);
        String rowId = rows().get(0).getId();

        serverA.deliverPendingReturns(awayOn());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(loggerOf(1));
        order.verify(loggerOf(1)).warn(argThat((String line) -> line.contains(rowId) && line.contains("DIAMOND x10")));
        order.verify(loggerOf(1)).warn(argThat((String line) -> line.contains(rowId) && line.contains("nothing from it was handed over")));
    }

    @Test
    @DisplayName("A hand-over whose player save returned writes one line naming the player, UUID, row and items; one whose save crashed writes none (gate-1 top-up F1)")
    void aSavedHandOverIsConfirmedPerEntry() throws Exception {
        TradeService serverA = server();
        stakeIsPending(serverA);
        String rowId = rows().get(0).getId();
        PlayerMock onA = awayOn();

        serverA.deliverPendingReturns(onA);

        verify(loggerOf(0)).info(argThat((String line) -> line.contains(rowId) && line.contains("Away")
                && line.contains(awayId.toString()) && line.contains("DIAMOND x10") && line.contains("saved")));
    }

    @Test
    @DisplayName("A crash inside the player save leaves no saved line for the entry, so the operator's record says to give the items back (gate-1 top-up F1)")
    void aCrashInsideTheSaveLeavesNoSavedLine() throws Exception {
        TradeService serverA = server();
        stakeIsPending(serverA);
        String rowId = rows().get(0).getId();
        PlayerMock onA = org.mockito.Mockito.spy(new PlayerMock(server, "Away", awayId));
        org.mockito.Mockito.doThrow(new SimulatedCrash()).when(onA).saveData();

        try {
            serverA.deliverPendingReturns(onA);
        } catch (SimulatedCrash expected) {
            // the server died here
        }

        verify(loggerOf(0), org.mockito.Mockito.never()).info(argThat((String line) -> line.contains(rowId) && line.contains("saved")));
    }

    @Test
    @DisplayName("Order: the hand-over line precedes the claim, the claim precedes the player save, and the saved line follows the save (gate-1 top-up F4)")
    void theRecordBracketsTheClaimAndTheSave() throws Exception {
        List<String> events = new ArrayList<>();
        DataOperator<PendingStakeReturn> recording = recordingClaims(database.openAs(PendingStakeReturn.class), events);
        TradeService serverA = serverWith(recording);
        org.mockito.Mockito.doAnswer(inv -> events.add("warn:" + inv.getArgument(0))).when(loggerOf(0)).warn(anyString());
        org.mockito.Mockito.doAnswer(inv -> events.add("info:" + inv.getArgument(0))).when(loggerOf(0)).info(anyString());
        stakeIsPending(serverA);
        String rowId = rows().get(0).getId();
        PlayerMock onA = org.mockito.Mockito.spy(new PlayerMock(server, "Away", awayId));
        org.mockito.Mockito.doAnswer(inv -> events.add("save")).when(onA).saveData();
        events.clear();

        serverA.deliverPendingReturns(onA);

        int handing = indexOf(events, e -> e.startsWith("warn:") && e.contains(rowId) && e.contains("DIAMOND x10"));
        int claim = events.indexOf("claim");
        int save = events.indexOf("save");
        int saved = indexOf(events, e -> e.startsWith("info:") && e.contains(rowId) && e.contains("saved"));
        assertThat(handing).as("hand-over line written").isNotNegative();
        assertThat(claim).as("claim made").isGreaterThan(handing);
        assertThat(save).as("player saved after the claim").isGreaterThan(claim);
        assertThat(saved).as("saved line after the save").isGreaterThan(save);
    }

    @Test
    @DisplayName("A claim that committed but whose call threw is not reported as 'tried again': the line says the outcome is unknown, naming row and items (gate-1 top-up F2)")
    void aClaimThatCommittedThenThrewIsReportedAsUnknown() throws Exception {
        DataOperator<PendingStakeReturn> real = database.openAs(PendingStakeReturn.class);
        @SuppressWarnings("unchecked")
        DataOperator<PendingStakeReturn> throwingAfterCommit = (DataOperator<PendingStakeReturn>) java.lang.reflect.Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().equals("updateIf")) {
                        throw new IllegalStateException("connection lost after the statement was applied");
                    }
                    return result;
                });
        TradeService serverA = serverWith(throwingAfterCommit);
        stakeIsPending(serverA);
        String rowId = rows().get(0).getId();
        PlayerMock onA = awayOn();

        serverA.deliverPendingReturns(onA);

        verify(loggerOf(0), org.mockito.Mockito.never()).error(any(Throwable.class),
                argThat((String line) -> line.contains("tried again")));
        verify(loggerOf(0)).error(any(Throwable.class), argThat((String line) -> line.contains(rowId)
                && line.contains("unknown") && line.contains("DIAMOND x10")));
        assertThat(count(onA, Material.DIAMOND)).as("items taken back: whether they were claimed is not known").isZero();
    }

    private static int indexOf(List<String> events, java.util.function.Predicate<String> match) {
        for (int i = 0; i < events.size(); i++) {
            if (match.test(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** {@code operator}, recording "claim" in {@code events} at each conditional update. */
    @SuppressWarnings("unchecked")
    private static DataOperator<PendingStakeReturn> recordingClaims(DataOperator<PendingStakeReturn> operator, List<String> events) {
        return (DataOperator<PendingStakeReturn>) java.lang.reflect.Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    if (method.getName().equals("updateIf")) {
                        events.add("claim");
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** A process death at a chosen point; nothing after it runs. */
    static final class SimulatedCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    /** {@code operator}, with {@code hook} run once just before its first write (update, conditional update or delete). */
    @SuppressWarnings("unchecked")
    private static DataOperator<PendingStakeReturn> beforeFirstWrite(DataOperator<PendingStakeReturn> operator, Runnable hook) {
        boolean[] ran = {false};
        return (DataOperator<PendingStakeReturn>) java.lang.reflect.Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    boolean write = name.equals("update") || name.equals("updateCounted") || name.equals("updateIf")
                            || name.equals("delById") || name.equals("del");
                    if (write && !ran[0]) {
                        ran[0] = true;
                        hook.run();
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
