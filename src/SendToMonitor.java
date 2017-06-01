import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by longlingwang on 5/31/17.
 */
public class SendToMonitor {
    String ip;
    int port;

    public SendToMonitor (String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void send (String message) {
        try {
            Socket s = new Socket(ip, port);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(message);
            out.flush();
            out.close();
            s.close();
        } catch (IOException e) {
            System.out.println("Monitor can't be reached!");
        }
    }
}
