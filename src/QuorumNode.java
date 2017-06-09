/**
 * Created by yuan on 6/2/17.
 */
public class QuorumNode {
    Address info;
    boolean isAlive;

    public QuorumNode(Address info) {
        this.info = info;
        this.isAlive = true;
    }

    public Address getInfo() {
        return info;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setInfo(Address info) {
        this.info = info;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }
}
