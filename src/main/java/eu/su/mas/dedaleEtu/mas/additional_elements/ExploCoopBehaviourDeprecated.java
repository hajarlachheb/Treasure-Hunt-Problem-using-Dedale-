package eu.su.mas.dedaleEtu.mas.additional_elements;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.FollowMeBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.protocols.DedaleAchieveREInitiator;
import jade.core.AID;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


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
public class ExploCoopBehaviourDeprecated extends SimpleBehaviour {

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
	private String expectedDestination;

	// Status Parameters
	private int openSafesCount;

	private List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
	private List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
	private List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
	private List<Couple<String,List<Couple<Observation,Integer>>>> Collected; // Treasures already collected


	// Group Formation Parameters
	private boolean inGroup;
	private List<String> GroupMembers;
	private List<String> allReceivers;

	private FollowMeBehaviour followMe;

/**
 *
 * @param myagent
 * @param myMap known map of the world the agent is living in
 * @param agentNames name of the agents to share the map with
 */
	public ExploCoopBehaviourDeprecated(final AbstractDedaleAgent myagent, MapRepresentation myMap, List<String> agentNames) {
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

		// Group Formation
		this.inGroup = false;
		this.GroupMembers = new ArrayList<>();
		GroupMembers.add(this.myAgent.getName());
		allReceivers = new ArrayList<>();
		allReceivers.addAll(AgentAttributes.Explorers);
		allReceivers.addAll(AgentAttributes.Collectors);
		allReceivers.addAll(AgentAttributes.Tankers);
		allReceivers.add("Coordinator1");
		allReceivers.remove(myAgent.getLocalName());
	}

	@Override
	public void action() {

		/*********************************
		 * ENVIRONMENT DATA COLLECTION
		 ********************************/

		// Generate the map if null
		if(this.myMap==null) {
			this.myMap= new MapRepresentation();
			this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent,300,this.myMap,list_agentNames));
			followMe = new FollowMeBehaviour(this.myAgent, 10, this.myMap);
			this.myAgent.addBehaviour(followMe);
		}

		// Retrieve the current position
		String myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();
		expectedDestination = null;

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
				this.myAgent.doWait(1000);
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
				if (myPosition!=nodeId) {
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
			 * EXPLORATION
			 ********************************/

			if (!this.myMap.hasOpenNode())
			{
				//Explo finished
				//finished=true;
				System.out.println(this.myAgent.getLocalName()+" - Exploration successufully done, behaviour removed.");
			}
			else {
				// Search the closest open node when there is no a directly reachable open node
				if (nextNode == null) {
					List<String> mypath = this.myMap.getShortestPathToClosestOpenNode(myPosition);
					nextNode = mypath.get(0);
					expectedDestination = mypath.get(mypath.size() -1);
				}

			}

			// In case of get stuck
			if (this.StuckIter > 5)
			{
				this.StuckRecover = 10;
			}
			if ((this.StuckIter > 5)||(this.StuckRecover > 0)) {
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

				//
				this.StuckRecover--;
			}


			/*********************************
			 * GROUP FORMATION VS GROUP FORMED
			 ********************************/

			switch (currentGroupState) {
				case GROUPING:
					//groupFormationMechanism();
					break;
				case FORMED:
					//TODO: Open Safe with coordination
					//TODO: Member Delegation
					//TODO: Task Assignment
					//TODO: Group Meeting
					break;

			}


			/*********************************
			 * AGENT MOVEMENT
			 ********************************/

			/*
			if (this.expectedDestination == null) {
				this.expectedDestination = nextNode;
				this.followMe.setExpectedPath(nextNode);
			}*/


			//TODO: Agent Blockage.
			// You can use the variables myPosition (agent's position), lobs (surrounding nodes), previousPositions
			// For messaging, you can use ACLMessage

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

	public void groupFormationMechanism()
	{

		ACLMessage formationRequest = new ACLMessage(ACLMessage.REQUEST);
		formationRequest.setProtocol("GROUP-FORMATION");
		formationRequest.setContent("Abogadroooooooor");
		for (String agentName : allReceivers) {
			formationRequest.addReceiver(new AID(agentName,AID.ISLOCALNAME));
		}
		myAgent.addBehaviour( new DedaleAchieveREInitiator(myAgent, formationRequest) {
			protected void handleInform(ACLMessage inform) {
				System.out.println("Protocol finished. Rational Effect achieved. " +
						"Received the following message: "+inform);
			}
		});

		MessageTemplate mt =
				AchieveREResponder.createMessageTemplate("GROUP-FORMATION");
		myAgent.addBehaviour( new AchieveREResponder(myAgent, mt) {

			/*
			protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage
					response) {
				System.out.println("Responder has received the following message: " +
						request);
				ACLMessage informDone = request.createReply();
				informDone.setPerformative(ACLMessage.INFORM);
				informDone.setContent("inform done" + openSafesCount);
				return informDone;
			}*/

			@Override
			protected ACLMessage prepareResponse(ACLMessage request){
				System.out.println("Responder (Response) has received the following message: " +
						request);
				ACLMessage informDone = request.createReply();
				informDone.setPerformative(ACLMessage.INFORM);
				informDone.setContent("inform done AAAAAA" + openSafesCount);
				return informDone;
			}
		});
	}

}