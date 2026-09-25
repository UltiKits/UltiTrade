package com.ultikits.plugins.trade.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for UltiTrade.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Getter
@Setter
@ConfigEntity(TradeConfig.CONFIG_FILE)
public class TradeConfig extends AbstractConfigEntity {

    /**
     * This entity's file, relative to the module's folder. The one place the path is written: the
     * annotation above, the constructor and the removed-key check in {@code UltiTrade} all read it
     * from here, so they cannot drift apart (UltiKits/UltiTrade#17, #18).
     */
    public static final String CONFIG_FILE = "config/trade.yml";
    
    // ==================== Basic Settings ====================

    @Range(min = 5, max = 600)
    @ConfigEntry(path = "request-timeout", comment = "交易请求超时时间（秒）")
    private int requestTimeout = 30;

    @Range(min = 0, max = 1000)
    @ConfigEntry(path = "max-distance", comment = "交易最大距离（格），0为无限制")
    private int maxDistance = 50;
    
    @ConfigEntry(path = "allow-cross-world", comment = "允许跨世界交易")
    private boolean allowCrossWorld = false;
    
    // ==================== Trade Features ====================
    
    @ConfigEntry(path = "enable-money-trade", comment = "启用金币交易（需要Vault）")
    private boolean enableMoneyTrade = true;
    
    @ConfigEntry(path = "enable-exp-trade", comment = "启用经验交易")
    private boolean enableExpTrade = true;
    
    @ConfigEntry(path = "enable-shift-click", comment = "启用Shift+右键玩家发起交易")
    private boolean enableShiftClick = true;
    
    // ==================== Tax Settings ====================

    @Range(min = 0.0, max = 1.0)
    @ConfigEntry(path = "trade-tax", comment = "金币交易税率（0-1之间，0为不收税）")
    private double tradeTax = 0.0;

    @Range(min = 0.0, max = 1.0)
    @ConfigEntry(path = "exp-tax-rate", comment = "经验交易税率（0-1之间，0为不收税）")
    private double expTaxRate = 0.0;

    // ==================== Confirmation Settings ====================

    @Range(min = 0.0, max = 1000000000.0)
    @ConfigEntry(path = "confirm-threshold", comment = "大额交易确认阈值（金币或经验超过此值需二次确认）")
    private double confirmThreshold = 10000;
    
    // ==================== Log Settings ====================

    @ConfigEntry(path = "enable-trade-log", comment = "启用交易日志记录")
    private boolean enableTradeLog = true;

    @Range(min = 1, max = 365)
    @ConfigEntry(path = "log-retention-days", comment = "日志保留天数")
    private int logRetentionDays = 30;

    @Range(min = 1, max = 168)
    @ConfigEntry(path = "cleanup-interval-hours", comment = "日志清理间隔（小时）")
    private int cleanupIntervalHours = 24;
    
    // ==================== Effect Settings ====================
    
    @ConfigEntry(path = "enable-sounds", comment = "启用交易音效")
    private boolean enableSounds = true;
    
    @ConfigEntry(path = "enable-particles", comment = "启用交易粒子效果")
    private boolean enableParticles = true;
    
    @ConfigEntry(path = "enable-bossbar", comment = "启用BossBar请求倒计时")
    private boolean enableBossbar = true;
    
    @ConfigEntry(path = "enable-clickable-buttons", comment = "启用可点击的聊天按钮")
    private boolean enableClickableButtons = true;
    
    // ==================== GUI Settings ====================

    @NotEmpty
    @ConfigEntry(path = "gui-title", comment = "交易界面标题")
    private String guiTitle = SHIPPED_GUI_TITLE;
    
    // ==================== Messages ====================

    @NotEmpty
    @ConfigEntry(path = "messages.request-sent", comment = "发送交易请求")
    private String requestSentMessage = SHIPPED_REQUEST_SENT_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.request-received", comment = "收到交易请求")
    private String requestReceivedMessage = SHIPPED_REQUEST_RECEIVED_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.request-timeout", comment = "请求超时")
    private String requestTimeoutMessage = SHIPPED_REQUEST_TIMEOUT_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.trade-complete", comment = "交易完成")
    private String tradeCompleteMessage = SHIPPED_TRADE_COMPLETE_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.trade-cancelled", comment = "交易取消")
    private String tradeCancelledMessage = SHIPPED_TRADE_CANCELLED_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.trade-disabled", comment = "交易已关闭")
    private String tradeDisabledMessage = SHIPPED_TRADE_DISABLED_MESSAGE;

    @NotEmpty
    @ConfigEntry(path = "messages.player-blocked", comment = "被拉黑")
    private String playerBlockedMessage = SHIPPED_PLAYER_BLOCKED_MESSAGE;
    
    // ==================== Text defaults an earlier version shipped ====================
    // The trade-window title and every message below shipped one fixed Chinese default in every
    // earlier version. Each is still the setting's Java default, which the framework writes for a
    // missing key, and one of the values materializeText() recognises as built-in text in an operator's
    // file; that method then writes the language file's text in the server's language (maintainer
    // decision 2026-09-25, UltiKits/UltiTrade#16).

