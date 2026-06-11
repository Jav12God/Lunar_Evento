package com.lunar.entity;

import com.lunar.LunarPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestiona las apariciones de "La Presencia", la entidad fantasma que
 * materializa la amenaza del evento Lunar.
 *
 * Características:
 *  - Aparece entre 10 y 25 bloques del jugador
 *  - Sin IA, silenciosa, no dropea nada
 *  - Desaparece tras 3-10 segundos con partículas de smoke
 *  - En ciclos altos puede tener IA activa y atacar
 *  - Invisible con efecto glowing configurable
 */
public final class PresenceManager {

    private final LunarPlugin plugin;
    // Tracking de UUIDs de presencias activas
    private final List<UUID> trackedPresences = new ArrayList<>();

    public PresenceManager(LunarPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Spawning
    // =========================================================================

    public void spawnPresenceNearPlayer(Player player) {
        World world = player.getWorld();
        if (world == null) return;

        Location baseLoc  = player.getLocation();
        double   angle    = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
        double   distance = ThreadLocalRandom.current().nextDouble(10, 25);

        double targetX = baseLoc.getX() + (distance * Math.cos(angle));
        double targetZ = baseLoc.getZ() + (distance * Math.sin(angle));

        // Intentar superficie, con fallback a la posición del jugador
        double targetY;
        try {
            targetY = world.getHighestBlockYAt((int) targetX, (int) targetZ);
            if (Math.abs(targetY - baseLoc.getY()) > 6.0) {
                targetY = baseLoc.getY();
            }
        } catch (Exception e) {
            targetY = baseLoc.getY();
        }

        Location spawnLoc = new Location(world, targetX, targetY, targetZ);

        // Resolver tipo de entidad desde config
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(
                    plugin.getLunarConfig().getPresenceEntityType().toUpperCase());
        } catch (IllegalArgumentException e) {
            entityType = EntityType.WITHER_SKELETON;
        }

        // El spawn de entidades DEBE ocurrir en el hilo principal
        EntityType finalType = entityType;
        Bukkit.getScheduler().runTask(plugin, () -> doSpawn(player, world, spawnLoc, finalType));
    }

    private void doSpawn(Player player, World world, Location spawnLoc, EntityType entityType) {
        if (!player.isOnline()) return; // Jugador desconectado antes de que se ejecutara el spawn

        LivingEntity phantom;
        try {
            phantom = (LivingEntity) world.spawnEntity(spawnLoc, entityType);
        } catch (Exception e) {
            plugin.getLogger().warning("[Lunar] No se pudo spawnar La Presencia: " + e.getMessage());
            return;
        }

        // Configuración base — sin IA ni drops
        phantom.setSilent(true);
        phantom.setRemoveWhenFarAway(true);
        phantom.setPersistent(false);
        phantom.setMetadata("lunar_phantom", new FixedMetadataValue(plugin, true));

        // En ciclos bajos la presencia no tiene IA (solo observa)
        // En ciclos 3+ puede activar la IA para atacar si está configurado
        int cycleCount    = plugin.getDataManager().getCycleCount();
        boolean canAttack = cycleCount >= 3; // A partir del ciclo 3
        phantom.setAI(canAttack);

        // Equipo visual
        if (phantom.getEquipment() != null) {
            phantom.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
        }

        // Efecto de invisibilidad (la presencia se siente, no se ve directamente)
        if (plugin.getLunarConfig().isPresenceInvisible()) {
            phantom.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION,
                    0, false, false, false
            ));
        }

        if (plugin.getLunarConfig().isPresenceGlowing()) {
            phantom.setGlowing(true);
        }

        UUID phantomId = phantom.getUniqueId();
        trackedPresences.add(phantomId);

        // Partículas de aparición
        world.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 1, 0),
                40, 0.4, 0.4, 0.4, 0.1);

        // Programar la desaparición
        int despawnDelay = ThreadLocalRandom.current().nextInt(
                plugin.getLunarConfig().getPresenceDespawnMin(),
                plugin.getLunarConfig().getPresenceDespawnMax() + 1
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phantom.isValid()) {
                phantom.getWorld().spawnParticle(
                        Particle.SMOKE,
                        phantom.getLocation().add(0, 1, 0),
                        30, 0.2, 0.2, 0.2, 0.05
                );
                phantom.remove();
            }
            trackedPresences.remove(phantomId);
        }, despawnDelay * 20L);
    }

    // =========================================================================
    // Limpieza
    // =========================================================================

    /** Elimina todas las presencias activas en todos los mundos. */
    public void clearAllPresences() {
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.getLunarConfig().isWorldAllowed(world.getName())) continue;

            for (Entity entity : world.getEntities()) {
                if (!entity.hasMetadata("lunar_phantom")) continue;

                entity.getWorld().spawnParticle(
                        Particle.SMOKE,
                        entity.getLocation().add(0, 1, 0),
                        20, 0.1, 0.1, 0.1, 0.05
                );
                entity.remove();
            }
        }
        trackedPresences.clear();
    }

    public int getActivePresenceCount() {
        return trackedPresences.size();
    }
}
