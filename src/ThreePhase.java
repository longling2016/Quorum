import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.ArrayList;

public class ThreePhase extends QuorumSys {

    /**
     * addressBook has the addresses for all nodes in cluster, check address class
     * data saved in current node, initialize with value = 0. Example: change the value to 10: data.value = 10
     * ss is used for accepting the connection and receive message from socket.
     * Lock is used for strictQuorumWrite/read operation lock, every time before doing the operation, test and set the lock as true,
     * and set lock as false after operation is dontion from monitor will also test and set the lock.
     * info please check Info class for details.e. Details check Lock class.
     * Remember to check the lock first, since the read opera
     */

    public ThreePhase(Address[] addressBook, Data data, ServerSocket ss, Lock lock, Info info) {
        super(addressBook, data, ss, lock, info);
    }

    /**
     * Return boolean to indicate of the strictQuorumWrite operation is successful or not.
     */

    public boolean strictQuorumWrite(int updateValue) {

        // increment blockingCounter every time when a isBlocking is detected
        isCoodinator = true;

        ArrayList<Integer> nodes = chooseQuorumNodeList(quorumNodes, info.writingQuorum);
        if (nodes.size() < info.writingQuorum) {
            LogSys.debug("can't find enough alive nodes in this system");
            return false;
        }

        //phase 1: request set status to APPLY_COMMIT
        if (!quorumApply(nodes, updateValue)) {
            LogSys.debug("quorum write error in applying phase");
            quorumAbort();
            return false;
        }

        //phase 2: quorumPrecommit
        if (!quorumPrecommit()) {
            LogSys.debug("quorum write error in quorumPrecommit phase");
            return false;
        }

        //phase 3: quorumCommit
        quorumCommit();


        // TODO: simulate random crash, Please remember to change the ifCrash boolean in info before crash and after recovery
        // TODO: handle situation for random crash
        // TODO: detect isBlocking

        /** You may use the ListeningThread class I created to create a individual thread to keep listening to
         the server socket, the message received can be processed in this thread.

         But I am pretty sure you can have so many other ways to do the same thing :)
         */


        isCoodinator = false;
        curOp4Coordinator.clear();
        return true;
    }

    public boolean quorumPrecommit() {
        ArrayList<QuorumNode> done = new ArrayList<>();
        for (QuorumNode node : curOp4Node.getQuorumInfra().getQuorumNodes()) {
            if (isNeed2Crash(info.crashRate)) {
                crashing();
                coordinatorRestart();
                return false;
            }

            String res = Util.sAr(node, "quorumPrecommit");
            if (res.equals("OK")) {
                done.add(node);
            } else if (res.equals("crashed")) {
//                for (QuorumNode n : done) {
//                    Util.sAr(node, "APPLY_ABORT");
//                }
                LogSys.debug("node " + node.info.hostID + " crashed");
                continue;
            }
        }
        curOp4Coordinator.setStatus("GLOBAL_PRECOMMIT");
        return true;
    }


    public String recvQuorumPrecommit(PrintStream out) {
        if (isNeed2Crash(info.crashRate)) {
            out.println("crashed");
            crashing();
            qNodeRecover();
            return "";
        }
        curOp4Node.setStatus("PRECOMMIT");
        return "success";
    }

    public void qNodeRecover() {
        //if has unfinished job, finished it
        String res = null;

        switch (curOp4Node.getStatus()) {
            case "FREE":
                break;
            case "READY":
            case "PRECOMMIT":
                res = requestQuorumDecision();
                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    quorumAbort();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("COMMIT")) {
                    quorumCommit();
                    break;
                }
                break;
            default:
                break;
        }
        isAlive = true;
    }

    public void coordinatorRestart( ){
        boolean isAbort = false;

        if(curOp4Coordinator.getStatus().equals("GLOBAL_APPLY")){
            curOp4Coordinator.setStatus("GLOBAL_ABORT");
        }else if (curOp4Coordinator.getStatus().equals("GLOBAL_PRECOMMIT")){
            QuorumInfra infra = curOp4Coordinator.getQuorumInfra();
            String request = "quorumStatus4Nodes," + infra.coordinator.info.hostID + "," + curOp4Coordinator.getqSeq();
            String res = null;

            for (QuorumNode node : infra.getQuorumNodes()) {
                res = Util.sAr(node, request);
                if(res.equals("ABORT")){
                    curOp4Coordinator.setStatus("GLOBAL_COMMIT");
                    isAbort = true;
                    break;
                }
            }

            if(!isAbort) curOp4Coordinator.setStatus("GLOBAL_COMMIT");
        }

        if(!curOp4Coordinator.getStatus().equals("FREE")) {
            log4Coordinator.put(curOp4Node.getqSeq(), new Op4Coordinator(curOp4Coordinator));
            curOp4Coordinator.clear();
        }
        isAlive = true;
    }


    public String requestQuorumDecision() {

        String res = null;
        String op = "quorumStatus,";
        String request = curOp4Node.getQuorumInfra().coordinator.info.hostID + "," + curOp4Node.getqSeq();
        QuorumInfra infra = curOp4Node.getQuorumInfra();
        boolean isAllReady = false;

        res = Util.sAr(infra.coordinator, op + request);
        if (!res.equals("crashed")) {
            return res;
        }

        op = "quorumStatus4Nodes";
        for (QuorumNode node : infra.getQuorumNodes()) {
            res = Util.sAr(node, op + request);
            switch (res) {
                case "ABORT":
                case "INIT":
                    return "ABORT";
                case "COMMIT":
                case "PRECOMMIT":
                    return "COMMIT";
                default://ready and crashed
                    LogSys.debug("receive a unknown decision "+res);
                    break;
            }

        }
        return "ABORT";
    }

}
