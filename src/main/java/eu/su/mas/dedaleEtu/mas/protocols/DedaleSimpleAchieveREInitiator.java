package eu.su.mas.dedaleEtu.mas.protocols;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import jade.util.leap.Iterator;

import java.util.Date;
import java.util.Vector;

public class DedaleSimpleAchieveREInitiator extends SimpleBehaviour {
    public final String REQUEST_KEY;
    public final String REQUEST_SENT_KEY;
    public final String SECOND_REPLY_KEY;
    public final String ALL_RESPONSES_KEY;
    public final String ALL_RESULT_NOTIFICATIONS_KEY;
    private MessageTemplate mt;
    private int state;
    private boolean finished;
    private long timeout;
    private long endingTime;
    private final Logger logger;

    public DedaleSimpleAchieveREInitiator(Agent a, ACLMessage msg) {
        this(a, msg, new DataStore());
    }

    public DedaleSimpleAchieveREInitiator(Agent a, ACLMessage msg, DataStore store) {
        super(a);
        this.REQUEST_KEY = "_request" + this.hashCode();
        this.REQUEST_SENT_KEY = "_request_sent" + this.hashCode();
        this.SECOND_REPLY_KEY = "_2nd_reply" + this.hashCode();
        this.ALL_RESPONSES_KEY = "_all-responses" + this.hashCode();
        this.ALL_RESULT_NOTIFICATIONS_KEY = "_all-result-notification" + this.hashCode();
        this.mt = null;
        this.state = 0;
        this.timeout = -1L;
        this.endingTime = 0L;
        this.logger = Logger.getMyLogger(this.getClass().getName());
        this.setDataStore(store);
        this.getDataStore().put(this.REQUEST_KEY, msg);
        this.finished = false;
    }

