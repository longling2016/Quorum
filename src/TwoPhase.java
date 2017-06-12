import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.ArrayList;

public class TwoPhase extends QuorumSys {
    private boolean isBlocking;
    /**
     * addressBook has the addresses for all nodes in cluster, check address class
     * data saved in current node, initialize with value = 0. Example: change the value to 10: data.value = 10
     * ss is used for accepting the connection and receive message from socket.
     * Lock is used for strictQuorumWrite/read operation lock, every time before doing the operation, test and set the lock as true,
     and set lock as false after operation is done. Details check Lock class.
     Remember to check the lock first, since the read operation from monitor will also test and set the lock.
     * info please check Info class for details.
     */

    public TwoPhase(Address[] addressBook, Data data, ServerSocket ss, Lock lock, Info info) {
        super(addressBook, data, ss, lock, info);
        isBlocking = false;
    }


    public String quorumApply(ArrayList<Integer> nodes, int value, PrintStream out) {
        if(nodes.size() == 0 ){
            LogSys.debug("no quorum node in this op");
            out.println("fail");

            return "ABORT";
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
                out.println("fail");

                return "ABORT";
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
                out.println("fail");

                return "ABORT";
            }
        }

        return "success";
    }

    /** Return boolean to indicate of the strictQuorumWrite operation is successful or not.
     */

    public boolean strictQuorumWrite(int updateValue, PrintStream out) {

        // TODO: simulate random crash, Please remember to change the ifCrash boolean in info before crash and after recovery
        // TODO: handle situation for random crash
        // TODO: detect isBlocking

        // increment blockingCounter every time when a isBlocking is detected
        isCoodinator = true;

        ArrayList<Integer> nodes = chooseQuorumNodeList(quorumNodes, info.writingQuorum);
        if (nodes.size() < info.writingQuorum) {
            LogSys.debug("can't find enough alive nodes in this system");
            isCoodinator = false;
            return false;
        }

        //phase 1: request set status to APPLY_COMMIT
        String res = quorumApply(nodes, updateValue, out);
        if(res.equals("ABORT")){
            quorumAbort();
            isCoodinator = false;
            LogSys.debug("quorum write error in applying phase");
            return false;
        }

        //phase 2: quorumCommit
        quorumCommit(out);


        quorumComplete();

        isCoodinator = false;

        return true;
    }

    public String requestQuorumDecision(){
        String res = null;
        QuorumInfra infra = curOp4Node.getQuorumInfra();

        while(true) {
            LogSys.debug("request quorum decision");
            String op = "quorumStatus,";
            String request = infra.coordinator.info.hostID+","+ curOp4Node.getqSeq();
            if(!isBlocking){
                request +=",BLOCKING";
                LogSys.debug("blocking occurs");
                isBlocking = true;
            }
            res = Util.sAr(infra.coordinator, op + request);
            if(!res.equals("crashed")){
                isBlocking = false;

                return res;
            }

            op = "quorumStatus4Nodes,";
            for(QuorumNode node : infra.getQuorumNodes()){
                res = Util.sAr(node,op + request);
                LogSys.debug("receive quorumStatus "+res);
                if(res.equals("INIT")){
                    isBlocking = false;
                    return "ABORT";
                }
                if(!res.equals("crashed") && !res.equals("READY") ){
                    isBlocking = false;
                    return res;
                }
            }

            try {
                Thread.sleep((long)(info.crashDuration/2));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void quorumBlocking(){
        if(!curOp4Coordinator.isBlocking()) {
            info.blockingCounter++;
            curOp4Coordinator.setBlocking(true);
        }
    }

    public boolean coordinatorRestart( ){

        if(curOp4Coordinator.getStatus().equals("GLOBAL_APPLY")){
            curOp4Coordinator.setStatus("GLOBAL_ABORT");
        }

        if(!curOp4Coordinator.getStatus().equals("FREE")) {
            log4Coordinator.put(curOp4Coordinator.getqSeq(), new Op4Coordinator(curOp4Coordinator));
            curOp4Coordinator.clear();
        }

        isAlive = true;
        info.ifCrash = false;

        return true;
    }

    public void qNodeRecover( ){
        //if has unfinished job, finished it
        String res = null;
        LogSys.debug("node recover");

        switch (curOp4Node.getStatus()) {
            case "FREE":
                break;
            case "READY":
                res = requestQuorumDecision();
                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    LogSys.debug("quorum abort: " + data.value);
                    quorumAbort4Node();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("COMMIT")) {
                    LogSys.debug("quorum commit from " + data.value);
                    quorumCommit4Node();
                    LogSys.debug("to: " + data.value);
                    break;
                }
                break;
            default: //COMMIT,ABORT
                LogSys.debug("receive a unknown decision "+res);
                break;
        }

        isAlive = true;
        info.ifCrash = false;
    }

    public  String recvCoordinatorCrashed(){
        //if has unfinished job, finished it
        String res = null;
        LogSys.debug("recv Coordinator Crashed");

        switch (curOp4Node.getStatus()) {
            case "FREE":
            case "COMMIT":
            case "ABORT":
                LogSys.debug("receive QuorumDecision in"+ curOp4Node.getStatus());

                break;
            case "READY":
                res = requestQuorumDecision();
                LogSys.debug("get QuorumDecision "+ res);

                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    LogSys.debug("quorum abort: " + data.value);
                    quorumAbort4Node();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("COMMIT")) {
                    LogSys.debug("quorum commit from " + data.value);
                    quorumCommit4Node();
                    LogSys.debug("to: " + data.value);
                    break;
                }
                break;
            default: //COMMIT,ABORT
                LogSys.debug("unknown status "+res);
                break;
        }

        return "success";
    }
    public boolean quorumCommit( PrintStream out) {
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
                out.println("success");
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
        out.println("success");
        return true;
    }

}
