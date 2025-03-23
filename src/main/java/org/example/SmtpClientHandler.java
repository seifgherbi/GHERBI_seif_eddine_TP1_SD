package org.example;

import java.io.*;
import java.net.Socket;

public class SmtpClientHandler extends Thread {
    private Socket socket;
    private SmtpHandler handler;

    public SmtpClientHandler(Socket socket) {
        this.socket = socket;
        this.handler = new SmtpHandler();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Envoyer le message d'accueil
            out.println("220 Simple Mail Transfer Service Ready");
            String command;
            while ((command = in.readLine()) != null) {
                String response;
                try {
                    response = handler.processCommand(command);
                } catch (InvalidCommandOrderException e) {
                    response = "503 " + e.getMessage();
                } catch (IOException e) {
                    response = "451 Server error: " + e.getMessage();
                }
                if (!response.isEmpty()) {
                    out.println(response);
                }
                if (response.startsWith("221"))
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ex) { }
        }
    }
}

