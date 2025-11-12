package ca.carleton.sce.argus.trait;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentData implements ConfigurationSerializable {
    public String agentName;
    public String aslFile;
    public int score;

    @Override
    public String toString(){
        return "{name: " + agentName + ", aslFile: " + aslFile + ", score: " + score + "}";
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentName", agentName);
        data.put("aslFile", aslFile);
        data.put("score", score);
        return data;
    }

    public static AgentData deserialize(Map<String, Object> data) {
        AgentData agentData = new AgentData();
        agentData.agentName = (String) data.get("agentName");
        agentData.aslFile = (String) data.get("aslFile");
        agentData.score = (int) data.get("score");
        return agentData;
    }
}
