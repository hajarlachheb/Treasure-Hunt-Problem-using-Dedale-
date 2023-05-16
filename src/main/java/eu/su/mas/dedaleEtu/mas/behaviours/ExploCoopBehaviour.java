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


/**
 * <pre>
 * This behaviour allows an agent to explore the environment and learn the associated topological map.
 * The algorithm is a pseudo - DFS computationally consuming because its not optimised at all.
 * 
 * When all the nodes around him are visited, the agent randomly select an open node and go there to restart its dfs. 
 * This (non optimal) behaviour is done until all nodes are explored. 
 * 
 * Warning, this behaviour does not save the content of visited nodes, only the topology.
 * Warning, the sub-behaviour ShareMap periodically share the whole map
 * </pre>
 * @author hc
 *
 */
public class ExploCoopBehaviour extends SimpleBehaviour {

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
	private int backIter;
	private int desistIter;
	private int StuckRecover;
	private enum GroupState {GROUPING, FORMED}
	private GroupState currentGroupState;

	// Status Parameters
	private int openSafesCount;

	private final List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
	private final List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
	private final List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
	private final List<Couple<String,List<Couple<Observation,Integer>>>> Collected; // Treasures already collected


	// Group Formation Parameters
	private GroupType myGroupType;
	private List<AgentStatus> GroupMembers;
	private AgentStatus myStatus;

	// Intra-Group Communication
	private FollowMeBehaviour followMe;
	private List<String> expectedPath;

	// Task Selection

	private Couple<String,List<Couple<Observation,Integer>>> taskSelected;
	private boolean performTask;
	private List<Couple<String, String>> members2sendPath;
	private boolean waiting2sendPath;

/**
 * 
 * @param myMap known map of the world the agent is living in
 * @param agentNames name of the agents to share the map with
 */
	public ExploCoopBehaviour(final AbstractDedaleAgent myagent, MapRepresentation myMap,List<String> agentNames) {
		super(myagent);
		this.myMap=myMap;
		this.list_agentNames=agentNames;

		// IMAS Initialization
		this.currentGroupState = GroupState.GROUPING;
		this.previousPositions = new ArrayList<>();
		this.StuckIter = 0;
		this.StuckRecover = 0;

		// Status Parameters
		this.openSafesCount = 0;
		this.ToCollect = new ArrayList<>();
		this.OpenedSafes = new ArrayList<>();
		this.ToOpen = new ArrayList<>();
		this.Collected = new ArrayList<>();

		// Intra-Group Communication
		this.backIter = 0;
		this.desistIter = 0;
		this.performTask = false;
		this.waiting2sendPath = false;
		this.members2sendPath = new ArrayList<>();

		// Inter-Group Communication
		expectedPath = new ArrayList<>();


	}

	@Override
	public void action() {

		/*********************************
		 * ENVIRONMENT DATA COLLECTION
		 ********************************/

		// Generate the map if null
		if(this.myMap==null) {
			this.myMap= new MapRepresentation();
			this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent,200,this.myMap,list_agentNames));

			// Group Formation
			this.myStatus = new AgentStatus(myAgent.getLocalName(), AgentType.EXPLORER, Observation.NO_TREASURE,0);
			this.GroupMembers = new ArrayList<>();
			if (AgentAttributes.Explorers.get(0).equals(myAgent.getLocalName())) {
				this.myGroupType = GroupType.HYBRID;
				System.out.println(myAgent.getLocalName() + " became the leader of the HYBRID group");
			}
			else if (AgentAttributes.Explorers.get(1).equals(myAgent.getLocalName())) {
				this.myGroupType = GroupType.GOLD;
				System.out.println(myAgent.getLocalName() + " became the leader of the GOLD group");
			}
			else if (AgentAttributes.Explorers.get(2).equals(myAgent.getLocalName())) {
				this.myGroupType = GroupType.DIAMOND;
				System.out.println(myAgent.getLocalName() + " became the leader of the DIAMOND group");
			}
			else
				System.out.println(myAgent.getLocalName() + " - This explorer is not listed. There is no group type assigned.");

