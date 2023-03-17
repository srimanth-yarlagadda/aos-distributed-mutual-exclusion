import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.sql.*;
import java.util.concurrent.TimeUnit;

public class Client {
    private static boolean systemDebug = false;
    private Socket serverConnection;
    private static int Request = 1;
    private static int Release = 2;
    private static int Relinquish = 3;
    private static int Grant = 4;
    private static int Wait = 5;

    private static List<List<Integer>> quorumSet = new ArrayList<List<Integer>>();
    private static Set<Integer> grantList = new HashSet<Integer>();
    private static boolean CriticalSectionCompletion;

    // Establish connection with Server & create input & output stream objects
    public void lockServer(String serverIP, int port) {
        if (systemDebug) {System.out.println("Connection request: " + serverIP + port);}
        try {
            serverConnection = new Socket(serverIP, port);
            System.out.println("Connection Successful: " + serverConnection.getInetAddress().getHostName().toString());
            int serverID =  Integer.parseInt(serverConnection.getInetAddress().getHostName().toString().split("\\.")[0].substring(2,4));

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);


            DataOutputStream outChannel = new DataOutputStream(serverConnection.getOutputStream());
            DataInputStream inputDataStream = new DataInputStream(serverConnection.getInputStream());
            
            Request makeRequest = new Request();
            makeRequest.clientID = Integer.parseInt(InetAddress.getLocalHost().toString().split("\\.")[0].substring(2,4));
            makeRequest.requestTimestamp = new Timestamp(System.currentTimeMillis());

            objectOutputStream.writeObject(makeRequest);
            byte[] byteData = byteArrayOutputStream.toByteArray();
        
            outChannel.write(byteData);
            String requestString = makeRequest.clientID + "@" + makeRequest.requestTimestamp;
            System.out.println("Sending a request: " + requestString);

            while (true) {
                int getResponse = inputDataStream.readInt();
                if (getResponse == Grant) {
                    break;
                }
            }

            grantList.add(serverID);

            while (!CriticalSectionCompletion) {
                try {
                    TimeUnit.MICROSECONDS.sleep(5);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }
            
            // System.out.println("Releasing lock ... ");
            outChannel.writeInt(Release);
            serverConnection.close();
            System.out.println(String.format("Released server: \033[1m\033[32m%02d\033[0m, after " + requestString, serverID));
            return;

        } catch (IOException /*| InterruptedException*/ except) {
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
        List<Thread> threadList = new ArrayList<Thread>(); 
        // double requiredGrant = (double) (2*serverList.size())/3;
        int acquiredGrant = 0;
        for (int i = 0; i < serverList.size(); i++) {
            // System.out.println("Begin Lock attempt: " + i);
            final String serverAddress = serverList.get(i);
            if (systemDebug) {System.out.println("Begin Lock attempt: " + serverAddress);}
            Thread lockServerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final String serverIP = serverAddress.split(":")[0];
                    final String serverPort = serverAddress.split(":")[1];
                    new Client().lockServer(serverIP, Integer.parseInt(serverPort));
                }
            });
            lockServerThread.start();
            threadList.add(lockServerThread);
        }
        try {
            for (int i = 0; i < threadList.size(); i++) {
                Thread t = threadList.get(i);
                t.join();
            }
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }
        return;
    }

    public void criticalSection() {
        boolean criticalSectionGrant = false;
        while (!criticalSectionGrant) {
            for (int i = 0; i < quorumSet.size(); i++) {
                List<Integer> quorum = quorumSet.get(i);
                int required = quorum.size();
                for (int j = 0; j < quorum.size(); j++) {
                    int servID = quorum.get(j);
                    if (grantList.contains(servID)) {
                        if (systemDebug) {System.out.println("\n\n\nFound in grants " + servID);}
                        required--;
                    }
                }
                if (required <= 0) {
                    criticalSectionGrant = true;
                    break;
                }
            }
        }
        System.out.println("Entering critical section...");
        try {
            TimeUnit.SECONDS.sleep(6);
            CriticalSectionCompletion = true;
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }
        System.out.println("Exiting critical section !");
    }

    public static void main(String[] args) {
        /* IP address and Port declarations. Start connection */
        String ipaddress = "dc01.utdallas.edu";
        int port = 9038;
        // Client client =new Client();

        List<Integer> quorum1 = new ArrayList<Integer>();
        quorum1.add(1);
        quorum1.add(2);
        quorum1.add(3);
        quorumSet.add(quorum1);

        quorum1 = new ArrayList<Integer>();
        quorum1.add(1);
        quorum1.add(2);
        quorum1.add(4);
        quorumSet.add(quorum1);

        System.out.println("Quorum set:" + quorumSet);

        Random randomWaitTime = new Random();

        for (int i = 0; i < 3; i++) {
            grantList = new HashSet<Integer>();
            CriticalSectionCompletion = false;
            int waitTime = 5 + randomWaitTime.nextInt(5);
            try {

                TimeUnit.SECONDS.sleep(waitTime);

                Thread getGrantThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        new Client().getGrant(1);
                    }
                });
                Thread criticalSectionThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        new Client().criticalSection();
                    }
                });

                getGrantThread.start();
                criticalSectionThread.start();
                criticalSectionThread.join();
                getGrantThread.join();
                System.out.println("\n\n");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // client.startConnection(ipaddress, port);
        // System.out.println("\n=== END ===\n");

    }
}

class Request implements Serializable {
    int clientID;
    Timestamp requestTimestamp;
    boolean Grant;
}