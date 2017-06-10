import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.concurrent.Semaphore;


public class QuorumServThd implements Runnable {
    private Semaphore sem;
    private Socket session;
    private boolean isFree;
    private QuorumSys quorumSys;
    private boolean isEnd;

    public QuorumServThd(QuorumSys quorumSys) {
        sem = new Semaphore(0);
        this.quorumSys = quorumSys;
        this.isFree = true;
        this.isEnd = false;
    }

    public boolean isFree() {
        return isFree;
    }

    public void setFree(boolean free) {
        isFree = free;
    }

    public void setSession(Socket session) {
        this.session = session;
    }

    public void setBusy() {
        this.isFree = false;
    }

    public void awake() {
        this.sem.release();
    }
    public void end(){
        this.isEnd = true;
    }

    @Override
    public void run() {

        try {
            requestsProcessing();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void requestsProcessing() throws InterruptedException, IOException {

        boolean flag1 = true;
        Date date = new Date();

        while (true) {
            sem.acquire();
            if(isEnd){
                break;
            }

            BufferedReader in = null;
            PrintStream out = null;

            try {
                //output Stream of Socket
                out = new PrintStream(session.getOutputStream());
                //input Stream of Socket
                in = new BufferedReader(new InputStreamReader(session.getInputStream()));

                String line = in.readLine().trim();
                System.out.println("received message: " + line);
                String fc[] = line.split(",");
                String res = null;

                switch (fc[0]) {
                    case "write": //strictQuorumWrite 10
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }
                        LogSys.debug("receive write request, value = " + Integer.valueOf(fc[1].trim()));
                        if (quorumSys.strictQuorumWrite(Integer.valueOf(fc[1].trim()))) {
                            out.println("success");
                            LogSys.debug(" write success, value = " + Integer.valueOf(fc[1].trim()));

                        } else {
                            out.println("fail");
                            LogSys.debug(" write fail, value = " + Integer.valueOf(fc[1].trim()));
                        }
                        break;
                    case "apply":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }
                        QuorumInfra infra = quorumSys.parseQuorumInfra(fc[3]);
                        LogSys.debug("receive apply request from " + infra.coordinator.info.hostID + ", value = " + Integer.valueOf(fc[2].trim()));
                        res = quorumSys.recvQuorumApply(infra, Integer.valueOf(fc[1]), Integer.valueOf(fc[2]), out);
                        if (res.length() > 0) {
                            out.println(res);
                            LogSys.debug(" apply " + res + " , value = " + Integer.valueOf(fc[2].trim()));
                        } else {
                            LogSys.debug(" apply fail , value = " + Integer.valueOf(fc[2].trim()));
                        }
                        break;
                    case "quorumCommit":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;

                        }
                        if(quorumSys.curOp4Node.getQuorumInfra() == null){
                            int i = 1;
                            res = "";
                            break;
                        }
                        int coId = quorumSys.curOp4Node.getQuorumInfra().coordinator.info.hostID;
                        int value = quorumSys.curOp4Node.getValue();
                        LogSys.debug("receive quorumCommit request from " + coId + " value = " + value);

                        res = quorumSys.recvQuorumCommit(out);
                        if (res.length() > 0) {
                            out.println(res);
                            LogSys.debug("quorumCommit request from " + coId + res + " , value = " + value);

                        } else {
                            LogSys.debug("quorumCommit request from " + coId + "failed , value = " + value);

                        }
                        break;
                    case "quorumPrecommit":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }
                        coId = quorumSys.curOp4Node.getQuorumInfra().coordinator.info.hostID;
                        value = quorumSys.curOp4Node.getValue();

                        LogSys.debug("receive quorumPrecommit request from " + coId + value);

                        if (quorumSys instanceof ThreePhase) {
                            res = ((ThreePhase) quorumSys).recvQuorumPrecommit(out);

                        }
                        if (res.length() > 0) {
                            LogSys.debug("quorumPrecommit request from " + coId + res + " , value = " + value);
                            out.println(res);
                        } else {
                            out.println("fail");
                            LogSys.debug("quorumPrecommit request from " + coId + "failed , value = " + value);

                            break;
                        }
                        break;
                    case "GLOBAL_ABORT":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }

                        coId = quorumSys.curOp4Node.getQuorumInfra().coordinator.info.hostID;
                        value = quorumSys.curOp4Node.getValue();
                        LogSys.debug("receive GLOBAL_ABORT from " + coId + " value = " + value);

                        res = quorumSys.recvQuorumAbort(out);
                        if (res.length() > 0) {
                            out.println(res);
                        }
                        break;
                    case "quorumStatus":

                        if (quorumSys instanceof TwoPhase) {

                            if (fc.length == 4 && fc[3].equals("BLOCKING")) {
                                LogSys.debug("Blocking occurs ");

                                ((TwoPhase) quorumSys).quorumBlocking();
                            }
                        }

                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }

                        out.println(quorumSys.getQuorumStatus4Coordinator(Integer.valueOf(fc[1]), Integer.valueOf(fc[2])));
                        break;
                    case "quorumStatus4Nodes":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }

                        out.println(quorumSys.getQuorumStatus4Node(Integer.valueOf(fc[1]), Integer.valueOf(fc[2])));
                        break;
                    case "isAlive":
                        if (quorumSys.isAlive()) {
                            out.println("Alive");
                        } else {
                            out.println("Destination_Host_Crashed");
                        }
                        break;
                    case "COORDINATOR_CRASH":
                        if (!quorumSys.isAlive()) {
                            out.println("crashed");
                            break;
                        }
                        if (!quorumSys.curOp4Node.getStatus().equals("FREE")) {
                            coId = quorumSys.curOp4Node.getQuorumInfra().coordinator.info.hostID;
                            value = quorumSys.curOp4Node.getValue();
                            int seq = quorumSys.curOp4Node.getqSeq();
                            LogSys.debug("receive COORDINATOR_CRASH from " + coId + " value = " + value + " seq = " + seq + ", when " + quorumSys.curOp4Node.getStatus());
                        }else{
                            LogSys.debug("receive COORDINATOR_CRASH");

                        }
                        out.println("success");
                        quorumSys.recvCoordinatorCrashed();
                        break;
                    default:
                        LogSys.err("Received unknown command: " + fc[0]);
                }
            } catch (SocketTimeoutException e) {
                LogSys.debug("session: " + session.toString() + " is time out");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (out != null) out.close();
                if (in != null) in.close();
                session.close();
                isFree = true;
            }
        }
    }
}
