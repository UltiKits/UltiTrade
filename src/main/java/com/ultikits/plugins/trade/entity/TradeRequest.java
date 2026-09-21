package com.ultikits.plugins.trade.entity;

import java.util.UUID;

/**
 * Represents a trade request from one player to another.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public class TradeRequest {
    
    /** The shipped {@code request-timeout} default, used when no timeout is given. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final UUID sender;
    private final UUID receiver;
    private final long timestamp;
    private final int timeoutSeconds;

    public TradeRequest(UUID sender, UUID receiver) {
        this(sender, receiver, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Create a request that expires {@code timeoutSeconds} after now. The timeout is fixed when the
     * request is sent, so a later reload of {@code request-timeout} applies only to new requests
     * (UltiKits/UltiTrade#26).
     *
     * @param sender         the requesting player
     * @param receiver       the requested player
     * @param timeoutSeconds how long the receiver has to answer
     */
    public TradeRequest(UUID sender, UUID receiver, int timeoutSeconds) {
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = System.currentTimeMillis();
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public UUID getSender() {
        return sender;
    }
    
    public UUID getReceiver() {
        return receiver;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * @return whether this request has outlived the timeout it was sent with
     */
    public boolean isExpired() {
        return isExpired(timeoutSeconds);
    }

    public boolean isExpired(int timeoutSeconds) {
        return System.currentTimeMillis() - timestamp > timeoutSeconds * 1000L;
    }
}
