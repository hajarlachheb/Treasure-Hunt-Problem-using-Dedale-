package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.protocols.DedaleAchieveREInitiator;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;

import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes.*;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;
import org.stathissideris.ascii2image.text.CellSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class CollectorBehaviour extends TickerBehaviour {

    private static final long serialVersionUID = 9088209402507795289L;

    /************************
     * IMAS PARAMETERS
     ************************/
    private List<String> previousPositions;
    private int StuckIter;
    private int StuckRecover;
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
    private AgentStatus myStatus;
    private AgentStatus leaderStatus;

    // Following parameters
    private List<List<String>> paths2leader;
    private boolean followsLeader; // Following state
    private boolean followMsgReceived; // Following Message Received
    private int followPathIdx;
    private int followIter;
    private int backIter;
    private int desistIter;
    private String expectedDestination;

    // Task parameters
    private List<String> path2treasure;
    private boolean waitingTaskPath;
    private boolean performTask;


    public CollectorBehaviour (final AbstractDedaleAgent myagent) {
        super(myagent, 150);
        //super(myagent);

        // IMAS Initialization
        this.currentGroupState = GroupState.GROUPING;
        previousPositions = new ArrayList<>();
        this.StuckIter = 0;
        this.StuckRecover = 0;

        // Status Parameters
        this.openSafesCount = 0;
        this.amountCollected = 0;
        this.ToCollect = new ArrayList<>();
        this.OpenedSafes = new ArrayList<>();
        this.ToOpen = new ArrayList<>();
        this.Collected = new ArrayList<>();

        // Group Formation
        this.myStatus = null;
        this.leaderStatus = null;

        // Following Parameters
        this.paths2leader = new ArrayList<>();
        this.followsLeader = false;
        this.followMsgReceived = false;
        this.followPathIdx = -1;
        this.followIter = 0;
        this.backIter = 0;
        this.desistIter = 0;
        this.expectedDestination = "";

        // Task Parameters
        this.waitingTaskPath = false;
        this.performTask = false;

    }

    @Override
    public void onTick() {

        /*********************************
         * ENVIRONMENT DATA COLLECTION
         ********************************/

        //Example to retrieve the current position
        String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

        // Define my agent status
        if (myStatus == null) {
            Observation myTreasureType = ((AbstractDedaleAgent) this.myAgent).getMyTreasureType();
            int backpackCapacity = -1;
            for(Couple<Observation,Integer> o:((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace()){
                if (myTreasureType == o.getLeft())
                        backpackCapacity = o.getRight();
            }
            myStatus = new AgentStatus(this.myAgent.getLocalName(), AgentType.COLLECTOR, myTreasureType, backpackCapacity);
        }


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
             * UPDATE MY AGENT STATUS
             ********************************/

            // Update my agent status
            if (myStatus != null) {
                for(Couple<Observation,Integer> o:((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace()){
                    if (myStatus.treasureType == o.getLeft())
                        myStatus.setBackpackCapacity(o.getRight());
                }
            }

            /*********************************
             * GROUP FORMATION VS GROUP FORMED
             ********************************/

            // Define nextNode
            String nextNode = null;

            switch (currentGroupState) {
                case GROUPING:
                    groupFormation();
                    nextNode = randomMovement(lobs);
                    break;
                case FORMED:

                    if (!performTask)
                        taskReception();

                    // Going to a treasure
                    if(performTask)
                    {
                        System.out.println(this.myAgent.getLocalName() + " - Going to a treasure " + path2treasure);

                        if(path2treasure.size() > 0) {

                            //Remove the current position
                            if (path2treasure.get(0).equals(myPosition))
                                path2treasure.remove(0);

                            if(path2treasure.size() > 0) {

                                //Check if it is a valid node
                                boolean validPath = false;
                                for (Couple<String, List<Couple<Observation, Integer>>> o : lobs) {
                                    if (path2treasure.get(0).equals(o.getLeft())) {
                                        validPath = true;
                                        break;
                                    }
                                }

                                if (validPath) {
                                    nextNode = path2treasure.get(0);
                                }
                                else{
                                    System.out.println(this.myAgent.getLocalName() + " - found a not valid path");
                                    performTask = false;
                                }
                            }
                        }
                        else {
                            System.out.println(this.myAgent.getLocalName() + " - Reached the treasure destination");
                            performTask = false;
                        }
                    }

                    // If the agent is not waiting for a task, follow the leader
                    else if(!waitingTaskPath) {
                        // Receive messages
                        following();

                        // Following state
                        if (followsLeader) {
                            followIter = 6;
                            nextNode = followMovement(lobs, myPosition);

                            // In case of get stuck, behave random to unstuck
                            if (this.StuckIter > 5)
                                this.StuckRecover = 5;
                            if ((StuckIter > 2) || (StuckRecover > 0)) {
                                nextNode = randomMovement(lobs);

                                // Re-activate the following message received to look for valid paths
                                followMsgReceived = true;

                                if (StuckRecover > 0)
                                    StuckRecover--;
                            }
                        }
                        // Probably, if the agent lost the connexion, it can meet the leader going ahead
                        else if (followIter > 0) {
                            nextNode = randomMovement(lobs);
                            followIter--;
                            backIter = 12;
                        }
                        // If it doesn't find the leader, probably it chose the wrong path. Go back
                        else if ((backIter > 0) && (backIter < 5)) {
                            if (previousPositions.size() > 1) {
                                if (previousPositions.get(previousPositions.size() - 1).equals(myPosition))
                                    previousPositions.remove(previousPositions.size() - 1);
                                nextNode = previousPositions.get(previousPositions.size() - 1);
                            }
                            backIter--;
                            desistIter = 0;
                        }
                        // If nothing, behave random with the hope of meet again the leader
                        else {
                            if (desistIter > 6)
                                nextNode = randomMovement(lobs);
                            desistIter++;
                        }
                    }

                    break;

            }

            if (this.leaderStatus == null)
                currentGroupState = GroupState.GROUPING;
            else
                currentGroupState = GroupState.FORMED;

            /*********************************
             * AGENT MOVEMENT
             ********************************/

            // Reduce the following counter
            if(followIter > 0)
                followIter--;

            // Move to next node
            if (nextNode != null)
            {
                // Check if nextnode is reachable
                boolean inObs = false;
                for(Couple<String,List<Couple<Observation, Integer>>> o: lobs)
                    if(o.getLeft().equals(nextNode))
                        inObs = true;

                if (inObs)
                    ((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
                else
                    System.out.println(this.myAgent.getLocalName() + " - Not reachable node - " + nextNode + "/ My location:" + myPosition + "/ Observations: " + lobs + "/ Possible paths: " + paths2leader);
            }
        }

    }

    // Following function
    public void following()
    {
        MessageTemplate mt =
                AchieveREResponder.createMessageTemplate("FOLLOW-ME");
        myAgent.addBehaviour(new AchieveREResponder(myAgent, mt) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {

                // Get content of the message
                String rec = request.getContent();

                // DeSerialize data
                String[] data = rec.split("#");
                String[] paths = data[0].split("/");
                List<List<String>> viablePaths = new ArrayList<>();
                for(String p: paths) {
                    List<String> path = AgentAttributes.listDeserializer(p);
                    viablePaths.add(path);
                }

                if (data.length > 1)
                    expectedDestination = data[1];
                else
                    expectedDestination = "";

                // Save paths and activate following state
                paths2leader = viablePaths;
                followsLeader = true;
                followMsgReceived = true;

                // Send Response
                ACLMessage informPath = request.createReply();
                informPath.setPerformative(ACLMessage.INFORM);
                informPath.setContent(myStatus.SerializeContent() + "#" + ToOpen.toString() +"/" + OpenedSafes.toString() + "/"+ ToCollect.toString() + "/" + Collected.toString());
                return informPath;
            }
        });
    }

    // Grouping Request
    public void groupFormation()
    {
        // Prepare Following request
        ACLMessage followingRequest = new ACLMessage(ACLMessage.REQUEST);
        followingRequest.setProtocol("GROUP-FORMATION");
        followingRequest.setContent(myStatus.SerializeContent());
        for (String m : AgentAttributes.Explorers)
            followingRequest.addReceiver(new AID(m, AID.ISLOCALNAME));

        myAgent.addBehaviour(new DedaleAchieveREInitiator(myAgent, followingRequest) {

            // Handle group members inform
            protected void handleInform(ACLMessage inform) {

                // Avoid useless messages
                if (currentGroupState == GroupState.GROUPING) {

                    // Process content
                    String content = inform.getContent();
                    String[] infContent = content.split("/");

                    if (infContent[0].equals("ACCEPTED")) {
                        leaderStatus = new AgentStatus(infContent[1]);
                        System.out.println(this.myAgent.getLocalName() + " - Was accepted from " + leaderStatus.getLocalName() + "'s group");
                        currentGroupState = GroupState.FORMED;
                    } else if (infContent[0].equals("REJECTED"))
                        System.out.println(this.myAgent.getLocalName() + " - Was rejected from a group formation");
                    else
                        System.out.println(this.myAgent.getLocalName() + " - Invalid group formation inform received");
                }

            }
        });
    }

    /*********************************
     * NEXT NODE (RANDOM MOVE)
     ********************************/
    public String randomMovement(List<Couple<String,List<Couple<Observation,Integer>>>> lobs)
    {
        String nextNode = null;
        //Check neighbor nodes
        if (lobs.size() > 2) {

            // Generate an observation list
            List<Couple<String, List<Couple<Observation, Integer>>>> lobs2select = ((AbstractDedaleAgent) this.myAgent).observe();
            int i = 0;
            while (i < lobs2select.size()) {
                // Remove current position
                if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() - 1)))
                    lobs2select.remove(i);
                    // Remove previous nodes
                else if ((previousPositions.size() > 1) && (StuckIter < 2)) {
                    if (lobs2select.get(i).getLeft().equals(previousPositions.get(previousPositions.size() - 2)))
                        lobs2select.remove(i);
                    else
                        i++;
                } else
                    i++;
            }

            if (StuckIter > 5)
                System.out.println(this.myAgent.getLocalName() + " - Stuck: Obs: " + lobs + "| Viable paths:" + lobs2select);

            //Define next node with a random function
            int moveId = 0;
            if (lobs2select.size() > 1) {
                Random r = new Random();
                moveId = r.nextInt(0,lobs2select.size() - 1);
            }
            nextNode = lobs2select.get(moveId).getLeft();

        }
        //There is only one neighbor, select it
        else if (lobs.size() == 2)
            nextNode = lobs.get(1).getLeft();

        return nextNode;
    }

    /*********************************
     * NEXT NODE (FOLLOW RESPONSE)
     ********************************/
    public String followMovement(List<Couple<String,List<Couple<Observation,Integer>>>> lobs, String myPosition)
    {
        String nextNode = null;

        // Process new following message
        if (followMsgReceived)
        {
            System.out.println(this.myAgent.getLocalName() + " - Follow update");
            // Get the index of the paths2leader
            int i=0;
            boolean pathFound = false;
            followPathIdx = -1;
            while((i < this.paths2leader.size())&&(!pathFound))
            {
                // Iterate for each observation
                for(Couple<String,List<Couple<Observation, Integer>>> o: lobs)
                {
                    // Check if the agent can use the path
                    if(o.getLeft().equals(paths2leader.get(i).get(0)))
                    {
                        pathFound = true;
                        followPathIdx = i;
                    }
                }

                i++;
            }

            // Set boolean to false in order to define that the message was already processed
            followMsgReceived = false;
        }

        // Define next node if the agent didn't get stuck and there is a path
        if ((followPathIdx != -1) && (StuckIter < 10))
        {
            // Remove current position
            if(this.paths2leader.get(followPathIdx).get(0).equals(myPosition))
                this.paths2leader.get(followPathIdx).remove(0);

            if (this.paths2leader.get(followPathIdx).size() > 0) {
                // If the destination is my position, I have to move away
                if (expectedDestination.equals(myPosition)) {
                    // Generate an observation list
                    List<Couple<String, List<Couple<Observation, Integer>>>> lobs2select = ((AbstractDedaleAgent) this.myAgent).observe();
                    int i = 0;
                    while (i < lobs2select.size()) {
                        // Remove current position
                        if (lobs2select.get(i).getLeft().equals(myPosition))
                            lobs2select.remove(i);
                            // Remove path to the leader
                        else if (lobs2select.get(i).getLeft().equals(this.paths2leader.get(followPathIdx).get(0)))
                            lobs2select.remove(i);
                        else
                            i++;
                    }

                    //Define next node with a random function
                    int moveId = 0;
                    if (lobs2select.size() > 1) {
                        Random r = new Random();
                        moveId = r.nextInt(0, lobs2select.size() - 1);
                    }
                    nextNode = lobs2select.get(moveId).getLeft();
                }
                // Else, I can follow the leader
                else {
                    // Define next node
                    nextNode = this.paths2leader.get(followPathIdx).get(0);
                }
            }
            // There is no more nodes
            else {
                followsLeader = false;
                // Wait a bit to let the agent time to receive another message
                followIter = 4;
            }
        }
        // Not valid path or time-out
        else {
            followsLeader = false;
            // If time-out, re-check messages to recover the communication
            if (followPathIdx != -1)
                followMsgReceived = true;
        }

        if(!followsLeader)
            System.out.println(this.myAgent.getLocalName() + " - Follow path finished");

        return nextNode;
    }

    /*********************************
     * TASK RECEPTION
     ********************************/
    /*
    public void taskReception(){
        MessageTemplate mt =
                ContractNetResponder.createMessageTemplate("TASK");
        myAgent.addBehaviour(new ContractNetResponder(myAgent, mt) {
            protected ACLMessage handleCfp(ACLMessage request) {

                return request;
            }

            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept){
                return cfp;
            }

        });
    }*/

    public void taskReception(){
        MessageTemplate mt =
                AchieveREResponder.createMessageTemplate("TASK-ASSIGN");
        myAgent.addBehaviour(new AchieveREResponder(myAgent, mt) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {

                // Get content of the message
                String rec = request.getContent();
                System.out.println(this.myAgent.getLocalName() + " - Task assignment received");

                // Send Response
                ACLMessage informPath = request.createReply();
                informPath.setPerformative(ACLMessage.INFORM);
                informPath.setContent(this.myAgent.getLocalName() + "/"+ ((AbstractDedaleAgent)this.myAgent).getCurrentPosition());
                waitingTaskPath = true;
                return informPath;
            }
        });

        if (waitingTaskPath){
            MessageTemplate mt2 = AchieveREResponder.createMessageTemplate("TASK-PATH");
            myAgent.addBehaviour(new AchieveREResponder(myAgent, mt2) {
                @Override
                protected ACLMessage prepareResponse(ACLMessage request) {

                    // Get content of the message
                    String content = request.getContent();
                    if (!content.equals("NONE")) {
                        path2treasure = AgentAttributes.listDeserializer(content);
                        System.out.println(this.myAgent.getLocalName() + " - Task path received");
                        performTask = true;
                        waitingTaskPath = false;
                    }
                    else{
                        performTask = false;
                        waitingTaskPath = false;
                    }

                    // Send Response
                    ACLMessage informPath = request.createReply();
                    informPath.setPerformative(ACLMessage.INFORM);
                    informPath.setContent("ACK");

                    return informPath;
                }
            });
        }

    }
}