			// Following calls
			followMe = new FollowMeBehaviour(this.myAgent, 200, this.myMap);
			this.myAgent.addBehaviour(followMe);
		}

		// Retrieve the current position
		String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();
		expectedPath = null;

		if (myPosition!=null){

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
			List<Couple<String,List<Couple<Observation,Integer>>>> lobs=((AbstractDedaleAgent)this.myAgent).observe();//myPosition

			// Wait a defined time
			try {
				this.myAgent.doWait(200);
			} catch (Exception e) {
				e.printStackTrace();
			}

			/*********************************
			 * MAP UPDATES (THIS AGENT)
			 ********************************/

			//1) remove the current node from openlist and add it to closedNodes.
			this.myMap.addNode(myPosition, MapAttribute.closed);

			//2) get the surrounding nodes and, if not in closedNodes, add them to open nodes.
			String nextNode=null;
			Iterator<Couple<String, List<Couple<Observation, Integer>>>> iter=lobs.iterator();
			List<String> nNodes = new ArrayList<>();
			while(iter.hasNext()){

				String nodeId=iter.next().getLeft();

				boolean isNewNode=this.myMap.addNewNode(nodeId);
				//the node may exist, but not necessarily the edge
				if (!myPosition.equals(nodeId)) {
					this.myMap.addEdge(myPosition, nodeId);
					//if (isNewNode) {
					//	nNodes.add(nodeId);
						//System.out.println(this.myAgent.getLocalName()+" - New node found");
					//}
					if (nextNode == null && isNewNode)
						nextNode = nodeId;
				}
			}
			// Assign next node randomly
			/*
			if (nNodes.size() > 1)
			{
				Random r = new Random();
				int randval = r.nextInt(nNodes.size()-1);
				nextNode = nNodes.get(randval);
			}
			else if (nNodes.size() == 1)
				nextNode = nNodes.get(0);*/

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
				boolean inList = false;
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
			 * TASK MERGING
			 ********************************/

			taskMergingGroupMembers();

			/*********************************
			 * TASK SELECTION
			 ********************************/

			if (!performTask){
				// Select one of the tasks
				for(Couple<String,List<Couple<Observation, Integer>>> task: this.ToOpen){
					// Collect all the parameters
					Observation treasureType = null;
					int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

					for(Couple<Observation,Integer> o:task.getRight()){
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

					// Consider some restrictions to select the task
					// Type of treasure
					if ((myGroupType.equals(GroupType.DIAMOND) && Objects.equals(treasureType, Observation.DIAMOND)) ||
							(myGroupType.equals(GroupType.GOLD) && Objects.equals(treasureType, Observation.GOLD)) ||
							myGroupType.equals(GroupType.HYBRID)){

						boolean haveCollector = false;
						if(GroupMembers.size() > 0){
							for(AgentStatus as: GroupMembers){
								if(as.getAgentType().equals(AgentType.COLLECTOR))
									haveCollector = true;
							}
						}

						// Picking capabilities
						if (((lockPicking < 3)&&(strength < 3))|| haveCollector){
							//Reachability
							try{
								nextNode = myMap.getShortestPath(myPosition, task.getLeft()).get(0);
								performTask = true;
								taskSelected = task;
								System.out.println(this.myAgent.getLocalName() + " - Selected task: " + taskSelected);
								break;
							}catch (Exception ignored){}
						}
					}
				}
				// Select one of the tasks
				for(Couple<String,List<Couple<Observation, Integer>>> task: this.ToCollect){
					// Collect all the parameters
					Observation treasureType = null;
					int amount = -1, lockStatus = -1, strength = -1, lockPicking = -1;

					for(Couple<Observation,Integer> o:task.getRight()){
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

					// Consider some restrictions to select the task
					// Type of treasure
					if ((myGroupType.equals(GroupType.DIAMOND) && Objects.equals(treasureType, Observation.DIAMOND)) ||
							(myGroupType.equals(GroupType.GOLD) && Objects.equals(treasureType, Observation.GOLD)) ||
							myGroupType.equals(GroupType.HYBRID)){

						boolean haveCollector = false;
						if(GroupMembers.size() > 0){
							for(int i=0; i < GroupMembers.size(); i++){
								AgentStatus as = GroupMembers.get(i);
								if(((as.getTreasureType().equals(Observation.DIAMOND)&&Objects.equals(treasureType, Observation.DIAMOND))||
										(as.getTreasureType().equals(Observation.GOLD)&&Objects.equals(treasureType, Observation.GOLD)))
										&&as.getAgentType().equals(AgentType.COLLECTOR)){
									// Check that the agent is visible for the explorer
									int[] memVisibility = followMe.getMembersVisibility();
									if(memVisibility[i] > 0)
										haveCollector = true;
								}

							}
						}

						// Valid collector
						if (haveCollector){
							//Reachability
							try{
								nextNode = myMap.getShortestPath(myPosition, task.getLeft()).get(0);
								performTask = true;
								taskSelected = task;
								System.out.println(this.myAgent.getLocalName() + " - Selected task: " + taskSelected);
								break;
							}catch (Exception ignored){}
						}
					}
				}
			}
			/*********************************
			 * TASK ASSIGNMENT
			 ********************************/
			if (performTask){
				taskAssignmentMembers();
			}


			/*********************************
			 * EXPLORATION
			 ********************************/

			// Perform the task
			if (performTask) {
				try{
					nextNode = this.myMap.getShortestPath(myPosition, taskSelected.getLeft()).get(0);
				}catch (Exception e){}

				if(taskSelected.getLeft().equals(myPosition)){
					System.out.println(this.myAgent.getLocalName() + " - Reached to the treasure selected " + taskSelected);
					performTask = false;
				}
			}
			else if (!this.myMap.hasOpenNode())
			{
				//Explo finished
				//finished=true;
				System.out.println(this.myAgent.getLocalName()+" - Exploration successufully done");
			}
			else {
				// Search the closest open node when there is no a directly reachable open node
				if (nextNode == null) {
					List<String> mypath = this.myMap.getShortestPathToClosestOpenNode(myPosition);
					nextNode = mypath.get(0);
					expectedPath = mypath;
				}

			}

			// In case of get stuck
			if (this.StuckIter > 5)
				this.StuckRecover = 15;

			if ((this.StuckIter > 5)||(this.StuckRecover > 0)||(nextNode == null)) {

				// Random movement
				nextNode = randomMovement(lobs);

				// Update stuck recover
				if (StuckRecover > 0)
					this.StuckRecover--;

				// Update expected path
				expectedPath = new ArrayList<>();
				expectedPath.add(nextNode);
			}


			/*********************************
			 * GROUP FORMATION VS GROUP FORMED
			 ********************************/

			switch (currentGroupState) {
				case GROUPING:
					groupFormationMechanism();

					// Update group members
					List<AgentStatus> prevMembers = this.followMe.getGroupMembers();
					for (int i=0; i < this.GroupMembers.size(); i++){
						for (int j=0; j < prevMembers.size(); j++){
							if (prevMembers.get(j).getLocalName().equals(this.GroupMembers.get(i).getLocalName()))
								this.GroupMembers.set(i, prevMembers.get(j));
						}
					}
					this.followMe.updateGroupMembers(this.GroupMembers);

					break;
				case FORMED:

					// Update group members (it is supposed that the group do not change)
					this.GroupMembers = this.followMe.getGroupMembers();

					//TODO: Member Delegation
					//TODO: Task Assignment
					//TODO: Group Meeting
					break;

			}

			if((GroupMembers.size() > 1)&&(currentGroupState == GroupState.GROUPING)){
				currentGroupState = GroupState.FORMED;
				System.out.println(this.myAgent.getLocalName() + " - Group formed: " + GroupMembers.get(0).getLocalName() + " - " + GroupMembers.get(1).getLocalName());
			}

			/*********************************
			 * AGENT MOVEMENT
			 ********************************/

			// Send the expected path to other nodes
			this.followMe.setExpectedPath(expectedPath);

			// Check if the other agents are visible or not
			int[] membersVisibility = followMe.getMembersVisibility();
			boolean noLosses = true;
			if (this.GroupMembers.size() > 0) {
				noLosses = false;
				for (int i = 0; i < this.GroupMembers.size(); i++) {
					if (membersVisibility[i] > 0) {
						noLosses = true;
						break;
					}
				}
			}

			// Wait if the explorer lost all the agents
			if (noLosses){
				backIter = 8;
				desistIter = 8;
			}
			else if ((!performTask)&&(desistIter > 0)) {
				System.out.println(this.myAgent.getLocalName() + " - Waiting for a group member");

				// Go back to meet with the lost agent
				if ((previousPositions.size() > 1)&&(backIter > 0)&&(backIter < 6)) {
					if (previousPositions.get(previousPositions.size() - 1).equals(myPosition))
						previousPositions.remove(previousPositions.size() - 1);
					nextNode = previousPositions.get(previousPositions.size() - 1);
					System.out.println(this.myAgent.getLocalName() + " - Going back (" + backIter +")");
				}
				// Reduce the back iter counter
				if (backIter > 0) {
					backIter--;

				}
				// Reduce the waiting counter before desisting
				else if (desistIter > 0){
					desistIter--;
				}
			}

			// Move
			if ((nextNode != null)&&(!waiting2sendPath))
			{
				((AbstractDedaleAgent) this.myAgent).moveTo(nextNode);
			}
		}
	}

	@Override
	public boolean done() {
		return finished;
	}

	/*********************************************
	 * GROUP FORMATION MECHANISM
	 ********************************************/

	public void groupFormationMechanism()
	{
		// Receive group formation requests
		MessageTemplate mt =
				AchieveREResponder.createMessageTemplate("GROUP-FORMATION");
		myAgent.addBehaviour( new AchieveREResponder(myAgent, mt) {

			@Override
			protected ACLMessage prepareResponse(ACLMessage request){

				// Process request
				String content = request.getContent();
				AgentStatus senderStatus = new AgentStatus(content);

				// Check if it is a valid member
				boolean validMember = false;
				switch (myGroupType)
				{
					case HYBRID:
						if (senderStatus.getAgentType() == AgentType.COLLECTOR)
							validMember = true;
						break;
					case GOLD:
						if ((senderStatus.getAgentType() == AgentType.TANKER)||
								((senderStatus.getAgentType() == AgentType.COLLECTOR)&&
										(senderStatus.getTreasureType() == Observation.GOLD)))
							validMember = true;
						break;
					case DIAMOND:
						if ((senderStatus.getAgentType() == AgentType.TANKER)||
								((senderStatus.getAgentType() == AgentType.COLLECTOR)&&
										(senderStatus.getTreasureType() == Observation.DIAMOND)))
							validMember = true;
						break;
					default:
						break;
				}

				// Check if the agent type already exist
				for(AgentStatus as: GroupMembers){
					if ((as.getAgentType() == senderStatus.getAgentType()) &&
							(as.getTreasureType() == senderStatus.getTreasureType())) {
						validMember = false;
						break;
					}
				}

				// Accept/Reject the member
				String informContent;
				if (validMember){
					GroupMembers.add(senderStatus);
					informContent = "ACCEPTED/" + myStatus.SerializeContent();
					String gm = "->";
					for(AgentStatus as: GroupMembers)
						gm += as.getLocalName() + " ";
					System.out.println(this.myAgent.getLocalName() + " - accepted "  + senderStatus.getLocalName() + " in his group (" + gm + ")");
				}
				else {
					informContent = "REJECTED/" + myStatus.SerializeContent();
					String gm = "->";
					for(AgentStatus as: GroupMembers)
						gm += as.getLocalName() + " ";
					System.out.println(this.myAgent.getLocalName() + " - rejected "  + senderStatus.getLocalName() + " from his group (" + gm + ")");
				}

				// Generate inform
				ACLMessage informDone = request.createReply();
				informDone.setPerformative(ACLMessage.INFORM);
				informDone.setContent(informContent);
				return informDone;
			}
		});
	}

	/**************************************************
	 * RANDOM MOVEMENT
	 *************************************************/

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
				System.out.println(this.myAgent.getLocalName() + " - Stuck: Obs: " + lobs+ "| Viable paths:" + lobs2select);

			//Define next node with a random function
			int moveId = 0;
			if(lobs2select.size() > 1) {
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

	/**************************************************
	 * TASK MERGING
	 *************************************************/
	public void taskMergingGroupMembers(){
		// Merge task from group members
		if(GroupMembers.size() > 0)
		{
			List<Couple<String, String[]>> groupMemberTasks = followMe.getGroupMembersTasks();
			//System.out.print(this.myAgent.getLocalName() + " - Tasks updates from group members: ");
			//for (Couple<String, String[]> gmt: groupMemberTasks){
			//	System.out.print("<" + gmt.getLeft() + "| ");
			//	for(String t: gmt.getRight())
			//		System.out.print(t + ", ");
			//	System.out.print(">");
			//}
			//System.out.println();

			//Check the message received for each task
			for (Couple<String, String[]> gmt: groupMemberTasks){
				List<Couple<String, List<Couple<Observation, Integer>>>> toOpen =  AgentAttributes.observationsDeserializer(gmt.getRight()[0]);
				List<Couple<String, List<Couple<Observation, Integer>>>> opened =  AgentAttributes.observationsDeserializer(gmt.getRight()[0]);
				List<Couple<String, List<Couple<Observation, Integer>>>> toCollect =  AgentAttributes.observationsDeserializer(gmt.getRight()[0]);
				List<Couple<String, List<Couple<Observation, Integer>>>> collected =  AgentAttributes.observationsDeserializer(gmt.getRight()[0]);

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

			//System.out.println(this.myAgent.getLocalName() + " - Merged tasks from group members | To Open: "
			//		+ ToOpen.toString() + "| Opened: " + OpenedSafes.toString() + "| To Collect: " + ToCollect.toString() + "| Collected: " + Collected.toString());

		}
	}

	/*********************************************
	 * TASK ASSIGNMENT (INTRA-GROUP)
	 ********************************************/
	/*
	public void taskAssignmentMembers(){
		// Task Assignment request
		ACLMessage taskRequest = new ACLMessage(ACLMessage.REQUEST);
		taskRequest.setProtocol("TASK");
		taskRequest.setContent("A");
		for (AgentStatus m : this.GroupMembers)
			taskRequest.addReceiver(new AID(m.getLocalName(), AID.ISLOCALNAME));

		myAgent.addBehaviour(new DedaleContractNetInitiator(myAgent, taskRequest) {

			protected void handlePropose(ACLMessage propose, java.util.Vector acceptances) {



			}

			protected void handleInform(ACLMessage inform) {



			}
		});
	}*/

	public void taskAssignmentMembers(){
		// Prepare task assignment request
		ACLMessage taskAssignRequest = new ACLMessage(ACLMessage.REQUEST);
		taskAssignRequest.setProtocol("TASK-ASSIGN");
		taskAssignRequest.setContent("ASSIGN");
		for (AgentStatus m : GroupMembers)
			taskAssignRequest.addReceiver(new AID(m.getLocalName(), AID.ISLOCALNAME));

		myAgent.addBehaviour(new DedaleAchieveREInitiator(myAgent, taskAssignRequest) {

			// Handle group members inform
			protected void handleInform(ACLMessage inform) {
				String content = inform.getContent();
				String[] params = content.split("/");
				Couple<String, String> mem = new Couple<>(params[0],params[1]);
				members2sendPath.add(mem);
				System.out.println(this.myAgent.getLocalName() + " - Member location received: " + mem.toString());
				waiting2sendPath = true;
			}
		});

		for(Couple<String,String> member2path: members2sendPath) {

			// Prepare request with the agent's path
			ACLMessage taskPathRequest = new ACLMessage(ACLMessage.REQUEST);
			taskPathRequest.setProtocol("TASK-PATH");
			List<String> path = null;
			try {
				path = this.myMap.getShortestPath(member2path.getRight(), taskSelected.getLeft());
				System.out.println(this.myAgent.getLocalName() + " - Path " + path + " sent to " + member2path.getLeft());
			}
			catch (Exception e){
				System.out.println(this.myAgent.getLocalName() + " - Cannot compute the path for " + member2path.getLeft());
			}

			if (path == null)
				taskPathRequest.setContent("NONE");
			else
				taskPathRequest.setContent(path.toString());

			taskPathRequest.addReceiver(new AID(member2path.getLeft(), AID.ISLOCALNAME));

			myAgent.addBehaviour(new DedaleAchieveREInitiator(myAgent, taskPathRequest) {

				// Handle group members inform
				protected void handleInform(ACLMessage inform) {
					waiting2sendPath = false;
				}
			});

		}

		members2sendPath.clear();

	}

}