import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.net.InetAddress;

public class Server implements Runnable {
    
    private static ServerSocket serverSocket;
    private Socket clientSocket;
    private static int debug = 0;
    private static Integer myPort;
    
    // Create ServerSocket on the given port
    public void startServerInt(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started: " + serverSocket.getLocalPort());

            while (true) {
                try {
                    Socket receiveClientSocket = serverSocket.accept();
                    Thread clientThread = new Thread(() -> manageClients(receiveClientSocket));
                    clientThread.start();
                    // (new Thread(new Server().assignSocket(receiveClientSocket))).start();
                } catch (IOException except) {
                    break;
                }
            } 


        } catch (IOException e) {
            System.err.println("Server creation failed !");
            e.printStackTrace();
        }
    }

    public Server assignSocket(Socket socket) {
        clientSocket = socket;
        return this;
    }


    public void manageClients(Socket clientSocket) {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Started Thread: " + currentThread + " handling client: " + clientSocket);
    }

    public void run() {
        long currentThread = Thread.currentThread().getId();
        System.out.println("Started Thread: " + currentThread + " in run() method !");
    }


    // public void acceptClients() {
    //     while (true) {
    //         try {
    //             Socket receiveClientSocket = serverSocket.accept();
    //             Thread clientThread = new Thread(() -> new Server().manageClients(receiveClientSocket));
    //             clientThread.start();
    //             // (new Thread(new Server().assignSocket(receiveClientSocket))).start();
    //         } catch (IOException except) {
    //             break;
    //         }
    //     }
    // }

    public static void main(String[] args) {
        if (args.length != 0) { /* If debug argument proved, change flag */
            if (args[0].equals("1")) {
                debug = 1;
            }
        }

        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            String serverID = localAddress.toString().split("\\.")[0];
            myPort = 9037 + Integer.parseInt(serverID.substring(2,4));
            System.out.println(serverID + "   <=== Server | Port ===>   " + myPort);
        } catch (UnknownHostException except) {
            System.err.println("Host Unknown");
            except.printStackTrace();
        }
        Server server=new Server();
        // server.startServerInt(myPort); /* Port: arbitrarily chosen based on my NetID */

        Runnable createServer = new Runnable() {
            public void run() {
                try {
                    serverSocket = new ServerSocket(9038);
                    System.out.println("Server started:\n" + serverSocket);         
                } catch (IOException e) {
                    System.err.println("Server creation failed !");
                    e.printStackTrace();
                }
            }
        };

        // Thread thread1 = new Thread(new Server()::startServerInt);
        Thread serverThread = new Thread(() -> new Server().startServerInt(myPort));
        // Thread thread2 = new Thread(new MyThread()::method2);

        // Start the threads
        serverThread.start();
        // thread2.start();


        // ConnectionHandler startServer = new ConnectionHandler(createServer);
        // startServer.run();

        // if (serverID.equals("dc02")) {
            
        // }


    }
}

