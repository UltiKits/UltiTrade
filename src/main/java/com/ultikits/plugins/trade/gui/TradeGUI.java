package com.ultikits.plugins.trade.gui;

import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Trade GUI implementation with experience trading and item details.
 *
 * @author wisdomme
 * @version 2.0.0
 */
public class TradeGUI implements InventoryHolder {
    
    private final TradeService tradeService;
    private final TradeSession session;
    private final Player viewer;
    private final Inventory inventory;
    
    // GUI layout constants
    // Left side (0-3 columns): Your items (slots 0-3, 9-12, 18-21, 27-30)
    // Middle (column 4): Separator and buttons
    // Right side (5-8 columns): Their items (slots 5-8, 14-17, 23-26, 32-35)
    // Bottom row: Status and confirm button
    
    /**
     * How many slots this window has, and so how far its raw-slot range reaches.
     * <p>
     * Named here rather than written as a literal in each place that needs it, because the number
     * decides two separate things that have to agree: the inventory this class creates, and where
     * {@code TradeListener} stops treating a raw slot as part of the trade window and starts
     * treating it as one of the acting player's own inventory slots. When those two disagree the
     * consequence is silent — either a window slot is left ungoverned, or the player's own
     * inventory is governed as if it were part of the offer (UltiKits/UltiTrade#39).
     */
    public static final int SIZE = 54;

    public static final int[] YOUR_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    public static final int[] THEIR_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    public static final int[] SEPARATOR_SLOTS = {4, 13, 22, 31, 40};
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 45;
    public static final int YOUR_MONEY_SLOT = 36;
    public static final int THEIR_MONEY_SLOT = 44;
    public static final int YOUR_EXP_SLOT = 38;
    public static final int THEIR_EXP_SLOT = 42;
    public static final int YOUR_STATUS_SLOT = 37;
    public static final int THEIR_STATUS_SLOT = 43;
    
    public TradeGUI(TradeService tradeService, TradeSession session, Player viewer) {
        this.tradeService = tradeService;
        this.session = session;
        this.viewer = viewer;
        
        this.inventory = Bukkit.createInventory(this, SIZE, buildTitle());
        
        initializeGUI();
    }
    
    /**
     * Build the window title from the current {@code gui-title} configuration value.
     *
     * @return the colour-translated title naming the other player
     */
    public String buildTitle() {
        Player other = Bukkit.getPlayer(session.getOtherPlayer(viewer.getUniqueId()));
        String title = tradeService.getConfig().getGuiTitle()
            .replace("{PLAYER}", other != null ? other.getName() : "???");
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    /**
     * Initialize GUI elements.
     */
    private void initializeGUI() {
        // Fill separators
        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot : SEPARATOR_SLOTS) {
            inventory.setItem(slot, separator);
        }
        
        // Fill empty slots with glass
        ItemStack yourGlass = createItem(Material.LIME_STAINED_GLASS_PANE, text(tradeService.i18n("gui_your_items")));
        ItemStack theirGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, text(tradeService.i18n("gui_their_items")));
        
        for (int slot : YOUR_SLOTS) {
            inventory.setItem(slot, yourGlass);
        }
        for (int slot : THEIR_SLOTS) {
            inventory.setItem(slot, theirGlass);
        }
        
