package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A player's experience total at extreme levels
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a> item 1; maintainer decision
 * 2026-10-06 00:48: compute in {@code long} and clamp).
 *
 * <h2>The defect</h2>
 * {@code pointsToReachLevel}'s last branch computed {@code 9 * level * level} in {@code int}, which overflows
 * from level 15,466: a player set to 16,000 levels read a negative total, so every experience offer was
 * refused as insufficient and {@code /trade} showed a negative total.
 *
 * <h2>The reference</h2>
 * Minecraft's own table, evaluated exactly in {@link BigInteger}: {@code L^2 + 6L} up to 16,
 * {@code (5L^2 - 81L) / 2 + 360} from 17 to 31, {@code (9L^2 - 325L) / 2 + 2220} above. The module's answer
 * must equal it where it fits in an {@code int} and be {@code Integer.MAX_VALUE} where it does not.
 */
@DisplayName("Experience totals at extreme levels are never negative (UltiTrade#65 item 1)")
class TradeExperienceLevelTotalTest {

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private static BigInteger exact(long level) {
        BigInteger l = BigInteger.valueOf(level);
        if (level <= 16) {
            return l.multiply(l).add(l.multiply(BigInteger.valueOf(6)));
        } else if (level <= 31) {
            return l.multiply(l).multiply(BigInteger.valueOf(5)).subtract(l.multiply(BigInteger.valueOf(81)))
                    .divide(BigInteger.valueOf(2)).add(BigInteger.valueOf(360));
        }
        return l.multiply(l).multiply(BigInteger.valueOf(9)).subtract(l.multiply(BigInteger.valueOf(325)))
                .divide(BigInteger.valueOf(2)).add(BigInteger.valueOf(2220));
    }

    private static int clamped(BigInteger value) {
        return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
    }

    @Test
    @DisplayName("Control: every level from 0 to 15,465 reads exactly Minecraft's table (spot values 7, 352, 394, 1395, 1507, 1628, 30970)")
    void levelsBelowTheOldOverflowAreExact() {
        for (int level = 0; level <= 15_465; level++) {
            assertThat(TradeService.pointsToReachLevel(level)).as("level " + level).isEqualTo(exact(level).intValueExact());
        }
        assertThat(TradeService.pointsToReachLevel(1)).isEqualTo(7);
        assertThat(TradeService.pointsToReachLevel(16)).isEqualTo(352);
        assertThat(TradeService.pointsToReachLevel(17)).isEqualTo(394);
        assertThat(TradeService.pointsToReachLevel(30)).isEqualTo(1395);
        assertThat(TradeService.pointsToReachLevel(31)).isEqualTo(1507);
        assertThat(TradeService.pointsToReachLevel(32)).isEqualTo(1628);
        assertThat(TradeService.pointsToReachLevel(100)).isEqualTo(30970);
    }

    @ParameterizedTest(name = "level {0}")
    @ValueSource(ints = {15_466, 16_000, 21_000, 21_845, 21_846, 21_863, 21_864, 30_000, 46_341, 1_000_000, 1_000_000_000, Integer.MAX_VALUE})
    @DisplayName("From level 15,466 the total is the exact value while it fits an int, Integer.MAX_VALUE after; never negative")
    void highLevelsAreExactOrClamped(int level) {
        int total = TradeService.pointsToReachLevel(level);

        assertThat(total).isNotNegative();
        assertThat(total).isEqualTo(clamped(exact(level)));
    }

    @Test
    @DisplayName("A player at 16,000 levels with half a level of progress reads a positive total: the exact one, or Integer.MAX_VALUE")
    void playerAtSixteenThousandLevelsReadsAPositiveTotal() {
        Player player = UltiTradeTestHelper.createMockPlayer("Leveller", UUID.randomUUID());
        when(player.getLevel()).thenReturn(16_000);
        when(player.getExp()).thenReturn(0.5f);
        when(player.getExpToLevel()).thenReturn(9 * 16_000 - 158);

        int total = new TradeService().getTotalExperience(player);

        long expected = exact(16_000).longValueExact() + Math.round(0.5f * (9 * 16_000 - 158));
        assertThat(total).isPositive().isEqualTo((int) Math.min(expected, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("A player whose level total is clamped and who holds progress inside the level still reads Integer.MAX_VALUE, not an overflowed sum")
    void clampedLevelPlusProgressDoesNotOverflow() {
        Player player = UltiTradeTestHelper.createMockPlayer("Topped", UUID.randomUUID());
        when(player.getLevel()).thenReturn(30_000);
        when(player.getExp()).thenReturn(0.9f);
        when(player.getExpToLevel()).thenReturn(9 * 30_000 - 158);

        assertThat(new TradeService().getTotalExperience(player)).isEqualTo(Integer.MAX_VALUE);
    }
}
