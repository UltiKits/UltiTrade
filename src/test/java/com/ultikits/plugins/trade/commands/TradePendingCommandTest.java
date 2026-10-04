package com.ultikits.plugins.trade.commands;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdTarget;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operator command for hand-overs a crash left held (maintainer decision of 2026-10-04, "UltiTrade
 * pending-return crash reconciliation"; UltiKits/UltiTrade#55): {@code /trade pending list},
 * {@code /trade pending redeliver <id>}, {@code /trade pending void <id>}.
 */
@DisplayName("/trade pending: list and resolve held hand-overs (UltiTrade#55)")
class TradePendingCommandTest {

    private TradeService tradeService;
    private TradeCommand command;
    private CommandSender console;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        when(UltiTradeTestHelper.getMockPlugin().i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        tradeService = mock(TradeService.class);
        command = new TradeCommand(UltiTradeTestHelper.getMockPlugin(), tradeService, mock(TradeLogService.class));
        console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private List<String> sent() {
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(console, atLeastOnce()).sendMessage(lines.capture());
        return lines.getAllValues();
    }

    @Test
    @DisplayName("list names each held row: id, player name and UUID, items, claim time and server")
    void listNamesEveryField() {
        when(tradeService.heldClaims()).thenReturn(Collections.singletonList(new TradeService.HeldClaim(
                "row-1", "11111111-1111-1111-1111-111111111111", "Away", "DIAMOND x10, EMERALD x1",
                1759550400000L, "server-a")));

        command.pendingList(console);

        String all = String.join("\n", sent());
        assertThat(all).contains("row-1", "Away", "11111111-1111-1111-1111-111111111111", "DIAMOND x10, EMERALD x1",
                "server-a", "2025");
    }

    @Test
    @DisplayName("list with nothing held says so")
    void listEmpty() {
        when(tradeService.heldClaims()).thenReturn(Collections.emptyList());

        command.pendingList(console);

        assertThat(String.join("\n", sent())).contains("No").doesNotContain("row-");
    }

    @Test
    @DisplayName("redeliver and void resolve the row by id and report the outcome")
    void resolveReportsTheOutcome() {
        when(tradeService.redeliverHeld("row-1")).thenReturn(TradeService.HeldResolution.DONE);
        when(tradeService.voidHeld("row-2")).thenReturn(TradeService.HeldResolution.NOT_HELD);

        command.pendingRedeliver(console, "row-1");
        command.pendingVoid(console, "row-2");

        verify(tradeService).redeliverHeld("row-1");
        verify(tradeService).voidHeld("row-2");
        List<String> lines = sent();
        assertThat(lines.get(0)).contains("row-1").contains("next join");
        assertThat(lines.get(1)).contains("row-2").contains("not held");
    }

    @Test
    @DisplayName("every pending subcommand is permission-gated and usable from the console")
    void gatedAndConsoleUsable() {
        for (String name : Arrays.asList("pendingList", "pendingRedeliver", "pendingVoid")) {
            Method method = Arrays.stream(TradeCommand.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
            assertThat(method.getAnnotation(CmdMapping.class).permission()).as(name).isEqualTo("ultitrade.admin");
            assertThat(method.getAnnotation(CmdTarget.class)).as(name).isNotNull();
            assertThat(method.getAnnotation(CmdTarget.class).value()).as(name).isEqualTo(CmdTarget.CmdTargetType.BOTH);
        }
    }
}
