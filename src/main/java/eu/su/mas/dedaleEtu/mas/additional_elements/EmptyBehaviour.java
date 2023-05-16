package eu.su.mas.dedaleEtu.mas.additional_elements;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;

public class EmptyBehaviour extends TickerBehaviour {

    private static final long serialVersionUID = 9088209402507795289L;

    public EmptyBehaviour(final AbstractDedaleAgent myagent) {
        super(myagent, 150);
    }

    @Override
    public void onTick() {

    }
}
