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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TankerBehaviourV2 extends TickerBehaviour {
    /**
     * When an agent choose to migrate all its components should be serializable
     */
    private static final long serialVersionUID = 9088209402507795289L;

    /************************
     * IMAS PARAMETERS
     ************************/
    private List<String> previousPositions;
    private int StuckIter;
    private int StuckRecover;

    // Status Parameters

    private List<Couple<String, List<Couple<Observation, Integer>>>> ToOpen; // Safes to open
    private List<Couple<String, List<Couple<Observation, Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
    private List<Couple<String, List<Couple<Observation, Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
    private List<Couple<String, List<Couple<Observation, Integer>>>> Collected; // Treasures already collected

    private AgentStatus myStatus;
    private String tankerStation;

    // Communication Parameters

    public int waitingCounter;
    public int waitingGapCounter;
    public boolean performTask;
    List<String> path2treasure;

    // Control Parameters
    public int StuckIterThr = 2;
    public int waitingTimeOut = 10;
    public int waitingGap = 10;
    public int prevPositionsMax = 50;

    public TankerBehaviourV2(final AbstractDedaleAgent myagent) {

        super(myagent, 100);

        // IMAS Initialization
        previousPositions = new ArrayList<>();
        this.StuckIter = 0;
        this.StuckRecover = 0;

        // Status Parameters
        this.ToCollect = new ArrayList<>();
        this.OpenedSafes = new ArrayList<>();
        this.ToOpen = new ArrayList<>();
        this.Collected = new ArrayList<>();

        // Communication Parameters
        this.waitingCounter = 0;
        this.waitingGapCounter = 0;
        this.performTask = false;
    }

    @Override
    public void onTick() {

        /*********************************
         * ENVIRONMENT DATA COLLECTION
         ********************************/

        //Example to retrieve the current position
        String myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();

        // Define my agent status
        if (myStatus == null) {
            myStatus = new AgentStatus(this.myAgent.getLocalName(), AgentType.TANKER, Observation.ANY_TREASURE, 400);

            for(int i=0; i < AgentAttributes.Tankers.size(); i++){
                if (AgentAttributes.Tankers.get(i).equals(this.myAgent.getLocalName()))
                    this.tankerStation = AgentAttributes.tankerStations.get(i);
            }
        }

        if (myPosition != "") {

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

            // Obtain the list of observations
            List<Couple<String, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent).observe();//myPosition

            /*********************************
             * TASK PROCESSING
             ********************************/

            // Check each neighbor node
            List<Couple<Observation, Integer>> lObservations = lobs.get(0).getRight();

            // The treasure was found
            if (!lObservations.isEmpty()) {
                // Collect all the parameters
                Observation treasureType = null;
                boolean inList = false;
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
                 * UPDATE SAFE LISTS
                 ********************************/

                updateToOpenList(lobs, myPosition, lockStatus);

                if (lockStatus == 1) {

                    updateOpenedSafesList(lobs, myPosition);
                    updateToCollectList(lobs, myPosition);

                }
            }

            /*********************************
             * NEXT NODE
             ********************************/

            String nextNode = null;

            if (!this.tankerStation.equals(myPosition)) {

                if (waitingGapCounter > 0)
                    waitingGapCounter--;

                // The agent cannot detect other explorers until the waiting counter and waiting gap counters finished
                if ((waitingCounter == 0)&&(waitingGapCounter == 0))
                    detectExplorer();

                // If a waiting call is performed, do not move
                if ((waitingCounter > 0) && (!performTask)) {
                    if (waitingCounter > waitingTimeOut - 1)
                        requestTask(myPosition);
                    //System.out.println(this.myAgent.getLocalName() + " - Waiting to go to the station (" + waitingCounter + ")");
                    waitingCounter--;
                    waitingGapCounter = waitingGap;
                }
                // Perform a task
                else if (performTask) {
                    nextNode = goToPath(lobs, myPosition);
                } else
                    nextNode = randomMovement(lobs);
            }


            /*********************************
             * AGENT MOVEMENT
             ********************************/

            // Move to next node
            if (nextNode != null) {
                // Check if nextnode is reachable
                boolean inObs = false;
                for (Couple<String, List<Couple<Observation, Integer>>> o : lobs)
                    if (o.getLeft().equals(nextNode))
                        inObs = true;

                if (inObs)
                    ((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
                else
                    System.out.println(this.myAgent.getLocalName() + " - Not reachable node - " + nextNode + "/ My location:" + myPosition + "/ Observations: " + lobs);
            }
        }
    }

    /*********************************
     * UPDATE LIST
     ********************************/

    public void updateToOpenList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition, int lockStatus) {
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

    public void updateOpenedSafesList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition) {
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

    public void updateCollectedList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition) {
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

    public void updateToCollectList(List<Couple<String, List<Couple<Observation, Integer>>>> lobs, String myPosition) {
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

    public String goToPath(List<Couple<String,List<Couple<Observation, Integer>>>> lobs, String myPosition){

        String nextNode = null;

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
        return nextNode;
    }


    /*********************************
     * AGENTS COMMUNICATION
     ********************************/

    public void detectExplorer(){

        // Check if there is an explorer
        MessageTemplate msgTemplate = MessageTemplate.and(
                MessageTemplate.MatchProtocol("EXPLORER-HERE"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
        while(msgReceived != null) {
            String sgreceived = msgReceived.getContent();
            if (sgreceived.equals("I'M-HERE")) {
                waitingCounter = waitingTimeOut;
                //System.out.println("Message detected");
            }
            msgReceived = this.myAgent.receive(msgTemplate);
        }
    }

    public void requestTask(String myPosition){
        // Prepare Task request
        ACLMessage taskRequest = new ACLMessage(ACLMessage.REQUEST);
        taskRequest.setProtocol("TASK-REQUEST");
        taskRequest.setContent(myPosition + "/" + myStatus.SerializeContent() + "#" + ToOpen.toString() +"/" + OpenedSafes.toString() +
                "/"+ ToCollect.toString() + "/" + Collected.toString());
        for (String m : AgentAttributes.Explorers)
            taskRequest.addReceiver(new AID(m, AID.ISLOCALNAME));

        myAgent.addBehaviour(new DedaleAchieveREInitiator(myAgent, taskRequest) {

            // Handle group members inform
            protected void handleInform(ACLMessage inform) {

                if(!performTask) {
                    // Process content
                    String content = inform.getContent();
                    String[] cnt = content.split("#");

                    // Merge lists of tasks
                    String[] tasksLists = cnt[1].split("/");
                    List<Couple<String,List<Couple<Observation,Integer>>>> toOpen = AgentAttributes.observationsDeserializer(tasksLists[0]);
                    List<Couple<String,List<Couple<Observation,Integer>>>> openSafes = AgentAttributes.observationsDeserializer(tasksLists[1]);
                    List<Couple<String,List<Couple<Observation,Integer>>>> toCollect = AgentAttributes.observationsDeserializer(tasksLists[2]);
                    List<Couple<String,List<Couple<Observation,Integer>>>> collected = AgentAttributes.observationsDeserializer(tasksLists[3]);
                    taskListMerge(toOpen, openSafes, toCollect, collected);

                    // Process the path
                    System.out.println(this.myAgent.getLocalName() + " - Path received: " + cnt[0]);
                    if (content.equals("NONE")) {
                        System.out.println(this.myAgent.getLocalName() + " - There is no path to a treasure ");
                    } else {
                        performTask = true;
                        path2treasure = AgentAttributes.listDeserializer(cnt[0]);
                        System.out.println(this.myAgent.getLocalName() + " - Path received: " + cnt[0]);
                    }
                    waitingCounter = 0;
                }
            }
        });
    }

}