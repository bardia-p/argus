package ca.carleton.sce.argus;

import ca.carleton.sce.argus.cmd.SpawnAgentCommand;
import ca.carleton.sce.argus.jason.JasonService;
import ca.carleton.sce.argus.trait.JasonAgentTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Argus extends JavaPlugin {

    private JasonService jasonService;

    public static long GAME_DURATION_IN_TICKS = 20L * 60 * 3; // 3 minutes
    @Override
    public void onEnable() {
        getLogger().info("Loading Argus...");
        this.jasonService = new JasonService(this);

        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(JasonAgentTrait.class));

        // Register commands
        assert getCommand("spawnagent") != null;
        getCommand("spawnagent").setExecutor(new SpawnAgentCommand(this, jasonService));
        getCommand("spawnagent").setTabCompleter(new SpawnAgentCommand(this, jasonService));

        // (Optional) sanity check main-thread ops
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Plugin enabled off-main thread?!");
        }
        getLogger().info("JasonAgents enabled.");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            getLogger().info("Stopping all NPCs...");

            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.isSpawned() && npc.getTrait(JasonAgentTrait.class) != null) {
                    npc.getTrait(JasonAgentTrait.class).onDespawn();
                }

                npc.despawn();
            }

            // Shutdown the server
            Bukkit.shutdown();
        }, GAME_DURATION_IN_TICKS);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
