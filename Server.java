import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.net.InetAddress;
import java.sql.*;
import java.util.concurrent.TimeUnit;
import static java.lang.System.out;


public class Server implements Runnable {

    private static boolean systemDebug = true;
    
    private static Semaphore tokenSemaphore, pqSemaphore;
    private static ServerSocket serverSocket;
    private static String serverID;
    private Socket clientSocket;
    private static Socket parentConnectionSocket;
    private static int debug = 0;
    private static Integer myPort;

    public static int activeClients = 2;
    public static boolean killMain = false;
    public static int killTotal = 5;

    private static int Request = 1;
    private static int Release = 2;
    private static int Relinquish = 3;
    private static int Grant = 4;
    private static int Wait = 5;
    private static int Acknowledgement = 6;
    private static int ChildShutdown = 10;

    private boolean waitForGrant;
    private int clientResponseCode;
    private Server parentServer;

    private static int outGoingMessages = 0;
    private static int incomingMessages = 0;

    private static PriorityQueue<Request> requestQueue = new PriorityQueue<Request>(100,new Comparator<Request>() {
        @Override
        public int compare(Request rone, Request rtwo) {
            return Long.compare(rone.requestTimestamp.getTime(), rtwo.requestTimestamp.getTime());
        }
    });

    public void setWaitForGrant(boolean val) {waitForGrant = val;}
    public void setClientResponseCode(int val) {clientResponseCode = val;}
    public Server initThreadStatus(boolean val1, int val2) {
        this.waitForGrant = val1;
        this.clientResponseCode = val2;
        return this;
    }
    
