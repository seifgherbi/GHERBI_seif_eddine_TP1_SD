package org.example;

import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class popserveurfinal {

    private static final int PORT = 110;
    private static final String MAIL_DIR = "mailserver/";
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ReentrantLock fileLock = new ReentrantLock();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                threadPool.execute(new POP3ClientHandler(socket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            threadPool.shutdown();
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

                out.println("+OK POP3 server ready");

                String username = null;
                boolean authenticated = false;

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Command received: " + line);
                    String command = line.toUpperCase();

                    if (command.startsWith("QUIT")) {
                        out.println("+OK Goodbye");
                        break;

                    } else if (command.startsWith("USER")) {
                        username = line.substring(5).trim();
                        out.println("+OK User accepted");

                    } else if (command.startsWith("PASS")) {
                        if (username == null) {
                            out.println("-ERR USER command required before PASS");
                        } else {
                            String password = line.substring(5).trim();
                            if (checkUser(username, password)) {
                                out.println("+OK Authentication successful");
                                authenticated = true;
                            } else {
                                out.println("-ERR Authentication failed");
                                username = null;
                            }
                        }

                    } else if (authenticated && command.startsWith("LIST")) {
                        listEmails(out, username);

                    } else if (authenticated && command.startsWith("RETR")) {
                        int index = Integer.parseInt(line.substring(5).trim());
                        retrieveEmail(out, username, index);

                    } else if (authenticated && command.startsWith("DELE")) {
                        int index = Integer.parseInt(line.substring(5).trim());
                        deleteEmail(out, username, index);

                    } else if (authenticated && command.startsWith("STAT")) {
                        statEmails(out, username);

                    } else if (authenticated && command.startsWith("NOOP")) {
                        out.println("+OK");

                    } else if (authenticated && command.startsWith("RSET")) {
                        out.println("+OK Reset state");

                    } else {
                        out.println("-ERR Invalid command or not authenticated");
                    }
                }

            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean checkUser(String username, String password) {
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                authservice authService = (authservice) registry.lookup("AuthService");
                return authService.authenticate(username, password);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        private void listEmails(PrintWriter out, String username) {
            fileLock.lock();
            try {
                File userDir = new File(MAIL_DIR + username);
                if (!userDir.exists() || !userDir.isDirectory()) {
                    out.println("-ERR No mailbox found");
                    return;
                }

                File[] emails = userDir.listFiles((dir, name) -> name.endsWith(".eml"));
                if (emails == null || emails.length == 0) {
                    out.println("+OK 0 messages");
                    return;
                }

                out.println("+OK " + emails.length + " messages:");
                for (int i = 0; i < emails.length; i++) {
                    out.println((i + 1) + " " + emails[i].length());
                }
            } finally {
                fileLock.unlock();
            }
        }

        private void retrieveEmail(PrintWriter out, String username, int index) {
            fileLock.lock();
            try {
                File userDir = new File(MAIL_DIR + username);
                File[] emails = userDir.listFiles((dir, name) -> name.endsWith(".eml"));
                if (emails == null || index < 1 || index > emails.length) {
                    out.println("-ERR No such message");
                    return;
                }

                File emailFile = emails[index - 1];
                out.println("+OK " + emailFile.length() + " octets");
                try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                    }
                    out.println(".");
                }
            } catch (IOException e) {
                out.println("-ERR Error reading email");
            } finally {
                fileLock.unlock();
            }
        }

        private void deleteEmail(PrintWriter out, String username, int index) {
            fileLock.lock();
            try {
                File userDir = new File(MAIL_DIR + username);
                File[] emails = userDir.listFiles((dir, name) -> name.endsWith(".eml"));
                if (emails == null || index < 1 || index > emails.length) {
                    out.println("-ERR No such message");
                    return;
                }

                File emailFile = emails[index - 1];
                if (emailFile.delete()) {
                    out.println("+OK Message deleted");
                } else {
                    out.println("-ERR Unable to delete message");
                }
            } finally {
                fileLock.unlock();
            }
        }

        private void statEmails(PrintWriter out, String username) {
            fileLock.lock();
            try {
                File userDir = new File(MAIL_DIR + username);
                if (!userDir.exists() || !userDir.isDirectory()) {
                    out.println("-ERR No mailbox found");
                    return;
                }

                File[] emails = userDir.listFiles((dir, name) -> name.endsWith(".eml"));
                if (emails == null || emails.length == 0) {
                    out.println("+OK 0 0");
                    return;
                }

                int totalSize = 0;
                for (File email : emails) {
                    totalSize += email.length();
                }

                out.println("+OK " + emails.length + " " + totalSize);
            } finally {
                fileLock.unlock();
            }
        }
    }
}
