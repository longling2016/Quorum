import java.io.IOException;
import java.net.ServerSocket;

public class Main {

    public static void main(String[] args) {

        // the addressBook is an array to save the addresses of all nodes
        Address[] addressBook = new Address[] {new Address(0, "127.10.00.12", 80)};

        // lock.status is initialized as false, which is unlock
        Lock lock = new Lock();

        // data.value is initialized as 0
        Data data = new Data();

        try {
            ServerSocket ss = new ServerSocket(9090);

            // example: how I will call your protocol
            ProtocolMode pm = new NoPhase(addressBook, data);
            int blockingTimes = pm.execute(ss, lock);

        } catch (IOException e) {
            System.out.println(e);
        }




    }
}
