package ca.carleton.sce.argus.trait;

import ca.carleton.sce.argus.jason.JasonService;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;

import java.util.UUID;

/**
 * Citizens Trait that binds an NPC to a Jason agent instance.
 * Persists minimal info; Jason engine is recreated onNPCSpawn if needed.
 */
public class JasonAgentTrait extends Trait {
    private String agentName;
    private String aslFile;
    private transient String aslSource;
    private transient JasonService jason;

    // Persisted runtime info
    private UUID lastWorldId;

    public JasonAgentTrait() {
        super("jason-agent");
    }

    public void initialize(String agentName, String aslFile, String aslSource, JasonService jasonService) {
        this.agentName = agentName;
        this.aslFile = aslFile;
        this.aslSource = aslSource;
        this.jason = jasonService;

        if (jason != null && agentName != null && aslSource != null) {
            jason.startOrAttachAgent(npc, agentName, aslFile);
        }
    }

    @Override
    public void onSpawn() {
        if (npc != null && npc.getEntity() != null && npc.getEntity().getWorld() != null) {
            lastWorldId = npc.getEntity().getWorld().getUID();
        }
    }

    @Override
    public void onDespawn() {
        if (jason != null && agentName != null) {
            jason.detachAgent(agentName);
        }
    }

    @Override
    public void load(DataKey key) {
        this.agentName = key.getString("agentName", null);
        this.aslFile = key.getString("aslFile", null);
        // aslSource not persisted; reload from disk or resource if you want persistence across restarts
    }

    @Override
    public void save(DataKey key) {
        key.setString("agentName", agentName);
        key.setString("aslFile", aslFile);
    }
}
