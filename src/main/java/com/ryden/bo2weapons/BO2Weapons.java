
package com.ryden.bo2weapons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BO2Weapons extends JavaPlugin {

    private final Map<String, Material> weapons = new HashMap<String, Material>();

    @Override
    public void onEnable() {
        weapons.put("mp7", Material.IRON_HOE);
        weapons.put("msmc", Material.GOLD_HOE);
        weapons.put("an94", Material.DIAMOND_HOE);
        weapons.put("pdw57", Material.STONE_HOE);
        weapons.put("dsr50", Material.BOW);
        weapons.put("ballista", Material.WOOD_HOE);
        weapons.put("scarh", Material.IRON_SWORD);
        weapons.put("shotgun", Material.BLAZE_ROD);
        weapons.put("raygun", Material.EMERALD);

        getLogger().info("BO2Weapons enabled!");
    }

    private ItemStack createWeapon(String name) {
        Material material = weapons.get(name.toLowerCase());

        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "BO2 " + name.toUpperCase());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "BO2 Arsenal",
                ChatColor.YELLOW + "Uncraftable weapon"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return false;

        String display = ChatColor.stripColor(meta.getDisplayName());

        return display.startsWith("BO2 ");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!command.getName().equalsIgnoreCase("guns")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "BO2Weapons Commands:");
            sender.sendMessage("/guns give <player> <weapon>");
            sender.sendMessage("/guns list");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GREEN + "Weapons: " +
                    String.join(", ", weapons.keySet()));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {

            if (!(sender instanceof Player) ||
                    !sender.getName().equalsIgnoreCase("slimeden")) {
                sender.sendMessage(ChatColor.RED + "Only slimeden can give weapons!");
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage("/guns give <player> <weapon>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }

            ItemStack weapon = createWeapon(args[2]);

            if (weapon == null) {
                sender.sendMessage(ChatColor.RED + "Unknown weapon!");
                return true;
            }

            target.getInventory().addItem(weapon);
            sender.sendMessage(ChatColor.GREEN + "Weapon given!");
            target.sendMessage(ChatColor.GOLD + "You received a BO2 weapon!");

            return true;
        }

        return true;
    }
}
