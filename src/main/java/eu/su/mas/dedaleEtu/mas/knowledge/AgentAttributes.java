package eu.su.mas.dedaleEtu.mas.knowledge;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;

import java.io.Serializable;
import java.util.*;


public class AgentAttributes implements Serializable {

    public static List<String> Collectors = Arrays.asList("Collect1", "Collect2", "Collect3", "Collector4");
    public static List<String> Explorers = Arrays.asList("Explo1", "Explo2", "Explo3");
    public static List<String> Tankers = Arrays.asList("Tanker1", "Tanker2");
    public static List<String> Messengers = Arrays.asList("Messenger1", "Messenger2", "Messenger3");

    public static List<String> HybridGroup = Arrays.asList("Explo1", "Collect1", "Collect3");
    public static List<String> GoldGroup = Arrays.asList("Explo2", "Collect2", "Tanker1");
    public static List<String> DiamondGroup = Arrays.asList("Explo3", "Collect3", "Tanker2");

    public static List<String> tankerStations = Arrays.asList("-151235", "-142106");

    public enum AgentType {EXPLORER, COLLECTOR, TANKER, COORDINATOR, MESSENGER}
    public enum GroupType {GOLD, DIAMOND, HYBRID}


    // Task structures
    /*public static class CollectTask
    {
        private final String nodeID;
        private final String type;
        private final int value;

        public CollectTask(String nodeID, String type, int value)
        {
            this.nodeID = nodeID;
            this.type = type;
            this.value = value;
        }

        public String getNode(){ return this.nodeID;}

        public String getType(){ return this.type;}

        public int getValue(){ return this.value;}


    }

    public static class PickingTask
    {
        private final String nodeID;
        private final String type;
        private final int value;
        private final int Strength;
        private final int LockPicking;

        public PickingTask(String nodeID, String type, int value, int Strength, int LockPicking)
        {
            this.nodeID = nodeID;
            this.type = type;
            this.value = value;
            this.Strength = Strength;
            this.LockPicking = LockPicking;
        }

        public String getNode(){ return this.nodeID;}

        public String getType(){ return this.type;}

        public int getValue(){ return this.value;}

        public int getStrength(){ return this.Strength;}

        public int getLockPicking(){return this.LockPicking;}

    }*/

    /***************************************************
     * DESERIALZERS
     ***************************************************/
    public static List<String> listDeserializer(String messageSerialized)
    {
        String[] res = messageSerialized.replace("[","").replace("]","").split(", ");
        List<String> resList = new ArrayList<>();
        for (String r : res) {
            if (!r.equals(""))
                resList.add(r);
        }

        return resList;
    }

    public static List<Couple<String, List<Couple<Observation, Integer>>>> observationsDeserializer(String messageSerialized){
        List<Couple<String, List<Couple<Observation, Integer>>>> deserializedObs = new ArrayList<>();

        if (!Objects.equals(messageSerialized, "[]")){
        // Split observations
        String[] observations = messageSerialized.replace(">]>]","").split(">]>, <");
        for(String o: observations) {
            // Split node ID and observations
            String[] pAob = o.split(", \\[<");
            String pID = pAob[0].replace("<", "").replace("[", "");
            // Split list of observation parameters
            String[] params = pAob[1].split(">, <");
            List<Couple<Observation, Integer>> treas_params = new ArrayList<>();

            for (String p : params) {
                // Split observation name from its value
                String[] obStr = p.split(", ");
                Observation obval;
                switch (obStr[0]) {
                    case "Gold":
                        obval = Observation.GOLD;
                        break;
                    case "Diamond":
                        obval = Observation.DIAMOND;
                        break;
                    case "LockPicking":
                        obval = Observation.LOCKPICKING;
                        break;
                    case "LockIsOpen":
                        obval = Observation.LOCKSTATUS;
                        break;
                    case "Strength":
                        obval = Observation.STRENGH;
                        break;
                    default:
                        obval = Observation.STENCH;
                        break;
                }
                int obnum = Integer.parseInt(obStr[1]);

                // Generate couples
                Couple<Observation, Integer> ob = new Couple<>(obval, obnum);
                treas_params.add(ob);
            }
            // Generate each item of the treasure
            Couple<String, List<Couple<Observation, Integer>>> treasure = new Couple<>(pID, treas_params);
            deserializedObs.add(treasure);
        }
        }
        return deserializedObs;
    }

    /***************************************************
     * AGENT STATUS
     ***************************************************/
    public static class AgentStatus
    {
        public String LocalName;
        public AgentType agentType;
        public Observation treasureType;
        public int backpackCapacity;

        public AgentStatus(String localName, AgentType agentType, Observation treasureType, int backpackCapacity)
        {
            this.LocalName = localName;
            this.agentType = agentType;
            this.treasureType = treasureType;
            this.backpackCapacity = backpackCapacity;
        }

        public AgentStatus() {}

        public AgentStatus(String content) { DeserializeContent(content);}

        public void setLocalName(String localName){ this.LocalName = localName; }
        public void setAgentType(AgentType agentType){ this.agentType = agentType; }
        public void setTreasureType(Observation treasureType){ this.treasureType = treasureType; }
        public void setBackpackCapacity(int backpackCapacity){ this.backpackCapacity = backpackCapacity; }

        public String getLocalName(){ return this.LocalName; }
        public AgentType getAgentType(){ return this.agentType; }
        public Observation getTreasureType(){ return this.treasureType; }
        public int getBackpackCapacity(){ return this.backpackCapacity; }

        public String SerializeContent() {
            List<String> l = new ArrayList<>();
            l.add(this.LocalName);
            l.add(this.agentType.toString());
            l.add(this.treasureType.toString());
            l.add( Integer.toString(this.backpackCapacity));
            return l.toString();
        }

        public void DeserializeContent(String content){

            // Deserialize data
            List<String> l = listDeserializer(content);
            Observation o;
            if (l.get(2).equals("Gold"))
                o = Observation.GOLD;
            else if (l.get(2).equals("Diamond"))
                o = Observation.DIAMOND;
            else if (l.get(2).equals("Any"))
                o = Observation.ANY_TREASURE;
            else
                o = Observation.NO_TREASURE;

            // Save data
            this.LocalName = l.get(0);
            this.agentType = AgentType.valueOf(l.get(1));
            this.treasureType = o;
            this.backpackCapacity = Integer.parseInt(l.get(3));
        }
    }


}


