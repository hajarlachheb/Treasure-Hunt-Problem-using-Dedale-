package eu.su.mas.dedaleEtu.mas.additional_elements;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.protocols.DedaleAchieveREInitiator;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CollectorBehaviourDeprecated extends TickerBehaviour {

    private static final long serialVersionUID = 9088209402507795289L;

    /************************
     * IMAS PARAMETERS
     ************************/
    private List<String> previousPositions;
    private int StuckIter;
    private enum GroupState {GROUPING, FORMED}
    private GroupState currentGroupState;

    // Status Parameters
    private int openSafesCount;
    private int amountCollected;

    private List<Couple<String, List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
    private List<Couple<String, List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
    private List<Couple<String, List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
    private List<Couple<String, List<Couple<Observation,Integer>>>> Collected; // Treasures already collected


    // Group Formation Parameters
    private boolean inGroup;
    private List<String> GroupMembers;

    // Following parameters
    private boolean followResponse = false;
    private boolean followingPathSelected = false;
    private int shortestPathIdx = 0;
    private List<String> path2leader;
    private List<List<String>> paths2leaderDestination;
    private int ResponseWaitIter;


    public CollectorBehaviourDeprecated(final AbstractDedaleAgent myagent) {
        super(myagent, 1000);
        //super(myagent);

        // IMAS Initialization
        this.currentGroupState = GroupState.GROUPING;
        previousPositions = new ArrayList<>();
        this.StuckIter = 0;

        // Status Parameters
        this.openSafesCount = 0;
        this.amountCollected = 0;
        this.ToCollect = new ArrayList<>();
        this.OpenedSafes = new ArrayList<>();
        this.ToOpen = new ArrayList<>();
        this.Collected = new ArrayList<>();

        // Group Formation
        this.inGroup = false;
        this.GroupMembers = new ArrayList<>();
        GroupMembers.add(this.myAgent.getName());

        // Following Parameters
        this.path2leader = new ArrayList<>();
        this.paths2leaderDestination = new ArrayList<>();
        this.ResponseWaitIter = 0;

    }

    @Override
    public void onTick() {

        /*********************************
         * ENVIRONMENT DATA COLLECTION
         ********************************/

        //Example to retrieve the current position
        String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

        if (myPosition!=""){

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

            // Obtain the list of observations
            List<Couple<String, List<Couple<Observation,Integer>>>> lobs=((AbstractDedaleAgent)this.myAgent).observe();//myPosition


            /*********************************
             * TASK PROCESSING
             ********************************/

            // Check each neighbor node
            List<Couple<Observation,Integer>> lObservations= lobs.get(0).getRight();

            // The treasure was found
            if (!lObservations.isEmpty())
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
                 * OPEN SAFES (REACTIVE)
                 ********************************/
                if ((lockStatus == 0) && ((AbstractDedaleAgent) this.myAgent).openLock(treasureType)) {
                    System.out.println(this.myAgent.getLocalName() + " - Opened a safe ( Expertise: " +
                            ((AbstractDedaleAgent) this.myAgent).getMyExpertise() + ")");
                    lockStatus = 1;
                    this.openSafesCount++;
                }

                // Update lobs
                lobs = ((AbstractDedaleAgent)this.myAgent).observe();

                /*********************************
                 * UPDATE TO-OPEN LIST
                 ********************************/
                inList = false;
                int k = 0;
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
                     * COLLECT TREASURES (REACTIVELY)
                     ********************************/
                    List<Couple<Observation, Integer>> spaceBefore = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
                    int treasureGrabbed = ((AbstractDedaleAgent) this.myAgent).pick();
                    List<Couple<Observation, Integer>> spaceAfter = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();

                    if (treasureGrabbed > 0)
                        System.out.println(this.myAgent.getLocalName() + " - Grabbed " + treasureGrabbed +
                                " of " + treasureType + " (Space before: " + spaceBefore + ", Space after: " +
                                spaceAfter + ")");
                    this.amountCollected += treasureGrabbed;

                    /*********************************
                     * UPDATE COLLECTED LIST
                     ********************************/
                    if (((AbstractDedaleAgent) this.myAgent).observe().get(0).getRight().isEmpty()) {

                        inList = false;
                        for (Couple<String, List<Couple<Observation, Integer>>> ob : this.Collected)
                            if (ob.getLeft().equals(myPosition))
                                inList = true;
                        if (!inList) {
                            Collected.add(lobs.get(0));
                            System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in Collected list");
                        }
                    }

                    // Update lobs
                    lobs = ((AbstractDedaleAgent)this.myAgent).observe();


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
                            if (lobs.get(0).getRight().isEmpty())
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
                    if ((!inList) && !(lobs.get(0).getRight().isEmpty())) {
                        ToCollect.add(lobs.get(0));
                        System.out.println(this.myAgent.getLocalName() + " - " + lobs.get(0) + " added in ToCollect list");
                    }

                }
            }

            /*********************************
             * TREASURE STORAGE (REACTIVE)
             ********************************/

            //Trying to store everything in the tanker
            for(String tanker: AgentAttributes.Tankers) {
                boolean stored = ((AbstractDedaleAgent) this.myAgent).emptyMyBackPack(tanker);
                //if (stored)
                //    System.out.println(this.myAgent.getLocalName() + "- Store treasures to " + tanker);
            }

            /*********************************
             * NEXT NODE (FOLLOW RESPONSE)
             ********************************/

            //Random move from the current position
            String nextNode = null;

            if ((followResponse)&&(StuckIter < 6))
            {
                if(!followingPathSelected) {
                    // Filter paths to feasible ones
                    int j = 0;
                    shortestPathIdx = 0;
                    while (j < this.paths2leaderDestination.size()) {
                        // Remove non-direct nodes
                        int i = 0;
                        boolean inObs = false;
                        while ((i < this.paths2leaderDestination.get(j).size()) && (!inObs)) {
                            for (Couple<String, List<Couple<Observation, Integer>>> o : lobs)
                                if (o.getLeft().equals(this.paths2leaderDestination.get(j).get(i)))
                                    inObs = true;

                            if (!inObs) {
                                this.paths2leaderDestination.get(j).remove(i);
                            } else {
                                i++;
                            }
                        }
                        // Remove non-feasible paths
                        if (this.paths2leaderDestination.get(j).size() > 0) {
                            if (this.paths2leaderDestination.get(shortestPathIdx).size() > this.paths2leaderDestination.get(j).size()) {
                                shortestPathIdx = j;
                                followingPathSelected = true;
                            }
                            j++;
                        } else
                            this.paths2leaderDestination.remove(j);
                    }

                    // Remove non-direct nodes from leader path
                    int i = 0;
                    boolean inObs = false;
                    while ((i < this.path2leader.size()) && (!inObs)) {
                        for (Couple<String, List<Couple<Observation, Integer>>> o : lobs)
                            if (o.getLeft().equals(this.path2leader.get(i)))
                                inObs = true;

                        if (!inObs) {
                            this.path2leader.remove(i);
                        } else {
                            i++;
                        }
                    }

                    // Assign the next node of the path
                    System.out.println(this.myAgent.getLocalName() + " - Observations: " + lobs);
                    System.out.println(this.myAgent.getLocalName() + " - Path2destination: " + this.paths2leaderDestination);
                    System.out.println(this.myAgent.getLocalName() + " - Path2leader: " + this.path2leader);
                }
                // Select next node
                if (followingPathSelected) {
                    // Remove current position
                    if(this.paths2leaderDestination.get(shortestPathIdx).get(0).equals(myPosition))
                        this.paths2leaderDestination.get(shortestPathIdx).remove(0);

                    // Define next node
                    if(this.paths2leaderDestination.get(shortestPathIdx).size() > 0)
                        nextNode = this.paths2leaderDestination.get(shortestPathIdx).get(0);
                    else {
                        followResponse = false;
                        followingPathSelected = false;
                    }
                }
                else
                    followResponse = false;


                /*
                // Try to go to the same destination
                if (this.path2leaderDestination.size() > 0)
                {
                    // Verify that it is a legit path
                    int i=0;
                    boolean inObs = false;
                    while((i<this.path2leaderDestination.size())&&(!inObs))
                    {
                        inObs = false;
                        for(Couple<String, List<Couple<Observation,Integer>>> o: lobs)
                            if (o.getLeft().equals(this.path2leaderDestination.get(i)))
                                inObs = true;

                        if(!inObs)
                            this.path2leaderDestination.remove(i);
                        else
                            i++;
                    }

                    // Define next node if it is a valid path
                    if (inObs)
                    {
                        // Remove current position
                        if(this.path2leaderDestination.get(0).equals(myPosition))
                            this.path2leaderDestination.remove(0);

                        // Define next node
                        if(this.path2leaderDestination.size() > 0)
                            nextNode = this.path2leaderDestination.get(0);
                        else
                            followResponse = false;
                    }
                    else
                        followResponse = false;
                }
                // If the destination list is empty, it means that there is no direct path or the destination is the
                // location of this agent. Go to the opposite direction
                else
                {
                    // Verify that it is a legit path
                    int i=0;
                    boolean inObs = false;
                    while((i<this.path2leader.size())&&(!inObs))
                    {
                        inObs = false;
                        for(Couple<String, List<Couple<Observation,Integer>>> o: lobs)
                            if (o.getLeft().equals(this.path2leader.get(i)))
                                inObs = true;

                        if(!inObs)
                            this.path2leader.remove(i);
                        else
                            i++;
                    }

                    // Define next node if it is a valid path
                    // Remove current position
                    if (inObs)
                    {
                        //Remove Current position
                        if(this.path2leader.get(0).equals(myPosition))
                            this.path2leader.remove(0);


                        if(this.path2leader.size() > 0) {
                            // Search the observations to move
                            List<Couple<String, List<Couple<Observation, Integer>>>> lobs2select = ((AbstractDedaleAgent) this.myAgent).observe();
                            i = 0;
                            while (i < lobs2select.size()) {
                                if (lobs2select.get(i).getLeft().equals(myPosition))
                                    lobs2select.remove(i);
                                else if (lobs2select.get(i).getLeft().equals(path2leader.get(0)))
                                    lobs2select.remove(i);
                                else
                                    i++;
                            }

                            //Define next node
                            int moveId = 0;
                            if(lobs2select.size() > 1) {
                                Random r = new Random();
                                moveId = r.nextInt(lobs2select.size() - 1);
                            }
                            nextNode = lobs2select.get(moveId).getLeft();
                        }

                        // Admit more responses
                        followResponse = false;
                    }
                    else
                        followResponse = false;
                }*/

            }
            /*********************************
             * NEXT NODE (RANDOM MOVE)
             ********************************/
            if ((ResponseWaitIter < 1)&&(!followResponse || !followingPathSelected))
            {
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

                    if(StuckIter > 5)
                        System.out.println(this.myAgent.getLocalName() + " - Stucked: Obs: " + lobs+ "| Viable paths:" + lobs2select);

                    //Define next node with a random function
                    int moveId = 0;
                    if(lobs2select.size() > 1) {
                        Random r = new Random();
                        moveId = r.nextInt(lobs2select.size() - 1);
                    }
                    nextNode = lobs2select.get(moveId).getLeft();

                }
                //There is only one neighbor, select it
                else if (lobs.size() == 2)
                    nextNode = lobs.get(1).getLeft();


            }

            /*********************************
             * FOLLOWING CALL
             ********************************/

            following();

            /*********************************
             * GROUP FORMATION VS GROUP FORMED
             ********************************/

            switch (currentGroupState) {
                case GROUPING:
                    //TODO: Group Formation
                    break;
                case FORMED:

                    break;

            }

            /*********************************
             * AGENT MOVEMENT
             ********************************/

            //TODO: Agent Blockage.
            // You can use the variables myPosition (agent's position), lobs (surrounding nodes), previousPositions
            // For messaging, you can use ACLMessage

            if (ResponseWaitIter > 0)
                ResponseWaitIter--;

            if (nextNode != null)
            {
                ((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
            }
        }

    }

    public void following()
    {
        // Generate modeID list
        List<Couple<String,List<Couple<Observation,Integer>>>> Obs = ((AbstractDedaleAgent)this.myAgent).observe();
        List<String> nodeIDs = new ArrayList<>();
        for(Couple<String,List<Couple<Observation,Integer>>> o: Obs)
            nodeIDs.add(o.getLeft());

        // Prepare Following request
        ACLMessage followingRequest = new ACLMessage(ACLMessage.REQUEST);
        followingRequest.setProtocol("FOLLOW");
        followingRequest.setContent(nodeIDs.toString());
        followingRequest.addReceiver(new AID("Explo1",AID.ISLOCALNAME));
        //followingRequest.addReceiver(new AID("Explo2",AID.ISLOCALNAME));
        //followingRequest.addReceiver(new AID("Explo3",AID.ISLOCALNAME));

        myAgent.addBehaviour( new DedaleAchieveREInitiator(myAgent, followingRequest) {

            // Handle leader's inform
            protected void handleInform(ACLMessage inform) {

                // Process content
                //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                String content = inform.getContent();

                // Obtain the path from the leader's destination
                String[] paths = content.split("/#");
                String[] pathsDestStr = paths[0].split("/");
                List<List<String>> paths2dest = new ArrayList<>();
                for(String p: pathsDestStr)
                    paths2dest.add(AgentAttributes.listDeserializer(p));

                // Obtain the path from the leader
                List<String> path2lead = AgentAttributes.listDeserializer(paths[1]);

                // Define a waiter iterator to support the messaging delay (First response ignored)
                if (ResponseWaitIter == 0)
                    ResponseWaitIter = 6;

                // Filter paths
                //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                boolean followable = false;

                // If leader path is empty, the agent is at an unexplored node
                // If leader path is larger than the range, there is an unexplored shortcut. The given path can move
                // away the agent from the leader
                if ((path2lead.size() > 0)&&(path2lead.size() < 6))
                    followable = true;
                else
                    path2lead.clear();

                // Remove empty paths
                int i=0;
                while (i < paths2dest.size()) {
                    if (paths2dest.get(i).size() > 0) {
                        followable = true;
                        i++;
                    }
                    else {
                        paths2dest.remove(i);
                    }
                }


                // Inform the main behaviour
                //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                if((followable)&&(ResponseWaitIter < 4)) {

                    // Save paths
                    path2leader = path2lead;
                    paths2leaderDestination = paths2dest;

                    // Follow status
                    followResponse = true;
                    ResponseWaitIter = 0;
                }

            }
        });
    }

}
