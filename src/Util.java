import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Util {



    public static String sAr(QuorumNode dstNode, String content) {

        Address dst = dstNode.getInfo();
        Socket socket = new Socket();
        BufferedReader input;
        PrintWriter out;
        String res = null;

        try {
            socket.connect(new InetSocketAddress(dst.ip, dst.port), 500);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(content);
            res = input.readLine();

            out.close();
            input.close();
        } catch (SocketTimeoutException | SocketException e ) {
            //check if the destination host crashed
            LogSys.debug("destination "+ dst.hostID +" socket down, that should not happen");
            return "Destination_Host_Crashed";
        } catch (IOException e) {
            LogSys.debug("IO error ocurrs");
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    socket = null;
                    System.out.println("client error:" + e.getMessage());
                }
            }
        }
        if (res == null) {
            // maybe the destination crashed
            LogSys.debug("receive null response from " + dst.hostID);
            res = "fail";
        }
        return res;
    }


}
