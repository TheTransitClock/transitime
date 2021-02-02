package org.transitclock.ipc.servers;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.core.diversion.cache.DiversionsCacheFactory;
import org.transitclock.core.diversion.cache.DiversionsKey;
import org.transitclock.core.diversion.cache.DiversionsList;
import org.transitclock.core.diversion.model.Diversion;
import org.transitclock.db.structs.Trip;
import org.transitclock.ipc.data.IpcDiversion;
import org.transitclock.ipc.data.IpcDiversions;
import org.transitclock.ipc.interfaces.DiversionsInterface;
import org.transitclock.ipc.interfaces.HoldingTimeInterface;
import org.transitclock.ipc.rmi.AbstractServer;

public class DiversionsServer extends AbstractServer implements DiversionsInterface {
	private static DiversionsServer singleton;

	private static final Logger logger = LoggerFactory.getLogger(DiversionsServer.class);

	protected DiversionsServer(String agencyId) {
		super(agencyId, DiversionsInterface.class.getSimpleName());
	}

	public static DiversionsServer start(String agencyId) {
		if (singleton == null) {
			singleton = new DiversionsServer(agencyId);
		}
		if (!singleton.getAgencyId().equals(agencyId)) {
			logger.error(
					"Tried calling DiversionsServer.start() for "
							+ "agencyId={} but the singleton was created for agencyId={}",
					agencyId, singleton.getAgencyId());
			return null;
		}
		return singleton;
	}

	@Override
	public IpcDiversions getDiversionsForTrip(String tripId) throws RemoteException {
		Core core = Core.getInstance();
		Trip trip = core.getDbConfig().getTrip(tripId);

		if (trip != null) {
			DiversionsKey key = new DiversionsKey(tripId, trip.getRouteId());

			DiversionsList diversions = DiversionsCacheFactory.getInstance().getDiversions(key);

			List<IpcDiversion> ipcDiversions = new ArrayList<IpcDiversion>();

			for (Diversion diversion : diversions.getDiversions()) {
				ipcDiversions.add(new IpcDiversion(diversion));
			}

			return new IpcDiversions(ipcDiversions);
		} else {
			return null;
		}
	}

	@Override
	public IpcDiversions getDiversionsForRoute(String routeId) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addDiversion(IpcDiversion diversion) throws RemoteException {

		DiversionsCacheFactory.getInstance().putDiversion(new Diversion(diversion));

	}

	@Override
	public void removeDiversion(IpcDiversion diversion) throws RemoteException {
		// TODO Auto-generated method stub

	}

}
