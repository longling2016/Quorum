import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by longlingwang on 5/22/17.
 */
public class ListeningThread implements Runnable {
    ServerSocket ss;

    public ListeningThread(ServerSocket ss) {
        this.ss = ss;
    }

    public void run() {
        DataInputStream dIn = null;
        Socket socket;
        try {
            while (true) {
                socket = ss.accept();
                dIn = new DataInputStream(socket.getInputStream());

                String message = dIn.readUTF();

                Node.listen(message);
//                System.out.println(message);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dIn != null) {
                try {
                    dIn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

