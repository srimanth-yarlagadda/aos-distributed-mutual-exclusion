import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Client {
    private Socket serverConnection;
    private static int Request = 1;
    private static int Release = 2;
    private static int Relinquish = 3;
    private static int Grant = 4;
    private static int Wait = 5;

    // Establish connection with Server & create input & output stream objects
    public void lockServer(String serverIP, int port) {
        System.out.println("Connection request: " + serverIP + port);
        try {
            serverConnection = new Socket(serverIP, port);
            System.out.println("Connection Successful: " + serverConnection);
        } catch (IOException except) {
            System.err.println("Connection failed!");
            except.printStackTrace();
        }
    }

    public List<String> generateQuorumIPAddresses(int serverID) {
        List<String> serverList = new ArrayList<String>();
        if (serverID >= 4) {
            serverList.add(String.format("dc%02d.utdallas.edu:%d", serverID, 9037 + serverID));
        } else {
            serverList.add(String.format("dc%02d.utdallas.edu:%d", serverID, 9037 + serverID));
            serverList.add(String.format("dc%02d.utdallas.edu:%d", (serverID*2), 9037 + (serverID*2)));
            serverList.add(String.format("dc%02d.utdallas.edu:%d", (serverID*2)+1, 9037 + (serverID*2) +1));
        }
        return serverList;
    }

    public void getGrant(int serverID) {
        List<String> serverList = generateQuorumIPAddresses(serverID);
        double requiredGrant = (double) (2*serverList.size())/3;
        int acquiredGrant = 0;
        for (int i = 0; i < serverList.size(); i++) {
            // System.out.println("Begin Lock attempt: " + i);
            final String serverAddress = serverList.get(i);
            System.out.println("Begin Lock attempt: " + serverAddress + serverList.get(i));
            Thread lockServerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final String serverIP = serverAddress.split(":")[0];
                    final String serverPort = serverAddress.split(":")[1];
                    new Client().lockServer(serverIP, Integer.parseInt(serverPort));
                }
            });
            lockServerThread.start();
        }
        System.out.println(requiredGrant);
    }

    public static void main(String[] args) {
        /* IP address and Port declarations. Start connection */
        String ipaddress = "dc01.utdallas.edu";
        int port = 9038;
        // Client client =new Client();

        Random randomServerIDGenerator = new Random();
        final int mutexGrantServer = 1 + randomServerIDGenerator.nextInt(8);
        Thread getGrantThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Client().getGrant(mutexGrantServer);
            }
        });
        getGrantThread.start();

        // client.startConnection(ipaddress, port);

        System.out.println("\n=== END ===\n");

    }
}