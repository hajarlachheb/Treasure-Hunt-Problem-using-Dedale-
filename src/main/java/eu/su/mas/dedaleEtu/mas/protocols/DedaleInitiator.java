package eu.su.mas.dedaleEtu.mas.protocols;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.states.MsgReceiver;
import jade.util.leap.HashMap;
import jade.util.leap.Iterator;
import jade.util.leap.Map;

import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

abstract class DedaleInitiator extends FSMBehaviour {
    protected final String INITIATION_K = "__initiation" + this.hashCode();;
    protected final String ALL_INITIATIONS_K = "__all-initiations" + this.hashCode();
    protected final String REPLY_K = "__reply" + this.hashCode();
    protected Map sessions;
    protected MsgReceiver replyReceiver;
    protected MessageTemplate replyTemplate;
    private ACLMessage initiation;
    private static int cnt = 0;

    protected DedaleInitiator(Agent a, ACLMessage initiation, DataStore store) {
        super(a);
        this.sessions = new HashMap();
        this.replyReceiver = null;
        this.replyTemplate = null;
        this.setDataStore(store);
        this.initiation = initiation;
        this.registerDefaultTransition("Prepare-initiations", "Send-initiations");
        this.registerTransition("Send-initiations", "Dummy-final", 0);
        this.registerDefaultTransition("Send-initiations", "Receive-reply");
        this.registerTransition("Receive-reply", "Check-sessions", -1001);
        this.registerTransition("Receive-reply", "Check-sessions", -1002);
        this.registerDefaultTransition("Receive-reply", "Check-in-seq");
        this.registerTransition("Check-in-seq", "Handle-not-understood", 10);
        this.registerTransition("Check-in-seq", "Handle-failure", 6);
        this.registerDefaultTransition("Check-in-seq", "Handle-out-of-seq");
        this.registerDefaultTransition("Handle-not-understood", "Check-sessions");
        this.registerDefaultTransition("Handle-failure", "Check-sessions");
        this.registerDefaultTransition("Handle-out-of-seq", "Receive-reply");
        this.registerDefaultTransition("Check-sessions", "Receive-reply", this.getToBeReset());
        Behaviour b;
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818000L;

            public void action() {
                DataStore ds = this.getDataStore();
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> allInitiations = (Vector<ACLMessage>)ds.get(DedaleInitiator.this.ALL_INITIATIONS_K);
                if (allInitiations == null || allInitiations.size() == 0) {
                    allInitiations = DedaleInitiator.this.prepareInitiations((ACLMessage)ds.get(DedaleInitiator.this.INITIATION_K));
                    ds.put(DedaleInitiator.this.ALL_INITIATIONS_K, allInitiations);
                }

            }
        };
        b.setDataStore(this.getDataStore());
        this.registerFirstState(b, "Prepare-initiations");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818001L;

            public void action() {
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> allInitiations = (Vector<ACLMessage>)this.getDataStore().get(DedaleInitiator.this.ALL_INITIATIONS_K);
                if (allInitiations != null) {
                    DedaleInitiator.this.sendInitiations(allInitiations);
                }

            }

