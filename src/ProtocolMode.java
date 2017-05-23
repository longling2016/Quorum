import java.net.ServerSocket;

/**
 * Created by longlingwang on 5/22/17.
 */
public interface ProtocolMode {
    public int execute(ServerSocket ss, Lock lock);
}
