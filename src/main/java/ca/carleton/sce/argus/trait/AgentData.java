package ca.carleton.sce.argus.trait;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentData implements ConfigurationSerializable {
    public String agentName;
    public String aslFile;
    public int score;

    public String weapon;

    public int woodsChopped;

    public int numHouses;

    public int woodsDonated;

    public int numHitsOnZombies;

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
        data.put("weapon", weapon);
        data.put("woodsChopped", woodsChopped);
        data.put("numHouses", numHouses);
        data.put("woodsDonated", woodsDonated);
        data.put("numHitsOnZombies", numHitsOnZombies);
        return data;
    }

    public static AgentData deserialize(Map<String, Object> data) {
        AgentData agentData = new AgentData();
        agentData.agentName = (String) data.get("agentName");
        agentData.aslFile = (String) data.get("aslFile");
        agentData.score = (int) data.get("score");
        agentData.weapon = (String) data.get("weapon");
        agentData.woodsChopped = (int) data.get("woodsChopped");
        agentData.numHouses = (int) data.get("numHouses");
        agentData.woodsDonated = (int) data.get("woodsDonated");
        agentData.numHitsOnZombies = (int) data.get("numHitsOnZombies");
        return agentData;
    }
}
