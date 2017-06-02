import java.net.ServerSocket;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Random;

/**
 * Created by Zhengyu Chen on 6/1/17.
 */
public class NoPhase implements ProtocolMode {
    static class QuorumBook {
        String ip;
        int port;

        public QuorumBook(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    static Address[] addressBook;
    static Data data;
    static ServerSocket ss;
    static Lock lock;
    static Info info;
    static Address monitor;
    static int quorumCounter = 0;
    static int localPort = 0;
    static String localIP = null;
    static HashSet<QuorumBook> quorumSet = new HashSet<>();
    Thread noPhaseThread;

    /**
     * addressBook has the addresses for all nodes in cluster, check address class
     * data saved in current node, initialize with value = 0. Example: change the value to 10: data.value = 10
     * ss is used for accepting the connection and receive message from socket.
     * Lock is used for write/read operation lock, every time before doing the operation, test and set the lock as true,
     * and set lock as false after operation is done. Details check Lock class.
     * Remember to check the lock first, since the read operation from monitor will also test and set the lock.
     * info please check Info class for details.
     */

    public NoPhase(Address[] addressBook, Data data, ServerSocket ss, Lock lock, Info info, Address monitor) {
        this.addressBook = addressBook;
        this.data = data;
        this.ss = ss;
        this.lock = lock;
        this.info = info;
        this.monitor = monitor;
        try {
            this.localPort = ss.getLocalPort();
            this.localIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /** Return boolean to indicate of the write operation is successful or not.
     */

    public void execute() {
        noPhaseThread = new Thread(new NoPhaseThread(ss));
        noPhaseThread.start();

        // increment blockingCounter every time when a blocking is detected
        int blockingCounter = 0;
        info.blockingCounter = blockingCounter;
        // TODO: simulate random crash, Please remember to change the ifCrash boolean in info before crash and after recovery
        // TODO: handle situation for random crash
        // TODO: detect blocking
    }

    public void end() {
        try {
            noPhaseThread.interrupt();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public static void noPhaseListen(String message) {
        if (message.substring(0, 4).equals("getq")) {// request for quorum
            String[] f = message.split(("@"));
            if (lock.status == false) {// Not occupied, lock is free
                lock.status = true;// Lock myself
                NoPhase.sendMessage("qrgt@" + NoPhase.localIP + "@" + NoPhase.localPort, f[1], Integer.parseInt(f[2]));// qrgt == quorum granted
            } else {// I am locked, request denied
                NoPhase.sendMessage("qrdn", f[1], Integer.parseInt(f[2]));// qrdn == quorum denied
            }
        } else if (message.substring(0, 4).equals("qrgt")) {// Count how many nodes reply quorum granted
            String[] f = message.split("@");
            if (NoPhase.quorumCounter < info.writingQuorum) {
                NoPhase.quorumSet.add(new NoPhase.QuorumBook(f[1], Integer.parseInt(f[2])));
                NoPhase.quorumCounter++;
            } else {
                // Do nothing, since we have enough quorum.
            }
        } else if (message.substring(0, 4).equals("qrdn")) {// Received quorum denied
            // Do nothing
        } else if (message.substring(0, 4).equals("unlk")) {// Unlock instruction
            lock.status = false;
        } else if (message.substring(0, 4).equals("wrte")) {// wrte == write, in no phase, commit immediately
            String[] f = message.split("@");
            if (new Random().nextInt(info.crashRate) != 0) {// Normal
                commit(f[1]);
            } else {// Crash
                Thread crashThread = new Thread(new CrashWaitingThread(info.crashDuration));
                crashThread.start();
            }
        } else if (message.substring(0, 5).equals("write")) {
            String valueString= message.substring(5);
            boolean success = doNoPhase(valueString);
            if (success) {
                sendMessage("success", monitor.ip, monitor.port);
            } else {
                sendMessage("fail", monitor.ip, monitor.port);
            }
        }
    }

    public static boolean doNoPhase(String value) {
        boolean crash = false;
        lock.status = true;// lock myself.
        if (getQuorum(addressBook, info)) {
            /**
             * Nodes in quorumSet are selected, then send unlock to other nodes
             */
            for (int i = 0; i < addressBook.length; i++) {
                if (quorumSet.contains(new QuorumBook(addressBook[i].ip, addressBook[i].port)) == false) {
                    sendMessage("unlk", addressBook[i].ip, addressBook[i].port);
                }
            }
            /**
             * Write the value to all nodes in the quorumSet
             */
            for(QuorumBook qSet:quorumSet){
                if (new Random().nextInt(info.crashRate) != 0) {// Normal
                    sendMessage("wrte@" + value, qSet.ip, qSet.port);
                } else {// Crash
                    Thread crashThread = new Thread(new CrashWaitingThread(info.crashDuration));
                    crashThread.start();
                    crash = true;
                    break;// sending terminate
                }
            }
        } else {// Not enough quorum
            return false;
        }

        /**
         * Unlock all nodes
         */
        broadcast("unlk", addressBook);
        lock.status = false;// unlock myself.
        /**
         *
         */
        if (crash == false) {
            return true;
        } else {
            return false;
        }
    }

    public static void commit(String value) {
        data.value = Integer.parseInt(value);
    }


    /**
     * Send message to a specific node
     * @param message
     * @param ip
     * @param port
     */
    public static void sendMessage(String message, String ip, int port) {
        try {
            Socket s = new Socket(ip, port);
            DataOutputStream dOut = new DataOutputStream(s.getOutputStream());
            dOut.writeUTF(message);
            dOut.flush();
            dOut.close();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Broadcast to all hosts, expect myself
     * @param message
     * @param addressBook
     */
    public static void broadcast(String message, Address[] addressBook) {
        try {
            for (Address each : addressBook) {
                if (each.ip.equals(localIP) && each.port == localPort) {// skip myself
                    continue;
                }
                Socket s = new Socket(each.ip, each.port);
                DataOutputStream dOut = new DataOutputStream(s.getOutputStream());
                dOut.writeUTF(message);
                dOut.flush();
                dOut.close();
                s.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return can have enough quorum
     * @param addressBook
     * @param info
     * @return
     */
    private static boolean getQuorum(Address[] addressBook, Info info) {
        quorumCounter = 0;
        broadcast("getq@" + localIP + "@" + localPort, addressBook);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (quorumCounter < info.writingQuorum) {// cannot get enough quorum
            return false;
        } else {
            return true;
        }
    }
}