        // Bottom row
        ItemStack bottomFiller = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, bottomFiller);
        }
        
        // Confirm button
        updateConfirmButton();
        
        // Cancel button
        ItemStack cancelBtn = createItem(Material.BARRIER, text(tradeService.i18n("gui_cancel")));
        inventory.setItem(CANCEL_SLOT, cancelBtn);
        
        // Money display
        updateMoneyDisplay();
        
        // Experience display
        updateExpDisplay();
        
        // Status display
        updateStatusDisplay();
    }
    
    /**
     * Update the GUI with current trade state.
     */
    public void update() {
        UUID viewerUuid = viewer.getUniqueId();
        
        // Update your items
        Map<Integer, ItemStack> yourItems = session.getPlayerItems(viewerUuid);
        for (int i = 0; i < YOUR_SLOTS.length; i++) {
            ItemStack item = yourItems.get(i);
            if (item != null) {
                inventory.setItem(YOUR_SLOTS[i], item);
            } else {
                inventory.setItem(YOUR_SLOTS[i], createItem(Material.LIME_STAINED_GLASS_PANE, text(tradeService.i18n("gui_your_items"))));
            }
        }
        
        // Update their items (display only) with detail lore
        Map<Integer, ItemStack> theirItems = session.getOtherPlayerItems(viewerUuid);
        for (int i = 0; i < THEIR_SLOTS.length; i++) {
            ItemStack item = theirItems.get(i);
            if (item != null) {
                inventory.setItem(THEIR_SLOTS[i], createItemWithDetails(item));
            } else {
                inventory.setItem(THEIR_SLOTS[i], createItem(Material.CYAN_STAINED_GLASS_PANE, text(tradeService.i18n("gui_their_items"))));
            }
        }
        
        updateMoneyDisplay();
        updateExpDisplay();
        updateStatusDisplay();
        updateConfirmButton();
    }
    
    /**
     * Create a clone of item with detailed information in lore.
     */
    private ItemStack createItemWithDetails(ItemStack original) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        // Add separator
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "───────────────");
        lore.add(text(tradeService.i18n("item_details")));
        
        // Add enchantments
        if (!item.getEnchantments().isEmpty()) {
            lore.add(text(tradeService.i18n("item_enchantments")));
            for (Map.Entry<Enchantment, Integer> ench : item.getEnchantments().entrySet()) {
                lore.add(ChatColor.GRAY + "  • " + getEnchantmentName(ench.getKey()) + " " + toRoman(ench.getValue()));
            }
        }
        
        // Add durability
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int maxDurability = item.getType().getMaxDurability();
            if (maxDurability > 0) {
                int currentDurability = maxDurability - damageable.getDamage();
                double percent = (double) currentDurability / maxDurability * 100;
                ChatColor color = percent > 50 ? ChatColor.GREEN : (percent > 25 ? ChatColor.YELLOW : ChatColor.RED);
                lore.add(text(tradeService.i18n("item_durability"))
                         .replace("{CURRENT}", color.toString() + currentDurability)
                         .replace("{MAX}", maxDurability + ChatColor.GRAY.toString())
                         + " (" + String.format("%.1f", percent) + "%)");
            }
        }
        
        // Add item flags info
        if (!meta.getItemFlags().isEmpty()) {
            lore.add(text(tradeService.i18n("item_flags").replace("{COUNT}", String.valueOf(meta.getItemFlags().size()))));
        }
        
        // Add unbreakable info
        if (meta.isUnbreakable()) {
            lore.add(text(tradeService.i18n("item_unbreakable")));
        }
        
        // Add custom model data
        if (meta.hasCustomModelData()) {
            lore.add(text(tradeService.i18n("item_custom_model")).replace("{ID}", ChatColor.WHITE.toString() + meta.getCustomModelData()));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get enchantment display name.
     */
    private String getEnchantmentName(Enchantment enchantment) {
        // Return a readable name
        String name = enchantment.getKey().getKey();
        name = name.replace("_", " ");
        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * Convert number to roman numeral.
     */
    private String toRoman(int number) {
        if (number <= 0 || number > 10) return String.valueOf(number);
        String[] romanNumerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romanNumerals[number - 1];
    }
    
    /**
     * Update money display.
     */
    private void updateMoneyDisplay() {
        UUID viewerUuid = viewer.getUniqueId();
        
        if (tradeService.hasEconomy()) {
            double yourMoney = session.getPlayerMoney(viewerUuid);
            double theirMoney = session.getOtherPlayerMoney(viewerUuid);
            
            // Calculate tax
            double taxRate = tradeService.getConfig().getTradeTax();
            double yourTax = yourMoney * taxRate;
            double theirReceive = yourMoney - yourTax;
            
            List<String> yourLore = new ArrayList<>();
            yourLore.add(text(tradeService.i18n("gui_money_click")));
            if (taxRate > 0 && yourMoney > 0) {
                yourLore.add(taxLine(tradeService.i18n("gui_tax_rate"), "{RATE}", String.format("%.1f%%", taxRate * 100)));
                yourLore.add(taxLine(tradeService.i18n("gui_tax"), "{AMOUNT}", String.format("%.2f", yourTax)));
                yourLore.add(taxLine(tradeService.i18n("gui_other_receives"), "{AMOUNT}", String.format("%.2f", theirReceive)));
            }
            
            ItemStack yourMoneyItem = createItem(Material.GOLD_NUGGET, 
                text(tradeService.i18n("gui_your_money")) + ": " + ChatColor.WHITE + String.format("%.2f", yourMoney),
                yourLore.toArray(new String[0]));
            inventory.setItem(YOUR_MONEY_SLOT, yourMoneyItem);
            
            ItemStack theirMoneyItem = createItem(Material.GOLD_NUGGET,
                text(tradeService.i18n("gui_their_money")) + ": " + ChatColor.WHITE + String.format("%.2f", theirMoney));
            inventory.setItem(THEIR_MONEY_SLOT, theirMoneyItem);
        } else {
            ItemStack disabled = createItem(Material.BARRIER, text(tradeService.i18n("gui_money_disabled")));
            inventory.setItem(YOUR_MONEY_SLOT, disabled);
            inventory.setItem(THEIR_MONEY_SLOT, disabled);
        }
    }
    
    /**
     * Update experience display.
     */
    private void updateExpDisplay() {
        UUID viewerUuid = viewer.getUniqueId();
        
        if (tradeService.getConfig().isEnableExpTrade()) {
            int yourExp = session.getPlayerExp(viewerUuid);
            int theirExp = session.getOtherPlayerExp(viewerUuid);
            int totalExp = tradeService.getTotalExperience(viewer);
            
            // Calculate tax
            double taxRate = tradeService.getConfig().getExpTaxRate();
            int yourTax = (int)(yourExp * taxRate);
            int theirReceive = yourExp - yourTax;
            
            List<String> yourLore = new ArrayList<>();
            yourLore.add(text(tradeService.i18n("gui_exp_click")));
            yourLore.add(taxLine(tradeService.i18n("gui_your_total_exp"), "{AMOUNT}", String.valueOf(totalExp)));
            if (taxRate > 0 && yourExp > 0) {
                yourLore.add(taxLine(tradeService.i18n("gui_tax_rate"), "{RATE}", String.format("%.1f%%", taxRate * 100)));
                yourLore.add(taxLine(tradeService.i18n("gui_tax"), "{AMOUNT}", String.valueOf(yourTax)));
                yourLore.add(taxLine(tradeService.i18n("gui_other_receives"), "{AMOUNT}", String.valueOf(theirReceive)));
            }
            
            ItemStack yourExpItem = createItem(Material.EXPERIENCE_BOTTLE, 
                text(tradeService.i18n("gui_your_exp")) + ": " + ChatColor.WHITE + yourExp,
                yourLore.toArray(new String[0]));
            inventory.setItem(YOUR_EXP_SLOT, yourExpItem);
            
            ItemStack theirExpItem = createItem(Material.EXPERIENCE_BOTTLE,
                text(tradeService.i18n("gui_their_exp")) + ": " + ChatColor.WHITE + theirExp);
            inventory.setItem(THEIR_EXP_SLOT, theirExpItem);
        } else {
            ItemStack disabled = createItem(Material.BARRIER, text(tradeService.i18n("gui_exp_disabled")));
            inventory.setItem(YOUR_EXP_SLOT, disabled);
            inventory.setItem(THEIR_EXP_SLOT, disabled);
        }
    }
    
    /**
     * Update status display.
     */
    private void updateStatusDisplay() {
        UUID viewerUuid = viewer.getUniqueId();
        
        boolean yourConfirmed = session.isConfirmed(viewerUuid);
        boolean theirConfirmed = session.isConfirmed(session.getOtherPlayer(viewerUuid));
        
        ItemStack yourStatus = createItem(
            yourConfirmed ? Material.LIME_WOOL : Material.RED_WOOL,
            yourConfirmed
                ? text(tradeService.i18n("gui_status_self_confirmed"))
                : text(tradeService.i18n("gui_status_self_pending"))
        );
        inventory.setItem(YOUR_STATUS_SLOT, yourStatus);
        
        ItemStack theirStatus = createItem(
            theirConfirmed ? Material.LIME_WOOL : Material.RED_WOOL,
            theirConfirmed
                ? text(tradeService.i18n("gui_status_other_confirmed"))
                : text(tradeService.i18n("gui_status_other_pending"))
        );
        inventory.setItem(THEIR_STATUS_SLOT, theirStatus);
    }
    
    /**
     * Update confirm button.
     */
    private void updateConfirmButton() {
        boolean confirmed = session.isConfirmed(viewer.getUniqueId());
        
        ItemStack confirmBtn = createItem(
            confirmed ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE,
            confirmed ? text(tradeService.i18n("gui_unconfirm")) : text(tradeService.i18n("gui_confirm")),
            confirmed ? text(tradeService.i18n("gui_confirm_locked")) : text(tradeService.i18n("gui_confirm_hint"))
        );
        inventory.setItem(CONFIRM_SLOT, confirmBtn);
    }
    
    /** The language file's text, {@code &} colour codes applied. */
    private static String text(String languageText) {
        return ChatColor.translateAlternateColorCodes('&', languageText);
    }

    /** A lore line from the language file with one placeholder filled, colour codes applied. */
    private static String taxLine(String languageText, String placeholder, String value) {
        return text(languageText.replace(placeholder, value));
    }

    /**
     * Create an item with name and lore.
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Check if slot is a "your items" slot.
     */
    public boolean isYourSlot(int slot) {
        for (int s : YOUR_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }
    
    /**
     * Check if slot is the experience slot.
     */
    public boolean isExpSlot(int slot) {
        return slot == YOUR_EXP_SLOT;
    }
    
    /**
     * Check if slot is the money slot.
     */
    public boolean isMoneySlot(int slot) {
        return slot == YOUR_MONEY_SLOT;
    }
    
    /**
     * Get item index from slot.
     */
    public int getItemIndex(int slot) {
        for (int i = 0; i < YOUR_SLOTS.length; i++) {
            if (YOUR_SLOTS[i] == slot) return i;
        }
        return -1;
    }
    
    /**
     * Play item place sound.
     */
    public void playItemSound() {
        tradeService.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_PLING);
    }
    
    public TradeSession getSession() {
        return session;
    }
    
    public Player getViewer() {
        return viewer;
    }
    
    public TradeService getTradeService() {
        return tradeService;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
