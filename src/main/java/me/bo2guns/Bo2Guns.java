package me.bo2guns;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Bo2Guns extends JavaPlugin implements Listener {

    static class Gun {
        final String id, name; final int data;
        final double damage, range, spread; final long cooldownMs;
        final int mag, reloadTicks, pellets; final Sound sound; final float pitch;
        Gun(String id, String name, int data, double damage, long cooldownMs, int mag,
            int reloadTicks, double range, int pellets, double spread, Sound sound, float pitch) {
            this.id = id; this.name = name; this.data = data; this.damage = damage;
            this.cooldownMs = cooldownMs; this.mag = mag; this.reloadTicks = reloadTicks;
            this.range = range; this.pellets = pellets; this.spread = spread;
            this.sound = sound; this.pitch = pitch;
        }
    }

    private final List<Gun> guns = Arrays.asList(
        new Gun("m8a1",        "M8A1",        0, 4.0, 140, 30,  50, 60, 1, 0.020, Sound.ZOMBIE_METAL, 1.9f),
        new Gun("an94",        "AN-94",       1, 5.0, 170, 30,  55, 65, 1, 0.012, Sound.ZOMBIE_METAL, 1.6f),
        new Gun("mp7",         "MP7",         2, 3.0,  95, 40,  45, 40, 1, 0.045, Sound.ZOMBIE_METAL, 2.0f),
        new Gun("smr",         "SMR",         3, 4.5, 125, 100, 140, 55, 1, 0.035, Sound.EXPLODE,      1.8f),
        new Gun("peacekeeper", "Peacekeeper", 4, 4.0, 120, 30,  55, 50, 1, 0.030, Sound.ZOMBIE_METAL, 1.4f),
        new Gun("dsr50",       "DSR 50",      5, 18.0, 1400, 4, 90, 150, 1, 0.002, Sound.EXPLODE,      1.0f),
        new Gun("remington",   "Remington 870 MCS", 6, 2.0, 800, 6, 70, 22, 8, 0.090, Sound.EXPLODE,   1.3f),
        new Gun("fiveseven",   "Five-seven",  7, 3.5, 200, 20,  35, 45, 1, 0.025, Sound.ZOMBIE_METAL, 2.0f)
    );

    private final Map<UUID, Long> lastShot = new HashMap<UUID, Long>();
    private final Set<UUID> reloading = new HashSet<UUID>();
    private final Random rnd = new Random();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Bo2Guns enabled with " + guns.size() + " guns.");
    }

    private Gun byId(String id) {
        for (Gun g : guns) if (g.id.equalsIgnoreCase(id)) return g;
        return null;
    }

    private ItemStack make(Gun g) {
        ItemStack s = new ItemStack(Material.INK_SACK, 1, (short) g.data);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(ChatColor.WHITE + g.name);
        m.setLore(Arrays.asList(ammoLine(g.mag, g)));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        s.setItemMeta(m);
        return s;
    }

    private String ammoLine(int ammo, Gun g) {
        return ChatColor.DARK_GRAY + "Ammo: " + ChatColor.WHITE + ammo + ChatColor.DARK_GRAY + "/" + g.mag;
    }

    private Gun gunOf(ItemStack s) {
        if (s == null || s.getType() != Material.INK_SACK || !s.hasItemMeta()) return null;
        ItemMeta m = s.getItemMeta();
        if (!m.hasLore() || m.getLore().isEmpty()) return null;
        if (!ChatColor.stripColor(m.getLore().get(0)).startsWith("Ammo:")) return null;
        for (Gun g : guns) if (g.data == s.getDurability()) return g;
        return null;
    }

    private int ammoOf(ItemStack s) {
        String t = ChatColor.stripColor(s.getItemMeta().getLore().get(0));
        try { return Integer.parseInt(t.substring(6, t.indexOf('/')).trim()); }
        catch (Exception e) { return 0; }
    }

    private void setAmmo(ItemStack s, Gun g, int ammo) {
        ItemMeta m = s.getItemMeta();
        m.setLore(Arrays.asList(ammoLine(ammo, g)));
        s.setItemMeta(m);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getItemInHand();
        Gun g = gunOf(hand);
        if (g == null) return;
        e.setCancelled(true);
        if (!p.hasPermission("bo2guns.use")) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            shoot(p, hand, g);
        } else if (e.getAction() == Action.LEFT_CLICK_AIR) {
            reload(p, g);
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (gunOf(e.getPlayer().getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Gun g = gunOf(e.getItemDrop().getItemStack());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getPlayer().hasPermission("bo2guns.use")) reload(e.getPlayer(), g);
    }

    private void shoot(Player p, ItemStack hand, Gun g) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastShot.get(id);
        if (last != null && now - last < g.cooldownMs) return;
        if (reloading.contains(id)) return;

        int ammo = ammoOf(hand);
        if (ammo <= 0) {
            p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.5f);
            lastShot.put(id, now);
            reload(p, g);
            return;
        }
        lastShot.put(id, now);
        setAmmo(hand, g, ammo - 1);
        p.setItemInHand(hand);

        for (int i = 0; i < g.pellets; i++) fire(p, g);

        p.getWorld().playSound(p.getLocation(), g.sound, 1.2f, g.pitch);
        p.getWorld().playEffect(p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8)),
                                Effect.SMOKE, 4);
    }

    private void fire(Player p, Gun g) {
        Location eye = p.getEyeLocation();
        double sp = g.spread;
        if (p.isSneaking()) sp *= 0.4;
        if (p.isSprinting()) sp *= 2.0;
        Vector dir = eye.getDirection().clone();
        dir.add(new Vector((rnd.nextDouble() - .5) * 2 * sp,
                           (rnd.nextDouble() - .5) * 2 * sp,
                           (rnd.nextDouble() - .5) * 2 * sp)).normalize();

        List<LivingEntity> targets = new ArrayList<LivingEntity>();
        for (Entity en : p.getNearbyEntities(g.range, g.range, g.range))
            if (en instanceof LivingEntity && !en.equals(p) && !(en instanceof ArmorStand))
                targets.add((LivingEntity) en);

        for (double d = 0.5; d < g.range; d += 0.2) {
            Location pos = eye.clone().add(dir.clone().multiply(d));
            if (pos.getBlock().getType().isSolid()) {
                p.getWorld().playEffect(pos, Effect.SMOKE, 4);
                return;
            }
            for (LivingEntity t : targets) {
                Location l = t.getLocation();
                if (Math.abs(pos.getX() - l.getX()) < 0.4 && Math.abs(pos.getZ() - l.getZ()) < 0.4
                        && pos.getY() > l.getY() - 0.05 && pos.getY() < l.getY() + t.getEyeHeight() + 0.15) {
                    boolean head = pos.getY() >= l.getY() + t.getEyeHeight() - 0.25;
                    t.setNoDamageTicks(0);
                    t.damage(g.damage * (head ? 2.0 : 1.0), p);
                    p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 1f, head ? 2f : 1f);
                    p.getWorld().playEffect(pos, Effect.COLOURED_DUST, 0);
                    return;
                }
            }
            if (((int) (d * 5)) % 8 == 0) p.getWorld().playEffect(pos, Effect.CRIT, 0);
        }
    }

    private void reload(final Player p, final Gun g) {
        final UUID id = p.getUniqueId();
        if (reloading.contains(id)) return;
        ItemStack hand = p.getItemInHand();
        if (gunOf(hand) != g || ammoOf(hand) >= g.mag) return;
        reloading.add(id);
        final int slot = p.getInventory().getHeldItemSlot();
        p.playSound(p.getLocation(), Sound.PISTON_EXTEND, 1f, 1.6f);
        p.sendMessage(ChatColor.GRAY + "Reloading " + g.name + "...");
        new BukkitRunnable() {
            public void run() {
                reloading.remove(id);
                if (!p.isOnline() || p.getInventory().getHeldItemSlot() != slot) return;
                ItemStack cur = p.getInventory().getItem(slot);
                if (gunOf(cur) != g) return;
                setAmmo(cur, g, g.mag);
                p.getInventory().setItem(slot, cur);
                p.playSound(p.getLocation(), Sound.PISTON_RETRACT, 1f, 1.8f);
            }
        }.runTaskLater(this, g.reloadTicks);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("bo2guns.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            StringBuilder sb = new StringBuilder(ChatColor.GOLD + "Guns: " + ChatColor.WHITE);
            for (Gun g : guns) sb.append(g.id).append(' ');
            s.sendMessage(sb.toString());
            return true;
        }
        if (a[0].equalsIgnoreCase("give") && a.length >= 2) {
            Gun g = byId(a[1]);
            if (g == null) { s.sendMessage(ChatColor.RED + "Unknown gun. /bo2 list"); return true; }
            Player t = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player ? (Player) s : null);
            if (t == null) { s.sendMessage(ChatColor.RED + "Player not found."); return true; }
            t.getInventory().addItem(make(g));
            s.sendMessage(ChatColor.GREEN + "Gave " + g.name + " to " + t.getName());
            return true;
        }
        s.sendMessage(ChatColor.YELLOW + "/bo2 give <gun> [player]  |  /bo2 list");
        return true;
    }
}
