package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class Pop3Server {
    private static final int PORT = 110;
    private static final int MAX_THREADS = 10;

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.execute(new Pop3ClientHandler(socket));
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
}

