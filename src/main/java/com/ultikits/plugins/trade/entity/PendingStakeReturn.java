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
 * The stacks are stored with Bukkit's own item serialization (the form a {@code YamlConfiguration}
 * writes for an {@code ItemStack}), which keeps every component of the item — the name, lore and
 * enchantments, and also a shulker box's contents or a book's pages. The trade log's
 * {@code SerializedItemStack} is a display summary and cannot rebuild an item, so it is not used here.
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

    /** The staked stacks, as YAML under the key {@code items} (a list of item stacks). */
    @Column(value = "items", type = "TEXT")
    private String items;

    /** How many stacks {@link #items} holds, for an operator reading the table directly. */
    @Column("stack_count")
    private int stackCount;

    /** When the stake was saved, in epoch milliseconds. */
    @Column("created_at")
    private long createdAt;
}
