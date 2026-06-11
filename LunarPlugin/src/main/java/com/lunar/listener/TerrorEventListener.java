package com.lunar.listener;

import com.lunar.LunarPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.TimeSkipEvent;

/**
 * Intercepta eventos del mundo para mantener la atmósfera del evento Lunar.
 *
 * - Bloquea saltos de tiempo por votaciones o /sleep durante el evento.
 * - Cancela el uso de camas para que no se pueda saltar la noche.
 *
 * NOTA: El tiempo del mundo NO se fuerza a noche durante el evento normal
 * (solo /lunar forcecycle hace eso). Aquí solo impedimos que algo
 * externo salte el tiempo hacia el día.
 */
public final class TerrorEventListener implements Listener {

    private final LunarPlugin plugin;

    public TerrorEventListener(LunarPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bloquea saltos automáticos de tiempo (sleep-most, votaciones, comandos de tiempo)
     * mientras el evento está activo, para que la noche no pueda ser saltada.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (!plugin.getEventManager().isEventActive()) return;

        if (event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP
                || event.getSkipReason() == TimeSkipEvent.SkipReason.CUSTOM) {

            if (plugin.getLunarConfig().isWorldAllowed(event.getWorld().getName())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Impide que los jugadores duerman en camas durante el evento,
     * evitando que el ciclo de terror sea interrumpido.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!plugin.getEventManager().isEventActive()) return;
        if (!plugin.getLunarConfig().isWorldAllowed(event.getPlayer().getWorld().getName())) return;

        event.setCancelled(true);
    }
}
