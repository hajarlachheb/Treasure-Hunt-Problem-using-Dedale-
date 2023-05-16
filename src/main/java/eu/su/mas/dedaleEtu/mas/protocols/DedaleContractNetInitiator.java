package eu.su.mas.dedaleEtu.mas.protocols;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.util.leap.Serializable;

import java.util.Vector;

public class DedaleContractNetInitiator extends DedaleInitiator {
    public final String ALL_ACCEPTANCES_KEY = "__all-acceptances" + this.hashCode();
    public final String ALL_RESPONSES_KEY = "__all-responses" + this.hashCode();
    public final String ALL_RESULT_NOTIFICATIONS_KEY = "__all-result-notifications" + this.hashCode();
    private int step;
    private boolean skipNextRespFlag;
    private boolean moreAcceptancesToSend;
    private String[] toBeReset;

    public DedaleContractNetInitiator(Agent a, ACLMessage cfp) {
        this(a, cfp, new DataStore());
    }

    public DedaleContractNetInitiator(Agent a, ACLMessage cfp, DataStore store) {
        super(a, cfp, store);
        this.step = 1;
        this.skipNextRespFlag = false;
        this.moreAcceptancesToSend = false;
        this.toBeReset = null;
        this.registerTransition("Check-in-seq", "Handle-propose", 11);
        this.registerTransition("Check-in-seq", "Handle-refuse", 14);
        this.registerTransition("Check-in-seq", "Handle-inform", 7);
        this.registerDefaultTransition("Handle-propose", "Check-sessions");
        this.registerDefaultTransition("Handle-refuse", "Check-sessions");
        this.registerDefaultTransition("Handle-inform", "Check-sessions");
        this.registerTransition("Check-sessions", "Handle-all-responses", 1);
        this.registerTransition("Check-sessions", "Handle-all-result-notifications", 2);
        this.registerDefaultTransition("Handle-all-responses", "Send-initiations", this.getToBeReset());
        this.registerTransition("Handle-all-result-notifications", "Send-initiations", 3, this.getToBeReset());
        this.registerDefaultTransition("Handle-all-result-notifications", "Dummy-final");
        Behaviour b = null;
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895819003L;

            public void action() {
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> acceptances = (Vector<ACLMessage>)this.getDataStore().get(DedaleContractNetInitiator.this.ALL_ACCEPTANCES_KEY);
                ACLMessage propose = (ACLMessage)this.getDataStore().get(DedaleContractNetInitiator.this.REPLY_K);
                DedaleContractNetInitiator.this.handlePropose(propose, acceptances);
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-propose");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895819004L;

            public void action() {
                DedaleContractNetInitiator.this.handleRefuse((ACLMessage)this.getDataStore().get(DedaleContractNetInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-refuse");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818006L;

            public void action() {
                DedaleContractNetInitiator.this.handleInform((ACLMessage)this.getDataStore().get(DedaleContractNetInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-inform");
        b = new OneShotBehaviour(this.myAgent) {
            public void action() {
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> responses = (Vector<ACLMessage>)this.getDataStore().get(DedaleContractNetInitiator.this.ALL_RESPONSES_KEY);
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> acceptances = (Vector<ACLMessage>)this.getDataStore().get(DedaleContractNetInitiator.this.ALL_ACCEPTANCES_KEY);
                DedaleContractNetInitiator.this.handleAllResponses(responses, acceptances);
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-all-responses");
        b = new OneShotBehaviour(this.myAgent) {
            @SuppressWarnings("unchecked")
            public void action() {
                DedaleContractNetInitiator.this.handleAllResultNotifications((Vector<ACLMessage>)this.getDataStore().get(DedaleContractNetInitiator.this.ALL_RESULT_NOTIFICATIONS_KEY));
            }

            public int onEnd() {
                return DedaleContractNetInitiator.this.moreAcceptancesToSend ? 3 : super.onEnd();
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-all-result-notifications");
    }

    protected Vector<ACLMessage> prepareInitiations(ACLMessage initiation) {
        return this.prepareCfps(initiation);
    }

    @SuppressWarnings("unchecked")
    protected void sendInitiations(Vector<ACLMessage> initiations) {
        if (this.step >= 2) {
            initiations = (Vector<ACLMessage>)this.getDataStore().get(this.ALL_ACCEPTANCES_KEY);
            if (this.moreAcceptancesToSend) {
                this.moreAcceptancesToSend = false;
                this.getDataStore().put(this.ALL_RESULT_NOTIFICATIONS_KEY, new Vector<>());
            }
        }

        super.sendInitiations(initiations);
    }

    protected boolean checkInSequence(ACLMessage reply) {
        boolean ret = false;
        String inReplyTo = reply.getInReplyTo();
        DedaleContractNetInitiator.Session s = (DedaleContractNetInitiator.Session)this.sessions.get(inReplyTo);
        if (s != null) {
            int perf = reply.getPerformative();
            if (s.update(perf)) {
                @SuppressWarnings("unchecked")
                Vector<ACLMessage> all = (Vector<ACLMessage>)this.getDataStore().get(this.step == 1 ? this.ALL_RESPONSES_KEY : this.ALL_RESULT_NOTIFICATIONS_KEY);
                all.addElement(reply);
                ret = true;
            }

            if (s.isCompleted()) {
                this.sessions.remove(inReplyTo);
            }
        }

        return ret;
    }

    protected int checkSessions(ACLMessage reply) {
        if (this.skipNextRespFlag) {
            this.sessions.clear();
        }

        int ret = this.step == 1 ? 1 : 2;
        if (reply != null) {
            if (this.sessions.size() > 0) {
                ret = -1;
            }
        } else {
            this.sessions.clear();
        }

        if (ret != -1) {
            ++this.step;
        }

        return ret;
    }

    protected String[] getToBeReset() {
        if (this.toBeReset == null) {
            this.toBeReset = new String[]{"Handle-propose", "Handle-refuse", "Handle-not-understood", "Handle-inform", "Handle-failure", "Handle-out-of-seq"};
        }

        return this.toBeReset;
    }

    protected Vector<ACLMessage> prepareCfps(ACLMessage cfp) {
        Vector<ACLMessage> v = new Vector<>(1);
        v.addElement(cfp);
        return v;
    }

    protected void handlePropose(ACLMessage propose, Vector<ACLMessage> acceptances) {
    }

    protected void handleRefuse(ACLMessage refuse) {
    }

    protected void handleInform(ACLMessage inform) {
    }

    protected void handleAllResponses(Vector<ACLMessage> responses, Vector<ACLMessage> acceptances) {
    }

    protected void handleAllResultNotifications(Vector<? extends ACLMessage> ignoredResultNotifications) {
    }

    protected void reinit() {
        this.step = 1;
        this.skipNextRespFlag = false;
        super.reinit();
    }

    protected void initializeDataStore(ACLMessage msg) {
        super.initializeDataStore(msg);
        DataStore ds = this.getDataStore();
        Vector<ACLMessage> l = new Vector<>();
        ds.put(this.ALL_RESPONSES_KEY, l);
        l = new Vector<>();
        ds.put(this.ALL_RESULT_NOTIFICATIONS_KEY, l);
        l = new Vector<>();
        ds.put(this.ALL_ACCEPTANCES_KEY, l);
    }

    protected DedaleInitiator.ProtocolSession getSession(ACLMessage msg, int sessionIndex) {
        if (msg.getPerformative() == 3) {
            return new Session(1);
        } else {
            return msg.getPerformative() == 0 ? new Session(2) : null;
        }
    }

    static class Session implements DedaleInitiator.ProtocolSession, Serializable {
        private int state = 0;
        private final int sessionStep;

        public Session(int s) {
            this.sessionStep = s;
        }

        public String getId() {
            return null;
        }

        public boolean update(int perf) {
            if (this.state == 0) {
                if (this.sessionStep == 1) {
                    switch (perf) {
                        case 6:
                        case 10:
                        case 11:
                        case 14:
                            this.state = 1;
                            return true;
                        case 7:
                        case 8:
                        case 9:
                        case 12:
                        case 13:
                        default:
                            return false;
                    }
                } else {
                    switch (perf) {
                        case 6:
                        case 7:
                        case 10:
                            this.state = 1;
                            return true;
                        case 8:
                        case 9:
                        default:
                            return false;
                    }
                }
            } else {
                return false;
            }
        }

        public int getState() {
            return this.state;
        }

        public boolean isCompleted() {
            return this.state == 1;
        }
    }
}
