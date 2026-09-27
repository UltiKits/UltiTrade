package com.ultikits.plugins.trade.gui;

import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Trade confirmation page for large trades.
 * Shows summary of the trade and requires explicit confirmation.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public class TradeConfirmPage implements InventoryHolder {
    
    private final TradeService tradeService;
    private final TradeSession session;
    private final Player viewer;
    private final Inventory inventory;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    
    // GUI layout
    public static final int ROWS = 5;
    public static final int SIZE = ROWS * 9;
    
    // Button positions
    public static final int CONFIRM_SLOT = 38;
    public static final int CANCEL_SLOT = 42;
    public static final int INFO_SLOT = 13;
    
    // Display positions
    public static final int YOUR_ITEMS_START = 10;
    public static final int THEIR_ITEMS_START = 14;
    public static final int YOUR_MONEY_SLOT = 28;
    public static final int YOUR_EXP_SLOT = 29;
    public static final int THEIR_MONEY_SLOT = 32;
    public static final int THEIR_EXP_SLOT = 33;
    
    public TradeConfirmPage(TradeService tradeService, TradeSession session, Player viewer,
                            Runnable onConfirm, Runnable onCancel) {
        this.tradeService = tradeService;
        this.session = session;
        this.viewer = viewer;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        
        Player other = Bukkit.getPlayer(session.getOtherPlayer(viewer.getUniqueId()));
        String title = text(tradeService.i18n("confirm_title")
            .replace("{PLAYER}", other != null ? other.getName() : "???"));
        
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        initializeGUI();
    }
    
    /**
     * Initialize the GUI.
     */
    private void initializeGUI() {
        UUID viewerUuid = viewer.getUniqueId();
        Player other = Bukkit.getPlayer(session.getOtherPlayer(viewerUuid));
        
        // Fill background
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, background);
        }
        
        // Info item
        double yourMoney = session.getPlayerMoney(viewerUuid);
        double theirMoney = session.getOtherPlayerMoney(viewerUuid);
        int yourExp = session.getPlayerExp(viewerUuid);
        int theirExp = session.getOtherPlayerExp(viewerUuid);
        double taxRate = tradeService.getConfig().getTradeTax();
        double expTaxRate = tradeService.getConfig().getExpTaxRate();
        
        double threshold = tradeService.getConfig().getConfirmThreshold();
        
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(text(tradeService.i18n("confirm_needed")));
        infoLore.add(filled(tradeService.i18n("confirm_threshold"), "{AMOUNT}", String.valueOf((int) threshold)));
        infoLore.add("");
        infoLore.add(text(tradeService.i18n("confirm_you_give")));
        infoLore.add(filled(tradeService.i18n("confirm_money_line"), "{AMOUNT}", String.format("%.2f", yourMoney)));
        if (taxRate > 0 && yourMoney > 0) {
            infoLore.add(filled(tradeService.i18n("confirm_tax_line"), "{AMOUNT}", String.format("%.2f", yourMoney * taxRate)));
        }
        infoLore.add(filled(tradeService.i18n("confirm_exp_line"), "{AMOUNT}", String.valueOf(yourExp)));
        if (expTaxRate > 0 && yourExp > 0) {
            infoLore.add(filled(tradeService.i18n("confirm_tax_line"), "{AMOUNT}", String.valueOf((int) (yourExp * expTaxRate))));
        }
        infoLore.add(filled(tradeService.i18n("confirm_items_line"), "{COUNT}", String.valueOf(session.getPlayerItems(viewerUuid).size())));
        infoLore.add("");
        infoLore.add(text(tradeService.i18n("confirm_you_receive")));
        infoLore.add(filled(tradeService.i18n("confirm_money_line"), "{AMOUNT}", String.format("%.2f", theirMoney * (1 - taxRate))));
        infoLore.add(filled(tradeService.i18n("confirm_exp_line"), "{AMOUNT}", String.valueOf((int) (theirExp * (1 - expTaxRate)))));
        infoLore.add(filled(tradeService.i18n("confirm_items_line"), "{COUNT}", String.valueOf(session.getOtherPlayerItems(viewerUuid).size())));
        infoLore.add("");
        infoLore.add(text(tradeService.i18n("confirm_check_carefully")));
        
        ItemStack infoItem = createItem(Material.PAPER, text(tradeService.i18n("confirm_info_title")), infoLore);
        inventory.setItem(INFO_SLOT, infoItem);
        
        // Display your items (3 slots)
        displayItems(session.getPlayerItems(viewerUuid), YOUR_ITEMS_START, text(tradeService.i18n("gui_your_items")));
        
        // Display their items (3 slots)
        displayItems(session.getOtherPlayerItems(viewerUuid), THEIR_ITEMS_START, text(tradeService.i18n("gui_their_items")));
        
        // Money display
        ItemStack yourMoneyItem = createItem(Material.GOLD_INGOT, 
            text(tradeService.i18n("confirm_your_money")),
            Arrays.asList(
                filled(tradeService.i18n("confirm_amount"), "{AMOUNT}", String.format("%.2f", yourMoney)),
                taxRate > 0 ? filled(tradeService.i18n("confirm_other_receives_after_tax"), "{AMOUNT}", String.format("%.2f", yourMoney * (1 - taxRate))) : ""
            ));
        inventory.setItem(YOUR_MONEY_SLOT, yourMoneyItem);
        
        ItemStack theirMoneyItem = createItem(Material.GOLD_INGOT,
            text(tradeService.i18n("confirm_their_money")),
            Arrays.asList(
                filled(tradeService.i18n("confirm_amount"), "{AMOUNT}", String.format("%.2f", theirMoney)),
                taxRate > 0 ? filled(tradeService.i18n("confirm_you_receive_amount"), "{AMOUNT}", String.format("%.2f", theirMoney * (1 - taxRate))) : ""
            ));
        inventory.setItem(THEIR_MONEY_SLOT, theirMoneyItem);
        
        // Exp display
        ItemStack yourExpItem = createItem(Material.EXPERIENCE_BOTTLE,
            text(tradeService.i18n("confirm_your_exp")),
            Arrays.asList(
                filled(tradeService.i18n("confirm_exp_amount"), "{AMOUNT}", String.valueOf(yourExp)),
                expTaxRate > 0 ? filled(tradeService.i18n("confirm_other_receives_after_tax"), "{AMOUNT}", String.valueOf((int) (yourExp * (1 - expTaxRate)))) : ""
            ));
        inventory.setItem(YOUR_EXP_SLOT, yourExpItem);
        
        ItemStack theirExpItem = createItem(Material.EXPERIENCE_BOTTLE,
            text(tradeService.i18n("confirm_their_exp")),
            Arrays.asList(
                filled(tradeService.i18n("confirm_exp_amount"), "{AMOUNT}", String.valueOf(theirExp)),
                expTaxRate > 0 ? filled(tradeService.i18n("confirm_you_receive_amount"), "{AMOUNT}", String.valueOf((int) (theirExp * (1 - expTaxRate)))) : ""
            ));
        inventory.setItem(THEIR_EXP_SLOT, theirExpItem);
        
        // Confirm button
        ItemStack confirmBtn = createItem(Material.LIME_CONCRETE,
            text(tradeService.i18n("confirm_button")),
            Arrays.asList(
                text(tradeService.i18n("confirm_button_lore")),
                "",
                text(tradeService.i18n("confirm_button_warning"))
            ));
        inventory.setItem(CONFIRM_SLOT, confirmBtn);
        
        // Cancel button
        ItemStack cancelBtn = createItem(Material.RED_CONCRETE,
            text(tradeService.i18n("confirm_back_button")),
            Arrays.asList(
                text(tradeService.i18n("confirm_back_lore")),
                "",
                text(tradeService.i18n("confirm_back_note"))
            ));
        inventory.setItem(CANCEL_SLOT, cancelBtn);
    }
    
    /**
     * Display items in the GUI.
     */
    private void displayItems(Map<Integer, ItemStack> items, int startSlot, String emptyName) {
        int displaySlots = 3;
        List<ItemStack> itemList = new ArrayList<>(items.values());
        
        for (int i = 0; i < displaySlots; i++) {
            if (i < itemList.size()) {
                ItemStack item = itemList.get(i).clone();
                // Add info to lore
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(text(tradeService.i18n("confirm_item_marker")));
                    item.setItemMeta(meta);
                }
                inventory.setItem(startSlot + i, item);
            } else {
                ItemStack empty = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, emptyName);
                inventory.setItem(startSlot + i, empty);
            }
        }
        
        // If more than 3 items, show count
        if (itemList.size() > displaySlots) {
            ItemStack moreItem = createItem(Material.CHEST,
                filled(tradeService.i18n("confirm_more_items"), "{COUNT}", String.valueOf(itemList.size() - displaySlots)),
                Arrays.asList(text(tradeService.i18n("confirm_more_items_lore"))));
            inventory.setItem(startSlot + displaySlots - 1, moreItem);
        }
    }
    
    /**
     * Handle click event.
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        
        if (slot == CONFIRM_SLOT) {
            viewer.closeInventory();
            if (onConfirm != null) {
                onConfirm.run();
            }
        } else if (slot == CANCEL_SLOT) {
            viewer.closeInventory();
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }
    
    /**
     * Create an item with name and lore.
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                // Filter out empty strings
                List<String> filteredLore = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.isEmpty()) {
                        filteredLore.add(line);
                    }
                }
                meta.setLore(filteredLore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Create an item with name only.
     */
    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }
    
    /**
     * Open the confirm page for a player.
     */
    public void open() {
        viewer.openInventory(inventory);
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public Player getViewer() {
        return viewer;
    }

    /** The language file's text, {@code &} colour codes applied. */
    private static String text(String languageText) {
        return ChatColor.translateAlternateColorCodes('&', languageText);
    }

    /** A line from the language file with one placeholder filled, colour codes applied. */
    private static String filled(String languageText, String placeholder, String value) {
        return text(languageText.replace(placeholder, value));
    }
}
