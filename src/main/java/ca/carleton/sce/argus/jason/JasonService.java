package ca.carleton.sce.argus.jason;

import ca.carleton.sce.argus.Argus;

import jason.RevisionFailedException;
import jason.asSemantics.Agent;
import jason.asSemantics.TransitionSystem;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JasonService {
    private final Argus plugin;
    private final Map<String, RuntimeHandle> runtimes = new ConcurrentHashMap<>();

    public JasonService(Argus plugin) {
        this.plugin = plugin;
    }

    public RuntimeHandle getRuntimeHandle(String agent) {
        return runtimes.getOrDefault(agent, null);
    }

    public void startOrAttachAgent(NPC npc, String agentName, String aslFile) {
        if (runtimes.containsKey(agentName)) {
            plugin.getLogger().info("Agent " + agentName + " is already running.");
            return;
        }

        ArgusAgArch arch = new ArgusAgArch(plugin, npc, aslFile);
        Agent agent = new Agent();
        agent.setTS(new TransitionSystem(agent, null, null, arch));

        plugin.getLogger().info("Starting agent " + agentName);
        agent.initAg();

        // Load AgentSpeak into agent
        try {
            agent.parseAS(getClass().getClassLoader().getResource("asl/" + aslFile));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ASL file: " + aslFile, e);
        }

        try {
            agent.addInitialBelsInBB();
        } catch (RevisionFailedException e) {
            throw new RuntimeException(e);
        }
        agent.addInitialGoalsInTS();

        RuntimeHandle handle = new RuntimeHandle(agentName, agent, arch);
        runtimes.put(agentName, handle);

        plugin.getLogger().info(arch.getTS().getAg().getPL().toString());

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!npc.isSpawned()) return;
            try {
                agent.getTS().reasoningCycle(); // runs one BDI step
            } catch (Throwable t) {
                plugin.getLogger().severe("TS error: " + t.getMessage());
                t.printStackTrace();
            }
        }, 10L, 2L);
    }

    public void detachAgent(String agentName) {
        RuntimeHandle handle = runtimes.remove(agentName);
        if (handle != null) {
            handle.architecture().shutdown();
        }
    }

    public record RuntimeHandle(String name, Agent agent,
                                ArgusAgArch architecture) {
    }
}
