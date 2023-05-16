package eu.su.mas.dedaleEtu.mas.behaviours;

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

import java.io.IOException;
import java.util.List;

/**
 * The agent periodically share its map.
 * It blindly tries to send all its graph to its friend(s)  	
 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

 * @author hc
 *
 */
public class ExploInformBehaviour extends TickerBehaviour{

	private List<String> receivers;

	/**
	 * The agent periodically share its map.
	 * It blindly tries to send all its graph to its friend(s)
	 * If it was written properly, this sharing action would NOT be in a ticker behaviour and only a subgraph would be shared.

	 * @param a the agent
	 * @param period the periodicity of the behaviour (in ms)
	 * @param receivers the list of agents to send the map to
	 */
	public ExploInformBehaviour(Agent a, long period, List<String> receivers) {
		super(a, period);
		this.receivers=receivers;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -568863390879327961L;

	@Override
	protected void onTick() {
		// Send a message informing of its presence
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.setProtocol("EXPLORER-HERE");
		msg.setSender(this.myAgent.getAID());
		for (String agentName : receivers) {
			msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
		}
		msg.setContent("I'M-HERE");
		((AbstractDedaleAgent)this.myAgent).sendMessage(msg);

	}

}
