package com.lunar.listener;

import com.lunar.LunarPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * Aplica multiplicadores de atributos adicionales a mobs hostiles mientras
 * el evento Lunar esté activo, sobre los valores que ya tuvieran de LevelledMobs
 * e InfernalMobsPlus.
 *
 * Usa NamespacedKey únicos ("lunar_health", "lunar_damage", "lunar_speed") para
 * poder identificar y eliminar solo los modificadores de este plugin sin afectar
 * a los de otros sistemas.
 *
 * Prioridad HIGH para ejecutarse después de LevelledMobs (NORMAL) pero antes
 * de que el mob entre en juego.
 */
public final class MobModificationListener implements Listener {

    private final LunarPlugin plugin;

    public MobModificationListener(LunarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!plugin.getEventManager().isEventActive()) return;
        if (!plugin.getLunarConfig().isMobModifiersEnabled()) return;
        if (!plugin.getLunarConfig().isWorldAllowed(event.getLocation().getWorld().getName())) return;

        // Solo aplicar a mobs hostiles (Enemy interface en Bukkit 1.20+)
        if (!(event.getEntity() instanceof Enemy)) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (!(mob instanceof Attributable attributable)) return;

        // Escalado por ciclo: cada ciclo suma un 10% adicional al multiplicador base
        int cycleCount   = plugin.getDataManager().getCycleCount();
        double scaleFactor = 1.0 + (cycleCount * 0.10);

        applyScaledModifier(
                attributable, Attribute.MAX_HEALTH,
                new NamespacedKey(plugin, "lunar_health"),
                (plugin.getLunarConfig().getMobHealthMultiplier() - 1.0) * scaleFactor
        );

        applyScaledModifier(
                attributable, Attribute.ATTACK_DAMAGE,
                new NamespacedKey(plugin, "lunar_damage"),
                (plugin.getLunarConfig().getMobDamageMultiplier() - 1.0) * scaleFactor
        );

        applyScaledModifier(
                attributable, Attribute.MOVEMENT_SPEED,
                new NamespacedKey(plugin, "lunar_speed"),
                (plugin.getLunarConfig().getMobSpeedMultiplier() - 1.0) * scaleFactor
        );

        // Ajustar HP actual al nuevo máximo
        AttributeInstance maxHealth = attributable.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            mob.setHealth(maxHealth.getValue());
        }
    }

    /**
     * Aplica un modificador ADD_SCALAR solo si el mob aún no lo tiene.
     * ADD_SCALAR multiplica sobre el valor base: value = base + (base * scalar).
     */
    private void applyScaledModifier(Attributable attributable, Attribute attribute,
                                      NamespacedKey key, double scalar) {
        AttributeInstance instance = attributable.getAttribute(attribute);
        if (instance == null) return;

        // Evitar duplicados
        boolean alreadyHas = instance.getModifiers().stream()
                .anyMatch(mod -> mod.getKey().equals(key));
        if (alreadyHas) return;

        instance.addModifier(new AttributeModifier(
                key,
                scalar,
                AttributeModifier.Operation.ADD_SCALAR,
                EquipmentSlotGroup.ANY
        ));
    }

    /** Impedir que La Presencia dropee ítems o experiencia al morir. */
    @EventHandler(ignoreCancelled = true)
    public void onPresenceDeath(EntityDeathEvent event) {
        if (event.getEntity().hasMetadata("lunar_phantom")) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
}
