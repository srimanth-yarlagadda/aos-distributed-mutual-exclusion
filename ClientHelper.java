import java.util.*;

public class ClientHelper {

    private static List<List<Integer>> quorumSet = new ArrayList<List<Integer>>(); 

    public List<List<Integer>> getQuorums() {
        List<Integer> quorum1;
        quorum1 = new ArrayList<Integer>(Arrays.asList(1,2,4));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(1,2,5));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(1,3,6));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(1,3,7));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(2,3,4,6));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(2,3,4,7));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(2,3,5,6));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(2,3,5,7));
        quorumSet.add(quorum1);

        quorum1 = new ArrayList<Integer>(Arrays.asList(2,4,6,7));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(2,5,6,7));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(4,5,3,6));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(4,5,3,7));
        quorumSet.add(quorum1);

        quorum1 = new ArrayList<Integer>(Arrays.asList(1,4,5));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(1,6,7));
        quorumSet.add(quorum1);
        quorum1 = new ArrayList<Integer>(Arrays.asList(4,5,6,7));
        quorumSet.add(quorum1);
        return quorumSet;
    }

    // Since servers are in binary tree, this function is used to generate their address and ports.
    public List<String> generateQuorumIPAddresses() {
        Set<String> serverList = new HashSet<String>();
        for (int serverID = 1; serverID <= 3; serverID++) {
            serverList.add(String.format("dc%02d.utdallas.edu:%d", serverID, 9037 + serverID));
            serverList.add(String.format("dc%02d.utdallas.edu:%d", (serverID*2), 9037 + (serverID*2)));
            serverList.add(String.format("dc%02d.utdallas.edu:%d", (serverID*2)+1, 9037 + (serverID*2) +1));
        }
        return new ArrayList<String>(serverList);
    }

    public static void main(String[] args) {

    }
}