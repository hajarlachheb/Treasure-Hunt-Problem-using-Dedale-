package eu.su.mas.dedaleEtu.mas.additional_elements;

import java.util.ArrayList;
import java.util.List;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.startMyBehaviours;

import jade.core.behaviours.Behaviour;

/**
 * This example class start a Dummy agent that will possess two behaviours :
 * <ol>
 * <li> move randomly and test the API methods {@link RandomWalkBehaviour}.
 * <li> send a meaningless message to two other agents {@link SayHelloBehaviour} 
 * </ol>
 * @author hc
 *
 */
public class DummyMovingAgent extends AbstractDedaleAgent{

	private static final long serialVersionUID = -2991562876411096907L;
	

	/**
	 * This method is automatically called when "agent".start() is executed.
	 * Consider that Agent is launched for the first time. 
	 * 			1) set the agent attributes 
	 *	 		2) add the behaviours
	 *          
	 */
	protected void setup(){
		super.setup();

		//get the parameters given into the object[]
		final Object[] args = getArguments();
		System.out.println("Arg given by the user to "+this.getLocalName()+": "+args[2]);
		
		//use them as parameters for your behaviours is you want
		
		List<Behaviour> lb=new ArrayList<Behaviour>();
		
		/************************************************
		 * 
		 * ADD the inititial behaviours of the Dummy Moving Agent here
		 * 
		 ************************************************/
		lb.add(new RandomWalkBehaviour(this));
		lb.add(new SayHelloBehaviour(this));
		
		
		/***
		 * MANDATORY TO ALLOW YOUR AGENT TO BE DEPLOYED CORRECTLY
		 */
		
		addBehaviour(new startMyBehaviours(this,lb));

	}


	/**
	 * This method is automatically called after doDelete()
	 */
	protected void takeDown(){
		super.takeDown();
	}
	
	/**
	 * This method is automatically called before migration. 
	 * You can add here all the saving you need
	 */
	protected void beforeMove(){
		super.beforeMove();
	}
	
	/**
	 * This method is automatically called after migration to reload. 
	 * You can add here all the info regarding the state you want your agent to restart from 
	 * 
	 */
	protected void afterMove(){
		super.afterMove();
	}

}