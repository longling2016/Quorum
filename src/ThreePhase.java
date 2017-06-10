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
        String res = quorumPrecommit();
        if (res.equals("COMMIT")) {
            LogSys.debug("quorum write commit in quorumPrecommit phase");
            return true;
        }else if(res.equals("ABORT")) {
            LogSys.debug("quorum write abort in quorumPrecommit phase");
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

        quorumComplete();

        isCoodinator = false;
        return true;
    }

    public String quorumPrecommit() {
        ArrayList<QuorumNode> done = new ArrayList<>();
        curOp4Coordinator.setStatus("GLOBAL_PRECOMMIT");
        for (QuorumNode node : curOp4Coordinator.getQuorumInfra().getQuorumNodes()) {
            if (isNeed2Crash(info.crashRate)) {
                LogSys.debug("crashed");
                isAlive = false;
                info.ifCrash = true;

                for (QuorumNode n : curOp4Coordinator.getQuorumInfra().getQuorumNodes()) {
                    Util.sAr(n, "COORDINATOR_CRASH");
                }
                crashing();
                if(coordinatorRestart()){
                    return "COMMIT";
                }
                return "ABORT";

            }

            String res = Util.sAr(node, "quorumPrecommit");
            if (res.equals("OK")) {
                done.add(node);
            } else if (res.equals("crashed")) {
//                for (QuorumNode n : done) {
//                    Util.sAr(node, "APPLY_ABORT");
//                }
                LogSys.debug("node " + node.info.hostID + " crashed in precommit phase");
                continue;
            }
        }
        return "CONTINUE";
    }


    public String recvQuorumPrecommit(PrintStream out) {
        if (isNeed2Crash(info.crashRate)) {
            LogSys.debug("crashed");
            isAlive = false;
            info.ifCrash = true;

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
            case "ABORT":
                LogSys.debug("receive QuorumDecision in"+ curOp4Node.getStatus());
                break;
            case "READY":
            case "PRECOMMIT":
                res = requestQuorumDecision();
                LogSys.debug("get QuorumDecision "+ res);

                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    quorumAbort4Node();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("GLOBAL_PRECOMMIT") || res.equals("COMMIT")) {
                    quorumCommit4Node();
                    break;
                }
                break;
            default:
                break;
        }
        isAlive = true;
        info.ifCrash = false;
    }

    public boolean coordinatorRestart( ){
        boolean isAbort = false;

        if(curOp4Coordinator.getStatus().equals("GLOBAL_APPLY")){
            curOp4Coordinator.setStatus("GLOBAL_ABORT");
        }else if (curOp4Coordinator.getStatus().equals("GLOBAL_PRECOMMIT")){
            QuorumInfra infra = curOp4Coordinator.getQuorumInfra();
            String request = "quorumStatus4Nodes," + infra.coordinator.info.hostID + "," + curOp4Coordinator.getqSeq();
            String res = null;

            for (QuorumNode node : infra.getQuorumNodes()) {
                res = Util.sAr(node, request);
                LogSys.debug("get QuorumDecision "+ res);

                if(res.equals("ABORT")){
                    curOp4Coordinator.setStatus("GLOBAL_ABORT");
                    isAbort = true;
                    break;
                }
            }

            if(!isAbort) curOp4Coordinator.setStatus("GLOBAL_COMMIT");
        }

        if(!curOp4Coordinator.getStatus().equals("FREE")) {
            log4Coordinator.put(curOp4Coordinator.getqSeq(), new Op4Coordinator(curOp4Coordinator));
            curOp4Coordinator.clear();
        }
        isAlive = true;
        info.ifCrash = false;

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return !isAbort;
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

        op = "quorumStatus4Nodes,";
        for (QuorumNode node : infra.getQuorumNodes()) {
            res = Util.sAr(node, op + request);
            switch (res) {
                case "ABORT":
                case "INIT":
                    return "ABORT";
                case "COMMIT":
                case "PRECOMMIT":
                    return "COMMIT";
                case "READY":
                    break;
                default://ready and crashed
                    LogSys.debug("receive a unknown decision "+res);
                    break;
            }

        }
        // all is ready
        return "ABORT";
    }

    public  String recvCoordinatorCrashed(){
        //if has unfinished job, finished it
        String res = null;

        switch (curOp4Node.getStatus()) {
            case "FREE":
            case "ABORT":
                LogSys.debug("receive QuorumDecision in"+ curOp4Node.getStatus());
                break;
            case "READY":
                res = requestQuorumDecision();
                LogSys.debug("get QuorumDecision "+ res);
                if (res.equals("GLOBAL_ABORT") || res.equals("ABORT")) {
                    quorumAbort4Node();
                    break;
                } else if (res.equals("GLOBAL_COMMIT") || res.equals("COMMIT")) {
                    quorumCommit4Node();
                    break;
                }
                break;
            case "PRECOMMIT":
                quorumCommit4Node();
                break;
            default:
                LogSys.debug("unknown Quorum Decision "+res);
                break;
        }

        return "success";

    }

}
