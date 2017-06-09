
public class Op4Coordinator {
    QuorumInfra quorumInfra;
    int qSeq;
    int value;
    String status; //free,"GLOBAL_APPLY", GLOBAL_COMMIT, GLOBAL_ABORT, GLOBAL_PRECOMMIT
    boolean isBlocking;

    public Op4Coordinator() {
        this.status = "FREE";
        this.isBlocking = false;
    }

    public Op4Coordinator(Op4Coordinator op){
        this.quorumInfra = op.getQuorumInfra();
        this.value = op.getValue();
        this.qSeq = op.getqSeq();
        this.status = op.getStatus();
        this.isBlocking = op.isBlocking();
    }

    public void opInitiate(QuorumInfra quorumInfra, int qSeq, int value) {
        this.quorumInfra = quorumInfra;
        this.qSeq = qSeq;
        this.value = value;
        this.status = "GLOBAL_APPLY";
    }

    public void clear(){
        this.quorumInfra = null;
        this.qSeq = -1;
        this.value = -1;
        this.status = "FREE";
    }

    public void setQuorumInfra(QuorumInfra quorumInfra) {
        this.quorumInfra = quorumInfra;
    }

    public QuorumInfra getQuorumInfra() {
        return quorumInfra;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getqSeq() {
        return qSeq;
    }

    public int getValue() {
        return value;
    }

    public void setBlocking(boolean blocking) {
        this.isBlocking = blocking;
    }

    public boolean isBlocking() {
        return isBlocking;
    }
}
