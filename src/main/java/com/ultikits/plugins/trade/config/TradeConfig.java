package com.ultikits.plugins.trade.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

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

    @ConfigEntry(path = "gui-title", comment = "交易界面标题（留空：使用语言文件中的文本）")
    private String guiTitle = "";
    
    // ==================== Messages ====================

    @ConfigEntry(path = "messages.request-sent", comment = "发送交易请求（留空：使用语言文件中的文本）")
    private String requestSentMessage = "";

    @ConfigEntry(path = "messages.request-received", comment = "收到交易请求（留空：使用语言文件中的文本）")
    private String requestReceivedMessage = "";

    @ConfigEntry(path = "messages.request-timeout", comment = "请求超时（留空：使用语言文件中的文本）")
    private String requestTimeoutMessage = "";

    @ConfigEntry(path = "messages.trade-complete", comment = "交易完成（留空：使用语言文件中的文本）")
    private String tradeCompleteMessage = "";

    @ConfigEntry(path = "messages.trade-cancelled", comment = "交易取消（留空：使用语言文件中的文本）")
    private String tradeCancelledMessage = "";

    @ConfigEntry(path = "messages.trade-disabled", comment = "交易已关闭（留空：使用语言文件中的文本）")
    private String tradeDisabledMessage = "";

    @ConfigEntry(path = "messages.player-blocked", comment = "被拉黑（留空：使用语言文件中的文本）")
    private String playerBlockedMessage = "";
    
    // ==================== Text defaults an earlier version shipped ====================
    // The trade-window title and every message below used to ship a fixed Chinese default. Each now
    // defaults to blank and reads the language file's text, in the server's language, while it stays
    // blank (maintainer ruling 2026-09-24 (d), UltiKits/UltiTrade#16). These are the old values, one
    // per setting across this module's history, kept only so migrateLegacyDefaults() can recognise
    // them in an upgraded operator's file.

    /** The default every earlier version shipped for {@code gui-title}; compared, never shown. */
    static final String SHIPPED_GUI_TITLE = "&6与 {PLAYER} 交易";

    /** The default every earlier version shipped for {@code messages.request-sent}; compared, never shown. */
    static final String SHIPPED_REQUEST_SENT_MESSAGE = "&a已向 &f{PLAYER} &a发送交易请求！";

    /** The default every earlier version shipped for {@code messages.request-received}; compared, never shown. */
    static final String SHIPPED_REQUEST_RECEIVED_MESSAGE = "&e{PLAYER} &f请求与你交易！输入 /trade accept 接受";

    /** The default every earlier version shipped for {@code messages.request-timeout}; compared, never shown. */
    static final String SHIPPED_REQUEST_TIMEOUT_MESSAGE = "&c交易请求已超时！";

    /** The default every earlier version shipped for {@code messages.trade-complete}; compared, never shown. */
    static final String SHIPPED_TRADE_COMPLETE_MESSAGE = "&a交易完成！";

    /** The default every earlier version shipped for {@code messages.trade-cancelled}; compared, never shown. */
    static final String SHIPPED_TRADE_CANCELLED_MESSAGE = "&c交易已取消！";

    /** The default every earlier version shipped for {@code messages.trade-disabled}; compared, never shown. */
    static final String SHIPPED_TRADE_DISABLED_MESSAGE = "&c对方已关闭交易功能！";

    /** The default every earlier version shipped for {@code messages.player-blocked}; compared, never shown. */
    static final String SHIPPED_PLAYER_BLOCKED_MESSAGE = "&c对方已将你加入黑名单！";

    public TradeConfig() {
        super(CONFIG_FILE);
    }

    /**
     * {@code configured}, or {@code languageText} when {@code configured} is null, empty or only
     * whitespace.
     *
     * @param configured   the value in {@code config/trade.yml}
     * @param languageText the language file's text for the same setting
     * @return the text to show
     */
    static String configuredOr(String configured, String languageText) {
        return configured == null || configured.trim().isEmpty() ? languageText : configured;
    }

    /**
     * The language file's text for {@code key}, in the server's language, read through the plugin
     * this configuration was bound to at load. Before that binding there is no language to read, so
     * the key itself is returned, as the framework renders a missing key.
     *
     * @param key the language-file key
     * @return the text for the key
     */
    private String i18n(String key) {
        UltiToolsPlugin plugin = getUltiToolsPlugin();
        return plugin == null ? key : plugin.i18n(key);
    }

    /**
     * {@code gui-title}, or the language file's {@code gui_title} text when left blank.
     *
     * @return the text to show
     */
    public String getGuiTitle() {
        return configuredOr(guiTitle, i18n("gui_title"));
    }

    /**
     * {@code messages.request-sent}, or the language file's {@code request_sent} text when left blank.
     *
     * @return the text to show
     */
    public String getRequestSentMessage() {
        return configuredOr(requestSentMessage, i18n("request_sent"));
    }

    /**
     * {@code messages.request-received}, or the language file's {@code request_received} text when left blank.
     *
     * @return the text to show
     */
    public String getRequestReceivedMessage() {
        return configuredOr(requestReceivedMessage, i18n("request_received"));
    }

    /**
     * {@code messages.request-timeout}, or the language file's {@code request_timeout} text when left blank.
     *
     * @return the text to show
     */
    public String getRequestTimeoutMessage() {
        return configuredOr(requestTimeoutMessage, i18n("request_timeout"));
    }

    /**
     * {@code messages.trade-complete}, or the language file's {@code trade_complete} text when left blank.
     *
     * @return the text to show
     */
    public String getTradeCompleteMessage() {
        return configuredOr(tradeCompleteMessage, i18n("trade_complete"));
    }

    /**
     * {@code messages.trade-cancelled}, or the language file's {@code trade_cancelled} text when left blank.
     *
     * @return the text to show
     */
    public String getTradeCancelledMessage() {
        return configuredOr(tradeCancelledMessage, i18n("trade_cancelled"));
    }

    /**
     * {@code messages.trade-disabled}, or the language file's {@code trade_disabled_target} text when left blank.
     *
     * @return the text to show
     */
    public String getTradeDisabledMessage() {
        return configuredOr(tradeDisabledMessage, i18n("trade_disabled_target"));
    }

    /**
     * {@code messages.player-blocked}, or the language file's {@code player_blocked} text when left blank.
     *
     * @return the text to show
     */
    public String getPlayerBlockedMessage() {
        return configuredOr(playerBlockedMessage, i18n("player_blocked"));
    }

    /**
     * Rewrites the trade-window title and every message that still holds the default an earlier
     * version shipped to blank, so the language file's text takes over; any other value is the
     * operator's and is kept. Idempotent: a blank value matches no shipped default. The caller saves
     * the file when this returns true (maintainer ruling 2026-09-24 (d)).
     *
     * @return whether any value was rewritten
     */
    public boolean migrateLegacyDefaults() {
        boolean changed = false;
        if (SHIPPED_GUI_TITLE.equals(guiTitle)) {
            guiTitle = "";
            changed = true;
        }
        if (SHIPPED_REQUEST_SENT_MESSAGE.equals(requestSentMessage)) {
            requestSentMessage = "";
            changed = true;
        }
        if (SHIPPED_REQUEST_RECEIVED_MESSAGE.equals(requestReceivedMessage)) {
            requestReceivedMessage = "";
            changed = true;
        }
        if (SHIPPED_REQUEST_TIMEOUT_MESSAGE.equals(requestTimeoutMessage)) {
            requestTimeoutMessage = "";
            changed = true;
        }
        if (SHIPPED_TRADE_COMPLETE_MESSAGE.equals(tradeCompleteMessage)) {
            tradeCompleteMessage = "";
            changed = true;
        }
        if (SHIPPED_TRADE_CANCELLED_MESSAGE.equals(tradeCancelledMessage)) {
            tradeCancelledMessage = "";
            changed = true;
        }
        if (SHIPPED_TRADE_DISABLED_MESSAGE.equals(tradeDisabledMessage)) {
            tradeDisabledMessage = "";
            changed = true;
        }
        if (SHIPPED_PLAYER_BLOCKED_MESSAGE.equals(playerBlockedMessage)) {
            playerBlockedMessage = "";
            changed = true;
        }
        return changed;
    }
}
