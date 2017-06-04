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
    static int writingQuorum;
    static int phaseProtocol;
    static final Object trigger = new Object();

    // config: TODO modify
    static final int crashRate = 5;
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

            int counter = 0;

            while (counter < 3) {

                phaseProtocol = -1;

                synchronized(trigger) {
                    try {
                        while (phaseProtocol == -1) {
                            trigger.wait();
                        }
                    } catch (InterruptedException e) {
                        System.out.println(e);
                    }
                }

                if (phaseProtocol == 0) {
                    data = new Data();
                    lock = new Lock();
                    info = new Info(0, crashRate, writingQuorum, crashDuration, false);
                    pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
                    System.out.println("Start testing on no-phase protocol.");
                    pm.execute();

                } else if (phaseProtocol == 2) {
                    data = new Data();
                    lock = new Lock();
                    info = new Info(0, crashRate, writingQuorum, crashDuration, false);
                    pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
                    System.out.println("Start testing on two-phase protocol.");
                    pm.execute();

                } else if (phaseProtocol == 3) {
                    data = new Data();
                    lock = new Lock();
                    info = new Info(0, crashRate, writingQuorum, crashDuration, false);
                    pm = new NoPhase(addressBook, data, ss, lock, info, new Address(999, monitorIP, monitorPort));
                    System.out.println("Start testing on three-phase protocol.");
                    pm.execute();

                } else {
                    System.out.println("Illegal number of phaseProtocol = " + phaseProtocol);
                }
                counter --;
            }

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
            phaseProtocol = 0;
                synchronized (trigger) {
                    trigger.notifyAll();
            }

        } else if (message.equals("twoP")) {
            // test on two-phase protocol
            phaseProtocol = 2;
            synchronized (trigger) {
                trigger.notifyAll();
            }

        } else if (message.equals("threeP")) {
            // test on three-phase protocol
           phaseProtocol = 3;
            synchronized (trigger) {
                trigger.notifyAll();
            }

        } else if (message.equals("ping")) {
            if (info.ifCrash) {
//                System.out.println("send crash.");
                sm.send("crash");
            } else {
//                System.out.println("send ack.");
                sm.send("ack");
            }

        } else if (message.equals("read")) {
            sm.send("value" + nodeID + ":" + Integer.toString(data.value));

        } else if (message.equals("block?")) {
            sm.send("block" + info.blockingCounter);

        } else if (message.equals("end")) {
            pm.end();

        } else if (message.length() > 5 && message.substring(0, 6).equals("quorum")) {
           writingQuorum = Integer.parseInt(message.substring(6, message.length()));

        } else {
            System.out.println("Received wrong message: " + message);
        }

    }

}
