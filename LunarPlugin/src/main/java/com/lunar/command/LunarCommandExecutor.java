package com.lunar.command;

import com.lunar.LunarPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona el comando raíz /lunar y sus subcomandos.
 * Solo accesible para jugadores/consola con el permiso lunar.admin.
 */
public final class LunarCommandExecutor implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS =
            List.of("start", "stop", "status", "reload", "forcecycle");

    private final LunarPlugin plugin;

    public LunarCommandExecutor(LunarPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Ejecución de comandos
    // =========================================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("lunar.admin")) {
            sender.sendMessage(MM.deserialize(
                    "<red>No tienes permiso para administrar el motor Lunar.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                if (plugin.getEventManager().isEventActive()) {
                    sender.sendMessage(MM.deserialize("<red>El evento Lunar ya está activo.</red>"));
                    return true;
                }
                plugin.getEventManager().startEvent();
                sender.sendMessage(MM.deserialize("<green>Evento Lunar iniciado manualmente.</green>"));
            }

            case "stop" -> {
                if (!plugin.getEventManager().isEventActive()) {
                    sender.sendMessage(MM.deserialize("<red>No hay ningún evento Lunar activo.</red>"));
                    return true;
                }
                plugin.getEventManager().stopEvent();
                sender.sendMessage(MM.deserialize("<green>Evento Lunar detenido de forma segura.</green>"));
            }

            case "status" -> sendStatus(sender);

            case "reload" -> {
                plugin.getLunarConfig().reload();
                sender.sendMessage(MM.deserialize("<green>Configuración de Lunar recargada.</green>"));
            }

            case "forcecycle" -> {
                plugin.getCycleManager().forceNextCycle();
                sender.sendMessage(MM.deserialize(
                        "<green>Ciclo forzado. La noche ha sido instaurada y el evento arrancado.</green>"));
            }

            default -> sendUsage(sender);
        }

        return true;
    }

    // =========================================================================
    // Helpers de salida
    // =========================================================================

    private void sendStatus(CommandSender sender) {
        boolean active     = plugin.getEventManager().isEventActive();
        int     currentDay = plugin.getDataManager().getCurrentDay();
        int     cycleCount = plugin.getDataManager().getCycleCount();
        int     daysTotal  = plugin.getLunarConfig().getDaysPerCycle();
        int     remaining  = plugin.getEventManager().getSecondsRemaining();

        sender.sendMessage(MM.deserialize("<gold>══════════ Estado de Lunar ══════════</gold>"));
        sender.sendMessage(MM.deserialize(
                "<gray>Motor: </gray>" +
                (active
                        ? "<red>ACTIVO <dark_red>[TERROR]</dark_red></red>"
                        : "<green>Inactivo <dark_green>[acumulación]</dark_green></green>")
        ));
        sender.sendMessage(MM.deserialize(
                "<gray>Ciclos completados: </gray><yellow>" + cycleCount + "</yellow>"
        ));
        sender.sendMessage(MM.deserialize(
                "<gray>Día del ciclo actual: </gray><yellow>"
                        + currentDay + " / " + daysTotal + "</yellow>"
        ));
        if (active) {
            int minutes = remaining / 60;
            int seconds = remaining % 60;
            sender.sendMessage(MM.deserialize(
                    "<gray>Tiempo restante: </gray><red>"
                            + String.format("%d:%02d", minutes, seconds) + "</red>"
            ));
        }
        sender.sendMessage(MM.deserialize(
                "<gray>Mundos activos: </gray><aqua>"
                        + String.join(", ", getConfiguredWorlds()) + "</aqua>"
        ));
        sender.sendMessage(MM.deserialize("<gold>═════════════════════════════════════</gold>"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
                "<red>Uso: /lunar [start | stop | status | reload | forcecycle]</red>"));
    }

    private List<String> getConfiguredWorlds() {
        List<String> worlds = new ArrayList<>();
        for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
            if (plugin.getLunarConfig().isWorldAllowed(w.getName())) {
                worlds.add(w.getName());
            }
        }
        return worlds;
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {

        if (!sender.hasPermission("lunar.admin")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }

        return List.of();
    }
}
