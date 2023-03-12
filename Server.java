import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.net.InetAddress;

public class Server implements Runnable {
    
    private static Semaphore tokenSemaphore;
    private static ServerSocket serverSocket;
    private static String serverID;
    private Socket clientSocket;
    private static Socket parentConnectionSocket;
    private static int debug = 0;
    private static Integer myPort;

    private static int Request = 1;
    private static int Release = 2;
    private static int Relinquish = 3;
    private static int Grant = 4;
    private static int Wait = 5;
    
    // Create ServerSocket on the given port
    public void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started: " + serverSocket.getLocalPort());
            // Accept and manage clients
            while (true) {
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

        } catch (IOException e) {
            System.err.println("Server creation failed !");
            e.printStackTrace();
        }
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
        System.out.println("Thread: " + currentThread + " handling\033[1m\033[32m client: \033[0m " + clientSocket);
    }

    public void manageChild(Socket clientSocket) {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " handling\033[1m\033[34m child: \033[0m" + clientSocket);
    }

    public void run() {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " in run() method !");
    }

    public static void main(String[] args) {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            serverID = localAddress.toString().split("\\.")[0];
            myPort = 9037 + Integer.parseInt(serverID.substring(2,4));
            System.out.println("Server | Port ===>   " + serverID + " | " + myPort);
        } catch (UnknownHostException except) {
            System.err.println("Host Unknown");
            except.printStackTrace();
        }
        tokenSemaphore = new Semaphore(1);

        // Server server=new Server();

        Runnable createServer = new Runnable() {
            public void run() {
                try {
                    serverSocket = new ServerSocket(myPort);
                    System.out.println("Server started:\n" + serverSocket);         
                } catch (IOException e) {
                    System.err.println("Server creation failed !");
                    e.printStackTrace();
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

        Thread connectParentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Server().connectTreeParent();
            }
        });
        connectParentThread.start();
        // System.out.println("\n=== SERVER END ===\n");
    }
}

