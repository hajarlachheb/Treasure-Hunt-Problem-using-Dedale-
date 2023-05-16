package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;

import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes.*;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;

import eu.su.mas.dedaleEtu.mas.protocols.*;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;

public class ExplorerBehaviourV2 extends SimpleBehaviour {

    private static final long serialVersionUID = 8567689731496787661L;

    private boolean finished = false;

    /**
     * Current knowledge of the agent regarding the environment
     */
    private MapRepresentation myMap;

    private List<String> list_agentNames;

    /************************
     * IMAS PARAMETERS
     ************************/

    private List<String> agents2notify;
    private final List<String> previousPositions;
    private int StuckIter;
    private int StuckRecover;

    // Status Parameters
    private int openSafesCount;
    public int iteration;

    private boolean endExploration;

    private final List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
    private final List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
    private final List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
    private final List<Couple<String,List<Couple<Observation,Integer>>>> Collected; // Treasures already collected

    // Treasure collection

    private boolean goToNode;
    private String destinationNode;

    // Control Parameters

    public int period = 100;
    public int periodMap = 150;
    public int periodAgents = 100;
    public int StuckIterThr = 5;
    public int RecoverIterThr = 15;
    public int prevPositionsMax = 50;
    public int iterationUpdate = 50;

    public ExplorerBehaviourV2(final AbstractDedaleAgent myagent, MapRepresentation myMap,List<String> agentNames) {
        super(myagent);
        this.myMap = myMap;
        this.list_agentNames = agentNames;

        // IMAS Initialization
        this.agents2notify = new ArrayList<>();
        this.agents2notify.addAll(AgentAttributes.Collectors);
        this.agents2notify.addAll(AgentAttributes.Tankers);
        System.out.println(this.agents2notify);
        this.previousPositions = new ArrayList<>();
        this.StuckIter = 0;
        this.StuckRecover = 0;

        // Status Parameters
        this.openSafesCount = 0;
        this.iteration = 0;
        this.ToCollect = new ArrayList<>();
        this.OpenedSafes = new ArrayList<>();
        this.ToOpen = new ArrayList<>();
        this.Collected = new ArrayList<>();
        this.endExploration = false;

        // Treasure collection
        this.goToNode = false;
        this.destinationNode = null;
    }

    @Override
    public void action() {

        iteration++;

        if ((iteration % iterationUpdate == 0)&&(this.myAgent.getLocalName().equals("Explo1")))
            System.out.println(this.myAgent.getLocalName() + " - Iteration: " + iteration);

        /*********************************
         * ENVIRONMENT DATA COLLECTION
         ********************************/

        // Generate the map if null
        if(this.myMap==null) {
            this.myMap = new MapRepresentation();
            this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, periodMap, this.myMap, list_agentNames));
            this.myAgent.addBehaviour(new ShareTaskBehaviour(this.myAgent, periodMap, list_agentNames, this.ToOpen, this.OpenedSafes, this.ToCollect, this.Collected));
            this.myAgent.addBehaviour(new ExploInformBehaviour(this.myAgent, periodAgents, agents2notify));

