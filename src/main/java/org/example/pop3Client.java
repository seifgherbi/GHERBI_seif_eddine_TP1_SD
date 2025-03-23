package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;

public class pop3Client extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String user;
    private Set<Integer> deletionMarks = new HashSet<>();

    public static final String MAIL_DIR = "mailserver/";

    public pop3Client(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    public List<Path> getEmailFiles() {
        List<Path> files = new ArrayList<>();
        if (user == null) return files;
        String userDir = MAIL_DIR + user;
        try {
            Path dirPath = java.nio.file.Paths.get(userDir);
            if (java.nio.file.Files.exists(dirPath) && java.nio.file.Files.isDirectory(dirPath)) {
                java.nio.file.Files.list(dirPath).forEach(files::add);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collections.sort(files);
        return files;
    }

    public void markMessageForDeletion(int index) {
        deletionMarks.add(index);
    }

    public boolean isMarkedForDeletion(int index) {
        return deletionMarks.contains(index);
    }

    public void resetDeletionMarks() {
        deletionMarks.clear();
    }

    public void performDeletions() {
        if (user == null) return;
        List<Path> files = getEmailFiles();
        for (Integer index : deletionMarks) {
            if (index >= 0 && index < files.size()) {
                try {
                    java.nio.file.Files.delete(files.get(index));
                } catch (IOException e) {
                    System.err.println("Error deleting file: " + files.get(index).toString());
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            out.println("+OK POP3 server ready");
            String command;
            Pop3Handler handler = new Pop3Handler();
            while ((command = in.readLine()) != null) {
                String response;
                try {
                    response = handler.processCommand(command, this);
                } catch (InvalidPop3CommandOrderException e) {
                    response = "-ERR " + e.getMessage();
                }
                out.println(response);
                if (response.startsWith("+OK Goodbye"))
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }
}

