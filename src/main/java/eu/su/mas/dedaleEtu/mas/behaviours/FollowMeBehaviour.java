package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes.*;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.protocols.DedaleAchieveREInitiator;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FollowMeBehaviour extends TickerBehaviour {
    /**
     * When an agent choose to migrate all its components should be serializable
     */
    private static final long serialVersionUID = -568863390879327961L;
    private MapRepresentation myMap;
    private List<String> expectedPath;
    private String expectedDestination;
    private List<AgentStatus> groupMembers;
    private List<Couple<String,String[]>> groupMembersTasks;
    private int[] membersVisibility;
    private final int maxVisibility = 2;
    private final List<List<String>> previousObservations;

    public FollowMeBehaviour(Agent myagent, long period, MapRepresentation myMap) {

        super(myagent, period);
        this.myMap = myMap;
        this.expectedPath = null;
        this.expectedDestination = null;
        System.out.println(this.myAgent.getLocalName() + " - FollowMe Behaviour initialized");
        this.groupMembers = new ArrayList<>();
        this.groupMembersTasks = new ArrayList<>();
        this.previousObservations = new ArrayList<>();

        // Generate a fixed visibility list
        this.membersVisibility = new int[3];
        for(int i=0; i < 3; i++){
            membersVisibility[i] = maxVisibility;
        }

    }

    public void setExpectedPath(List<String> expectedPath) {
        this.expectedPath = expectedPath;
        //System.out.println(this.myAgent.getLocalName() + " - FollowMe, expected destination updated to " + expectedDestination);
    }

    public void setExpectedDestination(String expectedDestination) {
        this.expectedDestination = expectedDestination;
        //System.out.println(this.myAgent.getLocalName() + " - FollowMe, expected destination updated to " + expectedDestination);
    }

    public void updateGroupMembers(List<AgentStatus> groupMembers)
    {
        // Generate visibility vector
        int[] newVisibility = new int[3];
        for(int i=0; i < groupMembers.size(); i++){
            newVisibility[i] = maxVisibility;
        }
        //System.out.println(Arrays.toString(newVisibility) + Arrays.toString(membersVisibility) + this.groupMembers.toString());
        if ((membersVisibility != null)&&(membersVisibility.length > 0)){
            for(int i=0; i < groupMembers.size(); i++){
                for(int j=0; j < this.groupMembers.size(); j++){
                    if(this.groupMembers.get(j).getLocalName().equals(groupMembers.get(i).getLocalName()))
                        newVisibility[i] = membersVisibility[j];
                }
            }
        }

        // Update parameters
        this.membersVisibility = newVisibility;
        this.groupMembers = groupMembers;
        //System.out.println(Arrays.toString(newVisibility) + Arrays.toString(membersVisibility) + this.groupMembers.toString());
    }

    /**************************************
     * Get data
     *************************************/

    public List<AgentStatus> getGroupMembers(){ return this.groupMembers; }
    public int[] getMembersVisibility(){ return this.membersVisibility; }
    public List<Couple<String, String[]>> getGroupMembersTasks(){ return this.groupMembersTasks; }


    /**************************************
     * Periodical behaviour
     *************************************/

    @Override
    public void onTick() {

        // Check if there are members in the group
        if(this.groupMembers.size() > 0)
        {
            // Generate the list of observation IDs
            List<Couple<String, List<Couple<Observation, Integer>>>> Obs = ((AbstractDedaleAgent) this.myAgent).observe();
            List<String> Obslist = new ArrayList<>();
            for (Couple<String, List<Couple<Observation, Integer>>> o : Obs)
                Obslist.add(o.getLeft());

            // Add Observation to list
            if ((previousObservations.size() < 1) ||
                    (!previousObservations.get(previousObservations.size() - 1).get(0).equals(Obslist.get(0))))
                previousObservations.add(Obslist);

            // Check if expected destination is out of date
            if (this.expectedDestination != null) {
                for(List<String> prev_ob: this.previousObservations)
                    if (prev_ob.get(0).equals(this.expectedDestination)) {
                        expectedDestination = null;
                        break;
                    }
            }

            // Compute the path to the expected destination
            /*
            if (this.expectedDestination != null) {
                expectedPath = null;
                try{
                    expectedPath = myMap.getShortestPath(((AbstractDedaleAgent) this.myAgent).getCurrentPosition(), expectedDestination);
                }catch (Exception ignored) {}
            }*/

            // Generate list of viable paths
            List<List<String>> membersPaths = new ArrayList<>();
            for (int i = 0; i < 8; i++) // Iterations to previous nodes (if i=4, it will return the paths from the 4 previous steps)
            {
                //Verify that we have these observations
                if (previousObservations.size() > i) {
                    // Add only current location
                    if (i == 0) {
                        List<String> p = new ArrayList<>();
                        //if (expectedPath != null) // Copy the expected path
                        //    p.addAll(expectedPath);

                        p.add(0,previousObservations.get(previousObservations.size() - 1).get(0));
                        membersPaths.add(p);
                    }

                    // Check all the observations of that step
                    for (String o : previousObservations.get(previousObservations.size() - 1 - i)) {

                        if (!o.equals(previousObservations.get(previousObservations.size() - 1 - i).get(0))) {
                            // Generate the path from the leader to that node
                            List<String> p = new ArrayList<>();
                            //if (expectedPath != null) // Copy the expected path
                            //    p.addAll(expectedPath);

                            for (int j = 0; j < (i + 1); j++) // Add the future nodes
                                p.add(0, previousObservations.get(previousObservations.size() - 1 - j).get(0));

                            // Add the farthest node
                            p.add(0, o);

                            if (p.size() > 2) {
                                // Add the path to the list avoiding some non-direct paths
                                if (!o.equals(p.get(p.size() - 3)))
                                    membersPaths.add(p);
                            }
                            // Add the path to the list (i=0)
                            else
                                membersPaths.add(p);
                        }
                    }
                }
            }

            // Parse paths
            StringBuilder serializedPaths = new StringBuilder();
            for (List<String> p : membersPaths)
                serializedPaths.append(p.toString()).append("/"); // Append all the paths in a string
            String serPaths = serializedPaths.toString();
            serPaths = serPaths.substring(0, serPaths.length() - 1); // Remove the last /

            // Prepare Following request
            ACLMessage followingRequest = new ACLMessage(ACLMessage.REQUEST);
            followingRequest.setProtocol("FOLLOW-ME");
            if (expectedDestination == null)
                followingRequest.setContent(serPaths);
            else
                followingRequest.setContent(serPaths + "#" + expectedDestination);
            for (AgentStatus m : groupMembers)
                followingRequest.addReceiver(new AID(m.getLocalName(), AID.ISLOCALNAME));

            myAgent.addBehaviour(new DedaleAchieveREInitiator(myAgent, followingRequest) {

                // Handle group members inform
                protected void handleInform(ACLMessage inform) {

                    // Process content
                    String content = inform.getContent();
                    String[] cnt = content.split("#");
                    AgentStatus agentStatus = new AgentStatus(cnt[0]);

                    // Update visibility
                    for(int i=0; i < groupMembers.size(); i++){
                        if (groupMembers.get(i).getLocalName().equals(agentStatus.getLocalName())){
                            groupMembers.set(i, agentStatus);
                            membersVisibility[i] = maxVisibility;
                        }
                    }

                    // Update group member tasks
                    String[] tasksLists = cnt[1].split("/");
                    boolean taskExist = false;
                    Couple<String, String[]> mtasks = new Couple<>(agentStatus.getLocalName(), tasksLists);
                    for(int i=0; i < groupMembersTasks.size(); i++){
                        if (groupMembersTasks.get(i).getLeft().equals(agentStatus.getLocalName())){
                            taskExist = true;
                            groupMembersTasks.set(i, mtasks);
                        }
                    }
                    if(!taskExist)
                        groupMembersTasks.add(mtasks);

                }
            });

            // Update visibility (decay)
            if (membersVisibility != null){
                if (membersVisibility.length > 0){
                    for(int i=0;i < membersVisibility.length; i++){
                        if (membersVisibility[i] > 0)
                            membersVisibility[i] -= 1;
                    }
                }
            }
        }

    }

}
