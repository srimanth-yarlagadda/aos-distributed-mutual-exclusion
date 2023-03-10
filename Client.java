import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Client {
    private Socket serverConnection;

    // Establish connection with Server & create input & output stream objects
    public void startConnection(String serverIP, int port) {
        try {
            serverConnection = new Socket(serverIP, port);
            System.out.println("Connection Successful: " + serverConnection);
        } catch (IOException except) {
            System.err.println("Connection failed!");
            except.printStackTrace();
        }
    }

    public static void main(String[] args) {
        /* IP address and Port declarations. Start connection */
        String ipaddress = "dc01.utdallas.edu";
        int port = 9038;
        Client connect =new Client();
        connect.startConnection(ipaddress, port);

        System.out.println("\n***END\n");

    }
}