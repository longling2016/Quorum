
public class Op4Node {
    private QuorumInfra quorumInfra;
    private int value;
    private int qSeq;
    private String status; //free,READY,ABORT,COMMIT,PRECOMMIT
    private boolean isBlocking;

    public Op4Node() {
        this.status = "FREE";
        this.isBlocking = false;
    }

    public Op4Node(Op4Node op){
        this.quorumInfra = op.getQuorumInfra();
        this.value = op.getValue();
        this.qSeq = op.getqSeq();
        this.status = op.getStatus();
        this.isBlocking = op.isBlocking();
    }

    public void opInitiate(QuorumInfra quorumInfra, int qSeq, int value) {
        LogSys.debug("Initiate op4node" + quorumInfra.coordinator.info.hostID + " qseq = " + qSeq+ " value =" + value);
        this.quorumInfra = quorumInfra;
        this.qSeq = qSeq;
        this.value = value;
        this.status = "READY";
    }

    public void clear(){
        this.quorumInfra = null;
        this.qSeq = -1;
        this.value = -1;
        this.status = "FREE";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public QuorumInfra getQuorumInfra() {
        return quorumInfra;
    }

    public String getStatus() {
        return status;
    }

    public int getValue() {
        return value;
    }

    public int getqSeq() {
        return qSeq;
    }
    public void setBlocking(boolean blocking) {
        this.isBlocking = blocking;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

}
