package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.List;

import dataStructures.serializableGraph.SerializableSimpleGraph;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/**
 * The agent periodically share its map.
 * It blindly tries to send all its graph to its friend(s)  	
 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

 * @author hc
 *
 */
public class ShareMapBehaviour extends TickerBehaviour{
	
	private MapRepresentation myMap;
	private List<String> receivers;

	/**
	 * The agent periodically share its map.
	 * It blindly tries to send all its graph to its friend(s)  	
	 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

	 * @param a the agent
	 * @param period the periodicity of the behaviour (in ms)
	 * @param mymap (the map to share)
	 * @param receivers the list of agents to send the map to
	 */
	public ShareMapBehaviour(Agent a, long period,MapRepresentation mymap, List<String> receivers) {
		super(a, period);
		this.myMap=mymap;
		this.receivers=receivers;
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
		msg.setProtocol("SHARE-TOPO");
		msg.setSender(this.myAgent.getAID());
		for (String agentName : receivers) {
			msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
		}
			
		SerializableSimpleGraph<String, MapAttribute> sg=this.myMap.getSerializableGraph();
		try {					
			msg.setContentObject(sg);
		} catch (IOException e) {
			e.printStackTrace();
		}
		((AbstractDedaleAgent)this.myAgent).sendMessage(msg);

		/********************
		 * MESSAGE RECEPTION
		 *******************/

		// Check if he received a graph from a teammate. The sending operation is made in ShareMapBehaviour
		MessageTemplate msgTemplate = MessageTemplate.and(
				MessageTemplate.MatchProtocol("SHARE-TOPO"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
		if (msgReceived != null) {
			SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
			try {
				sgreceived = (SerializableSimpleGraph<String, MapAttribute>) msgReceived.getContentObject();
			} catch (UnreadableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.myMap.mergeMap(sgreceived);
			//System.out.println(this.myAgent.getLocalName() + " - MapMerged");
		}

		
	}

}