            // Add the stations as closed nodes to not battle against the tankers to reach that node
            for (String station: AgentAttributes.tankerStations){
                this.myMap.addNode(station, MapAttribute.closed);
            }
        }

        // Retrieve the current position
        String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

        if (myPosition!=null) {

            // Count iterations stuck
            if (!previousPositions.isEmpty()) {
                if (previousPositions.get(previousPositions.size() - 1).equals(myPosition))
                    this.StuckIter++;
                else
                    this.StuckIter = 0;
            }

            // Add the position to the list
            if (previousPositions.isEmpty() || (!previousPositions.get(previousPositions.size() - 1).equals(myPosition)))
                previousPositions.add(myPosition);

            // Limit the maximum capacity of the previous position list
            if(previousPositions.size() > prevPositionsMax)
                previousPositions.remove(0);

            //List of observable from the agent's current position
            List<Couple<String, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent).observe();//myPosition

            // Wait a defined time
            try {
                this.myAgent.doWait(period);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*********************************
             * MAP UPDATES (THIS AGENT)
             ********************************/

            //1) remove the current node from openlist and add it to closedNodes.
            this.myMap.addNode(myPosition, MapAttribute.closed);

            //2) get the surrounding nodes and, if not in closedNodes, add them to open nodes.
            String nextNode = null;
            Iterator<Couple<String, List<Couple<Observation, Integer>>>> iter = lobs.iterator();
            List<String> nNodes = new ArrayList<>();
            while (iter.hasNext()) {

                String nodeId = iter.next().getLeft();

                boolean isNewNode = this.myMap.addNewNode(nodeId);
                //the node may exist, but not necessarily the edge
                if (!myPosition.equals(nodeId)) {
                    this.myMap.addEdge(myPosition, nodeId);
                    if (isNewNode) {
                    	nNodes.add(nodeId);
                    //System.out.println(this.myAgent.getLocalName()+" - New node found");
                    }
                    //if (nextNode == null && isNewNode)
                    //    nextNode = nodeId;
                }
            }
            // Assign next node randomly

			if (nNodes.size() > 1)
			{
				Random r = new Random();
				int randval = r.nextInt(0,nNodes.size());
				nextNode = nNodes.get(randval);
			}
			else if (nNodes.size() == 1)
				nextNode = nNodes.get(0);

            /*********************************
             * TASK PROCESSING
             ********************************/

            // Check each neighbor node
            List<Couple<Observation, Integer>> lObservations = lobs.get(0).getRight();

            // The treasure was found
            if (!lObservations.isEmpty()) {
                // Collect all the parameters
                Observation treasureType = null;
                int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

                for (Couple<Observation, Integer> o : lObservations) {
                    switch (o.getLeft()) {
                        case DIAMOND:
                        case GOLD:
                            treasureType = o.getLeft();
                            amount = o.getRight();
                            break;
                        case LOCKSTATUS:
                            lockStatus = o.getRight();
                            break;
                        case STRENGH:
                            strength = o.getRight();
                            break;
                        case LOCKPICKING:
                            lockPicking = o.getRight();
                            break;
                        default:
                            break;
                    }
                }
                //System.out.println(this.myAgent.getLocalName()+ "All observations -" + lobs);
                //System.out.println(this.myAgent.getLocalName() + " - Treasure found: " + treasureType +
                //        "(" + amount + "), lock status (" + lockStatus + "), strength (" + strength +
                //        "), lock picking (" + lockPicking + ")");

                /*********************************
                 * OPEN SAFES (REACTIVE)
                 ********************************/
                if ((lockStatus == 0) && ((AbstractDedaleAgent) this.myAgent).openLock(treasureType)) {
                    System.out.println(this.myAgent.getLocalName() + " - Opened a safe ( Expertise: " +
                            ((AbstractDedaleAgent) this.myAgent).getMyExpertise() + ")");
                    lockStatus = 1;
                    this.openSafesCount++;
                }

                // Update lobs
                lobs = ((AbstractDedaleAgent) this.myAgent).observe();

                /*********************************
                 * UPDATE LISTS
                 ********************************/
                updateToOpenList(lobs, myPosition, lockStatus);

                if (lockStatus == 1) {
                    updateOpenedSafesList(lobs, myPosition);
                    updateToCollectList(lobs, myPosition);
                }
            }


            /*********************************
             * EXPLORATION
             ********************************/

            // If the explorer has a task, go for it
            if (goToNode){
                try{
                    nextNode = this.myMap.getShortestPath(myPosition, this.destinationNode).get(0);
                }catch(Exception e){
                    goToNode = false;
                }

                if (myPosition.equals(this.destinationNode)) {
                    System.out.println(this.myAgent.getLocalName() + " - Reached the destination");
                    goToNode = false;
                }
            }

            if (!goToNode) {
                // Explore if there are opened nodes
                if (!this.myMap.hasOpenNode()) {
                    //Explo finished
                    //finished=true;
                    if (!endExploration)
                        System.out.println(this.myAgent.getLocalName() + " - Exploration successfully done");
                    endExploration = true;
                } else {
                    // Search the closest open node when there is no a directly reachable open node
                    if (nextNode == null) {
                        List<String> mypath = this.myMap.getShortestPathToClosestOpenNode(myPosition);
                        nextNode = mypath.get(0);
                    }
                }
            }

            // In case of get stuck, move randomly
            if (this.StuckIter > this.StuckIterThr)
                this.StuckRecover = this.RecoverIterThr;

            if ((this.StuckIter > this.StuckIterThr) || (this.StuckRecover > 0) || (nextNode == null)) {
                // Random movement
                nextNode = randomMovement(lobs);

                // Update stuck recover
                if (StuckRecover > 0)
                    this.StuckRecover--;
            }

            /*********************************
             * TASK PROCESSING
             ********************************/

            sendTask();

            /*********************************
             * AGENT MOVEMENT
             ********************************/

            if (nextNode != null) {
                ((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
            }
        }
    }

    @Override
    public boolean done() {
        return finished;
    }

    /*********************************
     * UPDATE LIST
     ********************************/

    public void updateToOpenList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition, int lockStatus){
        boolean inList = false;
        int k = 0;
        while (k < this.ToOpen.size()) {
            Couple<String, List<Couple<Observation, Integer>>> ob = this.ToOpen.get(k);
            if (ob.getLeft().equals(lobs.get(0).getLeft())) {
                inList = true;
                if (lockStatus == 1) {
                    ToOpen.remove(k);
                    System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " removed from ToOpen list");
                    //System.out.println(ToOpen);
                } else
                    k++;
            } else
                k++;
        }
        if ((lockStatus == 0) && (!inList)) {
            ToOpen.add(lobs.get(0));
            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in ToOpen list");
            //System.out.println(ToOpen);
        }
    }

    public void updateOpenedSafesList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition){
        boolean inList = false;
        for (Couple<String, List<Couple<Observation, Integer>>> ob : this.OpenedSafes)
            if (ob.getLeft().equals(lobs.get(0).getLeft()))
                inList = true;
        if (!inList) {
            OpenedSafes.add(lobs.get(0));
            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in OpenedSafes list");
            //System.out.println(OpenedSafes);
        }
    }

    public void updateCollectedList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition){
        boolean inList = false;
        for (Couple<String, List<Couple<Observation, Integer>>> ob : this.Collected)
            if (ob.getLeft().equals(lobs.get(0).getLeft()))
                inList = true;
        if (!inList) {
            Collected.add(lobs.get(0));
            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in Collected list");
            //System.out.println(Collected);
        }
    }

    public void updateToCollectList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition){
        boolean inList = false;
        int k = 0;
        while (k < this.ToCollect.size()) {
            Couple<String, List<Couple<Observation, Integer>>> ob = this.ToCollect.get(k);
            if (ob.getLeft().equals(lobs.get(0).getLeft())) {
                inList = true;
                if (lobs.get(0).getRight().isEmpty()) {
                    ToCollect.remove(k);
                    System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " removed from ToCollect list");
                } else
                    k++;
            } else
                k++;
        }
        if ((!inList) && !(lobs.get(0).getRight().isEmpty())) {
            ToCollect.add(lobs.get(0));
            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in ToCollect list");
            //System.out.println(ToCollect);
        }
    }

    public void taskListMerge(List<Couple<String, List<Couple<Observation, Integer>>>> toOpen,
                          List<Couple<String, List<Couple<Observation, Integer>>>> opened,
                          List<Couple<String, List<Couple<Observation, Integer>>>> toCollect,
                          List<Couple<String, List<Couple<Observation, Integer>>>> collected){

        //System.out.println(this.myAgent.getLocalName() + " - Deserialized items from " + gmt.getLeft() + " To Open: "
        //		+ toOpen.toString() + "| Opened: " + opened.toString() + "| To Collect: " + toCollect.toString() + "| Collected: " + collected.toString());

        // Add new elements from one list to another (toOpen)
        for(Couple<String, List<Couple<Observation, Integer>>> treasure: toOpen){
            boolean inList = false;
            int k=0;
            for(Couple<String, List<Couple<Observation, Integer>>> element: this.ToOpen){
                if(treasure.getLeft().equals(element.getLeft())) { inList = true; break; }
            }
            if (!inList)
                this.ToOpen.add(treasure);
        }

        // Add new elements from one list to another (Opened)
        for(Couple<String, List<Couple<Observation, Integer>>> treasure: opened){
            boolean inList = false;
            int k=0;
            for(Couple<String, List<Couple<Observation, Integer>>> element: this.OpenedSafes){
                if(treasure.getLeft().equals(element.getLeft())) { inList = true; break; }
            }
            if (!inList)
                this.OpenedSafes.add(treasure);
        }

        // Add new elements from one list to another (ToCollect)
        for(Couple<String, List<Couple<Observation, Integer>>> treasure: toCollect){
            boolean inList = false;
            int k=0;
            for(Couple<String, List<Couple<Observation, Integer>>> element: this.ToCollect){
                if(treasure.getLeft().equals(element.getLeft())) { inList = true; break; }
            }
            if (!inList)
                this.ToCollect.add(treasure);
        }

        // Add new elements from one list to another (Collected)
        for(Couple<String, List<Couple<Observation, Integer>>> treasure: collected){
            boolean inList = false;
            int k=0;
            for(Couple<String, List<Couple<Observation, Integer>>> element: this.Collected){
                if(treasure.getLeft().equals(element.getLeft())) { inList = true; break; }
            }
            if (!inList)
                this.Collected.add(treasure);
        }

        // Remove repeated elements between list
        for (Couple<String, List<Couple<Observation, Integer>>> treasure: this.OpenedSafes){
            int k=0;
            while(k < this.ToOpen.size()){
                if (this.ToOpen.get(k).getLeft().equals(treasure.getLeft())) { this.ToOpen.remove(k); }
                else { k++; }
            }
        }
        for (Couple<String, List<Couple<Observation, Integer>>> treasure: this.Collected){
            int k=0;
            while(k < this.ToCollect.size()){
                if (this.ToCollect.get(k).getLeft().equals(treasure.getLeft())) { this.ToCollect.remove(k); }
                else { k++; }
            }
        }

    System.out.println(this.myAgent.getLocalName() + " - Merged tasks from group members | To Open: "
    		+ ToOpen.toString() + "| Opened: " + OpenedSafes.toString() + "| To Collect: " + ToCollect.toString() + "| Collected: " + Collected.toString());

    }


    /*********************************
     * MOVEMENT
     ********************************/

    public String randomMovement(List<Couple<String,List<Couple<Observation, Integer>>>> lobs){

        String nextNode = null;

        //Check neighbor nodes
        if (lobs.size() > 2) {

            // Generate an observation list
            List<Couple<String, List<Couple<Observation, Integer>>>> lobs2select = ((AbstractDedaleAgent) this.myAgent).observe();
            int i = 0;
            while (i < lobs2select.size()) {
                // Remove current position
                if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() -1)))
                    lobs2select.remove(i);
                    // Remove previous nodes
                else if ((previousPositions.size() > 1)&&(StuckIter < StuckIterThr))
                {
                    if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() -2)))
                        lobs2select.remove(i);
                    else
                        i++;
                }
                else
                    i++;
            }

            //Define next node with a random function
            int moveId = 0;
            if(lobs2select.size() > 1) {
                Random r = new Random();
                moveId = r.nextInt(0,lobs2select.size());
            }
            nextNode = lobs2select.get(moveId).getLeft();

            //if(StuckIter > StuckIterThr)
            //    System.out.println(this.myAgent.getLocalName() + " - Stuck: Obs: " + lobs+ "| Viable paths:" + lobs2select + " | Node selected:" + nextNode + "(" + moveId + ")");

        }
        //There is only one neighbor, select it
        else if (lobs.size() == 2)
            nextNode = lobs.get(1).getLeft();

        return nextNode;
    }


    /*********************************
     * COMMUNICATION
     ********************************/

    public void sendTask(){
        MessageTemplate mt =
                AchieveREResponder.createMessageTemplate("TASK-REQUEST");
        myAgent.addBehaviour(new AchieveREResponder(myAgent, mt) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {

                // Get content of the message
                String content = request.getContent();
                System.out.println(this.myAgent.getLocalName() + " - Message received: " + content);
                String[] cnt = content.split("#");
                String[] agentInfo = cnt[0].split("/");
                String agentPosition = agentInfo[0];
                AgentStatus agentStatus = new AgentStatus(agentInfo[1]);

                // Merge lists of tasks
                String[] tasksLists = cnt[1].split("/");
                List<Couple<String,List<Couple<Observation,Integer>>>> toOpen = AgentAttributes.observationsDeserializer(tasksLists[0]);
                List<Couple<String,List<Couple<Observation,Integer>>>> openSafes = AgentAttributes.observationsDeserializer(tasksLists[1]);
                List<Couple<String,List<Couple<Observation,Integer>>>> toCollect = AgentAttributes.observationsDeserializer(tasksLists[2]);
                List<Couple<String,List<Couple<Observation,Integer>>>> collected = AgentAttributes.observationsDeserializer(tasksLists[3]);
                taskListMerge(toOpen, openSafes, toCollect, collected);

                // Choose a task
                List<String> path = null;

                // Send the tankers to the station
                if (agentStatus.getAgentType().equals(AgentType.TANKER)){
                    // Obtain the station ID
                    String tankerStation = null;
                    for(int i=0; i < AgentAttributes.Tankers.size(); i++){
                        if (AgentAttributes.Tankers.get(i).equals(agentStatus.getLocalName()))
                            tankerStation = AgentAttributes.tankerStations.get(i);
                    }

                    // compute the path
                    if (tankerStation != null){
                        try{
                            path = myMap.getShortestPath(agentPosition, tankerStation);
                            System.out.println(this.myAgent.getLocalName() + " - Path generated to the station: " + path);
                        }catch(Exception ignore){}
                    }
                }
                // Send the collectors to treasures or stations
                else {
                    path = chooseTaskPath(agentPosition, agentStatus);
                    System.out.println(this.myAgent.getLocalName() + " - Path generated: " + path);
                }

                // Send Response
                ACLMessage informPath = request.createReply();
                informPath.setPerformative(ACLMessage.INFORM);
                if (path == null)
                    informPath.setContent("NONE" + "#" + ToOpen.toString() +"/" + OpenedSafes.toString() +
                            "/"+ ToCollect.toString() + "/" + Collected.toString());
                else
                    informPath.setContent(path.toString() + "#" + ToOpen.toString() +"/" + OpenedSafes.toString() +
                            "/"+ ToCollect.toString() + "/" + Collected.toString());
                return informPath;
            }
        });
    }

    public List<String> chooseTaskPath(String agentPosition, AgentStatus agentStatus){

        List<String> path = null;

        // The beckpack is not empty, send to treasure
        if(agentStatus.getBackpackCapacity() > 0){

        // Select the treasure from ToCollect
        for(Couple<String, List<Couple<Observation, Integer>>> t: this.ToCollect){
            Observation treasureType = null;
            int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

            for (Couple<Observation, Integer> o : t.getRight()) {
                switch (o.getLeft()) {
                    case DIAMOND: case GOLD: treasureType = o.getLeft(); amount = o.getRight(); break;
                    case LOCKSTATUS: lockStatus = o.getRight(); break;
                    case STRENGH: strength = o.getRight();break;
                    case LOCKPICKING: lockPicking = o.getRight(); break;
                    default: break;
                }
            }
            if((agentStatus.getTreasureType().equals(treasureType))){
                try { path = this.myMap.getShortestPath(agentPosition, t.getLeft()); }
                catch (Exception ignored){}
                break;
            }
        }

        if (path != null){
            List<String> path2station = null;
            for(String id: AgentAttributes.tankerStations){
                try{
                    List<String> p = myMap.getShortestPath(path.get(path.size()-1), id);
                    //
                    if (path2station == null){
                        path2station = p;
                    }
                    else if(p.size() < path2station.size()){
                        path2station = p;
                    }
                }
                catch(Exception ignore){}
            }

            if (path2station != null){
                path.addAll(path2station);

                // Remove the station location to avoid blockages
                path.remove(path.size()-1);
            }
        }



        if(path == null) {


            // Select the treasure from ToOpen
            for (Couple<String, List<Couple<Observation, Integer>>> t : this.ToOpen) {
                Observation treasureType = null;
                int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

                for (Couple<Observation, Integer> o : t.getRight()) {
                    switch (o.getLeft()) {
                        case DIAMOND:
                        case GOLD:
                            treasureType = o.getLeft();
                            amount = o.getRight();
                            break;
                        case LOCKSTATUS:
                            lockStatus = o.getRight();
                            break;
                        case STRENGH:
                            strength = o.getRight();
                            break;
                        case LOCKPICKING:
                            lockPicking = o.getRight();
                            break;
                        default:
                            break;
                    }
                }

                // If the safe can be opened by a collector
                if (agentStatus.getTreasureType().equals(treasureType) && (((lockPicking < 2) && (strength < 2)))) {
                    try {
                        path = this.myMap.getShortestPath(agentPosition, t.getLeft());
                    } catch (Exception ignored) {
                    }

                }
                // If not, coordination
                else if (agentStatus.getTreasureType().equals(treasureType)){
                    System.out.println(this.myAgent.getLocalName() + " - Tries to open " + t + " with coordination (" + agentStatus.toString() + ")");
                    try {
                        path = this.myMap.getShortestPath(agentPosition, t.getLeft());
                        goToNode = true;
                        this.destinationNode = t.getLeft();
                    } catch (Exception ignored) {
                    }
                    break;
                }
                else{
                    System.out.println(this.myAgent.getLocalName() + " - Not valid treasure type " + t + " to perform coordination (" + agentStatus.toString() + ")");
                }
            }
        }


        }
        // The backpack is empty, send to the nearest station
        else{
            for(String id: AgentAttributes.tankerStations){
                try{
                    List<String> p = myMap.getShortestPath(agentPosition, id);
                    //
                    if (path == null){
                        path = p;
                    }
                    else if(p.size() < path.size()){
                        path = p;
                    }
                }
                catch(Exception ignore){}
            }

            // Remove the station location to avoid blockages
            if (path != null)
                path.remove(path.size()-1);
        }


        return path;
    }
}
