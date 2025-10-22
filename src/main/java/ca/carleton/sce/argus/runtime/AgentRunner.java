package ca.carleton.sce.argus.runtime;

import ca.carleton.sce.argus.Argus;
import ca.carleton.sce.argus.jason.ArgusAgArch;
import jason.asSemantics.TransitionSystem;
import net.citizensnpcs.api.npc.NPC;

import java.util.concurrent.atomic.AtomicBoolean;

public class AgentRunner implements Runnable {
    private final Argus plugin;
    private final String name;
    private final NPC npc;
    private final ArgusAgArch agArch;
    private final TransitionSystem ts;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public AgentRunner(Argus argus, String name, NPC npc, ArgusAgArch agArch, TransitionSystem ts) {
        this.plugin = argus;
        this.name = name;
        this.npc = npc;
        this.agArch = agArch;
        this.ts = ts;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "JasonAgent-" + name);
            thread.start();
        }
    }

    @Override
    public void run() {
        try {
            while (running.get() && plugin.isEnabled()) {
                ts.reasoningCycle();
                Thread.sleep(50L);
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Agent " + name + " encountered an error: " + t.getMessage());
        } finally {
            if (npc.isSpawned())
                plugin.getServer().getScheduler().runTask(plugin, () -> npc.despawn());
        }
    }

    public void shutdown() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }
}
