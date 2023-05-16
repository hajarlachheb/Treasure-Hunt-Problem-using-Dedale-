package eu.su.mas.dedaleEtu.mas.protocols;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.util.leap.ArrayList;
import jade.util.leap.Iterator;
import jade.util.leap.List;
import jade.util.leap.Serializable;

import java.util.Vector;

public class DedaleAchieveREInitiator extends DedaleInitiator {
    public final String REQUEST_KEY;
    public final String ALL_REQUESTS_KEY;
    public final String REPLY_KEY;
    public final String ALL_RESPONSES_KEY;
    public final String ALL_RESULT_NOTIFICATIONS_KEY;
    private boolean allResponsesReceived;
    private String[] toBeReset;

    public DedaleAchieveREInitiator(Agent a, ACLMessage msg) {
        this(a, msg, new DataStore());
    }

    public DedaleAchieveREInitiator(Agent a, ACLMessage msg, DataStore store) {
        super(a, msg, store);
        this.REQUEST_KEY = this.INITIATION_K;
        this.ALL_REQUESTS_KEY = this.ALL_INITIATIONS_K;
        this.REPLY_KEY = this.REPLY_K;
        this.ALL_RESPONSES_KEY = "__all-responses" + this.hashCode();
        this.ALL_RESULT_NOTIFICATIONS_KEY = "__all-result-notifications" + this.hashCode();
        this.allResponsesReceived = false;
        this.toBeReset = null;
        this.registerTransition("Check-in-seq", "Handle-agree", 1);
        this.registerTransition("Check-in-seq", "Handle-inform", 7);
        this.registerTransition("Check-in-seq", "Handle-refuse", 14);
        this.registerDefaultTransition("Handle-agree", "Check-sessions");
        this.registerDefaultTransition("Handle-inform", "Check-sessions");
        this.registerDefaultTransition("Handle-refuse", "Check-sessions");
        this.registerTransition("Check-sessions", "Handle-all-responses", 1);
        this.registerTransition("Check-sessions", "Handle-all-result-notifications", 2);
        this.registerDefaultTransition("Handle-all-responses", "Check-again");
        this.registerTransition("Check-again", "Handle-all-result-notifications", 0);
        this.registerDefaultTransition("Check-again", "Receive-reply", this.toBeReset);
        Behaviour b;
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818003L;

            public void action() {
                DedaleAchieveREInitiator.this.handleAgree((ACLMessage)this.getDataStore().get(DedaleAchieveREInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-agree");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818004L;

            public void action() {
                DedaleAchieveREInitiator.this.handleRefuse((ACLMessage)this.getDataStore().get(DedaleAchieveREInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-refuse");
        b = new OneShotBehaviour(this.myAgent) {
            private static final long serialVersionUID = 3487495895818006L;

            public void action() {
                DedaleAchieveREInitiator.this.handleInform((ACLMessage)this.getDataStore().get(DedaleAchieveREInitiator.this.REPLY_K));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-inform");
        b = new OneShotBehaviour(this.myAgent) {
            @SuppressWarnings("unchecked")
            public void action() {
                DedaleAchieveREInitiator.this.handleAllResponses((Vector<ACLMessage>)this.getDataStore().get(DedaleAchieveREInitiator.this.ALL_RESPONSES_KEY));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Handle-all-responses");
        b = new OneShotBehaviour(this.myAgent) {
            @SuppressWarnings("unchecked")
            public void action() {
                DedaleAchieveREInitiator.this.handleAllResultNotifications((Vector<ACLMessage>)this.getDataStore().get(DedaleAchieveREInitiator.this.ALL_RESULT_NOTIFICATIONS_KEY));
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerLastState(b, "Handle-all-result-notifications");
        b = new OneShotBehaviour(this.myAgent) {
            public void action() {
            }

            public int onEnd() {
                return DedaleAchieveREInitiator.this.sessions.size();
            }
        };
        b.setDataStore(this.getDataStore());
        this.registerState(b, "Check-again");
    }

    protected Vector<ACLMessage> prepareInitiations(ACLMessage initiation) {
        return this.prepareRequests(initiation);
    }

    @SuppressWarnings("unchecked")
    protected boolean checkInSequence(ACLMessage reply) {
        String inReplyTo = reply.getInReplyTo();
        DedaleAchieveREInitiator.Session s = (DedaleAchieveREInitiator.Session)this.sessions.get(inReplyTo);
        if (s != null) {
            int perf = reply.getPerformative();
            if (s.update(perf)) {
                switch (s.getState()) {
                    case 1:
                    case 2:
                        Vector<ACLMessage> allRsp = (Vector<ACLMessage>)this.getDataStore().get(this.ALL_RESPONSES_KEY);
                        allRsp.addElement(reply);
                        break;
                    case 3:
                        Vector<ACLMessage> allNotif = (Vector<ACLMessage>)this.getDataStore().get(this.ALL_RESULT_NOTIFICATIONS_KEY);
                        allNotif.addElement(reply);
                        break;
                    default:
                        return false;
                }

                if (s.isCompleted()) {
                    this.sessions.remove(inReplyTo);
                }

                return true;
            }
        }

        return false;
    }

    protected int checkSessions(ACLMessage reply) {
        int ret = -1;
        if (this.getLastExitValue() == -1001 && !this.allResponsesReceived) {
            List sessionsToRemove = new ArrayList(this.sessions.size());
            Iterator i = this.sessions.keySet().iterator();

            while(i.hasNext()) {
                Object key = i.next();
                DedaleAchieveREInitiator.Session s = (DedaleAchieveREInitiator.Session)this.sessions.get(key);
                if (s.getState() == 0) {
                    sessionsToRemove.add(key);
                }
            }

            i = sessionsToRemove.iterator();

            while(i.hasNext()) {
                this.sessions.remove(i.next());
            }

        } else if (reply == null) {
            this.sessions.clear();
        }

        if (!this.allResponsesReceived) {
            this.allResponsesReceived = true;
            Iterator it = this.sessions.values().iterator();

            while(it.hasNext()) {
                DedaleAchieveREInitiator.Session s = (DedaleAchieveREInitiator.Session)it.next();
                if (s.getState() == 0) {
                    this.allResponsesReceived = false;
                    break;
                }
            }

            if (this.allResponsesReceived) {
                this.replyReceiver.setDeadline(-1L);
                ret = 1;
            }
        } else if (this.sessions.size() == 0) {
            ret = 2;
        }

        return ret;
    }

    protected String[] getToBeReset() {
        if (this.toBeReset == null) {
            this.toBeReset = new String[]{"Handle-agree", "Handle-refuse", "Handle-not-understood", "Handle-inform", "Handle-failure", "Handle-out-of-seq"};
        }

        return this.toBeReset;
    }

    protected Vector<ACLMessage> prepareRequests(ACLMessage request) {
        Vector<ACLMessage> l = new Vector<>(1);
        l.addElement(request);
        return l;
    }

    protected void handleAgree(ACLMessage ignoredAgree) {
    }

    protected void handleRefuse(ACLMessage refuse) {
    }

    protected void handleInform(ACLMessage inform) {
    }

    protected void handleAllResponses(Vector<? extends ACLMessage> ignoredResponses) {
    }

    protected void handleAllResultNotifications(Vector<ACLMessage> resultNotifications) {
    }

    protected void reinit() {
        this.allResponsesReceived = false;
        super.reinit();
    }

    protected void initializeDataStore(ACLMessage msg) {
        super.initializeDataStore(msg);
        Vector<ACLMessage> l = new Vector<>();
        this.getDataStore().put(this.ALL_RESPONSES_KEY, l);
        l = new Vector<>();
        this.getDataStore().put(this.ALL_RESULT_NOTIFICATIONS_KEY, l);
    }

    protected DedaleInitiator.ProtocolSession getSession(ACLMessage msg, int sessionIndex) {
        return new DedaleAchieveREInitiator.Session();
    }

    private static class Session implements DedaleInitiator.ProtocolSession, Serializable {
        private int state;

        private Session() {
            this.state = 0;
        }

        public String getId() {
            return null;
        }

        public boolean update(int perf) {
            switch (this.state) {
                case 0:
                    switch (perf) {
                        case 1:
                            this.state = 1;
                            return true;
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
                            return false;
                        case 6:
                        case 7:
                            this.state = 3;
                            return true;
                        case 10:
                        case 14:
                            this.state = 2;
                            return true;
                    }
                case 1:
                    switch (perf) {
                        case 6:
                        case 7:
                            this.state = 3;
                            return true;
                        default:
                            return false;
                    }
                default:
                    return false;
            }
        }

        public int getState() {
            return this.state;
        }

        public boolean isCompleted() {
            return this.state == 2 || this.state == 3;
        }
    }
}