    @SuppressWarnings("unchecked")
    public final void action() {
        ACLMessage secondReply;
        DataStore ds;
        Vector<ACLMessage> allResNot;
        switch (this.state) {
            case 0:
                secondReply = this.prepareRequest((ACLMessage)this.getDataStore().get(this.REQUEST_KEY));
                this.getDataStore().put(this.REQUEST_SENT_KEY, secondReply);
                this.state = 1;
                break;
            case 1:
                ds = this.getDataStore();
                ACLMessage request = (ACLMessage)ds.get(this.REQUEST_SENT_KEY);
                if (request == null) {
                    this.finished = true;
                } else {
                    String conversationID;
                    if (request.getConversationId() == null) {
                        conversationID = "C" + this.hashCode() + "_" + System.currentTimeMillis();
                        request.setConversationId(conversationID);
                    } else {
                        conversationID = request.getConversationId();
                    }

                    this.mt = MessageTemplate.MatchConversationId(conversationID);
                    Iterator receivers = request.getAllReceiver();
                    AID r = (AID)receivers.next();
                    request.clearAllReceiver();
                    request.addReceiver(r);
                    if (receivers.hasNext() && this.logger.isLoggable(Logger.WARNING)) {
                        this.logger.log(Logger.WARNING, "The message you are sending has more than one receivers. The message will be sent only to the first one !!");
                    }

                    if (r.equals(this.myAgent.getAID())) {
                        this.mt = MessageTemplate.and(this.mt, MessageTemplate.not(MessageTemplate.MatchCustom(request, true)));
                    }

                    Date d = request.getReplyByDate();
                    if (d != null) {
                        this.timeout = d.getTime() - (new Date()).getTime();
                    } else {
                        this.timeout = -1L;
                    }

                    this.endingTime = System.currentTimeMillis() + this.timeout;
                    this.myAgent.send(request);
                    this.state = 2;
                }
                break;
            case 2:
                secondReply = this.myAgent.receive(this.mt);
                if (secondReply != null) {
                    ds = this.getDataStore();
                    switch (secondReply.getPerformative()) {
                        case 1:
                            this.state = 3;
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESPONSES_KEY);
                            allResNot.addElement(secondReply);
                            this.handleAgree(secondReply);
                            this.handleAllResponses((Vector<ACLMessage>)this.getDataStore().get(this.ALL_RESPONSES_KEY));
                            return;
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 8:
                        case 9:
                        case 11:
                        case 12:
                        case 13:
                        default:
                            this.state = 2;
                            this.handleOutOfSequence(secondReply);
                            return;
                        case 6:
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESULT_NOTIFICATIONS_KEY);
                            allResNot.addElement(secondReply);
                            this.state = 5;
                            this.handleFailure(secondReply);
                            return;
                        case 7:
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESULT_NOTIFICATIONS_KEY);
                            allResNot.addElement(secondReply);
                            this.state = 5;
                            this.handleInform(secondReply);
                            return;
                        case 10:
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESPONSES_KEY);
                            allResNot.addElement(secondReply);
                            this.state = 4;
                            this.handleNotUnderstood(secondReply);
                            return;
                        case 14:
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESPONSES_KEY);
                            allResNot.addElement(secondReply);
                            this.state = 4;
                            this.handleRefuse(secondReply);
                    }
                } else if (this.timeout > 0L) {
                    long blockTime = this.endingTime - System.currentTimeMillis();
                    if (blockTime <= 0L) {
                        this.state = 4;
                    } else {
                        this.block(blockTime);
                    }
                } else {
                    this.block();
                }
                break;
            case 3:
                secondReply = this.myAgent.receive(this.mt);
                if (secondReply != null) {
                    ds = this.getDataStore();
                    switch (secondReply.getPerformative()) {
                        case 6:
                            this.state = 5;
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESULT_NOTIFICATIONS_KEY);
                            allResNot.addElement(secondReply);
                            this.handleFailure(secondReply);
                            return;
                        case 7:
                            this.state = 5;
                            allResNot = (Vector<ACLMessage>)ds.get(this.ALL_RESULT_NOTIFICATIONS_KEY);
                            allResNot.addElement(secondReply);
                            this.handleInform(secondReply);
                            return;
                        default:
                            this.state = 2;
                            this.handleOutOfSequence(secondReply);
                    }
                } else {
                    this.block();
                }
                break;
            case 4:
                this.state = 5;
                this.handleAllResponses(((Vector<ACLMessage>)this.getDataStore().get(this.ALL_RESPONSES_KEY)));
                break;
            case 5:
                this.finished = true;
                this.handleAllResultNotifications(((Vector<ACLMessage>)this.getDataStore().get(this.ALL_RESULT_NOTIFICATIONS_KEY)));
        }

    }

    public void onStart() {
        this.initializeDataStore();
    }

    public boolean done() {
        return this.finished;
    }

    protected ACLMessage prepareRequest(ACLMessage msg) {
        return msg;
    }

    protected void handleAgree(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINE)) {
            this.logger.log(Logger.FINE, "in HandleAgree: " + msg.toString());
        }

    }

    protected void handleRefuse(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINE)) {
            this.logger.log(Logger.FINE, "in HandleRefuse: " + msg.toString());
        }

    }

    protected void handleNotUnderstood(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINE)) {
            this.logger.log(Logger.FINE, "in HandleNotUnderstood: " + msg.toString());
        }

    }

    protected void handleInform(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINE)) {
            this.logger.log(Logger.FINE, "in HandleInform: " + msg.toString());
        }

    }

    protected void handleFailure(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINEST)) {
            this.logger.log(Logger.FINEST, "in HandleFailure: " + msg.toString());
        }

    }

    protected void handleOutOfSequence(ACLMessage msg) {
        if (this.logger.isLoggable(Logger.FINEST)) {
            this.logger.log(Logger.FINEST, "in HandleOutOfSequence: " + msg.toString());
        }

    }

    protected void handleAllResponses(Vector<ACLMessage> ignoredMsgs) {
        if (this.logger.isLoggable(Logger.FINEST)) {
            this.logger.log(Logger.FINEST, this.myAgent.getName() + "in handleAllResponses: ");
        }

    }

    protected void handleAllResultNotifications(Vector<ACLMessage> ignoredMsgs) {
        if (this.logger.isLoggable(Logger.FINEST)) {
            this.logger.log(Logger.FINEST, this.myAgent.getName() + "in HandleAllResultNotification: ");
        }

    }

    public void reset() {
        this.reset(null);
    }

    public void reset(ACLMessage msg) {
        this.finished = false;
        this.state = 0;
        this.getDataStore().put(this.REQUEST_KEY, msg);
        this.initializeDataStore();
        super.reset();
    }

    private void initializeDataStore() {
        Vector<ACLMessage> l = new Vector<>();
        this.getDataStore().put(this.ALL_RESPONSES_KEY, l);
        l = new Vector<>();
        this.getDataStore().put(this.ALL_RESULT_NOTIFICATIONS_KEY, l);
    }
}
