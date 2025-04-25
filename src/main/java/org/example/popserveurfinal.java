package org.example;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.*;

public class popserveurfinal {

    private static final int PORT = 110;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

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
                            try {
                                if (DatabaseUtils.authenticateUser(username, password)) {
                                    out.println("+OK Authentication successful");
                                    authenticated = true;
                                } else {
                                    out.println("-ERR Authentication failed");
                                    username = null;
                                }
                            } catch (SQLException e) {
                                out.println("-ERR Database error");
                                e.printStackTrace();
                            }
                        }

                    } else if (authenticated && command.startsWith("LIST")) {
                        listEmails(out, username);

                    } else if (authenticated && command.startsWith("RETR")) {
                        try {
                            int index = Integer.parseInt(line.substring(5).trim());
                            retrieveEmail(out, username, index);
                        } catch (NumberFormatException | SQLException e) {
                            out.println("-ERR Invalid message number or database error");
                        }

                    } else if (authenticated && command.startsWith("DELE")) {
                        try {
                            int index = Integer.parseInt(line.substring(5).trim());
                            deleteEmail(out, username, index);
                        } catch (NumberFormatException | SQLException e) {
                            out.println("-ERR Invalid message number or database error");
                        }

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

        private void listEmails(PrintWriter out, String username) {
            try {
                ResultSet rs = DatabaseUtils.fetchEmails(username);
                int count = 0;
                long totalSize = 0;
                StringBuilder response = new StringBuilder();

                while (rs.next()) {
                    count++;
                    int id = rs.getInt("id");
                    String content = DatabaseUtils.getEmailContent(id).next() ? rs.getString("content") : "";
                    int size = content != null ? content.length() : 0;
                    totalSize += size;
                    response.append(id).append(" ").append(size).append("\n");
                }

                if (count == 0) {
                    out.println("+OK 0 messages");
                } else {
                    out.println("+OK " + count + " messages:");
                    out.print(response.toString());
                    out.println(".");
                }
            } catch (SQLException e) {
                out.println("-ERR Database error");
                e.printStackTrace();
            }
        }

        private void retrieveEmail(PrintWriter out, String username, int mailId) throws SQLException {
            ResultSet rs = DatabaseUtils.getEmailContent(mailId);
            if (rs.next() && rs.getString("recipient").equalsIgnoreCase(username)) {
                String content = rs.getString("content");
                int size = content != null ? content.length() : 0;
                out.println("+OK " + size + " octets");
                out.println(content);
                out.println(".");
            } else {
                out.println("-ERR No such message or unauthorized");
            }
        }

        private void deleteEmail(PrintWriter out, String username, int mailId) throws SQLException {
            ResultSet rs = DatabaseUtils.getEmailContent(mailId);
            if (rs.next() && rs.getString("recipient").equalsIgnoreCase(username)) {
                DatabaseUtils.deleteEmail(mailId);
                out.println("+OK Message deleted");
            } else {
                out.println("-ERR No such message or unauthorized");
            }
        }

        private void statEmails(PrintWriter out, String username) {
            try {
                ResultSet rs = DatabaseUtils.fetchEmails(username);
                int count = 0;
                long totalSize = 0;

                while (rs.next()) {
                    count++;
                    String content = DatabaseUtils.getEmailContent(rs.getInt("id")).next() ? rs.getString("content") : "";
                    totalSize += content != null ? content.length() : 0;
                }

                out.println("+OK " + count + " " + totalSize);
            } catch (SQLException e) {
                out.println("-ERR Database error");
                e.printStackTrace();
            }
        }
    }
}