    /** The default every earlier version shipped for {@code gui-title}; the Java default, compared byte for byte. */
    static final String SHIPPED_GUI_TITLE = "&6与 {PLAYER} 交易";

    /** The default every earlier version shipped for {@code messages.request-sent}; the Java default, compared byte for byte. */
    static final String SHIPPED_REQUEST_SENT_MESSAGE = "&a已向 &f{PLAYER} &a发送交易请求！";

    /** The default every earlier version shipped for {@code messages.request-received}; the Java default, compared byte for byte. */
    static final String SHIPPED_REQUEST_RECEIVED_MESSAGE = "&e{PLAYER} &f请求与你交易！输入 /trade accept 接受";

    /** The default every earlier version shipped for {@code messages.request-timeout}; the Java default, compared byte for byte. */
    static final String SHIPPED_REQUEST_TIMEOUT_MESSAGE = "&c交易请求已超时！";

    /** The default every earlier version shipped for {@code messages.trade-complete}; the Java default, compared byte for byte. */
    static final String SHIPPED_TRADE_COMPLETE_MESSAGE = "&a交易完成！";

    /** The default every earlier version shipped for {@code messages.trade-cancelled}; the Java default, compared byte for byte. */
    static final String SHIPPED_TRADE_CANCELLED_MESSAGE = "&c交易已取消！";

    /** The default every earlier version shipped for {@code messages.trade-disabled}; the Java default, compared byte for byte. */
    static final String SHIPPED_TRADE_DISABLED_MESSAGE = "&c对方已关闭交易功能！";

    /** The default every earlier version shipped for {@code messages.player-blocked}; the Java default, compared byte for byte. */
    static final String SHIPPED_PLAYER_BLOCKED_MESSAGE = "&c对方已将你加入黑名单！";

    public TradeConfig() {
        super(CONFIG_FILE);
    }

    /**
     * Writes the trade-window title and every message in the server's language (maintainer decision
     * 2026-09-25, UltiKits/UltiTrade#16): each setting whose value is still built-in text -- the default
     * an earlier version shipped, or this jar's text for it in any language -- and differs from the
     * current text is replaced with {@code text}'s current text, when that text fits the setting's own
     * limits. Any other value is the operator's and is kept. Idempotent. Must run after the module's
     * language is loaded ({@code registerSelf()} and {@code onReload()}), never from a change listener;
     * the caller saves the file when this returns {@code true}.
     *
     * @param text the module's {@code i18n}: catalogue key to text in the server's language
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(TradeConfig.class);
        boolean[] changed = {false};
        guiTitle = follow("guiTitle", guiTitle, text, jar, "gui_title", SHIPPED_GUI_TITLE, changed);
        requestSentMessage = follow("requestSentMessage", requestSentMessage, text, jar, "message_request_sent", SHIPPED_REQUEST_SENT_MESSAGE, changed);
        requestReceivedMessage = follow("requestReceivedMessage", requestReceivedMessage, text, jar, "message_request_received", SHIPPED_REQUEST_RECEIVED_MESSAGE, changed);
        requestTimeoutMessage = follow("requestTimeoutMessage", requestTimeoutMessage, text, jar, "request_timeout", SHIPPED_REQUEST_TIMEOUT_MESSAGE, changed);
        tradeCompleteMessage = follow("tradeCompleteMessage", tradeCompleteMessage, text, jar, "trade_complete", SHIPPED_TRADE_COMPLETE_MESSAGE, changed);
        tradeCancelledMessage = follow("tradeCancelledMessage", tradeCancelledMessage, text, jar, "trade_cancelled", SHIPPED_TRADE_CANCELLED_MESSAGE, changed);
        tradeDisabledMessage = follow("tradeDisabledMessage", tradeDisabledMessage, text, jar, "message_trade_disabled", SHIPPED_TRADE_DISABLED_MESSAGE, changed);
        playerBlockedMessage = follow("playerBlockedMessage", playerBlockedMessage, text, jar, "message_player_blocked", SHIPPED_PLAYER_BLOCKED_MESSAGE, changed);
        return changed[0];
    }

    /**
     * {@code value}, or {@code text}'s current text for {@code key} when {@code value} is still built-in
     * text other than that and the new text fits {@code field}'s constraints; sets {@code changed[0]}
     * when it replaces.
     */
    private static String follow(String field, String value, Function<String, String> text,
                                 Map<String, Map<String, String>> jar, String key, String shipped, boolean[] changed) {
        String result = ConfigTextDefaults.materialize(TradeConfig.class, field, value,
                ConfigTextDefaults.currentText(text, "", key), ConfigTextDefaults.tracked(jar, "", key, shipped));
        if (!Objects.equals(result, value)) {
            changed[0] = true;
        }
        return result;
    }
}
