package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * UltiKits/UltiTrade#15: `/ul reload UltiTrade` re-reads `config/trade.yml` through
 * {@code ConfigManager#reloadConfigs}, which calls {@code init(plugin)} again on the SAME
 * {@link TradeConfig} instance the container injected into {@link TradeService}. This test
 * proves the service observes such an in-place re-init on its very next request, i.e. it does
 * not cache a config value when it is created.
 */
@DisplayName("TradeService observes an in-place TradeConfig reload (UltiKits/UltiTrade#15)")
class TradeConfigReloadTest {

    @TempDir
    Path moduleFolder;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Test
    @DisplayName("editing max-distance and re-initialising the same config refuses the next too-far request")
    void inPlaceReloadOfMaxDistanceIsObserved() throws Exception {
        File configFile = moduleFolder.resolve("config").resolve("trade.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
        write(configFile, "max-distance: 50\n");

        UltiToolsPlugin plugin = mock(UltiTrade.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());

        TradeConfig config = new TradeConfig();
        config.init(plugin);
        assertThat(config.getMaxDistance()).isEqualTo(50);

        TradeService service = new TradeService();
        TradeLogService logService = mock(TradeLogService.class);
        when(logService.isTradeEnabled(any())).thenReturn(true);
        when(logService.isBlocked(any(), any())).thenReturn(false);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", logService);
        UltiTradeTestHelper.setField(service, "economy", mock(Economy.class));

        World world = mock(World.class);
        Player sender = UltiTradeTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        Player target = UltiTradeTestHelper.createMockPlayer("Target", UUID.randomUUID());
        when(sender.getWorld()).thenReturn(world);
        when(target.getWorld()).thenReturn(world);
        when(sender.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(target.getLocation()).thenReturn(new Location(world, 20, 64, 0));

        assertThat(service.sendRequest(sender, target)).isTrue();
        verify(sender, never()).sendMessage(contains("距离太远"));

        write(configFile, "max-distance: 10\n");
        config.init(plugin);

        assertThat(service.sendRequest(sender, target)).isFalse();
        verify(sender).sendMessage(contains("距离太远"));
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
