package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.List;

/**
 * The agent periodically share its map.
 * It blindly tries to send all its graph to its friend(s)  	
 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

 * @author hc
 *
 */
public class ShareTaskBehaviour extends TickerBehaviour{
	private List<String> receivers;

	private final List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen; // Safes to open
	private final List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes; // Safes opened to remove opened safes from previous list
	private final List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect; // Treasures to collect (the safe is already open)
	private final List<Couple<String,List<Couple<Observation,Integer>>>> Collected; // Treasures already collected

	/**
	 * The agent periodically share its map.
	 * It blindly tries to send all its graph to its friend(s)
	 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

	 * @param a the agent
	 * @param period the periodicity of the behaviour (in ms)
	 * @param receivers the list of agents to send the map to
	 */
	public ShareTaskBehaviour(Agent a, long period, List<String> receivers,
							  List<Couple<String,List<Couple<Observation,Integer>>>> ToOpen,
							  List<Couple<String,List<Couple<Observation,Integer>>>> OpenedSafes,
							  List<Couple<String,List<Couple<Observation,Integer>>>> ToCollect,
							  List<Couple<String,List<Couple<Observation,Integer>>>> Collected) {
		super(a, period);
		this.receivers=receivers;
		this.ToOpen = ToOpen;
		this.ToCollect = ToCollect;
		this.OpenedSafes = OpenedSafes;
		this.Collected = Collected;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -568863390879327961L;

	@Override
	protected void onTick() {
		//4) At each time step, the agent blindly send all its graph to its surrounding to illustrate how to share its knowledge (the topology currently) with the the others agents. 	
		// If it was written properly, this sharing action should be in a dedicated behaviour set, the receivers be automatically computed, and only a subgraph would be shared.
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.setProtocol("SHARE-TASKS");
		msg.setSender(this.myAgent.getAID());
		for (String agentName : receivers) {
			msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
		}
		msg.setContent(ToOpen.toString() +"/" + OpenedSafes.toString() + "/"+ ToCollect.toString() + "/" + Collected.toString());
		((AbstractDedaleAgent)this.myAgent).sendMessage(msg);

		/********************
		 * MESSAGE RECEPTION
		 *******************/

		// Check if he received a graph from a teammate. The sending operation is made in ShareMapBehaviour
		MessageTemplate msgTemplate = MessageTemplate.and(
				MessageTemplate.MatchProtocol("SHARE-TASKS"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
		if (msgReceived != null) {
			String content = msgReceived.getContent();

			// Merge lists of tasks
			String[] tasksLists = content.split("/");
			List<Couple<String,List<Couple<Observation,Integer>>>> toOpen = AgentAttributes.observationsDeserializer(tasksLists[0]);
			List<Couple<String,List<Couple<Observation,Integer>>>> openSafes = AgentAttributes.observationsDeserializer(tasksLists[1]);
			List<Couple<String,List<Couple<Observation,Integer>>>> toCollect = AgentAttributes.observationsDeserializer(tasksLists[2]);
			List<Couple<String,List<Couple<Observation,Integer>>>> collected = AgentAttributes.observationsDeserializer(tasksLists[3]);
			taskListMerge(toOpen, openSafes, toCollect, collected);
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

		//System.out.println(this.myAgent.getLocalName() + " - Merged tasks from group members | To Open: "
		//		+ ToOpen.toString() + "| Opened: " + OpenedSafes.toString() + "| To Collect: " + ToCollect.toString() + "| Collected: " + Collected.toString());

	}

}
