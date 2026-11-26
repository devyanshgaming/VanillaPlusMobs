package com.devyanshgaming.vanillaplusmobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class VanillaPlusMobs extends JavaPlugin implements Listener {
    private NamespacedKey enhancedKey;
    private Team enhancedTeam;
    private boolean createdTeam;
    private final Set<String> trackedEntries = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enhancedKey = new NamespacedKey(this, "enhanced_mob");
        setupTeam();
        getServer().getPluginManager().registerEvents(this, this);

        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living && isEnhanced(living)) {
                    applyGlow(living);
                }
            }
        }
        getLogger().info("VanillaPlusMobs has been enabled!");
    }

    @Override
    public void onDisable() {
        if (enhancedTeam != null) {
            for (String entry : trackedEntries) enhancedTeam.removeEntry(entry);
            if (createdTeam) enhancedTeam.unregister();
        }
        trackedEntries.clear();
    }

    private void setupTeam() {
        if (!getConfig().getBoolean("red-glow", true)) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard scoreboard = manager.getMainScoreboard();
        enhancedTeam = scoreboard.getTeam("vpm_enhanced");
        if (enhancedTeam == null) {
            enhancedTeam = scoreboard.registerNewTeam("vpm_enhanced");
            createdTeam = true;
        }
        enhancedTeam.setColor(ChatColor.RED);
    }

    @EventHandler
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity mob = event.getEntity();
        if (!isEligible(mob.getType())) return;

        double chance = Math.max(0.0, Math.min(1.0,
                getConfig().getDouble("enhanced-spawn-chance", 0.30)));
        if (ThreadLocalRandom.current().nextDouble() < chance) makeEnhanced(mob);
    }

    private boolean isEligible(EntityType type) {
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON
                || type == EntityType.CREEPER || type == EntityType.SPIDER
                || type == EntityType.ENDERMAN;
    }

    private void makeEnhanced(LivingEntity mob) {
        if (isEnhanced(mob)) return;
        mob.getPersistentDataContainer().set(enhancedKey, PersistentDataType.BYTE, (byte) 1);

        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double multiplier = Math.max(0.1, getConfig().getDouble("health-multiplier", 2.0));
            double newMaxHealth = maxHealth.getBaseValue() * multiplier;
            maxHealth.setBaseValue(newMaxHealth);
            mob.setHealth(newMaxHealth);
        }
        applyGlow(mob);
    }

    private boolean isEnhanced(LivingEntity mob) {
        return mob.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE);
    }

    private void applyGlow(LivingEntity mob) {
        if (!getConfig().getBoolean("red-glow", true)) return;
        mob.setGlowing(true);
        if (enhancedTeam != null) {
            String entry = mob.getUniqueId().toString();
            enhancedTeam.addEntry(entry);
            trackedEntries.add(entry);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living && isEnhanced(living)) applyGlow(living);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        if (attacker instanceof LivingEntity living && isEnhanced(living)) {
            double multiplier = Math.max(0.0, getConfig().getDouble("damage-multiplier", 1.5));
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler
    public void onEnhancedMobDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!isEnhanced(mob)) return;

        switch (mob.getType()) {
            case ZOMBIE -> {
                addDrop(event, "bonus-drops.ZOMBIE.rotten-flesh", Material.ROTTEN_FLESH);
                double chance = Math.max(0.0, Math.min(1.0,
                        getConfig().getDouble("bonus-drops.ZOMBIE.iron-ingot-chance", 0.05)));
                if (ThreadLocalRandom.current().nextDouble() < chance)
                    event.getDrops().add(new ItemStack(Material.IRON_INGOT));
            }
            case SKELETON -> {
                addDrop(event, "bonus-drops.SKELETON.bone", Material.BONE);
                addDrop(event, "bonus-drops.SKELETON.arrow", Material.ARROW);
            }
            case CREEPER -> addDrop(event, "bonus-drops.CREEPER.gunpowder", Material.GUNPOWDER);
            case SPIDER -> addDrop(event, "bonus-drops.SPIDER.string", Material.STRING);
            case ENDERMAN -> addDrop(event, "bonus-drops.ENDERMAN.ender-pearl", Material.ENDER_PEARL);
            default -> { }
        }

        String entry = mob.getUniqueId().toString();
        if (enhancedTeam != null) enhancedTeam.removeEntry(entry);
        trackedEntries.remove(entry);
    }

    private void addDrop(EntityDeathEvent event, String path, Material material) {
        int amount = getConfig().getInt(path, 0);
        if (amount > 0) event.getDrops().add(new ItemStack(material, amount));
    }
}
