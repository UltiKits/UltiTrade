package com.ultikits.plugins.trade.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Items a player staked in a trade that was cancelled while the server could not find them.
 * <p>
 * A cancelled trade hands each side's staked items back to that side. When one side cannot be
 * resolved at that moment there is no inventory to hand them to, so they are saved here and handed
 * over when that player next joins (UltiKits/UltiTrade#32; maintainer decision of 2026-09-24).
 * One row holds one player's stake from one cancelled trade.
 * <p>
 * The stacks are stored with Bukkit's own item serialization, the form a {@code YamlConfiguration}
 * writes for an {@code ItemStack} and reads back into one. The trade log's {@code SerializedItemStack}
 * is a display summary with no way back to an {@code ItemStack}, so it cannot be used here.
 *
 * @author wisdomme
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("trade_pending_returns")
public class PendingStakeReturn extends BaseDataEntity<String> {

    /** The UUID of the player the items belong to. */
    @Column("owner_uuid")
    private String ownerUuid;

    /**
     * The staked stacks, as YAML under the key {@code items} (a list of item stacks). LONGTEXT, not
     * TEXT: the framework writes the type into {@code CREATE TABLE} verbatim, and MySQL's TEXT holds
     * 65,535 bytes, less than a stake of written books or filled shulker boxes; SQLite reads LONGTEXT
     * as text.
     */
    @Column(value = "items", type = "LONGTEXT")
    private String items;

    /** How many stacks {@link #items} holds, for an operator reading the table directly. */
    @Column("stack_count")
    private int stackCount;

    /** When the stake was saved, in epoch milliseconds. */
    @Column("created_at")
    private long createdAt;

    /**
     * Set while a hand-over of this entry is in progress: the same token is written into the player's
     * persistent data together with the handed-over items, so the next join can tell whether that
     * hand-over reached the player's saved data. {@code null} when no hand-over is in progress.
     */
    @Column("delivery_token")
    private String deliveryToken;

    /**
     * While {@link #deliveryToken} is set: the stacks that stay listed once the hand-over is confirmed
     * (the part that did not fit), in the form of {@link #items}; blank when everything fitted.
     */
    @Column(value = "after_delivery", type = "LONGTEXT")
    private String afterDelivery;
}
