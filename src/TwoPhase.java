import java.net.ServerSocket;

/**
 * Created by longlingwang on 5/31/17.
 */
public class TwoPhase implements ProtocolMode {
    Address[] addressBook;
    Data data;
    ServerSocket ss;
    Lock lock;
    Info info;

    /**
     * addressBook has the addresses for all nodes in cluster, check address class
     * data saved in current node, initialize with value = 0. Example: change the value to 10: data.value = 10
     * ss is used for accepting the connection and receive message from socket.
     * Lock is used for write/read operation lock, every time before doing the operation, test and set the lock as true,
     and set lock as false after operation is done. Details check Lock class.
     Remember to check the lock first, since the read operation from monitor will also test and set the lock.
     * info please check Info class for details.
     */

    public TwoPhase(Address[] addressBook, Data data, ServerSocket ss, Lock lock, Info info) {
        this.addressBook = addressBook;
        this.data = data;
        this.ss = ss;
        this.lock = lock;
        this.info = info;
    }

    /** Return boolean to indicate of the write operation is successful or not.
     */

    public boolean write(int updateValue) {

        // increment blockingCounter every time when a blocking is detected
        int blockingCounter = 0;

        // TODO: simulate random crash, Please remember to change the ifCrash boolean in info before crash and after recovery
        // TODO: handle situation for random crash
        // TODO: detect blocking

        info.blockingCounter = blockingCounter;
        return true;
    }

}

