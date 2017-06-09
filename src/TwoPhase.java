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

            String op = "quorumStatus,";
            String request = infra.coordinator.info.hostID+","+ curOp4Node.getqSeq();
            if(isBlocking){
                request +=",BLOCKING";
            }
            res = Util.sAr(infra.coordinator, op + request);
            if(!res.equals("crashed")){
                return res;
            }

            op = "quorumStatus4Nodes,";
            for(QuorumNode node : infra.getQuorumNodes()){
                res = Util.sAr(node,op + request);
                if(res.equals("INIT")){
                    return "ABORT";
                }
                if(!res.equals("crashed") && !res.equals("READY") ){
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

    public void coordinatorRestart( ){

        if(curOp4Coordinator.getStatus().equals("GLOBAL_APPLY")){
            curOp4Coordinator.setStatus("GLOBAL_ABORT");
        }

        if(!curOp4Coordinator.getStatus().equals("FREE")) {
            log4Coordinator.put(curOp4Node.getqSeq(), new Op4Coordinator(curOp4Coordinator));
            curOp4Coordinator.clear();
        }

        isAlive = true;
    }

    public void qNodeRecover( ){
        //if has unfinished job, finished it
        String res = null;

        switch (curOp4Node.getStatus()) {
            case "FREE":
                break;
            case "READY":
                res = requestQuorumDecision();
                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    quorumAbort();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("COMMIT")) {
                    quorumCommit();
                    break;
                }
                break;
            default: //COMMIT,ABORT
                LogSys.debug("receive a unknown decision "+res);
                break;
        }
        isAlive = true;
    }
}
