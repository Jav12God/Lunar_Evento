package com.lunar.data;

import com.lunar.LunarPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Gestiona la persistencia del estado del ciclo Lunar entre reinicios del servidor.
 * Guarda y carga desde data.yml dentro de la carpeta del plugin.
 *
 * Campos persistidos:
 *  - current-day  : día actual dentro del ciclo (0 a daysPerCycle-1)
 *  - cycle-count  : número total de ciclos completados (usado para escalado de dificultad)
 */
public final class LunarDataManager {

    private final LunarPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    // Estado en memoria
    private int currentDay  = 0;
    private int cycleCount  = 0;

    public LunarDataManager(LunarPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "data.yml");
    }

    // -------------------------------------------------------------------------
    // Carga / Guardado
    // -------------------------------------------------------------------------

    public void loadData() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[Lunar] No se pudo crear data.yml: " + e.getMessage());
            }
        }

        this.config     = YamlConfiguration.loadConfiguration(file);
        this.currentDay = config.getInt("current-day",  0);
        this.cycleCount = config.getInt("cycle-count",  0);

        plugin.getLogger().info("[Lunar] Datos cargados → día=" + currentDay + ", ciclos completados=" + cycleCount);
    }

    /**
     * Guarda de forma síncrona. Llamar solo desde el hilo principal o desde onDisable().
     * Para guardados durante la operación normal usa {@link #saveDataAsync()}.
     */
    public void saveData() {
        if (config == null) return;
        config.set("current-day", this.currentDay);
        config.set("cycle-count", this.cycleCount);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Lunar] Error al guardar data.yml: " + e.getMessage());
        }
    }

    /**
     * Guardado asíncrono para no bloquear el hilo del servidor durante la operación normal.
     */
    public void saveDataAsync() {
        // Captura de valores actuales antes de pasar al hilo async
        final int daySnapshot   = this.currentDay;
        final int cycleSnapshot = this.cycleCount;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (config == null) return;
            config.set("current-day", daySnapshot);
            config.set("cycle-count", cycleSnapshot);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[Lunar] Error al guardar datos (async): " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public int getCurrentDay() {
        return currentDay;
    }

    public void setCurrentDay(int day) {
        this.currentDay = day;
        saveDataAsync();
    }

    public int getCycleCount() {
        return cycleCount;
    }

    /** Incrementa el contador global de ciclos completados y persiste. */
    public void incrementCycleCount() {
        this.cycleCount++;
        saveDataAsync();
    }

    /** Reinicia el día al inicio de un nuevo ciclo (sin tocar el contador de ciclos). */
    public void resetDay() {
        this.currentDay = 0;
        saveDataAsync();
    }
}
