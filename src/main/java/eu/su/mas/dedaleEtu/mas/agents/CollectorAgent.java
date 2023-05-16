package eu.su.mas.dedaleEtu.mas.agents;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.startMyBehaviours;
import eu.su.mas.dedaleEtu.mas.behaviours.CollectorBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.CollectorBehaviourV2;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CollectorAgent extends AbstractDedaleAgent {

    /**
     *
     */
    private static final long serialVersionUID = -1784844593772918359L;



    /**
     * This method is automatically called when "agent".start() is executed.
     * Consider that Agent is launched for the first time.
     * 			1) set the agent attributes
     *	 		2) add the behaviours
     *
     */
    protected void setup(){

        super.setup();

        List<Behaviour> lb=new ArrayList<Behaviour>();
        lb.add(new CollectorBehaviourV2(this));

        addBehaviour(new startMyBehaviours(this,lb));

        System.out.println("the  agent "+this.getLocalName()+ " is started");

    }



    /**
     * This method is automatically called after doDelete()
     */
    protected void takeDown(){

    }


}
