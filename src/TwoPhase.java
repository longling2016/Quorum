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

    /** Return boolean to indicate of the strictQuorumWrite operation is successful or not.
     */

    public boolean strictQuorumWrite(int updateValue) {

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
        if (!quorumApply(nodes, updateValue)) {
            quorumAbort();
            isCoodinator = false;
            LogSys.debug("quorum write error in applying phase");
            return false;
        }

        //phase 2: quorumCommit
        quorumCommit();


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

}
