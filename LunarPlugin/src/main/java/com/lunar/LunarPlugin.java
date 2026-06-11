package com.lunar;

import com.lunar.command.LunarCommandExecutor;
import com.lunar.config.LunarConfig;
import com.lunar.data.LunarDataManager;
import com.lunar.event.LunarCycleManager;
import com.lunar.event.LunarEventManager;
import com.lunar.listener.MobModificationListener;
import com.lunar.listener.TerrorEventListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Punto de entrada del plugin Lunar.
 *
 * Orden de inicialización:
 *  1. Configuración (LunarConfig) — carga config.yml
 *  2. Persistencia (LunarDataManager) — lee data.yml
 *  3. Motor de eventos (LunarEventManager)
 *  4. Monitor de ciclos (LunarCycleManager) — arranca la detección de noches
 *  5. Comandos y Listeners
 */
public final class LunarPlugin extends JavaPlugin {

    private LunarConfig      lunarConfig;
    private LunarDataManager dataManager;
    private LunarEventManager eventManager;
    private LunarCycleManager cycleManager;

    // =========================================================================
    // Ciclo de vida del plugin
    // =========================================================================

    @Override
    public void onEnable() {
        // 1. Volcar config.yml por defecto si no existe
        saveDefaultConfig();

        // 2. Cargar configuración
        this.lunarConfig  = new LunarConfig(this);

        // 3. Cargar persistencia
        this.dataManager  = new LunarDataManager(this);
        this.dataManager.loadData();

        // 4. Crear gestores (sin iniciar loops todavía)
        this.eventManager = new LunarEventManager(this);
        this.cycleManager = new LunarCycleManager(this);

        // 5. Registrar comando /lunar
        PluginCommand lunarCommand = getCommand("lunar");
        if (lunarCommand != null) {
            LunarCommandExecutor executor = new LunarCommandExecutor(this);
            lunarCommand.setExecutor(executor);
            lunarCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("[Lunar] No se encontró el comando 'lunar' en plugin.yml.");
        }

        // 6. Registrar listeners de eventos de Bukkit
        getServer().getPluginManager().registerEvents(
                new MobModificationListener(this), this);
        getServer().getPluginManager().registerEvents(
                new TerrorEventListener(this), this);

        // 7. Arrancar el monitor de ciclos
        this.cycleManager.startCycleTask();

        getLogger().info("[Lunar] Motor de terror psicológico activado. Ciclo: "
                + dataManager.getCycleCount()
                + " | Día actual: " + dataManager.getCurrentDay()
                + "/" + lunarConfig.getDaysPerCycle());
    }

    @Override
    public void onDisable() {
        // Detener el evento si está activo (limpia entidades y efectos)
        if (eventManager != null && eventManager.isEventActive()) {
            eventManager.stopEvent();
        }

        // Detener el monitor de ciclos
        if (cycleManager != null) {
            cycleManager.stopCycleTask();
        }

        // Guardar estado final de forma síncrona (onDisable no permite async)
        if (dataManager != null) {
            dataManager.saveData();
        }

        getLogger().info("[Lunar] Motor detenido de forma segura. Datos guardados.");
    }

    // =========================================================================
    // Accesores para las demás clases
    // =========================================================================

    public LunarConfig       getLunarConfig()  { return lunarConfig;  }
    public LunarDataManager  getDataManager()  { return dataManager;  }
    public LunarEventManager getEventManager() { return eventManager; }
    public LunarCycleManager getCycleManager() { return cycleManager; }
}