            public int onEnd() {
                return DedaleInitiator.this.sessions.size();
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Send-initiations");
        this.replyReceiver = new MsgReceiver(this.myAgent, (MessageTemplate)null, -1L, this.getDataStore(), this.REPLY_K);
        this.registerState(this.replyReceiver, "Receive-reply");
        b = new OneShotBehaviour(this.myAgent) {
            int ret;
            private static final long serialVersionUID = 3487495895818002L;

            public void action() {
                ACLMessage reply = (ACLMessage)this.getDataStore().get(DedaleInitiator.this.REPLY_K);
                if (DedaleInitiator.this.checkInSequence(reply)) {
                    this.ret = reply.getPerformative();
                } else {
                    this.ret = -1;
                }

            }

            public int onEnd() {
                return this.ret;
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Check-in-seq");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818005L;

            public void action() {
                DedaleInitiator.this.handleNotUnderstood((ACLMessage)this.getDataStore().get(DedaleInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-not-understood");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818007L;

            public void action() {
                DedaleInitiator.this.handleFailure((ACLMessage)this.getDataStore().get(DedaleInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-failure");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818008L;

            public void action() {
                DedaleInitiator.this.handleOutOfSequence((ACLMessage)this.getDataStore().get(DedaleInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-out-of-seq");
        b = new OneShotBehaviour(this.myAgent) {
            int ret;
            private static final long serialVersionUID = 3487495895818009L;

            public void action() {
                ACLMessage reply = (ACLMessage)this.getDataStore().get(DedaleInitiator.this.REPLY_K);
                this.ret = DedaleInitiator.this.checkSessions(reply);
            }

            public int onEnd() {
                return this.ret;
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Check-sessions");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818010L;

            public void action() {
            }
        };
        this.registerLastState(b, "Dummy-final");
    }

    protected abstract Vector<ACLMessage> prepareInitiations(ACLMessage var1);

    protected abstract boolean checkInSequence(ACLMessage var1);

    protected abstract int checkSessions(ACLMessage var1);

    protected abstract String[] getToBeReset();

    protected abstract DedaleInitiator.ProtocolSession getSession(ACLMessage var1, int var2);

    protected void sendInitiations(Vector<ACLMessage> initiations) {
        long currentTime = System.currentTimeMillis();
        long minTimeout = -1L;
        long deadline = -1L;
        String conversationID = this.createConvId(initiations);
        this.replyTemplate = MessageTemplate.MatchConversationId(conversationID);
        int cnt = 0;
        Vector<ACLMessage> sentMessages = new Vector<>();
        Enumeration<ACLMessage> e = initiations.elements();

        while(true) {
            Date d;
            long timeout;
            do {
                do {
                    do {
                        ACLMessage initiation;
                        do {
                            if (!e.hasMoreElements()) {
                                this.getDataStore().put(this.ALL_INITIATIONS_K, sentMessages);
                                this.replyReceiver.setTemplate(this.replyTemplate);
                                this.replyReceiver.setDeadline(deadline);
                                return;
                            }

                            initiation = (ACLMessage)e.nextElement();
                        } while(initiation == null);

                        Iterator receivers = initiation.getAllReceiver();

                        while(receivers.hasNext()) {
                            ACLMessage toSend = (ACLMessage)initiation.clone();
                            toSend.setConversationId(conversationID);
                            toSend.clearAllReceiver();
                            AID r = (AID)receivers.next();
                            toSend.addReceiver(r);
                            DedaleInitiator.ProtocolSession ps = this.getSession(toSend, cnt);
                            if (ps != null) {
                                String sessionKey = ps.getId();
                                if (sessionKey == null) {
                                    sessionKey = "R" + System.currentTimeMillis() + "_" + cnt;
                                }

                                toSend.setReplyWith(sessionKey);
                                this.sessions.put(sessionKey, ps);
                                this.adjustReplyTemplate(toSend);
                                ++cnt;
                            }
                            toSend.setSender(myAgent.getAID());
                            ((AbstractDedaleAgent) this.myAgent).sendMessage(toSend);
                            sentMessages.addElement(toSend);
                        }

                        d = initiation.getReplyByDate();
                    } while(d == null);

                    timeout = d.getTime() - currentTime;
                } while(timeout <= 0L);
            } while(timeout >= minTimeout && minTimeout > 0L);

            minTimeout = timeout;
            deadline = d.getTime();
        }
    }

    protected void handleNotUnderstood(ACLMessage ignoredNotUnderstood) {
    }

    protected void handleFailure(ACLMessage failure) {
    }

    protected void handleOutOfSequence(ACLMessage ignoredMsg) {
    }

    protected void registerPrepareInitiations(Behaviour b) {
        this.registerState(b, "Prepare-initiations");
        b.setDataStore(this.getDataStore());
    }

    public void reset() {
        this.reset(null);
    }

    public void reset(ACLMessage msg) {
        this.initiation = msg;
        this.reinit();
        super.reset();
    }

    protected void reinit() {
        this.replyReceiver.reset((MessageTemplate)null, -1L, this.getDataStore(), this.REPLY_K);
        this.sessions.clear();
        DataStore ds = this.getDataStore();
        ds.remove(this.INITIATION_K);
        ds.remove(this.ALL_INITIATIONS_K);
        ds.remove(this.REPLY_K);
    }

    public void onStart() {
        this.initializeDataStore(this.initiation);
    }

    public void setDataStore(DataStore ds) {
        super.setDataStore(ds);
        Iterator it = this.getChildren().iterator();

        while(it.hasNext()) {
            Behaviour b = (Behaviour)it.next();
            b.setDataStore(ds);
        }

    }

    protected void initializeDataStore(ACLMessage initiation) {
        this.getDataStore().put(this.INITIATION_K, initiation);
    }

    protected String createConvId(Vector<ACLMessage> msgs) {
        String convId = null;
        if (msgs.size() > 0) {
            ACLMessage msg = msgs.elementAt(0);
            if (msg != null && msg.getConversationId() != null) {
                convId = msg.getConversationId();
            } else {
                convId = "C" + this.hashCode() + "_" + this.myAgent.getLocalName() + "_" + System.currentTimeMillis() + "_" + getCnt();
            }
        }

        return convId;
    }

    private static synchronized int getCnt() {
        return cnt++;
    }

    protected void adjustReplyTemplate(ACLMessage msg) {
        AID r = (AID)msg.getAllReceiver().next();
        if (this.myAgent.getAID().equals(r)) {
            this.replyTemplate = MessageTemplate.and(this.replyTemplate, MessageTemplate.not(MessageTemplate.MatchCustom(msg, true)));
        }

    }

    protected interface ProtocolSession {
        String getId();

        boolean update(int var1);

        int getState();

        boolean isCompleted();
    }
}
