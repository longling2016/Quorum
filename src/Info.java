/**
 * Created by longlingwang on 5/24/17.
 */
public class Info {
    int blockingCounter;
    int crashRate;
    int writingQuorum;
    int crashDuration; // in millisecond
    boolean ifCrash; // if current node is online or crashed

    public Info(int blockingCounter, int crashRate, int writingQuorum, int crashDuration, boolean ifCrash) {
     this.blockingCounter = blockingCounter;
     this.crashRate = crashRate;
     this.writingQuorum = writingQuorum;
     this.crashDuration = crashDuration;
     this.ifCrash = ifCrash;
    }
}
