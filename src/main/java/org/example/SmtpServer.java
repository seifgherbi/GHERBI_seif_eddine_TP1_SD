package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmtpServer {
    private static final int PORT = 25;

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(new SmtpClientHandler(socket));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        pool.shutdown();
    }
}

