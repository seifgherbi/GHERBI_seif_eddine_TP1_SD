package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface authservice extends Remote {
    boolean authenticate(String username, String password) throws RemoteException;
    boolean createAccount(String username, String password) throws RemoteException;
    boolean updateAccount(String username, String newPassword) throws RemoteException;
    boolean deleteAccount(String username) throws RemoteException;
    boolean isAuthenticated(String username) throws RemoteException;
}

