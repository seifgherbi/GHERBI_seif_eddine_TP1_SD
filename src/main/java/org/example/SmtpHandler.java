package org.example;

import java.io.IOException;
import java.nio.file.*;

public class SmtpHandler {
    private boolean heloReceived = false;
    private boolean mailFromReceived = false;
    private boolean rcptReceived = false;
    private boolean dataMode = false;

    private String mailFrom;
    private StringBuilder emailData = new StringBuilder();
    private java.util.List<String> recipients = new java.util.ArrayList<>();

    public static final String MAIL_DIR = "mailserver/";

    // Traitement des commandes SMTP avec vérification d'ordre
    public String processCommand(String command) throws InvalidCommandOrderException, IOException {
        command = command.trim();
        if (command.equalsIgnoreCase("QUIT")) {
            // On peut choisir de ne pas sauvegarder une transaction incomplète.
            resetSession();
            return "221 Bye";
        }
        // Si en mode DATA, accumuler ou terminer la saisie du message
        if (dataMode) {
            if (command.equals(".")) {
                // Fin de DATA : Sauvegarde l'email pour chaque destinataire.
                saveEmail();
                resetSession();
                return "250 OK";
            } else {
                emailData.append(command).append("\r\n");
                return ""; // Pas de réponse intermédiaire
            }
        }
        // Traitement des commandes hors DATA
        if (command.toUpperCase().startsWith("HELO") || command.toUpperCase().startsWith("EHLO")) {
            heloReceived = true;
            return "250 Hello";
        } else if (command.toUpperCase().startsWith("MAIL FROM:")) {
            if (!heloReceived)
                throw new InvalidCommandOrderException("503 Bad sequence of commands: HELO required before MAIL FROM");
            mailFromReceived = true;
            mailFrom = extractEmail(command.substring(10));
            return "250 OK";
        } else if (command.toUpperCase().startsWith("RCPT TO:")) {
            if (!mailFromReceived)
                throw new InvalidCommandOrderException("503 Bad sequence of commands: MAIL FROM required before RCPT TO");
            String recipient = extractEmail(command.substring(8));
            // Crée le répertoire du destinataire (si inexistant)
            Path recipientDir = Paths.get(MAIL_DIR, recipient);
            Files.createDirectories(recipientDir);
            recipients.add(recipient);
            rcptReceived = true;
            return "250 OK";
        } else if (command.equalsIgnoreCase("DATA")) {
            if (!rcptReceived)
                throw new InvalidCommandOrderException("503 Bad sequence of commands: RCPT TO required before DATA");
            dataMode = true;
            return "354 Start mail input; end with <CRLF>.<CRLF>";
        } else {
            throw new InvalidCommandOrderException("503 Bad sequence of commands: Command not allowed in current state");
        }
    }

    private String extractEmail(String input) {
        // Suppression des espaces et des chevrons
        return input.trim().replaceAll("[<>]", "");
    }

    private void saveEmail() throws IOException {
        // Pour chaque destinataire, sauvegarde l’email dans MAIL_DIR/recipient/timestamp.txt
        for (String recipient : recipients) {
            Path userDir = Paths.get(MAIL_DIR, recipient);
            Files.createDirectories(userDir);
            String fileName = System.currentTimeMillis() + ".txt";
            Path filePath = userDir.resolve(fileName);
            // On vérifie l'unicité du nom (rarement nécessaire)
            int counter = 0;
            while (Files.exists(filePath)) {
                fileName = System.currentTimeMillis() + "_" + counter + ".txt";
                filePath = userDir.resolve(fileName);
                counter++;
            }
            Files.write(filePath, emailData.toString().getBytes());
        }
    }

    private void resetSession() {
        heloReceived = false;
        mailFromReceived = false;
        rcptReceived = false;
        dataMode = false;
        mailFrom = null;
        emailData.setLength(0);
        recipients.clear();
    }
}
