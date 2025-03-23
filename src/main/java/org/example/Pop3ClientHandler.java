package org.example;


import java.io.*;
import java.net.Socket;

public class Pop3ClientHandler extends Thread {
    private Socket socket;

    public Pop3ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            pop3Client client = new pop3Client(socket);
            client.run();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}

