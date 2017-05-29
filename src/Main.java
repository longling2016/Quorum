import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class Main {

    public static void main(String[] args) {

        // the addressBook is an array to save the addresses of all nodes
        Address[] addressBook = new Address[] {new Address(0, "127.10.00.12", 80)};

        // lock.status is initialized as false, which is unlock
        Lock lock = new Lock();

        // data.value is initialized as 0
        Data data = new Data();

        Random rand = new Random();

        try {
            ServerSocket ss = new ServerSocket(9090);
            Info info = new Info(0, 1000, 6, 5000);

            // example: how I will call your protocol
            ProtocolMode pm = new NoPhase(addressBook, data, ss, lock, info);
            boolean success = pm.write(rand.nextInt(10000));
            if (success) {
                // do something;
            } else {
                // do something;
            }

        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
