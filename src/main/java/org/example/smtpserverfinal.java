package org.example;

import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class smtpserverfinal {

    private static final int PORT = 25;
    private static final String MAIL_DIR = "mailserver/";
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
            "HELO", "EHLO", "MAIL FROM:", "RCPT TO:", "DATA", "QUIT", "NOOP", "VRFY", "RSET"
    ));

    private static final ReentrantLock fileLock = new ReentrantLock();

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
                List<String> recipients = new ArrayList<>();
                StringBuilder data = new StringBuilder();
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
                            if (command.startsWith("MAIL FROM:")) {
                                from = extractEmail(line);
                                if (from != null) {
                                    // Vérification de l'authentification via RMI
                                    if (!isAuthenticated(from)) {
                                        out.println(RESPONSE_550 + " User not authenticated");
                                        continue;
                                    }
                                    out.println(RESPONSE_250);
                                    state = "MAIL_FROM_RECEIVED";
                                } else {
                                    out.println(RESPONSE_550);
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
                                for (String recipient : recipients) {
                                    saveEmail(from, recipient, data.toString());
                                }
                                out.println(RESPONSE_250);
                                recipients.clear();
                                data.setLength(0);
                                state = "READY";
                            } else {
                                data.append(line).append("\n");
                            }
                            break;

                        default:
                            out.println("500 Syntax error, command unrecognized");
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

        private boolean isAuthenticated(String username) {
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                authservice authService = (authservice) registry.lookup("AuthService");
                return authService.isAuthenticated(username); // Vérification de l'authentification via RMI
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        private static String extractEmail(String line) {
            Pattern pattern = Pattern.compile("(?i)(?:MAIL FROM:|RCPT TO:)\s*<?([^<>\s]+@[^<>\s]+)>?");
            Matcher matcher = pattern.matcher(line);
            return matcher.find() ? matcher.group(1).trim() : null;
        }

        private static boolean isValidEmail(String email) {
            return email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        }

        private static void saveEmail(String from, String recipient, String content) {
            fileLock.lock();
            try {
                String recipientDir = MAIL_DIR + recipient.split("@")[0];
                File dir = new File(recipientDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String filename = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".eml";
                File emailFile = new File(dir, filename);

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(emailFile))) {
                    writer.write("From: " + from + "\n");
                    writer.write("To: " + recipient + "\n");
                    writer.write("Date: " + new Date() + "\n");
                    writer.write("Subject: (no subject)\n\n");
                    writer.write(content);
                }
            } catch (IOException e) {
                System.err.println("Error saving email: " + e.getMessage());
            } finally {
                fileLock.unlock();
            }
        }
    }
}
