import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.net.InetAddress;
import java.sql.*;
import java.util.concurrent.TimeUnit;


public class Server implements Runnable {

    private static boolean systemDebug = false;
    
    private static Semaphore tokenSemaphore;
    private static ServerSocket serverSocket;
    private static String serverID;
    private Socket clientSocket;
    private static Socket parentConnectionSocket;
    private static int debug = 0;
    private static Integer myPort;

    public static int activeClients = 0;
    public static boolean killMain = false;
    public static int killTotal = 1;

    private static int Request = 1;
    private static int Release = 2;
    private static int Relinquish = 3;
    private static int Grant = 4;
    private static int Wait = 5;
    private static int Acknowledgement = 6;
    private static int ChildShutdown = 10;

    private static Deque<Request> requestQueue = new ArrayDeque<>();
    
    // Create ServerSocket on the given port
    public void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started: " + serverSocket.getLocalPort());
            // Accept and manage clients
            while (!killMain) {
                try {
                    final Socket receiveClientSocket = serverSocket.accept();
                    String clientAddress = receiveClientSocket.getInetAddress().getHostName().toString().split("\\.")[0];
                    final int clientID = Integer.parseInt(clientAddress.substring(2,4));
                    Thread clientThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (clientID > 1 && clientID <= 7) {
                                new Server().manageChild(receiveClientSocket);
                            } else {
                                new Server().manageClients(receiveClientSocket);
                            }
                        }
                    });
                    clientThread.start();
                } catch (IOException except) {
                    break;
                }
            }
            serverSocket.close();
            return;
        } catch (IOException e) {
            System.err.println("Server creation failed !");
            e.printStackTrace();
        }
        System.out.println("\n\n\nEnding Server");
    }

    public void connectTreeParent() {
        int numParentPort = Integer.parseInt(serverID.substring(2,4));
        String parentAddress = String.format("%02d", numParentPort / 2);
        if (parentAddress.equals("00")) {
            System.out.println("Root !");
            return;
        } else {
            parentAddress = "dc" + parentAddress + ".utdallas.edu";
            int parentPort = 9037 + numParentPort/2;
            System.out.println("Resolved parent address " + parentAddress + " | " + parentPort);
            int retryConnection = 2;
            try {
                parentConnectionSocket = new Socket(parentAddress, parentPort);
                DataOutputStream outputDataStream = new DataOutputStream(parentConnectionSocket.getOutputStream());
                DataInputStream inputDataStream = new DataInputStream(parentConnectionSocket.getInputStream());
                while (killTotal > 0) {
                    killTotal = inputDataStream.readInt();
                }
                while (activeClients > 0) {
                    try {
                        TimeUnit.MICROSECONDS.sleep(5);
                    } catch (InterruptedException exc) {exc.printStackTrace();}
                }
                killMain = true;
                outputDataStream.writeInt(ChildShutdown);
                System.out.println("Sending shutdown confirmation !");
                return;
            } catch (IOException except) {
                System.err.println("Failed connecting to parent: " + except);
            }
        }
    }

    public Server assignSocket(Socket socket) {
        clientSocket = socket;
        return this;
    }


    public void manageClients(Socket clientSocket) {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " handling\033[1m\033[32m client:\033[0m " + clientSocket.getInetAddress().getHostName().toString());
        try {

            DataInputStream inputDataStream = new DataInputStream(clientSocket.getInputStream());
            byte[] getRequestBytes = new byte[1024];
            int reqSize = inputDataStream.read(getRequestBytes);
            byte[] getRequestBytesFinal = new byte[reqSize];
            System.arraycopy(getRequestBytes, 0, getRequestBytesFinal, 0, reqSize);

            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(getRequestBytesFinal);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            DataOutputStream outputDataStream = new DataOutputStream(clientSocket.getOutputStream());
            Request clientRequest = (Request) objectInputStream.readObject();
            if (clientRequest.status) {
                killTotal --;
                outputDataStream.writeInt(Acknowledgement);
                System.out.println("Thread: " + currentThread + " \033[1m\033[32mcomplete\033[0m, client: " + clientSocket.getInetAddress().getHostName().toString());
                return;
            }
            requestQueue.addLast(clientRequest);
            System.out.println("Received a request: " + clientRequest.clientID + " @ " + clientRequest.requestTimestamp + " => " + clientRequest.status);

            while (clientRequest.status == false) {
                if (systemDebug) {System.out.println("Status: " + clientRequest.status);}
                try {
                    TimeUnit.MICROSECONDS.sleep(6);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }

            System.out.println(String.format("=> Request %2d Granted !", clientRequest.clientID));

            
            outputDataStream.writeInt(Grant);
            while (true) {
                int getRelease = inputDataStream.readInt();
                if (getRelease == Release) {
                    break;
                }
            }

            tokenSemaphore.release();
            System.out.println("Thread: " + currentThread + " \033[1m\033[32mcomplete\033[0m, client: " + clientSocket.getInetAddress().getHostName().toString());

        } catch (IOException | ClassNotFoundException except) {
            except.printStackTrace();
        }
    }

    public void manageChild(Socket clientSocket) {
        long currentThread = Thread.currentThread().getId();
        String childID = clientSocket.getInetAddress().getHostName().toString();
        System.out.println("Thread: " + currentThread + " handling\033[1m\033[34m child:\033[0m " + childID);
        try {
            DataOutputStream outputDataStream = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream inputDataStream = new DataInputStream(clientSocket.getInputStream());
            int currentKillVal = killTotal;
            while (true) {
                try {
                    while (currentKillVal == killTotal) {
                        TimeUnit.MICROSECONDS.sleep(2);
                    }
                    outputDataStream.writeInt(killTotal);
                    currentKillVal = killTotal;
                    if (killTotal == 0) {break;}
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }
            int childShutdown = inputDataStream.readInt();
            activeClients--;
            if (childShutdown == ChildShutdown) {System.out.println(childID + " shutting down !");}
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void grantRequests() {
        while (true) {
            // Do nothing
        }
    }

    public void run() {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " in run() method !");
    }

    public void grantOperations() {
        while (!killMain) {
            if (systemDebug) {System.out.println("Grant ops" + requestQueue.size());}
            if (requestQueue.size() > 0) {
                try {
                    tokenSemaphore.acquire();
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
                Request currentGrant = requestQueue.removeFirst();
                currentGrant.status = true;
                System.out.println("Granted request: " + currentGrant.clientID);
                try {
                    TimeUnit.SECONDS.sleep(6);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            } else {
                try {
                    TimeUnit.MICROSECONDS.sleep(5);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }
        } 
        return;
    }

    public static void main(String[] args) {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            serverID = localAddress.toString().split("\\.")[0];
            myPort = 9037 + Integer.parseInt(serverID.substring(2,4));
            System.out.println("Server | Port ===>   " + serverID + " | " + myPort);
            if (Integer.parseInt(serverID.substring(2,4)) >= 4) {
                activeClients = 0;
            }
        } catch (UnknownHostException except) {
            System.err.println("Host Unknown");
            except.printStackTrace();
        }

        tokenSemaphore = new Semaphore(1);
        
        Runnable createServer = new Runnable() {
            public void run() {
                try {
                    serverSocket = new ServerSocket(myPort);
                    System.out.println("Server started:\n" + serverSocket);         
                } catch (IOException except) {
                    System.err.println("Server creation failed !");
                    except.printStackTrace();
                }
            }
        };

        // Create and start a server thread
        // Thread serverThread = new Thread(() -> new Server().startServer(myPort));
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Server().startServer(myPort);
            }
        });
        serverThread.start();

        Thread grantRequestsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Server().grantRequests();
            }
        });
        grantRequestsThread.start();

        Thread connectParentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Server().connectTreeParent();
            }
        });
        connectParentThread.start();

        Thread grantOperationsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Server().grantOperations();
            }
        });
        grantOperationsThread.start();

        try {
            grantOperationsThread.join();
            connectParentThread.join();
            // serverThread.join();
            System.out.println("\033[1;31mAll threads joined, completing process!\033[0m");
        } catch (InterruptedException e) {e.printStackTrace();}
        


    }
}


class Request implements Serializable {
    int clientID;
    Timestamp requestTimestamp;
    boolean status = false;
}

