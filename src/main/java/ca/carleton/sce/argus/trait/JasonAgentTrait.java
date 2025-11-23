package ca.carleton.sce.argus.trait;

import ca.carleton.sce.argus.jason.JasonService;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.npc.NPC;

/**
 * Citizens Trait that binds an NPC to a Jason agent instance.
 * Persists minimal info; Jason engine is recreated onNPCSpawn if needed.
 */
public class JasonAgentTrait extends Trait {
    private String agentName;
    private String aslFile;
    private transient JasonService jason;

    public JasonAgentTrait() {
        super("jason-agent");
    }

    public void initialize(String agentName, String aslFile, String aslSource, JasonService jasonService) {
        this.agentName = agentName;
        this.aslFile = aslFile;
        this.jason = jasonService;

        if (jason != null && agentName != null && aslSource != null) {
            jason.startOrAttachAgent(npc, agentName, aslFile);
        }
    }

    @Override
    public void onRemove()  {
        if (jason != null && agentName != null) {
            jason.detachAgent(agentName);
        }
    }

    public String getAgentName() { return  agentName; }

    public String getAslFile() { return  aslFile; }

    public int getAgentScore() {
        if (jason != null && agentName != null) {
            return jason.getAgentScore(agentName);
        }
        return -1;
    }

    @Override
    public void load(DataKey key) {
        this.agentName = key.getString("agentName", null);
        this.aslFile = key.getString("aslFile", null);
    }

    @Override
    public void save(DataKey key) {
        key.setString("agentName", agentName);
        key.setString("aslFile", aslFile);
    }
}
