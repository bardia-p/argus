package ca.carleton.sce.argus;

import ca.carleton.sce.argus.cmd.SpawnAgentCommand;
import ca.carleton.sce.argus.jason.JasonService;
import ca.carleton.sce.argus.trait.AgentData;
import ca.carleton.sce.argus.trait.JasonAgentTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.StreamSupport;

public final class Argus extends JavaPlugin {
    private JasonService jasonService;
    private Map<String, AgentData> deadAgents;
    private Map<String, AgentData> liveAgents;
    private UUID worldId;

    public static long GAME_DURATION_IN_TICKS = 20L * 60 * 3; // 3 minutes
    public static int TOTAL_GAME_REWARD = 10000;

    @Override
    public void onEnable() {
        getLogger().info("\n\n" +
                " █████╗ ██████╗  ██████╗ ██╗   ██╗███████╗██╗██╗\n" +
                "██╔══██╗██╔══██╗██╔════╝ ██║   ██║██╔════╝██║██║\n" +
                "███████║██████╔╝██║  ███╗██║   ██║███████╗██║██║\n" +
                "██╔══██║██╔══██╗██║   ██║██║   ██║╚════██║╚═╝╚═╝\n" +
                "██║  ██║██║  ██║╚██████╔╝╚██████╔╝███████║██╗██╗\n" +
                "╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝  ╚═════╝ ╚══════╝╚═╝╚═╝\n");
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

        // Register death listener
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onNpcDamage(EntityDamageEvent event) {
                NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
                if (npc == null) return;

                LivingEntity ent = (LivingEntity) npc.getEntity();
                if (ent == null) return;

                double healthAfterDamage = ent.getHealth() - event.getFinalDamage();
                if (healthAfterDamage <= 0) {
                    killNPC(npc, true);
                }
            }
        }, this);

        this.deadAgents = new HashMap<>();
        this.liveAgents = new HashMap<>();

        getLogger().info("JasonAgents enabled.");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.getLogger().info("The game has ended!");
            stopAllNPCS();
        }, GAME_DURATION_IN_TICKS);
    }

    @Override
    public void onDisable() {
        // Stop any remaining NPCs.
        if (!StreamSupport.stream(CitizensAPI.getNPCRegistry().spliterator(), false).anyMatch(NPC::isSpawned)) {
            stopAllNPCS();
        }

        // Assign the end of game rewards
        if (liveAgents.size() > 0) {
            int endReward = TOTAL_GAME_REWARD / liveAgents.size();
            liveAgents.forEach((name, data) -> data.score += endReward);
        }

        StringBuilder msg = new StringBuilder("\n=========================").append("\nSURVIVORS:\n\n");
        liveAgents.forEach((name, data) -> msg.append(name).append(": ").append(data.toString()).append("\n"));
        msg.append("--------------------").append("\nDECEASED:\n\n");
        deadAgents.forEach((name, data) -> msg.append(name).append(": ").append(data.toString()).append("\n"));
        msg.append("=========================");
        getLogger().info(msg.toString());

        saveScores();
    }

    private void stopAllNPCS(){
        getLogger().info("Stopping all NPCs...");

        for (NPC npc: CitizensAPI.getNPCRegistry()) {
            killNPC(npc, false);
        }
    }

    private void killNPC(NPC npc, boolean isDead) {
        JasonAgentTrait trait = npc.getTrait(JasonAgentTrait.class);
        if (npc.isSpawned() && trait != null) {
            AgentData agentData = new AgentData();
            agentData.agentName = trait.getAgentName();
            agentData.aslFile = trait.getAslFile();
            agentData.score = trait.getAgentScore();

            if (isDead) {
                deadAgents.put(npc.getName(), agentData);
            } else {
                liveAgents.put(npc.getName(), agentData);
            }
            trait.onRemove();
        }

        npc.despawn();

        // No more NPCs left shut down the server!
        if (!StreamSupport.stream(CitizensAPI.getNPCRegistry().spliterator(), false).anyMatch(NPC::isSpawned)) {
            Bukkit.shutdown();
        }
    }

    private void saveScores() {
        File file = new File(getDataFolder(), "game_data.yml");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<String, Object>> runs = (List<Map<String, Object>>) config.getList("runs");
        if (runs == null) runs = new ArrayList<>();

        // Add data
        Map<String, Object> newRun = new LinkedHashMap<>();
        newRun.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        newRun.put("world_id", Bukkit.getWorld("world").getUID().toString());
        newRun.put("survivors", liveAgents);
        newRun.put("deceased", deadAgents);
        runs.add(newRun);

        config.set("runs", runs);

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JasonService.RuntimeHandle getRuntimeHandle(String agent) {
        return jasonService.getRuntimeHandle(agent);
    }
}
