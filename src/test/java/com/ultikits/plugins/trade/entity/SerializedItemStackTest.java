package com.ultikits.plugins.trade.entity;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SerializedItemStack Tests")
class SerializedItemStackTest {

    @BeforeEach
    void setUp() throws Exception {
        com.ultikits.plugins.trade.UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        com.ultikits.plugins.trade.UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("fromItemStack")
    class FromItemStack {

        @Test
        @DisplayName("Should return null for null item")
        void nullItem() {
            assertThat(SerializedItemStack.fromItemStack(null)).isNull();
        }

        @Test
        @DisplayName("Should return null for air")
        void airItem() {
            ItemStack air = new ItemStack(Material.AIR);
            assertThat(SerializedItemStack.fromItemStack(air)).isNull();
        }

        @Test
        @DisplayName("Should serialize basic item")
        void basicItem() {
            ItemStack item = new ItemStack(Material.DIAMOND, 10);

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized).isNotNull();
            assertThat(serialized.getType()).isEqualTo("DIAMOND");
            assertThat(serialized.getAmount()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should serialize item with display name")
        void itemWithDisplayName() {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.hasDisplayName()).thenReturn(true);
            when(meta.getDisplayName()).thenReturn("Legendary Sword");

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.getDisplayName()).isEqualTo("Legendary Sword");
        }

        @Test
        @DisplayName("Should serialize item with lore")
        void itemWithLore() {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.hasLore()).thenReturn(true);
            when(meta.getLore()).thenReturn(Arrays.asList("Line 1", "Line 2"));

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.getLore()).containsExactly("Line 1", "Line 2");
        }

        @Test
        @DisplayName("Should serialize item with enchantments")
        void itemWithEnchantments() {
            Enchantment sharpness = mock(Enchantment.class);
            org.bukkit.NamespacedKey key = mock(org.bukkit.NamespacedKey.class);
            when(key.getKey()).thenReturn("sharpness");
            when(sharpness.getKey()).thenReturn(key);

            Map<Enchantment, Integer> enchants = new HashMap<>();
            enchants.put(sharpness, 5);

            ItemMeta meta = mock(ItemMeta.class);

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(enchants);

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.getEnchantments()).containsEntry("sharpness", 5);
        }

        @Test
        @DisplayName("Should serialize item with durability")
        void itemWithDurability() {
            org.bukkit.inventory.meta.Damageable damageable = mock(org.bukkit.inventory.meta.Damageable.class);
            // Damageable extends ItemMeta
            when(damageable.getDamage()).thenReturn(50);
            when(damageable.hasDisplayName()).thenReturn(false);
            when(damageable.hasLore()).thenReturn(false);
            when(damageable.hasCustomModelData()).thenReturn(false);
            when(damageable.isUnbreakable()).thenReturn(false);
            when(damageable.getItemFlags()).thenReturn(Collections.emptySet());

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_PICKAXE);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(damageable);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            // Material.DIAMOND_PICKAXE.getMaxDurability() returns the real enum value
            short expectedMax = Material.DIAMOND_PICKAXE.getMaxDurability();
            assertThat(serialized.getMaxDurability()).isEqualTo((int) expectedMax);
            assertThat(serialized.getDurability()).isEqualTo((int) expectedMax - 50);
        }

        @Test
        @DisplayName("Should serialize item with custom model data")
        void itemWithCustomModelData() {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.hasCustomModelData()).thenReturn(true);
            when(meta.getCustomModelData()).thenReturn(42);

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.getCustomModelData()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should serialize item with item flags")
        void itemWithItemFlags() {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.getItemFlags()).thenReturn(new java.util.HashSet<>(Arrays.asList(
                    org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES
            )));

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.getItemFlags()).hasSize(2);
            assertThat(serialized.getItemFlags()).contains("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");
        }

        @Test
        @DisplayName("Should serialize unbreakable item")
        void unbreakableItem() {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.isUnbreakable()).thenReturn(true);
            when(meta.getItemFlags()).thenReturn(Collections.emptySet());

            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized.isUnbreakable()).isTrue();
        }

        @Test
        @DisplayName("Should handle item with null meta")
        void nullMeta() {
            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND);
            when(item.getAmount()).thenReturn(1);
            when(item.getItemMeta()).thenReturn(null);
            when(item.getEnchantments()).thenReturn(Collections.emptyMap());

            SerializedItemStack serialized = SerializedItemStack.fromItemStack(item);

            assertThat(serialized).isNotNull();
            assertThat(serialized.getType()).isEqualTo("DIAMOND");
            assertThat(serialized.getDisplayName()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("Should build with all fields")
        void buildWithAllFields() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND")
                    .amount(10)
                    .displayName("Test Item")
                    .lore(Arrays.asList("Lore 1"))
                    .enchantments(Collections.singletonMap("sharpness", 5))
                    .durability(100)
                    .maxDurability(1000)
                    .customModelData(12345)
                    .itemFlags(Arrays.asList("HIDE_ENCHANTS"))
                    .unbreakable(true)
                    .build();

            assertThat(item.getType()).isEqualTo("DIAMOND");
            assertThat(item.getAmount()).isEqualTo(10);
            assertThat(item.getDisplayName()).isEqualTo("Test Item");
            assertThat(item.getLore()).containsExactly("Lore 1");
            assertThat(item.getEnchantments()).containsEntry("sharpness", 5);
            assertThat(item.getDurability()).isEqualTo(100);
            assertThat(item.getMaxDurability()).isEqualTo(1000);
            assertThat(item.getCustomModelData()).isEqualTo(12345);
            assertThat(item.getItemFlags()).containsExactly("HIDE_ENCHANTS");
            assertThat(item.isUnbreakable()).isTrue();
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {

        @Test
        @DisplayName("Should serialize to JSON")
        void toJson() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND")
                    .amount(10)
                    .build();

            String json = item.toJson();

            assertThat(json).isNotNull().isNotEmpty();
            assertThat(json).contains("DIAMOND");
            assertThat(json).contains("10");
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void fromJson() {
            String json = "{\"type\":\"DIAMOND\",\"amount\":10}";

            SerializedItemStack item = SerializedItemStack.fromJson(json);

            assertThat(item).isNotNull();
            assertThat(item.getType()).isEqualTo("DIAMOND");
            assertThat(item.getAmount()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should round-trip JSON serialization")
        void roundTrip() {
            SerializedItemStack original = SerializedItemStack.builder()
                    .type("DIAMOND_SWORD")
                    .amount(1)
                    .displayName("Test Sword")
                    .enchantments(Collections.singletonMap("sharpness", 5))
                    .build();

            String json = original.toJson();
            SerializedItemStack deserialized = SerializedItemStack.fromJson(json);

            assertThat(deserialized.getType()).isEqualTo(original.getType());
            assertThat(deserialized.getAmount()).isEqualTo(original.getAmount());
            assertThat(deserialized.getDisplayName()).isEqualTo(original.getDisplayName());
            assertThat(deserialized.getEnchantments()).isEqualTo(original.getEnchantments());
        }
    }

    @Nested
    @DisplayName("List Serialization")
    class ListSerialization {

        @Test
        @DisplayName("Should serialize list to JSON")
        void listToJson() {
            List<SerializedItemStack> items = Arrays.asList(
                    SerializedItemStack.builder().type("DIAMOND").amount(10).build(),
                    SerializedItemStack.builder().type("GOLD_INGOT").amount(5).build()
            );

            String json = SerializedItemStack.listToJson(items);

            assertThat(json).isNotNull().isNotEmpty();
            assertThat(json).contains("DIAMOND");
            assertThat(json).contains("GOLD_INGOT");
        }

        @Test
        @DisplayName("Should deserialize list from JSON")
        void listFromJson() {
            String json = "[{\"type\":\"DIAMOND\",\"amount\":10},{\"type\":\"GOLD_INGOT\",\"amount\":5}]";

            List<SerializedItemStack> items = SerializedItemStack.listFromJson(json);

            assertThat(items).hasSize(2);
            assertThat(items.get(0).getType()).isEqualTo("DIAMOND");
            assertThat(items.get(1).getType()).isEqualTo("GOLD_INGOT");
        }

        @Test
        @DisplayName("Should return empty list for null JSON")
        void listFromNullJson() {
            List<SerializedItemStack> items = SerializedItemStack.listFromJson(null);
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for empty JSON")
        void listFromEmptyJson() {
            List<SerializedItemStack> items = SerializedItemStack.listFromJson("");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("Should serialize ItemStack collection")
        void itemsToJson() {
            ItemStack item1 = new ItemStack(Material.DIAMOND, 10);
            ItemStack item2 = new ItemStack(Material.GOLD_INGOT, 5);
            Collection<ItemStack> items = Arrays.asList(item1, item2);

            String json = SerializedItemStack.itemsToJson(items);

            assertThat(json).isNotNull().isNotEmpty();
            assertThat(json).contains("DIAMOND");
            assertThat(json).contains("GOLD_INGOT");
        }

        @Test
        @DisplayName("Should filter null items in collection")
        void itemsToJsonFilterNulls() {
            Collection<ItemStack> items = Arrays.asList(
                    new ItemStack(Material.DIAMOND, 10),
                    null,
                    new ItemStack(Material.GOLD_INGOT, 5)
            );

            String json = SerializedItemStack.itemsToJson(items);
            List<SerializedItemStack> deserialized = SerializedItemStack.listFromJson(json);

            assertThat(deserialized).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("Should show display name if present")
        void summaryWithDisplayName() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND_SWORD")
                    .amount(1)
                    .displayName("Legendary Sword")
                    .build();

            String summary = item.getSummary();

            assertThat(summary).contains("Legendary Sword");
            assertThat(summary).contains("x1");
        }

        @Test
        @DisplayName("Should show type if no display name")
        void summaryWithoutDisplayName() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND")
                    .amount(10)
                    .build();

            String summary = item.getSummary();

            assertThat(summary).contains("DIAMOND");
            assertThat(summary).contains("x10");
        }

        @Test
        @DisplayName("Should include enchantments")
        void summaryWithEnchantments() {
            Map<String, Integer> enchants = new HashMap<>();
            enchants.put("sharpness", 5);
            enchants.put("fire_aspect", 2);

            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND_SWORD")
                    .amount(1)
                    .enchantments(enchants)
                    .build();

            String summary = item.getSummary();

            assertThat(summary).contains("sharpness 5");
            assertThat(summary).contains("fire_aspect 2");
        }

        @Test
        @DisplayName("Should handle no enchantments")
        void summaryWithoutEnchantments() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND")
                    .amount(10)
                    .build();

            String summary = item.getSummary();

            assertThat(summary).doesNotContain("[");
            assertThat(summary).isEqualTo("DIAMOND x10");
        }

        @Test
        @DisplayName("Should use type when display name is empty string")
        void summaryWithEmptyDisplayName() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("GOLD_INGOT")
                    .amount(5)
                    .displayName("")
                    .build();

            String summary = item.getSummary();

            assertThat(summary).isEqualTo("GOLD_INGOT x5");
        }

        @Test
        @DisplayName("Should handle single enchantment")
        void summaryWithSingleEnchantment() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND_SWORD")
                    .amount(1)
                    .enchantments(Collections.singletonMap("sharpness", 5))
                    .build();

            String summary = item.getSummary();

            assertThat(summary).contains("sharpness 5");
            assertThat(summary).contains("[");
            assertThat(summary).contains("]");
        }

        @Test
        @DisplayName("Should handle empty enchantments map")
        void summaryWithEmptyEnchantments() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("DIAMOND_SWORD")
                    .amount(1)
                    .enchantments(Collections.emptyMap())
                    .build();

            String summary = item.getSummary();

            assertThat(summary).doesNotContain("[");
        }

        @Test
        @DisplayName("Should handle null display name")
        void summaryWithNullDisplayName() {
            SerializedItemStack item = SerializedItemStack.builder()
                    .type("EMERALD")
                    .amount(3)
                    .displayName(null)
                    .build();

            String summary = item.getSummary();

            assertThat(summary).isEqualTo("EMERALD x3");
        }
    }

    @Nested
    @DisplayName("No-Args Constructor")
    class NoArgsConstructor {

        @Test
        @DisplayName("Should create empty instance")
        void emptyInstance() {
            SerializedItemStack item = new SerializedItemStack();
            assertThat(item).isNotNull();
            assertThat(item.getType()).isNull();
            assertThat(item.getAmount()).isZero();
            assertThat(item.getDisplayName()).isNull();
            assertThat(item.getLore()).isNull();
            assertThat(item.getEnchantments()).isNull();
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("Should set and get all fields")
        void setAndGetFields() {
            SerializedItemStack item = new SerializedItemStack();
            item.setType("DIAMOND");
            item.setAmount(64);
            item.setDisplayName("Fancy Diamond");
            item.setLore(Arrays.asList("Line 1"));
            item.setDurability(100);
            item.setMaxDurability(200);
            item.setCustomModelData(42);
            item.setUnbreakable(true);
            item.setItemFlags(Arrays.asList("HIDE_ENCHANTS"));

            assertThat(item.getType()).isEqualTo("DIAMOND");
            assertThat(item.getAmount()).isEqualTo(64);
            assertThat(item.getDisplayName()).isEqualTo("Fancy Diamond");
            assertThat(item.getLore()).containsExactly("Line 1");
            assertThat(item.getDurability()).isEqualTo(100);
            assertThat(item.getMaxDurability()).isEqualTo(200);
            assertThat(item.getCustomModelData()).isEqualTo(42);
            assertThat(item.isUnbreakable()).isTrue();
            assertThat(item.getItemFlags()).containsExactly("HIDE_ENCHANTS");
        }
    }
}
