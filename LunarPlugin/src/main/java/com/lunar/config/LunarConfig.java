package com.lunar.config;

import com.lunar.LunarPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carga y expone todos los parámetros del config.yml.
 * Soporta recarga en caliente via /lunar reload.
 *
 * Valores numéricos de daño: se almacenan en HP (corazones × 2) internamente.
 * En el config.yml se expresan en corazones para que sea intuitivo.
 */
public final class LunarConfig {

    private final LunarPlugin plugin;

    // Worlds
    private final Set<String> allowedWorlds = new HashSet<>();

    // Ciclo
    private int daysPerCycle;
    private int eventDurationSeconds;

    // Efectos
    private boolean blindnessEnabled;
    private int     blindnessAmplifier;
    private boolean darknessEnabled;
    private int     darknessAmplifier;

    // Sonidos
    private boolean      soundsEnabled;
    private int          soundMinInterval;
    private int          soundMaxInterval;
    private List<String> soundCategories;

    // Mensajes
    private boolean      messagesEnabled;
    private int          messageMinInterval;
    private int          messageMaxInterval;
    private List<String> spookyMessages;

    // Daño psicológico
    private boolean      damageEnabled;
    private int          damageMinInterval;
    private int          damageMaxInterval;
    private double       minDamage;   // en puntos HP (corazones × 2)
    private double       maxDamage;
    private List<String> damageMessages;
    private List<String> damageSounds;

    // Presencia
    private boolean      presenceEnabled;
    private int          presenceMinInterval;
    private int          presenceMaxInterval;
    private int          presenceDespawnMin;
    private int          presenceDespawnMax;
    private String       presenceEntityType;
    private boolean      presenceGlowing;
    private boolean      presenceInvisible;

    // Mobs
    private boolean mobModifiersEnabled;
    private double  mobHealthMultiplier;
    private double  mobDamageMultiplier;
    private double  mobSpeedMultiplier;

    public LunarConfig(LunarPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // =========================================================================
    // Carga de configuración
    // =========================================================================

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        // Ciclo
        daysPerCycle         = c.getInt("cycle.days-per-cycle",          4);
        eventDurationSeconds = c.getInt("cycle.event-duration-seconds",  600);

        // Mundos permitidos
        allowedWorlds.clear();
        allowedWorlds.addAll(c.getStringList("allowed-worlds"));

        // Efectos de poción
        blindnessEnabled  = c.getBoolean("effects.blindness.enabled",   true);
        blindnessAmplifier = c.getInt("effects.blindness.amplifier",    0);
        darknessEnabled   = c.getBoolean("effects.darkness.enabled",    true);
        darknessAmplifier  = c.getInt("effects.darkness.amplifier",     0);

        // Sonidos
        soundsEnabled    = c.getBoolean("sounds.enabled",               true);
        soundMinInterval = c.getInt("sounds.min-interval-seconds",      15);
        soundMaxInterval = c.getInt("sounds.max-interval-seconds",      45);
        soundCategories  = c.getStringList("sounds.categories");

        // Mensajes
        messagesEnabled    = c.getBoolean("spooky-messages.enabled",    true);
        messageMinInterval = c.getInt("spooky-messages.min-interval-seconds", 30);
        messageMaxInterval = c.getInt("spooky-messages.max-interval-seconds", 90);
        spookyMessages     = c.getStringList("spooky-messages.pool");

        // Daño psicológico
        damageEnabled    = c.getBoolean("psychological-damage.enabled", true);
        damageMinInterval = c.getInt("psychological-damage.min-interval-seconds", 60);
        damageMaxInterval = c.getInt("psychological-damage.max-interval-seconds", 180);
        // Convertir corazones → puntos HP
        minDamage = c.getDouble("psychological-damage.min-damage-hearts", 1.0) * 2.0;
        maxDamage = c.getDouble("psychological-damage.max-damage-hearts", 3.0) * 2.0;
        damageMessages = c.getStringList("psychological-damage.messages");
        damageSounds   = c.getStringList("psychological-damage.sounds");

        // Presencia
        presenceEnabled     = c.getBoolean("presence.enabled",          true);
        presenceMinInterval = c.getInt("presence.min-interval-seconds", 90);
        presenceMaxInterval = c.getInt("presence.max-interval-seconds", 240);
        presenceDespawnMin  = c.getInt("presence.despawn-delay-min-seconds", 3);
        presenceDespawnMax  = c.getInt("presence.despawn-delay-max-seconds", 10);
        presenceEntityType  = c.getString("presence.entity-type", "WITHER_SKELETON");
        presenceGlowing     = c.getBoolean("presence.glowing",          true);
        presenceInvisible   = c.getBoolean("presence.invisible",        true);

        // Mobs
        mobModifiersEnabled  = c.getBoolean("mob-modifiers.enabled",    true);
        mobHealthMultiplier  = c.getDouble("mob-modifiers.health-multiplier", 1.50);
        mobDamageMultiplier  = c.getDouble("mob-modifiers.damage-multiplier", 1.30);
        mobSpeedMultiplier   = c.getDouble("mob-modifiers.speed-multiplier",  1.20);
    }

