import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;
import java.util.Scanner;

public class Node {

    static Address[] addressBook;
    static String ip;
    static int port;
    static SendToMonitor sm;
    static ProtocolMode pm;
    static ServerSocket ss;

    static Data data;
    static Lock lock;
    static Info info;

    public static void main(String[] args) {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();

            ss = new ServerSocket(0);
            port = ss.getLocalPort();

            System.out.println("IP & port: " + ip + " " + port);

            Thread thread = new Thread(new ListeningThread(ss));
            thread.start();

            Random rand = new Random();



            int runningCounter = 0;
            while (runningCounter < 10) {
                boolean success = pm.write(rand.nextInt(10000));
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
            String monitorIP = info[0];
            int monitorPort = Integer.parseInt(info[1]);
            sm = new SendToMonitor(monitorIP, monitorPort);
            System.out.println("Get Monitor ip and port.");

        } else if (message.length() > 3 && message.substring(0, 4).equals("book")) {
            message = message.substring(4, message.length());
            String[] list = message.split(",");
            addressBook = new Address[list.length];
            for (int i = 0; i < list.length; i ++) {
                String[] info = list[i].split(" ");
                addressBook[i] = new Address(i, info[0], Integer.parseInt(info[1]));
            }
            System.out.println("Get address book from monitor.");

        } else if (message.equals("noP")) {
            // test on no-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, 1000, 6, 2000, false);
            pm = new NoPhase(addressBook, data, ss, lock, info);
            System.out.println("Start testing on no-phase protocol.");

        } else if (message.equals("twoP")) {
            // test on two-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, 1000, 6, 2000, false);
            pm = new TwoPhase(addressBook, data, ss, lock, info);
            System.out.println("Start testing on two-phase protocol.");

        } else if (message.equals("threeP")) {
            // test on three-phase protocol
            data = new Data();
            lock = new Lock();
            info = new Info(0, 1000, 6, 2000, false);
            pm = new ThreePhase(addressBook, data, ss, lock, info);
            System.out.println("Start testing on three-phase protocol.");

        } else if (message.equals("ping")) {
            if (info.ifCrash) {
                sm.send("crash");
            } else {
                sm.send("ack");
            }
        } else if (message.length() > 4 && message.substring(0, 5).equals("write")) {
            int value = Integer.parseInt(message.substring(5, message.length()));
            boolean success = pm.write(value);
            if (success) {
                sm.send("success");
            } else {
                sm.send("fail");
            }
        }


    }

}
