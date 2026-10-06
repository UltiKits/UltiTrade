package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Whether a player's experience total can be read exactly (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a>,
 * gate-1 finding F1 of PR #66): the true total, computed in {@code long}, must not exceed {@code Integer.MAX_VALUE}. The
 * boundary is decided on the true total, not on the capped value, so a total that is exactly {@code Integer.MAX_VALUE}
 * would still count as readable.
 */
@DisplayName("An experience total is readable exactly when the true total fits an int (UltiTrade#65 F1)")
class TradeExperienceReadableTest {

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @ParameterizedTest(name = "level {0}, progress {1}: readable {2}")
    @CsvSource({
        "30, 0.5, true",
        "21863, 0.0, true",
        // 21,863 levels is 2,147,407,943 points; the level needs 9 * 21863 - 158 = 196,609 more, so the last 75,704 points fit
        "21863, 0.38, true",
        "21863, 0.39, false",
        "21864, 0.0, false",
        "21865, 0.0, false",
        "25000, 0.5, false",
        "2147483647, 0.0, false"
    })
    void readableExactlyWhenTheTrueTotalFits(int level, float progress, boolean readable) {
        Player player = UltiTradeTestHelper.createMockPlayer("Reader", UUID.randomUUID());
        when(player.getLevel()).thenReturn(level);
        when(player.getExp()).thenReturn(progress);
        when(player.getExpToLevel()).thenReturn(level >= 31 ? 9 * level - 158 : level >= 16 ? 5 * level - 38 : 2 * level + 7);

        assertThat(TradeService.isExperienceTotalReadable(player)).isEqualTo(readable);
    }
}
