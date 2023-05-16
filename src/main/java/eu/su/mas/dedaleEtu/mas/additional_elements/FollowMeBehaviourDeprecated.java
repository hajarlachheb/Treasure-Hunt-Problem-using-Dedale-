package eu.su.mas.dedaleEtu.mas.additional_elements;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.AgentAttributes;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.util.ArrayList;
import java.util.List;

public class FollowMeBehaviourDeprecated extends TickerBehaviour {
    /**
     * When an agent choose to migrate all its components should be serializable
     */
    private static final long serialVersionUID = -568863390879327961L;
    private MapRepresentation myMap;
    private String expectedDestination;

    public FollowMeBehaviourDeprecated(Agent myagent, long period, MapRepresentation myMap) {

        super(myagent, period);
        this.myMap = myMap;
        this.expectedDestination = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
        System.out.println(this.myAgent.getLocalName() + " - FollowMe Behaviour initialized");

    }

    public void setExpectedDestination(String expectedDestination) {
        this.expectedDestination = expectedDestination;
        //System.out.println(this.myAgent.getLocalName() + " - FollowMe, expected destination updated to " + expectedDestination);
    }


    @Override
    public void onTick() {
        //System.out.println(this.myAgent.getLocalName() + " - Follow me behaviour running " + expectedDestination);
        MessageTemplate mt =
                AchieveREResponder.createMessageTemplate("FOLLOW");
        myAgent.addBehaviour(new AchieveREResponder(myAgent, mt) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {
                //System.out.println("Responder (Response) has received the following message: " + request);

                // Get content of the message
                List<String> fromPos = AgentAttributes.listDeserializer(request.getContent());

                // Path to destination
                List<List<String>> paths = new ArrayList<>();
                for (String pos : fromPos) {
                    List<String> path = new ArrayList<>();
                    try {
                        path = myMap.getShortestPath(pos, expectedDestination);
                        if (!pos.equals(fromPos.get(0)))
                            path.add(0, pos);
                    } catch (Exception e) {
                        //System.out.print("Cannot compute the path -- ");
                    }
                    paths.add(path);
                }

                // Path to me
                List<String> path2me = new ArrayList<>();
                try {
                    path2me = myMap.getShortestPath(fromPos.get(0), ((AbstractDedaleAgent) this.myAgent).getCurrentPosition());
                } catch (Exception e) { //
                }
                System.out.println(this.myAgent.getLocalName() + " - Follow request received from "
                        + request.getSender().getName() + "(" + fromPos + "), my destination (" +
                        expectedDestination + ")" + path2me);

                // Serialize Content
                StringBuilder content = new StringBuilder();
                for (List<String> p : paths)
                    content.append(p.toString()).append("/");
                content.append("#").append(path2me.toString());

                // Send Response
                ACLMessage informPath = request.createReply();
                informPath.setPerformative(ACLMessage.INFORM);
                informPath.setContent(content.toString());
                return informPath;
            }
        });

    }

}
