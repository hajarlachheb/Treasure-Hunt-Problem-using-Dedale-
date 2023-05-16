package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import jade.core.AID;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes.*;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class CoordinatorBehaviour extends SimpleBehaviour {

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
    private List<String> previousPositions;
    private int StuckIter;
    private int StuckRecover;
    private enum GroupState {GROUPING, FORMED}
    private GroupState currentGroupState;

    // Status Parameters
    private List<Couple<String, List<Couple<Observation, Integer>>>> ToOpen; // Safes to open
    private List<Couple<String, List<Couple<Observation, Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
    private List<Couple<String, List<Couple<Observation, Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
    private List<Couple<String, List<Couple<Observation, Integer>>>> Collected; // Treasures already collected


    // Group Formation Parameters

    public CoordinatorBehaviour(final AbstractDedaleAgent myagent, MapRepresentation myMap, List<String> agentNames) {
        super(myagent);
        this.myMap=myMap;
        this.list_agentNames=agentNames;

        // IMAS Initialization
        this.currentGroupState = GroupState.GROUPING;
        previousPositions = new ArrayList<>();
        this.StuckIter = 0;
        this.StuckRecover = 0;

        // Status Parameters
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

        if(this.myMap==null) {
            this.myMap= new MapRepresentation();
            this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent,100,this.myMap,list_agentNames));
        }

        // Retrieve the current position
        String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

        if (myPosition!=null) {

            // Count iterations stuck
            if (!previousPositions.isEmpty())
            {
                if (previousPositions.get(previousPositions.size()-1).equals(myPosition))
                    this.StuckIter++;
                else
                    this.StuckIter = 0;
            }

            // Add the position to the list
            if (previousPositions.isEmpty() || (!previousPositions.get(previousPositions.size() - 1).equals(myPosition)))
                previousPositions.add(myPosition);

            //List of observable from the agent's current position
            List<Couple<String, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent).observe();//myPosition

            //Wait a defined time
            try {
                this.myAgent.doWait(200);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*********************************
             * MAP UPDATES (THIS AGENT)
             ********************************/

            //1) remove the current node from openlist and add it to closedNodes.
            this.myMap.addNode(myPosition, MapRepresentation.MapAttribute.closed);

            if (previousPositions.size() > 1)
                this.myMap.addEdge(myPosition, previousPositions.get(previousPositions.size()-2));


            /*********************************
             * TASK PROCESSING
             ********************************/

            // Check each neighbor node
            List<Couple<Observation,Integer>> lObservations= lobs.get(0).getRight();

            // The treasure was found
            if (lObservations.size() > 2)
            {
                // Collect all the parameters
                Observation treasureType = null;
                boolean inList = false;
                int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

                for(Couple<Observation,Integer> o:lObservations){
                    switch (o.getLeft()) {
                        case DIAMOND:case GOLD:
                            treasureType = o.getLeft();
                            amount = o.getRight();
                            break;
                        case LOCKSTATUS: lockStatus = o.getRight(); break;
                        case STRENGH: strength = o.getRight(); break;
                        case LOCKPICKING: lockPicking = o.getRight(); break;
                        default: break;
                    }
                }
                //System.out.println(this.myAgent.getLocalName()+ "All observations -" + lobs);
                System.out.println(this.myAgent.getLocalName()+ " - Treasure found: "+ treasureType +
                        "(" + amount + "), lock status (" + lockStatus + "), strength (" + strength +
                        "), lock picking (" + lockPicking + ")");


                /*********************************
                 * UPDATE TO-OPEN LIST
                 ********************************/
                inList = false;
                int k=0;
                while(k < this.ToOpen.size()-1)
                {
                    Couple<String, List<Couple<Observation,Integer>>> ob = this.ToOpen.get(k);
                    if(ob.getLeft().equals(myPosition))
                    {
                        inList = true;
                        if (lockStatus == 1){
                            ToOpen.remove(k);
                            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " removed from ToOpen list");
                        }
                        else
                            k++;
                    }
                    else
                        k++;
                }
                if ((lockStatus == 0)&&(!inList)) {
                    ToOpen.add(lobs.get(0));
                    System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in ToOpen list");
                }

                if (lockStatus == 1) {

                    /*********************************
                     * UPDATE OPENED SAFES LIST
                     ********************************/
                    inList = false;
                    for(Couple<String, List<Couple<Observation,Integer>>> ob: this.OpenedSafes)
                        if(ob.getLeft().equals(myPosition))
                            inList = true;
                    if (!inList) {
                        OpenedSafes.add(lobs.get(0));
                        System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in OpenedSafes list");
                    }


                    /*********************************
                     * UPDATE TO-COLLECT LIST
                     ********************************/
                    inList = false;
                    k = 0;
                    while(k < this.ToCollect.size()-1)
                    {
                        Couple<String, List<Couple<Observation,Integer>>> ob = this.ToCollect.get(k);
                        if (ob.getLeft().equals(myPosition)) {
                            inList = true;
                            if (lobs.get(0).getRight().size() > 2)
                            {
                                ToCollect.remove(k);
                                System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " removed from ToCollect list");
                            }
                            else
                                k++;
                        }
                        else
                            k++;
                    }
                    if ((!inList) && !(lobs.get(0).getRight().size() > 2)) {
                        ToCollect.add(lobs.get(0));
                        System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in ToCollect list");
                    }

                }
            }

            /*********************************
             * NEXT AGENT NODE
             ********************************/

            String nextNode=null;


            if (this.myMap.hasOpenNode())
            {
                // Search the closest open node when there is no a directly reachable open node
                try {
                    nextNode = this.myMap.getShortestPathToClosestOpenNode(myPosition).get(0);
                }
                catch(Exception e)
                {
                    //System.out.println("ERROOOORRR... CANNOT GO TO THAT PATH");
                }
            }

            // In case of get stuck
            if (this.StuckIter > 2)
            {
                this.StuckRecover = 10;
            }
            if ((this.StuckIter > 2)||(this.StuckRecover > 0)||(nextNode == null))
            {
                if (lobs.size() > 2)
                {
                    // Generate an observation list
                    List<Couple<String, List<Couple<Observation, Integer>>>> lobs2select = ((AbstractDedaleAgent) this.myAgent).observe();
                    int i = 0;
                    while (i < lobs2select.size()) {
                        // Remove current position
                        if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() -1)))
                            lobs2select.remove(i);
                            // Remove previous nodes
                        else if ((previousPositions.size() > 1)&&(StuckIter < 2))
                        {
                            if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() -2)))
                                lobs2select.remove(i);
                            else
                                i++;
                        }
                        else
                            i++;
                    }

                    if (StuckIter > 5)
                        System.out.println(this.myAgent.getLocalName() + " - Stuck: Obs: " + lobs + "| Viable paths:" + lobs2select);

                    //Define next node with a random function
                    int moveId = 0;
                    if(lobs2select.size() > 1) {
                        Random r = new Random();
                        moveId = r.nextInt(0,lobs2select.size() - 1);
                    }
                    nextNode = lobs2select.get(moveId).getLeft();
                }
                else if (lobs.size() == 2)
                    nextNode = lobs.get(1).getLeft();

                this.StuckRecover--;
            }

            /*********************************
             * GROUP FORMATION VS GROUP FORMED
             ********************************/

            switch (currentGroupState) {
                case GROUPING:
                    //TODO: Group Formation
                    break;
                case FORMED:
                    //TODO: Task Assignment
                    break;

            }

            /*********************************
             * TRAFFIC CONTROL
             ********************************/
            //TODO: Traffic Control


            /*********************************
             * AGENT MOVEMENT
             ********************************/

            if (nextNode != null)
            {
                ((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
            }


        }

    }

    @Override
    public boolean done() {
        return finished;
    }
}
