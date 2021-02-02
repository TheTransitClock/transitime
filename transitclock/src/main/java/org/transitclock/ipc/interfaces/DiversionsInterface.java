package org.transitclock.ipc.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

import org.transitclock.ipc.data.IpcDiversion;
import org.transitclock.ipc.data.IpcDiversions;

public interface DiversionsInterface  extends Remote {
	IpcDiversions getDiversionsForTrip(String tripId)  throws RemoteException;
	IpcDiversions getDiversionsForRoute(String routeId)  throws RemoteException;
	void addDiversion(IpcDiversion diversion) throws RemoteException;
	void removeDiversion(IpcDiversion diversion) throws RemoteException;
}
