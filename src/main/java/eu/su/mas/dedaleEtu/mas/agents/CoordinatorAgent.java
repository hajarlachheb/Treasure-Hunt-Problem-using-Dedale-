package eu.su.mas.dedaleEtu.mas.agents;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.startMyBehaviours;
import eu.su.mas.dedaleEtu.mas.behaviours.CoordinatorBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.CoordinatorBehaviourV2;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import jade.core.behaviours.Behaviour;

import java.util.ArrayList;
import java.util.List;

public class CoordinatorAgent extends AbstractDedaleAgent {

    private static final long serialVersionUID = -7969469610241668140L;
    private MapRepresentation myMap;


    /**
     * This method is automatically called when "agent".start() is executed.
     * Consider that Agent is launched for the first time.
     * 			1) set the agent attributes
     *	 		2) add the behaviours
     *
     */
    protected void setup(){

        super.setup();

        //get the parameters added to the agent at creation (if any)
        final Object[] args = getArguments();

        List<String> list_agentNames=new ArrayList<String>();

        if(args.length==0){
            System.err.println("Error while creating the agent, names of agent to contact expected");
            System.exit(-1);
        }else{
            int i=2;// WARNING YOU SHOULD ALWAYS START AT 2. This will be corrected in the next release.
            while (i<args.length) {
                list_agentNames.add((String)args[i]);
                i++;
            }
        }

        List<Behaviour> lb=new ArrayList<Behaviour>();

        /************************************************
         *
         * ADD the behaviours of the Dummy Moving Agent
         *
         ************************************************/

        lb.add(new CoordinatorBehaviourV2(this, this.myMap, list_agentNames));



        /***
         * MANDATORY TO ALLOW YOUR AGENT TO BE DEPLOYED CORRECTLY
         */


        addBehaviour(new startMyBehaviours(this,lb));

        System.out.println("the  agent "+this.getLocalName()+ " is started");

    }
}
