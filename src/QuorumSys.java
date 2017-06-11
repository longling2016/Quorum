import sun.rmi.runtime.Log;

import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


public abstract class QuorumSys implements ProtocolMode, Runnable {
    QuorumNode quorumNodes[];
    Data data;
    ServerSocket ss;
    Lock lock;
    Info info;
    HashMap<Integer, Op4Coordinator> log4Coordinator;
    HashMap<QuorumNode,HashMap<Integer, Op4Node>> log4Node;
    Op4Coordinator curOp4Coordinator;
    Op4Node curOp4Node;
    Boolean isEnd;
    boolean isAlive;
    int quorumSeq;
    int threadsSize;
    boolean isCoodinator;
    QuorumNode myself;

    public QuorumSys(Address[] addressBook, Data data, ServerSocket ss, Lock lock, Info info) {
        this.quorumNodes = new QuorumNode[addressBook.length];
        for(int i = 0; i < addressBook.length; i++){
            this.quorumNodes[i] = new QuorumNode(addressBook[i]);
            String ip = null;
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            int port = ss.getLocalPort();
            if(ip.equals(addressBook[i].ip) && port == addressBook[i].port){
                myself = this.quorumNodes[i];
                LogSys.setLogSys(ip+":"+port,""+addressBook[i].hostID);
            }
        }
        this.data = data;
        this.ss = ss;
        this.lock = lock;
        this.info = info;
        this.log4Coordinator = new HashMap<>();
        this.log4Node = new HashMap<>();
        this.isAlive = true;
        this.quorumSeq = 0;
        this.isEnd = false;
        this.threadsSize = 3;
        this.isCoodinator = false;
        this.curOp4Coordinator = new Op4Coordinator();
        this.curOp4Node = new Op4Node();
    }

    public String getQuorumStatus4Coordinator(int coordinatorId, int qSequence) {

        QuorumNode coordinator = quorumNodes[coordinatorId];

        if(coordinator!=myself){
            return "GLOBAL_ABORT";
        }

        if(!curOp4Coordinator.status.equals("FREE") && curOp4Coordinator.getqSeq() == qSequence) {
            LogSys.debug("receive quorumStatus request, coordinator = "+myself.info.hostID + curOp4Coordinator.getValue());
            return curOp4Coordinator.status;
        }

        Op4Coordinator op = log4Coordinator.get(qSequence);
        if (op == null){
            return "GLOBAL_ABORT";
        }

        return op.status;
    }


    public String getQuorumStatus4Node(int coordinatorId, int qSequence) {

        QuorumNode coordinator = quorumNodes[coordinatorId];

        if(!curOp4Node.getStatus().equals("FREE") && curOp4Node.getQuorumInfra().coordinator == coordinator && curOp4Node.getqSeq() == qSequence){
            LogSys.debug("receive quorumStatus request, coordinator = " + curOp4Node.getQuorumInfra().coordinator.info.hostID + " value = " + curOp4Node.getValue());

            return curOp4Node.getStatus();
        }else {
            HashMap<Integer, Op4Node> logMap = log4Node.get(coordinator);
            if (logMap != null && logMap.get(qSequence)!=null) {
                return logMap.get(qSequence).getStatus();
            }
        }

        return "INIT";
    }




    public boolean isAlive(){
        return isAlive;
    }

    public boolean quorumApply(ArrayList<Integer> nodes, int value) {
        if(nodes.size() == 0 ){
            LogSys.debug("no quorum node in this op");
            return true;
        }
        int qSeq = quorumSeq++;
        ArrayList<QuorumNode> done = new ArrayList<>();
        String request = "apply,"+ qSeq +","+value+ ",";
        QuorumNode qNodes[] = new QuorumNode[nodes.size()];

        request += myself.info.hostID;
        int count = 0;
        for(int i = 0; i < nodes.size(); i++){
            request += ":"+nodes.get(i);
            qNodes[i] = quorumNodes[nodes.get(i)];
        }

        QuorumInfra infra = new QuorumInfra(myself, qNodes);
        curOp4Coordinator.opInitiate(infra,qSeq,value);

        for (int id : nodes) {
            QuorumNode node = quorumNodes[id];
            if (isNeed2Crash(info.crashRate)) {
                curOp4Coordinator.setStatus("GLOBAL_ABORT");
                LogSys.debug("coordinator crashed in apply phase");
                isAlive = false;
                info.ifCrash = true;
                for (QuorumNode n : done) {
                    LogSys.debug("send COORDINATOR_CRASH to "+n.info.hostID);
                    Util.sAr(n, "COORDINATOR_CRASH");
                }
                crashing();
                coordinatorRestart();

                return false;
            }

            String res = Util.sAr(node, request);
            if (res.equals("READY") ) {
                LogSys.debug("receive ready from node " + node.info.hostID +" "+res);
                done.add(node);
            } else {//might be abort or crashed
                LogSys.debug("quorumWrite failed because of node " + node.info.hostID +" "+res);
                for (QuorumNode n : done) {
                    Util.sAr(n, "GLOBAL_ABORT");
                }
                return false;
            }
        }


        return true;
    }

