package org.example;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class smtpserverfinal {

    private static final int PORT = 25;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final String RESPONSE_220 = "220 Welcome to SMTP Server";
    private static final String RESPONSE_250 = "250 OK";
    private static final String RESPONSE_221 = "221 Bye";
    private static final String RESPONSE_354 = "354 Start mail input; end with <CRLF>.<CRLF>";
    private static final String RESPONSE_503 = "503 Bad sequence of commands";
    private static final String RESPONSE_550 = "550 Invalid email address or no such user here";
    private static final String RESPONSE_500 = "500 Syntax error, command unrecognized";

    private static final Set<String> VALID_COMMANDS = new HashSet<>(Arrays.asList(
            "HE BMS", "EHLO", "MAIL FROM:", "RCPT TO:", "DATA", "QUIT", "NOOP", "VRFY", "RSET", "AUTH"
    ));

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server is listening on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                threadPool.execute(new SMTPClientHandler(socket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static class SMTPClientHandler implements Runnable {
        private final Socket socket;

        public SMTPClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println(RESPONSE_220);

                String from = null;
                String authenticatedUser = null;
                List<String> recipients = new ArrayList<>();
                StringBuilder data = new StringBuilder();
                String subject = "(no subject)";
                String state = "INIT";

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    String command = line.toUpperCase();
                    System.out.println("Current state: " + state);
                    System.out.println("Command received: " + line);

                    if (command.startsWith("QUIT")) {
                        out.println(RESPONSE_221);
                        return;
                    } else if (command.startsWith("NOOP")) {
                        out.println(RESPONSE_250);
                        continue;
                    } else if (command.startsWith("RSET")) {
                        from = null;
                        recipients.clear();
                        data.setLength(0);
                        subject = "(no subject)";
                        authenticatedUser = null;
                        state = "READY";
                        out.println(RESPONSE_250 + " (reset)");
                        continue;
                    }

                    switch (state) {
                        case "INIT":
                            if (command.startsWith("HELO") || command.startsWith("EHLO")) {
                                out.println(RESPONSE_250 + " Hello");
                                state = "READY";
                            } else {
                                out.println(RESPONSE_503);
                            }
                            break;

                        case "READY":
                            if (command.startsWith("AUTH")) {
                                String[] parts = line.split("\\s+");
                                if (parts.length == 2) {
                                    String user = parts[1];
                                    try {
                                        if (DatabaseUtils.authenticateUser(user, user)) { // Mot de passe = username pour simplifier
                                            authenticatedUser = user;
                                            out.println(RESPONSE_250 + " Authenticated as " + user);
                                            state = "AUTHENTICATED";
                                        } else {
                                            out.println(RESPONSE_550 + " Authentication failed");
                                        }
                                    } catch (SQLException e) {
                                        out.println(RESPONSE_550 + " Database error");
                                        e.printStackTrace();
                                    }
                                } else {
                                    out.println(RESPONSE_500 + " Usage: AUTH <username>");
                                }
                            } else {
                                out.println(RESPONSE_503);
                            }
                            break;

                        case "AUTHENTICATED":
                            if (command.startsWith("MAIL FROM:")) {
                                from = extractEmail(line);
                                if (from != null && from.equalsIgnoreCase(authenticatedUser)) {
                                    out.println(RESPONSE_250);
                                    state = "MAIL_FROM_RECEIVED";
                                } else {
                                    out.println(RESPONSE_550 + " Sender must match authenticated user");
                                }
                            } else {
                                out.println(RESPONSE_503);
                            }
                            break;

                        case "MAIL_FROM_RECEIVED":
                            if (command.startsWith("RCPT TO:")) {
                                String email = extractEmail(line);
                                if (email != null && isValidEmail(email)) {
                                    recipients.add(email.toLowerCase());
                                    out.println(RESPONSE_250);
                                    state = "RCPT_TO_RECEIVED";
                                } else {
                                    out.println(RESPONSE_550);
                                }
                            } else {
                                out.println(RESPONSE_503);
                            }
                            break;

                        case "RCPT_TO_RECEIVED":
                            if (command.startsWith("RCPT TO:")) {
                                String email = extractEmail(line);
                                if (email != null && isValidEmail(email)) {
                                    recipients.add(email.toLowerCase());
                                    out.println(RESPONSE_250);
                                } else {
                                    out.println(RESPONSE_550);
                                }
                            } else if (command.startsWith("DATA")) {
                                if (recipients.isEmpty()) {
                                    out.println(RESPONSE_503);
                                } else {
                                    out.println(RESPONSE_354);
                                    state = "DATA_RECEIVED";
                                }
                            } else {
                                out.println(RESPONSE_503);
                            }
                            break;

                        case "DATA_RECEIVED":
                            if (line.equals(".")) {
                                String emailContent = data.toString();
                                subject = extractSubject(emailContent);
                                String body = emailContent; // Simplification : tout le contenu est considéré comme le corps
                                for (String recipient : recipients) {
                                    try {
                                        DatabaseUtils.storeEmail(from, recipient, subject, body);
                                    } catch (SQLException e) {
                                        out.println(RESPONSE_550 + " Database error");
                                        e.printStackTrace();
                                        return;
                                    }
                                }
                                out.println(RESPONSE_250);
                                recipients.clear();
                                data.setLength(0);
                                subject = "(no subject)";
                                state = "READY";
                            } else {
                                data.append(line).append("\n");
                            }
                            break;

                        default:
                            out.println(RESPONSE_500);
                            break;
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

        private static String extractEmail(String line) {
            Pattern pattern = Pattern.compile("(?i)(?:MAIL FROM:|RCPT TO:|AUTH)\s*<?([^<>\s]+@[^<>\s]+)>?");
            Matcher matcher = pattern.matcher(line);
            return matcher.find() ? matcher.group(1).trim() : null;
        }

        private static boolean isValidEmail(String email) {
            return email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        }

        private static String extractSubject(String content) {
            Pattern pattern = Pattern.compile("(?i)^Subject:\\s*(.+)$", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find() ? matcher.group(1).trim() : "(no subject)";
        }
    }
}