    // =========================================================================
    // Accesores
    // =========================================================================

    public boolean isWorldAllowed(String worldName)  { return allowedWorlds.contains(worldName); }

    public int     getDaysPerCycle()                  { return daysPerCycle;          }
    public int     getEventDurationSeconds()          { return eventDurationSeconds;  }

    public boolean isBlindnessEnabled()               { return blindnessEnabled;      }
    public int     getBlindnessAmplifier()            { return blindnessAmplifier;    }
    public boolean isDarknessEnabled()                { return darknessEnabled;       }
    public int     getDarknessAmplifier()             { return darknessAmplifier;     }

    public boolean      isSoundsEnabled()             { return soundsEnabled;         }
    public int          getSoundMinInterval()         { return soundMinInterval;      }
    public int          getSoundMaxInterval()         { return soundMaxInterval;      }
    public List<String> getSoundCategories()          { return soundCategories;       }

    public boolean      isMessagesEnabled()           { return messagesEnabled;       }
    public int          getMessageMinInterval()       { return messageMinInterval;    }
    public int          getMessageMaxInterval()       { return messageMaxInterval;    }
    public List<String> getSpookyMessages()           { return spookyMessages;        }

    public boolean      isDamageEnabled()             { return damageEnabled;         }
    public int          getDamageMinInterval()        { return damageMinInterval;     }
    public int          getDamageMaxInterval()        { return damageMaxInterval;     }
    public double       getMinDamage()                { return minDamage;             }
    public double       getMaxDamage()                { return maxDamage;             }
    public List<String> getDamageMessages()           { return damageMessages;        }
    public List<String> getDamageSounds()             { return damageSounds;          }

    public boolean isPresenceEnabled()                { return presenceEnabled;       }
    public int     getPresenceMinInterval()           { return presenceMinInterval;   }
    public int     getPresenceMaxInterval()           { return presenceMaxInterval;   }
    public int     getPresenceDespawnMin()            { return presenceDespawnMin;    }
    public int     getPresenceDespawnMax()            { return presenceDespawnMax;    }
    public String  getPresenceEntityType()            { return presenceEntityType;    }
    public boolean isPresenceGlowing()                { return presenceGlowing;       }
    public boolean isPresenceInvisible()              { return presenceInvisible;     }

    public boolean isMobModifiersEnabled()            { return mobModifiersEnabled;   }
    public double  getMobHealthMultiplier()           { return mobHealthMultiplier;   }
    public double  getMobDamageMultiplier()           { return mobDamageMultiplier;   }
    public double  getMobSpeedMultiplier()            { return mobSpeedMultiplier;    }
}
