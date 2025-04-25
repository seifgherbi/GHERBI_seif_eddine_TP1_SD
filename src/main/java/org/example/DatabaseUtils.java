package org.example;

import java.sql.*;


public class DatabaseUtils {
    private static final String URL = "jdbc:mysql://localhost:3306/maildb";
    private static final String USER = "root"; // Remplacez par votre utilisateur MySQL
    private static final String PASSWORD = ""; // Remplacez par votre mot de passe MySQL

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static boolean authenticateUser(String username, String password) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password FROM vusers WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String dbPassword = rs.getString("password");
                return dbPassword.equals(password); // comparaison en clair
            }
            return false;
        }
    }


    public static void storeEmail(String sender, String recipient, String subject, String content) throws SQLException {
        try (Connection conn = getConnection();
             CallableStatement stmt = conn.prepareCall("{CALL vstore_email(?, ?, ?, ?)}")) {
            stmt.setString(1, sender);
            stmt.setString(2, recipient);
            stmt.setString(3, subject);
            stmt.setString(4, content);
            stmt.execute();
        }
    }

    public static ResultSet fetchEmails(String username) throws SQLException {
        Connection conn = getConnection();
        CallableStatement stmt = conn.prepareCall("{CALL vfetch_emails(?)}");
        stmt.setString(1, username);
        return stmt.executeQuery();
    }

    public static ResultSet getEmailContent(int mailId) throws SQLException {
        Connection conn = getConnection();
        CallableStatement stmt = conn.prepareCall("{CALL vget_email_content(?)}");
        stmt.setInt(1, mailId);
        return stmt.executeQuery();
    }

    public static void deleteEmail(int mailId) throws SQLException {
        try (Connection conn = getConnection();
             CallableStatement stmt = conn.prepareCall("{CALL vdelete_email(?)}")) {
            stmt.setInt(1, mailId);
            stmt.execute();
        }
    }

    public static void markEmailAsRead(int mailId) throws SQLException {
        try (Connection conn = getConnection();
             CallableStatement stmt = conn.prepareCall("{CALL vmark_email_as_read(?)}")) {
            stmt.setInt(1, mailId);
            stmt.execute();
        }
    }

    public static boolean userExists(String username) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM vusers WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }
}
