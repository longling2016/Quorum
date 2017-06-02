import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;
import java.util.Scanner;

public class Node {

    static Address[] addressBook;
    static String ip;
    static int portM;
    static int port;
    static SendToMonitor sm;
    static ProtocolMode pm;
    static ServerSocket ssM;
    static ServerSocket ss;
    static Data data;
    static Lock lock;
    static Info info;
    static String monitorIP;
    static int monitorPort;
    static int nodeID;

    // config: TODO modify
    static final int crashRate = 1000;
    static final int writingQuorum = 2;
    static final int crashDuration = 2000;

    public static void main(String[] args) {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();

            ssM = new ServerSocket(0);
            portM = ssM.getLocalPort();

            System.out.println("IP & port for Monitor: " + ip + " " + portM);

            Thread thread = new Thread(new ListeningThread(ssM));
            thread.start();

            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            System.out.println("IP & port for Nodes communication: " + ip + " " + port);

        } catch (IOException e) {
            System.out.println(e);
        }

    }

    public static void listen(String message) {
        // receive monitor ip and port
        if (message.length() > 3 && message.substring(0, 4).equals("moni")) {
            message = message.substring(4, message.length());
            String[] info = message.split(" ");
            monitorIP = info[0];
            monitorPort = Integer.parseInt(info[1]);
            sm = new SendToMonitor(monitorIP, monitorPort);
            System.out.println("Get Monitor ip and port.");

        } else if (message.length() > 3 && message.substring(0, 4).equals("book")) {
            message = message.substring(4, message.length());
            String[] list = message.split(",");
            addressBook = new Address[list.length];
            for (int i = 0; i < list.length; i ++) {
                String[] info = list[i].split(" ");
                int curPort = Integer.parseInt(info[1]);
                addressBook[i] = new Address(i, info[0], curPort);
                if (info[0].equals(ip) && curPort == port) {
                    nodeID = i;
                }
            }
            System.out.println("Get address book from monitor.");

        } else if (message.equals("noP")) {
            // test on no-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, crashRate, writingQuorum, crashDuration, false);
            pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
            pm.execute();
            System.out.println("Start testing on no-phase protocol.");

        } else if (message.equals("twoP")) {
            // test on two-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, crashRate, writingQuorum, crashDuration, false);
            pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
            pm.execute();
            System.out.println("Start testing on two-phase protocol.");

        } else if (message.equals("threeP")) {
            // test on three-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, crashRate, writingQuorum, crashDuration, false);
            pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
            pm.execute();
            System.out.println("Start testing on three-phase protocol.");

        } else if (message.equals("ping")) {
            if (info.ifCrash) {
                sm.send("crash");
            } else {
                sm.send("ack");
            }

        } else if (message.equals("read")) {
            sm.send("value" + nodeID + ":" + Integer.toString(data.value));

        } else if (message.equals("block?")) {
            sm.send("block" + info.blockingCounter);

        } else {
            System.out.println("Received wrong message: " + message);
        }

    }

}
