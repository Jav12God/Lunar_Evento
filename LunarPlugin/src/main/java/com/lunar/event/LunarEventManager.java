package com.lunar.event;

import com.lunar.LunarPlugin;
import com.lunar.entity.PresenceManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Núcleo del evento Lunar. Gestiona el inicio/fin del evento,
 * los loops de efectos, sonidos, mensajes, daño psicológico y presencias.
 *
 * Diferencias clave respecto a la versión original:
 *  - El tiempo del mundo NO se fuerza durante el evento (solo lo hace forcecycle).
 *  - revertMobModifiers() elimina ÚNICAMENTE modificadores con key "lunar_*".
 *  - El daño psicológico usa rango correcto (no agrega +1.0 sobre maxDamage convertido).
 *  - El escalado por ciclos afecta duración de efectos, probabilidad de daño y presencias.
 */
public final class LunarEventManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LunarPlugin     plugin;
    private final PresenceManager presenceManager;

    // Cooldowns individuales por jugador (en ms epoch)
    private final Map<UUID, Long> soundCooldowns    = new HashMap<>();
    private final Map<UUID, Long> messageCooldowns  = new HashMap<>();
    private final Map<UUID, Long> damageCooldowns   = new HashMap<>();
    private final Map<UUID, Long> presenceCooldowns = new HashMap<>();

    private boolean    eventActive       = false;
    private int        secondsRemaining  = 0;
    private BukkitTask timerTask;
    private BukkitTask heartbeatLoopTask;

    public LunarEventManager(LunarPlugin plugin) {
        this.plugin          = plugin;
        this.presenceManager = new PresenceManager(plugin);
    }

    // =========================================================================
    // Inicio / Fin del evento
    // =========================================================================

    public void startEvent() {
        if (eventActive) return;
        this.eventActive      = true;
        this.secondsRemaining = plugin.getLunarConfig().getEventDurationSeconds();

        int cycleCount = plugin.getDataManager().getCycleCount();
        plugin.getLogger().info("[Lunar] Evento iniciado. Ciclo #" + cycleCount);

        // Sonidos de apertura globales
        broadcastGlobalSound("entity.warden.emerge",    1.2f, 0.5f);
        broadcastGlobalSound("entity.warden.heartbeat", 1.0f, 0.7f);

        // Título de apertura
        Title eventTitle = Title.title(
                MM.deserialize("<dark_red><bold>LUNAR</bold></dark_red>"),
                MM.deserialize("<red>La luna ha despertado...</red>"),
                Title.Times.times(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2)
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAllowedWorld(player)) continue;
            player.showTitle(eventTitle);
            player.sendMessage(MM.deserialize("<red>Algo se mueve entre las sombras...</red>"));
            player.sendMessage(MM.deserialize("<dark_red>No mires atrás.</dark_red>"));
        }

        startEventLoops();
    }

    public void stopEvent() {
        if (!eventActive) return;
        this.eventActive = false;

        cancelTasks();
        presenceManager.clearAllPresences();

        // Limpiar efectos y notificar a todos los jugadores
        String endMessage = "<green>La luna vuelve a dormir...</green>";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAllowedWorld(player)) continue;
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.sendMessage(MM.deserialize(endMessage));
        }

        // Solo eliminar los modificadores del plugin Lunar (no los de LevelledMobs/InfernalMobs)
        revertLunarMobModifiers();

        // Limpiar cooldowns
        soundCooldowns.clear();
        messageCooldowns.clear();
        damageCooldowns.clear();
        presenceCooldowns.clear();

        plugin.getLogger().info("[Lunar] Evento detenido y estado limpiado.");
    }

    private void cancelTasks() {
        if (timerTask         != null) timerTask.cancel();
        if (heartbeatLoopTask != null) heartbeatLoopTask.cancel();
        timerTask         = null;
        heartbeatLoopTask = null;
    }

    // =========================================================================
    // Loops del evento
    // =========================================================================

    private void startEventLoops() {

        // Timer de cuenta regresiva — 1 tick = 1 segundo
        // NO fuerza el tiempo del mundo (solo forcecycle lo hace)
        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (secondsRemaining <= 0) {
                stopEvent();
                return;
            }
            secondsRemaining--;
        }, 20L, 20L);

        // Loop principal de efectos — cada segundo
        this.heartbeatLoopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isAllowedWorld(player)) continue;

                applyAtmosphericPotions(player);
                dispatchRelativeSounds(player, now);
                dispatchPsychologicalMessages(player, now);
                dispatchPsychologicalDamage(player, now);
                dispatchPresenceSpawns(player, now);
            }
        }, 20L, 20L);
    }

    // =========================================================================
    // Efectos atmosféricos
    // =========================================================================

    private void applyAtmosphericPotions(Player player) {
        int cycleBonus = plugin.getDataManager().getCycleCount();

        if (plugin.getLunarConfig().isBlindnessEnabled()) {
            // La duración base es de 7s (140 ticks) + 20 ticks por ciclo, máx 200 ticks
            int blindnessDuration = Math.min(140 + (cycleBonus * 20), 200);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    blindnessDuration,
                    plugin.getLunarConfig().getBlindnessAmplifier(),
                    false, false, false
            ));
        }
        if (plugin.getLunarConfig().isDarknessEnabled()) {
            int darknessDuration = Math.min(140 + (cycleBonus * 20), 200);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DARKNESS,
                    darknessDuration,
                    plugin.getLunarConfig().getDarknessAmplifier(),
                    false, false, false
            ));
        }
    }

    // =========================================================================
    // Sonidos posicionales
    // =========================================================================

    private void dispatchRelativeSounds(Player player, long now) {
        if (!plugin.getLunarConfig().isSoundsEnabled()) return;

        long nextTrigger = soundCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextTrigger) return;

        List<String> categories = plugin.getLunarConfig().getSoundCategories();
        if (categories.isEmpty()) return;

        // Intervalos más cortos en ciclos avanzados (mínimo la mitad del original)
        int cycleBonus   = plugin.getDataManager().getCycleCount();
        long minInterval = Math.max(plugin.getLunarConfig().getSoundMinInterval() - (cycleBonus * 2L), 5L);
        long maxInterval = Math.max(plugin.getLunarConfig().getSoundMaxInterval() - (cycleBonus * 3L), minInterval + 1L);

        long nextInterval = ThreadLocalRandom.current().nextLong(minInterval, maxInterval + 1) * 1000L;
        soundCooldowns.put(player.getUniqueId(), now + nextInterval);

        String sound = categories.get(ThreadLocalRandom.current().nextInt(categories.size()));

        Location soundLoc = calculateVectorOffset(player);
        float volume = (float) ThreadLocalRandom.current().nextDouble(0.35, 0.90);
        float pitch  = (float) ThreadLocalRandom.current().nextDouble(0.4,  1.1);

        player.playSound(soundLoc, sound, SoundCategory.AMBIENT, volume, pitch);
    }

    // =========================================================================
    // Mensajes psicológicos
    // =========================================================================

    private void dispatchPsychologicalMessages(Player player, long now) {
        if (!plugin.getLunarConfig().isMessagesEnabled()) return;

        long nextTrigger = messageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextTrigger) return;

        List<String> messages = plugin.getLunarConfig().getSpookyMessages();
        if (messages.isEmpty()) return;

        long nextInterval = ThreadLocalRandom.current().nextLong(
                plugin.getLunarConfig().getMessageMinInterval(),
                plugin.getLunarConfig().getMessageMaxInterval() + 1L
        ) * 1000L;
        messageCooldowns.put(player.getUniqueId(), now + nextInterval);

        String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        player.sendMessage(MM.deserialize(message));
    }

    // =========================================================================
    // Daño psicológico
    // =========================================================================

    private void dispatchPsychologicalDamage(Player player, long now) {
        if (!plugin.getLunarConfig().isDamageEnabled()) return;

        long nextTrigger = damageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextTrigger) return;

        // Probabilidad de daño aumenta por ciclo (base 15%, +5% por ciclo, máx 60%)
        int cycleCount      = plugin.getDataManager().getCycleCount();
        double damageChance = Math.min(0.15 + (cycleCount * 0.05), 0.60);
        if (ThreadLocalRandom.current().nextDouble() > damageChance) {
            // No daña esta vez pero reinicia el cooldown igualmente
        }

        long nextInterval = ThreadLocalRandom.current().nextLong(
                plugin.getLunarConfig().getDamageMinInterval(),
                plugin.getLunarConfig().getDamageMaxInterval() + 1L
        ) * 1000L;
        damageCooldowns.put(player.getUniqueId(), now + nextInterval);

        // Solo continúa si supera la probabilidad de daño
        if (ThreadLocalRandom.current().nextDouble() > damageChance) return;

        // minDamage y maxDamage ya vienen convertidos a puntos de HP (corazones × 2)
        double minDmg    = plugin.getLunarConfig().getMinDamage();
        double maxDmg    = plugin.getLunarConfig().getMaxDamage();
        double damageAmt = ThreadLocalRandom.current().nextDouble(minDmg, maxDmg);

        player.damage(damageAmt);

        List<String> dmgMessages = plugin.getLunarConfig().getDamageMessages();
        List<String> dmgSounds   = plugin.getLunarConfig().getDamageSounds();

        if (!dmgMessages.isEmpty()) {
            String msg = dmgMessages.get(ThreadLocalRandom.current().nextInt(dmgMessages.size()));
            player.sendMessage(MM.deserialize(msg));
        }
        if (!dmgSounds.isEmpty()) {
            String snd = dmgSounds.get(ThreadLocalRandom.current().nextInt(dmgSounds.size()));
            player.playSound(player.getLocation(), snd, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

    // =========================================================================
    // Apariciones de La Presencia
    // =========================================================================

    private void dispatchPresenceSpawns(Player player, long now) {
        if (!plugin.getLunarConfig().isPresenceEnabled()) return;

        long nextTrigger = presenceCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextTrigger) return;

        // Intervalos reducidos en ciclos altos
        int cycleBonus   = plugin.getDataManager().getCycleCount();
        long minInterval = Math.max(plugin.getLunarConfig().getPresenceMinInterval() - (cycleBonus * 10L), 20L);
        long maxInterval = Math.max(plugin.getLunarConfig().getPresenceMaxInterval() - (cycleBonus * 15L), minInterval + 1L);

        long nextInterval = ThreadLocalRandom.current().nextLong(minInterval, maxInterval + 1) * 1000L;
        presenceCooldowns.put(player.getUniqueId(), now + nextInterval);

        presenceManager.spawnPresenceNearPlayer(player);
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Calcula una ubicación desplazada relativa al jugador:
     *  - Tipo 0 → delante
     *  - Tipo 1 → a la derecha
     *  - Tipo 2 → a la izquierda
     *  - Tipo 3 → detrás (más aterrador)
     */
    private Location calculateVectorOffset(Player player) {
        Location baseLoc    = player.getLocation();
        double   yawRadians = Math.toRadians(baseLoc.getYaw());
        double   distance   = ThreadLocalRandom.current().nextDouble(2.0, 5.0);

        int    type = ThreadLocalRandom.current().nextInt(4);
        Vector finalOffset;

        switch (type) {
            case 0 -> // Delante
                    finalOffset = new Vector(
                            Math.sin(yawRadians) * distance, 0,
                            -Math.cos(yawRadians) * distance
                    );
            case 1 -> { // Derecha
                double rightAngle = yawRadians + Math.PI / 2;
                finalOffset = new Vector(
                        Math.sin(rightAngle) * distance, 0,
                        -Math.cos(rightAngle) * distance
                );
            }
            case 2 -> { // Izquierda
                double leftAngle = yawRadians - Math.PI / 2;
                finalOffset = new Vector(
                        Math.sin(leftAngle) * distance, 0,
                        -Math.cos(leftAngle) * distance
                );
            }
            default -> // Detrás (type 3) — el más inquietante
                    finalOffset = new Vector(
                            -Math.sin(yawRadians) * distance, 0,
                            Math.cos(yawRadians) * distance
                    );
        }

        double yOffset = ThreadLocalRandom.current().nextDouble(-0.5, 1.2);
        return baseLoc.clone().add(finalOffset).add(0, yOffset, 0);
    }

    private void broadcastGlobalSound(String sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAllowedWorld(player)) continue;
            player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, volume, pitch);
        }
    }

    private boolean isAllowedWorld(Player player) {
        return plugin.getLunarConfig().isWorldAllowed(player.getWorld().getName());
    }

    /**
     * Elimina ÚNICAMENTE los modificadores de atributos creados por Lunar
     * (identificados por NamespacedKey con namespace "lunar").
     * No toca los modificadores de LevelledMobs, InfernalMobsPlus ni otros plugins.
     */
    private void revertLunarMobModifiers() {
        NamespacedKey hpKey    = new NamespacedKey(plugin, "lunar_health");
        NamespacedKey dmgKey   = new NamespacedKey(plugin, "lunar_damage");
        NamespacedKey speedKey = new NamespacedKey(plugin, "lunar_speed");

        for (World world : Bukkit.getWorlds()) {
            if (!plugin.getLunarConfig().isWorldAllowed(world.getName())) continue;

            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Attributable attributable)) continue;

                removeLunarModifier(attributable, Attribute.MAX_HEALTH,     hpKey);
                removeLunarModifier(attributable, Attribute.ATTACK_DAMAGE,  dmgKey);
                removeLunarModifier(attributable, Attribute.MOVEMENT_SPEED, speedKey);
            }
        }
    }

    private void removeLunarModifier(Attributable attributable, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = attributable.getAttribute(attribute);
        if (instance == null) return;

        instance.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(instance::removeModifier);
    }

    // =========================================================================
    // Accesores públicos
    // =========================================================================

    public boolean isEventActive()     { return eventActive;      }
    public int     getSecondsRemaining() { return secondsRemaining; }
    public PresenceManager getPresenceManager() { return presenceManager; }
}
