import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.concurrent.Semaphore;


public class QuorumServThd implements Runnable{
    private Semaphore sem;
    private Socket session;
    private boolean isFree;
    private QuorumSys quorumSys;

    public QuorumServThd( QuorumSys quorumSys) {
        sem = new Semaphore(0);
        this.quorumSys = quorumSys;
        this.isFree = true;
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
            BufferedReader in = null;
            PrintStream out = null;

            try {
                //output Stream of Socket
                out = new PrintStream(session.getOutputStream());
                //input Stream of Socket
                in = new BufferedReader(new InputStreamReader(session.getInputStream()));

                String line = in.readLine().trim();
                String fc[] = line.split(",");
                String res = null;

                switch (fc[0]) {
                    case "write": //strictQuorumWrite 10
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;
                        }
                        if(quorumSys.strictQuorumWrite(Integer.valueOf(fc[1].trim()))) {
                            out.println("success");
                        }else{
                            out.println("fail");
                        }
                        break;
                    case "apply":
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;
                        }
                        QuorumInfra infra = quorumSys.parseQuorumInfra(fc[3]);
                        res = quorumSys.recvQuorumApply(infra,Integer.valueOf(fc[1]),Integer.valueOf(fc[2]),out);
                        if(res.length()>0) out.println(res);
                        break;
                    case "quorumCommit":
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;

                        }
                        res = quorumSys.recvQuorumCommit(out);
                        if(res.length()>0) out.println(res);
                        break;
                    case "quorumPrecommit":
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;

                        }
                        if (quorumSys instanceof ThreePhase) {
                            res = ((ThreePhase) quorumSys).recvQuorumPrecommit(out);
                        }else{
                            out.println("fail");
                            break;
                        }
                        if(res.length()>0) out.println(res);
                        break;
                    case "ABORT":
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;
                        }
                        res = quorumSys.recvQuorumAbort(out);
                        if(res.length()>0) out.println(res);
                        break;
                    case "quorumStatus":
                        if(quorumSys instanceof TwoPhase) {
                            if (fc.length == 4 && fc[3].equals("BLOCKING")) {
                                ((TwoPhase)quorumSys).quorumBlocking();
                            }
                        }
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;
                        }
                        out.println(quorumSys.getQuorumStatus4Coordinator(Integer.valueOf(fc[1]),Integer.valueOf(fc[2])));
                        break;
                    case "quorumStatus4Nodes":
                        if(!quorumSys.isAlive()){
                            out.println("crashed");
                            break;
                        }
                        out.println(quorumSys.getQuorumStatus4Node(Integer.valueOf(fc[1]),Integer.valueOf(fc[2])));
                        break;
                    case "isAlive":
                        if(quorumSys.isAlive()){
                            out.println("Alive");
                        }else{
                            out.println("Destination_Host_Crashed");
                        }
                        break;
                    default:
                        LogSys.err("Input is not valid, please input again.");
                }
            } catch (SocketTimeoutException e) {
                LogSys.debug("session: " + session.toString() + " is time out");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                out.close();
                in.close();
                session.close();
                isFree = true;
            }
        }
    }
}
