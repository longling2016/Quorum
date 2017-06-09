
public class QuorumInfra {
    QuorumNode coordinator;
    QuorumNode quorumNodes[];

    public QuorumInfra(QuorumNode coordinator, QuorumNode[] quorumNodes) {
        this.coordinator = coordinator;
        this.quorumNodes = quorumNodes;
    }

    public QuorumNode getCoordinator() {
        return coordinator;
    }

    public QuorumNode[] getQuorumNodes() {
        return quorumNodes;
    }
}