    // Create ServerSocket on the given port
    public void startServer(int port) {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: \033[1m\033[32m" + currentThread + "\033[0m maintaining Server");
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started: " + serverSocket.getLocalPort());
            // Accept and manage clients
            while (!killMain) {
                try {
                    final Socket receiveClientSocket = serverSocket.accept();
                    String clientAddress = receiveClientSocket.getInetAddress().getHostName().toString().split("\\.")[0];
                    final int clientID = Integer.parseInt(clientAddress.substring(2,4));
                    final Server myNewServer = new Server();
                    Thread clientThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (clientID > 1 && clientID <= 7) {
                                myNewServer.manageChild(receiveClientSocket);
                            } else {
                                myNewServer.initThreadStatus(true, 0);
                                myNewServer.manageClients(receiveClientSocket);
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
            if (systemDebug) {
                System.err.println("Server creation failed !");
                e.printStackTrace();
            }
        }
        System.out.println("\n\n\nEnding Server");
    }

    public void connectTreeParent() {
        int numParentPort = Integer.parseInt(serverID.substring(2,4));
        String parentAddress = String.format("%02d", numParentPort / 2);
        if (parentAddress.equals("00")) {
            out.println("\033[1m\033[32m[S1]\033[0m");
        } else {
            parentAddress = "dc" + parentAddress + ".utdallas.edu";
            int parentPort = 9037 + numParentPort/2;
            System.out.println("Resolved parent address " + parentAddress + " | " + parentPort);
            int retryConnection = 2;
            while (retryConnection > 0) {
                try {
                    parentConnectionSocket = new Socket(parentAddress, parentPort);
                    out.println(".... connected to parent.");
                    break;
                } catch (IOException exc) {
                    try {TimeUnit.SECONDS.sleep(3);} catch (InterruptedException e) {e.printStackTrace();}
                    retryConnection--;
                    out.println("retrying connection to parent ....");
                }
            }
            try {
                DataInputStream inputDataStream = new DataInputStream(parentConnectionSocket.getInputStream());
                while (killTotal > 0) {
                    killTotal = inputDataStream.readInt();
                }
            } catch (IOException except) {
                System.err.println("Failed connecting to parent: " + except);
            }
        }

        while (activeClients > 0) {
            try {
                TimeUnit.MICROSECONDS.sleep(1);
            } catch (InterruptedException exc) {exc.printStackTrace();}
        }

        if (!parentAddress.equals("00")) {
            try {
                DataOutputStream outputDataStream = new DataOutputStream(parentConnectionSocket.getOutputStream());
                killMain = true;
                outputDataStream.writeInt(ChildShutdown);
                System.out.println("Sending shutdown confirmation !");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (systemDebug) {out.println("\033[1m\033[32m Main KILLED \033[0m");}
            killMain = true;
        }
        return;
    }

    public Server assignSocket(Socket socket) {
        clientSocket = socket;
        return this;
    }

    public Server assignParent(Server parent) {
        this.parentServer = parent; return this;
    }


    public void interruptionListener(DataInputStream inputDataStream, Request curReq) {
        try {
            clientResponseCode = inputDataStream.readInt(); incomingMessages++;
            if (clientResponseCode == Release) {
                pqSemaphore.acquire();
                requestQueue.remove(curReq);
                pqSemaphore.release();
                parentServer.initThreadStatus(false, clientResponseCode);
                out.println("Ending interruptionListener: " + clientResponseCode);
            } else {
                out.println("interruptionListener got unknown code");
            }
        } catch (IOException | InterruptedException e) {
            try {
                pqSemaphore.acquire();
                requestQueue.remove(curReq);
                pqSemaphore.release();
                parentServer.initThreadStatus(false, clientResponseCode);
            } catch (InterruptedException ex) { ex.printStackTrace(); }
            out.println("\033[1m\033[33mInterruptionListener terminated! " + curReq.clientID + "\033[0m");
            // e.printStackTrace();
        }
        return;
    }

    public void manageClients(Socket clientSocket) {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " handling\033[1m\033[32m client:\033[0m " + clientSocket.getInetAddress().getHostName().toString() + "[" + waitForGrant + " " + clientResponseCode +"]");
        try {
            final DataInputStream inputDataStream = new DataInputStream(clientSocket.getInputStream());
            byte[] getRequestBytes = new byte[1024];
            int reqSize = inputDataStream.read(getRequestBytes);
            byte[] getRequestBytesFinal = new byte[reqSize];
            System.arraycopy(getRequestBytes, 0, getRequestBytesFinal, 0, reqSize);

            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(getRequestBytesFinal);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            DataOutputStream outputDataStream = new DataOutputStream(clientSocket.getOutputStream());
            final Request clientRequest = (Request) objectInputStream.readObject();incomingMessages++;
            if (clientRequest.status) {
                killTotal --;
                outputDataStream.writeInt(Acknowledgement); outGoingMessages++;
                System.out.println("Thread: " + currentThread + " \033[1m\033[32mcomplete\033[0m, client: " + clientSocket.getInetAddress().getHostName().toString());
                return;
            }
            requestQueue.add(clientRequest);
            System.out.println("Received a request: " + clientRequest.clientID + " @ " + clientRequest.requestTimestamp + " => " + clientRequest.status);

            final Server newListner = new Server();
            newListner.assignParent(this);
            Thread interruptionListenerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        newListner.interruptionListener(inputDataStream, clientRequest);
                    }
                });
            interruptionListenerThread.start();
            while ((clientRequest.status == false) & waitForGrant) {
                if (false) {System.out.println("Status: " + clientRequest.status);}
                try {
                    TimeUnit.MICROSECONDS.sleep(1);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }

            // try {
            //     TimeUnit.SECONDS.sleep(6);
            // } catch (InterruptedException exc) {
            //     exc.printStackTrace();
            // }

            if (waitForGrant) {
                outputDataStream.writeInt(Grant); outGoingMessages++;
                System.out.println(String.format("=> Request \033[1m\033[33m%2d\033[0m Granted !", clientRequest.clientID));
                while (true) {
                    if (clientResponseCode == Release) {
                        out.println("Got release code, breaking now " + clientRequest.clientID);
                        break;
                    } else if (!interruptionListenerThread.isAlive()) { 
                        out.println("\033[1m\033[33mILThread dead\033[0m, breaking now " + clientRequest.clientID);
                        break;
                    } else {
                        try {
                            TimeUnit.MICROSECONDS.sleep(3);
                            // out.println("response code not fulfilled: " + clientResponseCode);
                        } catch (InterruptedException exc) {
                            exc.printStackTrace();
                        }
                    }
                }
                tokenSemaphore.release();
                out.println("Semaphore release !");
                out.println("\033[1m\033[33mReleased by "+clientRequest.clientID+"\033[0m");
            } else {
                out.println("\033[1m\033[33m======>>Else statement..."+clientRequest.clientID+"\033[0m");
                if (clientRequest.status == true) {
                    tokenSemaphore.release();
                    out.println("\033[1m\033[33mReleased by "+clientRequest.clientID+"\033[0m");
                }
            }
            
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
                        TimeUnit.MICROSECONDS.sleep(1);
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


    public void run() {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Thread: " + currentThread + " in run() method !");
    }

    public void grantOperations() {
        while (!killMain) {
            if (requestQueue.size() > 0) {
                // out.println("SOMETHING IN Q: " + requestQueue.toString());
                // out.println("\033[1m\033[33mAcquire...\033[0m");
                boolean acquire = tokenSemaphore.tryAcquire();
                if (acquire) {
                    out.println("\033[1m\033[33mAcquired!\033[0m");
                    try {
                        pqSemaphore.acquire();
                        Request currentGrant = requestQueue.poll();
                        currentGrant.status = true;
                        out.println("\033[1m\033[33mGranted request: "+currentGrant.clientID+"\033[0m");
                        pqSemaphore.release();
                    } catch (InterruptedException e) {e.printStackTrace();}
                } else {
                    // out.println("[no acq] SOMETHING IN Q: " + requestQueue.toString());
                    try {
                        TimeUnit.MICROSECONDS.sleep(1);
                    } catch (InterruptedException exc) {
                        exc.printStackTrace();
                    }
                }
            } else {
                try {
                    TimeUnit.MICROSECONDS.sleep(1);
                    // out.println("EMPTY Q: " + requestQueue.toString());
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
        pqSemaphore = new Semaphore(1);

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
            serverSocket.close();
            serverThread.join();
            System.out.println("\033[1;31mAll threads joined, completing process!\033[0m");
            out.println("Total incoming messages: " + incomingMessages + ", outgoing messagest: " + outGoingMessages + ".");
            System.exit(0);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }
}


class Request implements Serializable {
    int clientID;
    Timestamp requestTimestamp;
    boolean status = false;
}