    public ArrayList<Integer> chooseQuorumNodeList(QuorumNode[] nodeList, int num) {

        Random rd = new Random();
        boolean isChosen[] = new boolean[nodeList.length];
        int count = 0;
        int aliveCount = nodeList.length;
        ArrayList<Integer> nodes = new ArrayList<>();

        while (count < num && aliveCount >= num) {
            int id = rd.nextInt(nodeList.length);
            if(id == myself.info.hostID) continue;
            QuorumNode node = nodeList[id];
            if (!isChosen[id]) {
                if (checkIsAlive(node)) {
                    nodes.add(id);
                    isChosen[id] = true;
                    count++;
                } else {
                    LogSys.debug("node " + node.info.hostID + "crashed");
                    isChosen[id] = true;
                    aliveCount--;
                }
            }
        }

        return nodes;
    }


    public boolean checkIsAlive(QuorumNode node) {

        String res = Util.sAr(node, "isAlive");
        if (res.equals("Destination_Host_Crashed") ) {
            return false;
        }
        return true;
    }

    public boolean isNeed2Crash(int probability){
        Random rd = new Random();

        int i = rd.nextInt(probability);
        if(i < 1){
            return true;
        }

        return false;
    }

    public void crashing(){

        try {
            //reparing
            Thread.sleep(info.crashDuration);
            //finished
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }




    public QuorumInfra parseQuorumInfra(String qInfo){
        String nodes[] = qInfo.split(":");

        QuorumNode coordinator = null;
        QuorumNode qNodes[] = new QuorumNode[nodes.length-1];
        boolean isCoordi = true;
        int count = 0;

        for(String node : nodes){
            QuorumNode qNode = quorumNodes[Integer.valueOf(node)];
            if(isCoordi){
                coordinator = qNode;
                isCoordi = false;
            }else{
                qNodes[count++] = qNode;
            }
        }

        return new QuorumInfra(coordinator, qNodes);
    }

    public void execute() {
        Thread server = new Thread(this);
        server.start();
    }


    public boolean quorumCommit() {
        ArrayList<QuorumNode> done = new ArrayList<>();
        curOp4Coordinator.setStatus("GLOBAL_COMMIT");
        for (QuorumNode node : curOp4Coordinator.getQuorumInfra().getQuorumNodes()) {
            if (isNeed2Crash(info.crashRate)) {
                LogSys.debug("crashed");
                isAlive = false;
                info.ifCrash = true;

                for (QuorumNode n : curOp4Coordinator.getQuorumInfra().getQuorumNodes()) {
                    Util.sAr(n, "COORDINATOR_CRASH");
                }
                crashing();
                coordinatorRestart();
                return true;
            }
            LogSys.debug("send quorumCommit to "+node.info.hostID);
            String res = Util.sAr(node, "quorumCommit");
            if (res.equals("COMMIT")) {
                done.add(node);
            } else if  (res.equals("crashed")){

                LogSys.debug("node "+ node.info.hostID +" crashed");
                continue;
            }
        }
        return true;
    }


    public String recvQuorumApply(QuorumInfra infra, int qSeq, int value, PrintStream out){
        synchronized (lock){
            if(lock.status) {
                LogSys.debug("lock conflict occurs");
                return "ABORT";
            }
            LogSys.debug("set lock because write "+ value +" from "+infra.coordinator.info.hostID+" seq =" + qSeq);
            lock.status = true;
        }

        if(infra == null){
            LogSys.debug("quorum Infra is null when received apply request");
            return "";
        }

        curOp4Node.opInitiate(infra,qSeq,value);
        if(isNeed2Crash(info.crashRate)){
            LogSys.debug("crashed");
            isAlive = false;
            info.ifCrash = true;

            out.println("crashed");

            crashing();
            qNodeRecover();
            return "";
        }
        return "READY";
    }

    public void quorumAbort(){
//        synchronized (lock) {
//            lock.status = false;
//            LogSys.debug("clear lock because write "+ curOp4Coordinator.getValue() +" from "+curOp4Coordinator.getQuorumInfra().coordinator.info.hostID);
//
//        }
        curOp4Coordinator.setStatus("GLOBAL_ABORT");
        log4Coordinator.put(curOp4Coordinator.getqSeq(), new Op4Coordinator(curOp4Coordinator));
        curOp4Coordinator.clear();

    }

    public void quorumComplete(){
        curOp4Coordinator.setStatus("GLOBAL_COMMIT");
        log4Coordinator.put(curOp4Coordinator.getqSeq(), new Op4Coordinator(curOp4Coordinator));
        curOp4Coordinator.clear();
    }

    public void quorumAbort4Node(){

        synchronized (lock) {
            lock.status = false;
            if(!curOp4Node.getStatus().equals("FREE")){
                LogSys.debug("clear lock because write "+ curOp4Node.getValue() +" abort from "+curOp4Node.getQuorumInfra().coordinator.info.hostID + " seq = " +curOp4Node.getqSeq());
            }else{
                LogSys.debug("current op in node is free");
            }
        }

        QuorumInfra infra = curOp4Node.getQuorumInfra();
        if(infra == null){
            LogSys.debug("qInfra is null");
            return;
        }

        QuorumNode coNode = infra.getCoordinator();
        if(coNode == null){
            LogSys.debug("node is null");
            return;
        }

        curOp4Node.setStatus("ABORT");

        HashMap<Integer,Op4Node> logMap = log4Node.get(coNode);
        if(logMap ==null){
            logMap = new HashMap<Integer,Op4Node>();
            log4Node.put(curOp4Node.getQuorumInfra().getCoordinator(), logMap);
        }
        logMap.put(curOp4Node.getqSeq(), new Op4Node(curOp4Node));
        curOp4Node.clear();

    }

    public String recvQuorumAbort(PrintStream out){
//        if(isNeed2Crash(info.crashRate)){
//            out.println("crashed");
//            crashing();
//            qNodeRecover();
//            return "";
//        }
        quorumAbort4Node();
        return "success";
    }

    public void quorumCommit4Node(){

        synchronized (lock) {
            lock.status = false;
            LogSys.debug("clear lock because write "+ curOp4Node.getValue() +" commit from "+curOp4Node.getQuorumInfra().coordinator.info.hostID + " seq = " +curOp4Node.getqSeq());
        }

        data.value = curOp4Node.getValue();
        curOp4Node.setStatus("COMMIT");
        HashMap<Integer,Op4Node> logMap = log4Node.get(curOp4Node.getQuorumInfra().getCoordinator());
        if(logMap ==null){
            logMap = new HashMap<Integer,Op4Node>();
            log4Node.put(curOp4Node.getQuorumInfra().getCoordinator(), logMap);
        }
        logMap.put(curOp4Node.getqSeq(), new Op4Node(curOp4Node));
        curOp4Node.clear();

    }

    public String recvQuorumCommit(PrintStream out){
        if(isNeed2Crash(info.crashRate)){
            LogSys.debug("crashed");
            isAlive = false;
            info.ifCrash = true;

            out.println("crashed");
            crashing();
            qNodeRecover();
            return "";
        }
        quorumCommit4Node();
        return "success";
    }

    @Override
    public void run() {

        Socket session = null;
        ArrayList<QuorumServThd> threadsPool = new ArrayList<QuorumServThd>(threadsSize);

        while (true) {

            try {
                session = ss.accept();
                LogSys.debug(session.toString());
            } catch (IOException e) {
                synchronized (isEnd) {
                    if (isEnd || session == null){
                        LogSys.debug("thread end");
                        for (int i = 0; i < threadsPool.size(); i++) {
                            QuorumServThd t = threadsPool.get(i);
                            t.end();
                            t.awake();
                        }
                        return;
                    }
                }
            }

            QuorumServThd cur = null;
            int count = 0;
            ArrayList<Socket> sessionPool = new ArrayList<>();

            for (int i = 0; i < threadsPool.size(); i++) {
                QuorumServThd t = threadsPool.get(i);
                if (t.isFree()) {
                    count++;
                    if (cur == null) cur = t;
                }
            }

            if (cur == null) {
                cur = new QuorumServThd(this);
                threadsPool.add(cur);
                Thread thread = new Thread(cur);
                thread.start();
                count++;
            }

            int timeout = 10000;

            try {
                if(session != null) session.setSoTimeout(timeout);
            } catch (SocketException e) {
                e.printStackTrace();
            }

            cur.setBusy();
            cur.setSession(session);
            cur.awake();

        }

//        if (session != null) {
//            try {
//                session.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

    }

    @Override
    public void end() {
        synchronized (isEnd) {
            isEnd = true;
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public abstract boolean strictQuorumWrite( int value);
    public abstract String requestQuorumDecision();
    public abstract void qNodeRecover( );
    public abstract boolean coordinatorRestart( );
    public abstract String recvCoordinatorCrashed();
}
