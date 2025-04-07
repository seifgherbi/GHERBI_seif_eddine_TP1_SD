package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class popserveurfinal {
    private static final int PORT = 110;
    private static final String MAIL_DIR = "mailserver/";
    private static final Set<String> VALID_COMMANDS = new HashSet<>(Arrays.asList(
            "USER", "PASS", "STAT", "LIST", "RETR", "DELE", "NOOP", "RSET", "TOP", "UIDL", "QUIT"));
    private static final String RESPONSE_OK = "+OK";
    private static final String RESPONSE_ERR = "-ERR";
    private static final String RESPONSE_BYE = "+OK Bye";
    private static final int THREAD_POOL_SIZE = 2;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                threadPool.execute(new POP3ClientHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static List<String> loadMessages(String user) {
        List<String> messages = new ArrayList<>();
        File userDir = new File(MAIL_DIR + user);

        if (!userDir.exists() || !userDir.isDirectory()) {
            System.out.println("Directory not found for user: " + user);
            return messages;
        }

        File[] files = userDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.out.println("No messages found for user: " + user);
            return messages;
        }

        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder message = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    message.append(line).append("\r\n");
                }
                messages.add(message.toString());
            } catch (IOException e) {
                System.err.println("Error reading file " + file.getName() + ": " + e.getMessage());
            }
        }
        return messages;
    }

    private static void deleteMarkedMessages(String user, Set<Integer> deletedMessages) {
        File userDir = new File(MAIL_DIR + user);
        File[] files = userDir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (deletedMessages.contains(i + 1)) {
                    if (!files[i].delete()) {
                        System.err.println("Failed to delete file: " + files[i].getName());
                    }
                }
            }
        }
    }

    private static String generateUID(int msgNum) {
        return "UID" + msgNum;
    }

    private static String extractCommand(String line) {
        if (line == null || line.trim().isEmpty()) return "";
        String[] parts = line.trim().split("\\s+", 2);
        return parts[0].toUpperCase();
    }

    private static boolean isValidCommand(String command) {
        return VALID_COMMANDS.contains(command);
    }

    private static int parseMessageNumber(String argument) {
        try {
            return Integer.parseInt(argument.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static class POP3ClientHandler implements Runnable {
        private final Socket socket;

        public POP3ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String user = null;
                List<String> messages = new ArrayList<>();
                Set<Integer> deletedMessages = new HashSet<>();
                String state = "AUTHORIZATION";

                out.println(RESPONSE_OK + " POP3 server ready");

                while (true) {
                    String line = in.readLine();
                    if (line == null) break;

                    String command = extractCommand(line);
                    String argument = line.contains(" ") ? line.substring(line.indexOf(" ") + 1).trim() : null;

                    if (!isValidCommand(command)) {
                        out.println(RESPONSE_ERR + " Command not recognized");
                        continue;
                    }

                    if ("QUIT".equals(command)) {
                        if ("TRANSACTION".equals(state)) {
                            state = "UPDATE";
                            deleteMarkedMessages(user, deletedMessages);
                        }
                        out.println(RESPONSE_BYE);
                        break;
                    }

                    switch (state) {
                        case "AUTHORIZATION":
                            if ("USER".equals(command)) {
                                if (argument != null && !argument.isEmpty()) {
                                    user = argument;
                                    if (new File(MAIL_DIR + user).exists()) {
                                        out.println(RESPONSE_OK + " User accepted");
                                    } else {
                                        out.println(RESPONSE_ERR + " User not found");
                                    }
                                } else {
                                    out.println(RESPONSE_ERR + " USER command requires an argument");
                                }
                            } else if ("PASS".equals(command)) {
                                if (user != null) {
                                    if (argument != null && !argument.isEmpty()) {
                                        out.println(RESPONSE_OK + " Authentication successful");
                                        state = "TRANSACTION";
                                        messages = loadMessages(user);
                                    } else {
                                        out.println(RESPONSE_ERR + " PASS command requires an argument");
                                    }
                                } else {
                                    out.println(RESPONSE_ERR + " Provide USER first");
                                }
                            } else {
                                out.println(RESPONSE_ERR + " Authentication required");
                            }
                            break;

                        case "TRANSACTION":
                            switch (command) {
                                case "STAT":
                                    lock.lock();
                                    try {
                                        int count = 0;
                                        int size = 0;
                                        for (int i = 0; i < messages.size(); i++) {
                                            if (!deletedMessages.contains(i + 1)) {
                                                count++;
                                                size += messages.get(i).length();
                                            }
                                        }
                                        out.println(RESPONSE_OK + " " + count + " " + size);
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "LIST":
                                    lock.lock();
                                    try {
                                        if (argument != null) {
                                            int msgNum = parseMessageNumber(argument);
                                            if (msgNum > 0 && msgNum <= messages.size() && !deletedMessages.contains(msgNum)) {
                                                out.println(RESPONSE_OK + " " + msgNum + " " + messages.get(msgNum - 1).length());
                                            } else {
                                                out.println(RESPONSE_ERR + " No such message");
                                            }
                                        } else {
                                            int count = 0;
                                            for (int i = 0; i < messages.size(); i++) {
                                                if (!deletedMessages.contains(i + 1)) {
                                                    count++;
                                                }
                                            }
                                            out.println(RESPONSE_OK + " " + count + " messages");
                                            for (int i = 0; i < messages.size(); i++) {
                                                if (!deletedMessages.contains(i + 1)) {
                                                    out.println((i + 1) + " " + messages.get(i).length());
                                                }
                                            }
                                            out.println(".");
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "RETR":
                                    lock.lock();
                                    try {
                                        if (argument != null) {
                                            int msgNum = parseMessageNumber(argument);
                                            if (msgNum > 0 && msgNum <= messages.size() && !deletedMessages.contains(msgNum)) {
                                                String message = messages.get(msgNum - 1);
                                                out.println(RESPONSE_OK + " " + message.length() + " octets");
                                                out.print(message);
                                                out.println(".");
                                            } else {
                                                out.println(RESPONSE_ERR + " No such message");
                                            }
                                        } else {
                                            out.println(RESPONSE_ERR + " RETR command requires an argument");
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "DELE":
                                    lock.lock();
                                    try {
                                        if (argument != null) {
                                            int msgNum = parseMessageNumber(argument);
                                            if (msgNum > 0 && msgNum <= messages.size()) {
                                                deletedMessages.add(msgNum);
                                                out.println(RESPONSE_OK + " Message " + msgNum + " marked for deletion");
                                            } else {
                                                out.println(RESPONSE_ERR + " No such message");
                                            }
                                        } else {
                                            out.println(RESPONSE_ERR + " DELE command requires an argument");
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "NOOP":
                                    out.println(RESPONSE_OK);
                                    break;

                                case "RSET":
                                    lock.lock();
                                    try {
                                        deletedMessages.clear();
                                        out.println(RESPONSE_OK + " Messages restored");
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "TOP":
                                    lock.lock();
                                    try {
                                        if (argument != null) {
                                            String[] topArgs = argument.split("\\s+");
                                            if (topArgs.length == 2) {
                                                int msgNum = parseMessageNumber(topArgs[0]);
                                                int numLines = parseMessageNumber(topArgs[1]);
                                                if (msgNum > 0 && msgNum <= messages.size() && !deletedMessages.contains(msgNum) && numLines >= 0) {
                                                    String message = messages.get(msgNum - 1);
                                                    String[] lines = message.split("\r\n");
                                                    out.println(RESPONSE_OK);
                                                    for (int i = 0; i < Math.min(numLines, lines.length); i++) {
                                                        out.println(lines[i]);
                                                    }
                                                    out.println(".");
                                                } else {
                                                    out.println(RESPONSE_ERR + " No such message or invalid number of lines");
                                                }
                                            } else {
                                                out.println(RESPONSE_ERR + " TOP command requires two arguments");
                                            }
                                        } else {
                                            out.println(RESPONSE_ERR + " TOP command requires arguments");
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                case "UIDL":
                                    lock.lock();
                                    try {
                                        if (argument != null) {
                                            int msgNum = parseMessageNumber(argument);
                                            if (msgNum > 0 && msgNum <= messages.size() && !deletedMessages.contains(msgNum)) {
                                                out.println(RESPONSE_OK + " " + msgNum + " " + generateUID(msgNum));
                                            } else {
                                                out.println(RESPONSE_ERR + " No such message");
                                            }
                                        } else {
                                            out.println(RESPONSE_OK);
                                            for (int i = 0; i < messages.size(); i++) {
                                                if (!deletedMessages.contains(i + 1)) {
                                                    out.println((i + 1) + " " + generateUID(i + 1));
                                                }
                                            }
                                            out.println(".");
                                        }
                                    } finally {
                                        lock.unlock();
                                    }
                                    break;

                                default:
                                    out.println(RESPONSE_ERR + " Command not valid in TRANSACTION state");
                                    break;
                            }
                            break;

                        default:
                            out.println(RESPONSE_ERR + " Invalid state");
                            break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace

                            ();
                }
            }
        }
    }
}