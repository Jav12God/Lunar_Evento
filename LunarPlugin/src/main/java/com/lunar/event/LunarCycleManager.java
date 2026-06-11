package com.lunar.event;

import com.lunar.LunarPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Monitoriza el paso de días de Minecraft de forma global y determina cuándo
 * activar el evento Lunar al completarse un ciclo.
 *
 * Lógica de ciclo:
 *  - Un ciclo = N días de Minecraft (configurado en days-per-cycle, default 4).
 *  - Se cuenta una "noche" por cada transición hacia tiempo ≥ 13000 ticks.
 *  - Al completar N noches → se dispara el evento.
 *  - El día actual persiste entre reinicios via LunarDataManager.
 */
public final class LunarCycleManager {

    private final LunarPlugin plugin;
    private BukkitTask cycleTask;

    // Guardia para no contar dos veces la misma noche
    private boolean nightCounted  = false;
    private long    previousTime  = -1L;

    public LunarCycleManager(LunarPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Ciclo de monitorización
    // -------------------------------------------------------------------------

    /** Arranca la tarea que detecta el paso de noches. Seguro llamarlo varias veces. */
    public void startCycleTask() {
        stopCycleTask();

        // Comprobación cada 10 segundos (200 ticks) — suficiente resolución, bajo impacto
        this.cycleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkNightTransition, 200L, 200L);
        plugin.getLogger().info("[Lunar] Monitor de ciclos iniciado.");
    }

    public void stopCycleTask() {
        if (cycleTask != null && !cycleTask.isCancelled()) {
            cycleTask.cancel();
        }
    }

    private void checkNightTransition() {
        // No contar noches mientras el evento esté activo
        if (plugin.getEventManager().isEventActive()) {
            // Reiniciar la guardia para cuando el evento termine
            nightCounted = false;
            return;
        }

        World trackedWorld = Bukkit.getWorlds().stream()
                .filter(w -> plugin.getLunarConfig().isWorldAllowed(w.getName()))
                .findFirst()
                .orElse(null);

        if (trackedWorld == null) return;

        long currentTime = trackedWorld.getTime();

        // Detectar transición hacia la noche (cruce del umbral de 13000 ticks)
        boolean isNight = (currentTime >= 13000 && currentTime < 23950);

        if (!nightCounted && isNight && previousTime >= 0 && previousTime < 13000) {
            onNightFallen();
            nightCounted = true;
        }

        // Volver a habilitar la detección al comenzar el día siguiente
        if (nightCounted && currentTime < 13000) {
            nightCounted = false;
        }

        this.previousTime = currentTime;
    }

    private void onNightFallen() {
        int newDay = plugin.getDataManager().getCurrentDay() + 1;
        int daysPerCycle = plugin.getLunarConfig().getDaysPerCycle();

        plugin.getLogger().info("[Lunar] Noche detectada → día de ciclo " + newDay + "/" + daysPerCycle);

        if (newDay >= daysPerCycle) {
            // Ciclo completado → disparar evento
            plugin.getDataManager().incrementCycleCount();
            plugin.getDataManager().resetDay();
            plugin.getLogger().info("[Lunar] ¡Ciclo " + plugin.getDataManager().getCycleCount() + " completado! Iniciando evento...");
            plugin.getEventManager().startEvent();
        } else {
            plugin.getDataManager().setCurrentDay(newDay);
        }
    }

    // -------------------------------------------------------------------------
    // Comando /lunar forcecycle
    // -------------------------------------------------------------------------

    /**
     * Fuerza el inicio inmediato del evento en el siguiente ciclo.
     * También fija la hora a medianoche en todos los mundos permitidos
     * (único caso donde se fuerza la noche, según el spec).
     */
    public void forceNextCycle() {
        plugin.getDataManager().incrementCycleCount();
        plugin.getDataManager().resetDay();

        // Fijar noche solo en forcecycle — comportamiento explícito del spec
        Bukkit.getWorlds().stream()
                .filter(w -> plugin.getLunarConfig().isWorldAllowed(w.getName()))
                .forEach(w -> w.setTime(18000L));

        plugin.getEventManager().startEvent();
        plugin.getLogger().info("[Lunar] Ciclo forzado por comando. Ciclo actual: "
                + plugin.getDataManager().getCycleCount());
    }
}
