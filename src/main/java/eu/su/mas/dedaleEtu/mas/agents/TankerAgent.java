package eu.su.mas.dedaleEtu.mas.agents;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.startMyBehaviours;
import eu.su.mas.dedaleEtu.mas.behaviours.TankerBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.TankerBehaviourV2;
import jade.core.behaviours.Behaviour;

import java.util.ArrayList;
import java.util.List;

public class TankerAgent extends AbstractDedaleAgent {

    private static final long serialVersionUID = -1784844593772918359L;

    protected void setup(){

        super.setup();

        // Add Behaviours
        List<Behaviour> lb=new ArrayList<Behaviour>();
        lb.add(new TankerBehaviourV2(this));

        addBehaviour(new startMyBehaviours(this,lb));

        System.out.println("the  agent "+this.getLocalName()+ " is started");

    }

    /**
     * This method is automatically called after doDelete()
     */
    protected void takeDown(){

    }
}
