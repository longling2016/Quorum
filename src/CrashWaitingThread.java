


/**
 * Created by Chesley on 6/1/17.
 * This thread is going to listen to the ServerSocket for NoPhase class
 */
public class CrashWaitingThread implements Runnable {
    static int duration;

    public CrashWaitingThread(int duration) {
        this.duration = duration;
    }

    public void run() {
        NoPhase.info.ifCrash = true;
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        NoPhase.info.ifCrash = false;
    }
}

