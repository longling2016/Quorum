/**
 * Created by longlingwang on 5/24/17.
 */
public class Info {
    int blockingCounter;
    int crashRate;
    int writingQuorum;
    int crashDuration; // in millisecond

    public Info(int blockingCounter, int crashRate, int writingQuorum, int crashDuration) {
     this.blockingCounter = blockingCounter;
     this.crashRate = crashRate;
     this.writingQuorum = writingQuorum;
     this.crashDuration = crashDuration;
    }
}
