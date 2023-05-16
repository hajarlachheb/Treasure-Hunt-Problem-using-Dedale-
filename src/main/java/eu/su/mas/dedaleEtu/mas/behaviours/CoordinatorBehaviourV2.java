package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import jade.core.behaviours.SimpleBehaviour;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class CoordinatorBehaviourV2 extends SimpleBehaviour {

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
    private final List<String> previousPositions;
    private int StuckIter;
    private int StuckRecover;

    // Status Parameters
    private int openSafesCount;

    private final List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
    private final List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
    private final List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
    private final List<Couple<String,List<Couple<Observation,Integer>>>> Collected; // Treasures already collected

    // Control Parameters

    public int period = 100;
    public int periodMap = 300;
    public int StuckIterThr = 5;
    public int RecoverIterThr = 15;

    public CoordinatorBehaviourV2(final AbstractDedaleAgent myagent, MapRepresentation myMap, List<String> agentNames) {
        super(myagent);
        this.myMap = myMap;
        this.list_agentNames = agentNames;

        // IMAS Initialization
        this.previousPositions = new ArrayList<>();
        this.StuckIter = 0;
        this.StuckRecover = 0;

        // Status Parameters
        this.openSafesCount = 0;
        this.ToCollect = new ArrayList<>();
        this.OpenedSafes = new ArrayList<>();
        this.ToOpen = new ArrayList<>();
        this.Collected = new ArrayList<>();
    }

    @Override
    public void action() {
        /*********************************
         * ENVIRONMENT DATA COLLECTION
         ********************************/

        // Generate the map if null
        if(this.myMap==null) {
            this.myMap = new MapRepresentation();
            this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, periodMap, this.myMap, list_agentNames));
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
            this.myMap.addNode(myPosition, MapRepresentation.MapAttribute.closed);

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
                    if (nextNode == null && isNewNode)
                        nextNode = nodeId;
                }
            }

            /*********************************
             * TASK PROCESSING
             ********************************/

            // Check each neighbor node
            List<Couple<Observation, Integer>> lObservations = lobs.get(0).getRight();

            // The treasure was found
            if (lObservations.size() > 2) {
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

            // Explore if there are opened nodes
            if (!this.myMap.hasOpenNode()) {
                //Explo finished
                //finished=true;
                System.out.println(this.myAgent.getLocalName() + " - Exploration successfully done");
            } else {
                // Search the closest open node when there is no a directly reachable open node
                if (nextNode == null) {
                    List<String> mypath = this.myMap.getShortestPathToClosestOpenNode(myPosition);
                    nextNode = mypath.get(0);
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
}
