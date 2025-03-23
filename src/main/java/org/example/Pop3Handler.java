package org.example;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Pop3Handler {
    private boolean userReceived = false;
    private boolean passReceived = false;
    private String username;
    public static final String MAIL_DIR = "mailserver/";

    // Traitement des commandes POP3 avec vérification d'ordre
    public String processCommand(String command, pop3Client client)
            throws InvalidPop3CommandOrderException, IOException {
        command = command.trim();
        if (command.equalsIgnoreCase("QUIT")) {
            client.performDeletions();
            reset();
            return "+OK Goodbye";
        }
        if (!userReceived) {
            if (command.toUpperCase().startsWith("USER")) {
                String[] parts = command.split(" ", 2);
                if (parts.length < 2) {
                    throw new InvalidPop3CommandOrderException("Missing username");
                }
                username = parts[1].trim();
                String userDir = MAIL_DIR + username;
                if (Files.exists(Paths.get(userDir)) && Files.isDirectory(Paths.get(userDir))) {
                    userReceived = true;
                    client.setUser(username);
                    return "+OK User accepted";
                } else {
                    throw new InvalidPop3CommandOrderException("No such user");
                }
            } else {
                throw new InvalidPop3CommandOrderException("USER command required");
            }
        } else if (!passReceived) {
            if (command.toUpperCase().startsWith("PASS")) {
                String[] parts = command.split(" ", 2);
                if (parts.length < 2) {
                    throw new InvalidPop3CommandOrderException("Missing password");
                }
                String passwordInput = parts[1].trim();
                // Vérifier le mot de passe dans un fichier central users.txt
                String usersFilePath = MAIL_DIR + "users.txt";
                try (BufferedReader reader = new BufferedReader(new FileReader(usersFilePath))) {
                    String line;
                    boolean valid = false;
                    while ((line = reader.readLine()) != null) {
                        String[] credentials = line.split(" ", 2);
                        if (credentials.length == 2 && credentials[0].equalsIgnoreCase(username)
                                && credentials[1].equals(passwordInput)) {
                            valid = true;
                            break;
                        }
                    }
                    if (!valid) {
                        throw new InvalidPop3CommandOrderException("Authentication failed");
                    }
                }
                passReceived = true;
                return "+OK Logged in";
            } else {
                throw new InvalidPop3CommandOrderException("PASS command required");
            }
        } else {
            // En état de Transaction : traiter STAT, LIST, RETR, DELE, NOOP, RSET, TOP
            String upper = command.toUpperCase();
            if (upper.equals("STAT")) {
                List<Path> files = client.getEmailFiles();
                long totalSize = 0;
                int count = 0;
                for (int i = 0; i < files.size(); i++) {
                    if (!client.isMarkedForDeletion(i)) {
                        count++;
                        try {
                            totalSize += Files.size(files.get(i));
                        } catch (IOException e) { }
                    }
                }
                return "+OK " + count + " " + totalSize;
            } else if (upper.startsWith("LIST")) {
                String[] partsList = command.split(" ");
                List<Path> files = client.getEmailFiles();
                List<Path> validFiles = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) {
                    if (!client.isMarkedForDeletion(i)) {
                        validFiles.add(files.get(i));
                    }
                }
                if (partsList.length == 1) {
                    StringBuilder response = new StringBuilder("+OK " + validFiles.size() + " messages\r\n");
                    int index = 1;
                    for (Path p : validFiles) {
                        try {
                            response.append(index++).append(" ").append(Files.size(p)).append("\r\n");
                        } catch (IOException e) {
                            response.append(index++).append(" 0\r\n");
                        }
                    }
                    response.append(".");
                    return response.toString();
                } else {
                    try {
                        int msgNum = Integer.parseInt(partsList[1].trim());
                        if (msgNum < 1 || msgNum > validFiles.size()) {
                            return "-ERR No such message";
                        }
                        Path p = validFiles.get(msgNum - 1);
                        return "+OK " + msgNum + " " + Files.size(p);
                    } catch (NumberFormatException e) {
                        return "-ERR Invalid message number";
                    } catch (IOException e) {
                        return "-ERR Error reading message size";
                    }
                }
            } else if (upper.startsWith("RETR")) {
                String[] partsRetr = command.split(" ", 2);
                if (partsRetr.length < 2) {
                    return "-ERR Missing message number";
                }
                int msgNumber;
                try {
                    msgNumber = Integer.parseInt(partsRetr[1].trim());
                } catch (NumberFormatException e) {
                    return "-ERR Invalid message number";
                }
                List<Path> files = client.getEmailFiles();
                List<Path> validFiles = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) {
                    if (!client.isMarkedForDeletion(i)) {
                        validFiles.add(files.get(i));
                    }
                }
                if (msgNumber < 1 || msgNumber > validFiles.size()) {
                    return "-ERR No such message";
                }
                Path filePath = validFiles.get(msgNumber - 1);
                StringBuilder response = new StringBuilder();
                try {
                    List<String> lines = Files.readAllLines(filePath);
                    response.append("+OK ").append(Files.size(filePath)).append(" octets\r\n");
                    for (String line : lines) {
                        response.append(line).append("\r\n");
                    }
                    response.append(".");
                } catch (IOException e) {
                    return "-ERR Unable to read message";
                }
                return response.toString();
            } else if (upper.startsWith("DELE")) {
                String[] partsDele = command.split(" ", 2);
                if (partsDele.length < 2) {
                    return "-ERR Missing message number";
                }
                int msgNumber;
                try {
                    msgNumber = Integer.parseInt(partsDele[1].trim());
                } catch (NumberFormatException e) {
                    return "-ERR Invalid message number";
                }
                List<Path> files = client.getEmailFiles();
                if (msgNumber < 1 || msgNumber > files.size()) {
                    return "-ERR No such message";
                }
                if (client.isMarkedForDeletion(msgNumber - 1)) {
                    return "-ERR Message " + msgNumber + " already deleted";
                }
                client.markMessageForDeletion(msgNumber - 1);
                return "+OK Message " + msgNumber + " marked for deletion";
            } else if (upper.equals("NOOP")) {
                return "+OK";
            } else if (upper.equals("RSET")) {
                client.resetDeletionMarks();
                return "+OK Deletion marks reset";
            }  else {
                return "-ERR Unknown command";
            }
        }
    }

    private void reset() {
        userReceived = false;
        passReceived = false;
        username = null;
    }
}
