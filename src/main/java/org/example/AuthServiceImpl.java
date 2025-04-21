package org.example;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Set;

public class AuthServiceImpl extends UnicastRemoteObject implements authservice {

    private final Set<String> authenticatedUsers = new HashSet<>();  // Initialisé ici
    private static final String FILE_PATH = "users.json";

    protected AuthServiceImpl() throws RemoteException {
        super();
        // Crée le fichier s'il n'existe pas
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(FILE_PATH)) {
                writer.write("[]");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized boolean createAccount(String username, String password) throws RemoteException {
        JSONArray accounts = readAccounts();

        for (Object obj : accounts) {
            JSONObject account = (JSONObject) obj;
            if (account.get("username").equals(username)) {
                return false; // déjà existant
            }
        }

        JSONObject newAccount = new JSONObject();
        newAccount.put("username", username);
        newAccount.put("password", password);
        accounts.add(newAccount);

        writeAccounts(accounts);
        return true;
    }

    @Override
    public synchronized boolean authenticate(String username, String password) throws RemoteException {
        JSONArray accounts = readAccounts();

        for (Object obj : accounts) {
            JSONObject account = (JSONObject) obj;
            if (account.get("username").equals(username) &&
                    account.get("password").equals(password)) {
                authenticatedUsers.add(username); // ajoute à la liste des authentifiés
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAuthenticated(String username) throws RemoteException {
        return authenticatedUsers.contains(username);
    }

    @Override
    public synchronized boolean updateAccount(String username, String newPassword) throws RemoteException {
        JSONArray accounts = readAccounts();

        for (Object obj : accounts) {
            JSONObject account = (JSONObject) obj;
            if (account.get("username").equals(username)) {
                account.put("password", newPassword);
                writeAccounts(accounts);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean deleteAccount(String username) throws RemoteException {
        JSONArray accounts = readAccounts();

        for (int i = 0; i < accounts.size(); i++) {
            JSONObject account = (JSONObject) accounts.get(i);
            if (account.get("username").equals(username)) {
                accounts.remove(i);
                writeAccounts(accounts);
                authenticatedUsers.remove(username); // enlève aussi s'il était authentifié
                return true;
            }
        }
        return false;
    }

    private static JSONArray readAccounts() {
        JSONParser parser = new JSONParser();
        try (FileReader reader = new FileReader(FILE_PATH)) {
            Object obj = parser.parse(reader);
            return (JSONArray) obj;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    private void writeAccounts(JSONArray accounts) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            writer.write(accounts.toJSONString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
