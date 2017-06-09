import java.text.SimpleDateFormat;
import java.util.Date;

public class LogSys {
    private static boolean isDebug = true;
    private static String whoami;
    private static SimpleDateFormat format =  new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );

    public static void log(String log){

        System.out.println(format.format(new Date()) + whoami+": "+log+"    ");
    }

    public static void err(String error){

        System.out.println(format.format(new Date())+ whoami+": "+error+"    ");
    }

    public static void debug(String debugInfo){
        if(isDebug) {
            System.out.println(format.format(new Date())+ whoami+": "+debugInfo+"    ");
        }
    }

    public static void setDebug(boolean is){
        isDebug = is;
    }

    public static void setLogSys(String ip, String id){
        //set local identity
        whoami = "   "+ id+" (" +ip +")";
    }
}
