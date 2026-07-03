package org.example.pillars.managers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.pillars.items.CommonItems;
import org.example.pillars.items.LegendaryItems;
import org.example.pillars.items.RareItems;

import java.util.Map;
import java.util.Random;

public class ItemManager {
    private final Random random = new Random();
    private final int legendaryPercent;
    private final int rarePercent;

    public ItemManager(JavaPlugin plugin) {
        this.legendaryPercent = Math.max(0, Math.min(100, plugin.getConfig().getInt("settings.itemRarity.legendaryPercent", 5)));
        this.rarePercent = Math.max(0, Math.min(100 - legendaryPercent, plugin.getConfig().getInt("settings.itemRarity.rarePercent", 15)));
    }

    public ItemStack getRandomItem() {
        int roll = random.nextInt(100) + 1; // 1-100

        if (roll <= legendaryPercent) {
            return getRandomFromMap(LegendaryItems.ITEMS);
        } else if (roll <= legendaryPercent + rarePercent) {
            return getRandomFromMap(RareItems.ITEMS);
        } else {
            return getRandomFromMap(CommonItems.ITEMS);
        }
    }

    private ItemStack getRandomFromMap(Map<Material, Integer> map) {
        int totalWeight = map.values().stream().mapToInt(i -> i).sum();
        int r = random.nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<Material, Integer> entry : map.entrySet()) {
            cumulative += entry.getValue();
            if (r < cumulative) {
                return new ItemStack(entry.getKey());
            }
        }
        return new ItemStack(Material.STONE);
    }

    public void giveRandomItem(Player player) {
        ItemStack item = getRandomItem();
        player.getInventory().addItem(item);
    }
}
