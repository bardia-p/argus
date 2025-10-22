package ca.carleton.sce.argus.runtime;

import ca.carleton.sce.argus.Argus;
import ca.carleton.sce.argus.jason.ArgusAgArch;
import jason.asSemantics.TransitionSystem;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArgusRegistry {
    private final Argus argus;
    private final Map<String, AgentRunner> runners = new ConcurrentHashMap<>();

    public ArgusRegistry(Argus plugin) {
        argus = plugin;
    }

    public void spawnCitizenNpcAndAgent(String name, Location spawn, String aslSource) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        if (!npc.spawn(spawn)) {
            throw new IllegalStateException("Could not spawn NPC!");
        }

        String asSrc = readAslSource(aslSource);
        ArgusAgArch agArch = new ArgusAgArch(argus, npc);
        TransitionSystem ts = agArch.getTS();
        AgentRunner runner = new AgentRunner(argus, name, npc, agArch, ts);
        runners.put(name, runner);
        runner.start();
    }

    private String readAslSource(String aslSource) {
        String path = "asl/" + aslSource;
        try (InputStream is = argus.getResource(path)) {
            if (is == null) {
                throw new IllegalArgumentException("ASL source not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read ASL source: " + path, e);
        }
    }
}
