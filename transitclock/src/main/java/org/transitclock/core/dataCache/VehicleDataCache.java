/*
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitclock.core.dataCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.configData.AgencyConfig;
import org.transitclock.core.VehicleState;
import org.transitclock.db.hibernate.HibernateUtils;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.VehicleConfig;
import org.transitclock.ipc.data.IpcVehicleComplete;
import org.transitclock.utils.ConcurrentHashMapNullKeyOk;
import org.transitclock.utils.Time;

/**
 * For storing and retrieving vehicle information that can be used by clients.
 * Is updated every time VehicleState is changed. The info is stored in an
 * immutable IpcExtVehicle object since VehicleState changes dynamically and is
 * therefore not always coherent. This way the info transmitted to clients will
 * always be coherent without having to synchronize VehicleState for when
 * converting to a IpcExtVehicle. Organizes vehicles info by vehicle ID but also
 * by route so can easily determine which vehicles are associated with a route.
 * 
 * @author SkiBu Smith
 */
public class VehicleDataCache {

	// Make this class available as a singleton
	private static VehicleDataCache singleton = new VehicleDataCache();

	// Keyed by vehicle ID
	private Map<String, IpcVehicleComplete> vehiclesMap = new ConcurrentHashMap<String, IpcVehicleComplete>();

	// Keyed by route_short_name. Key is null for vehicles that have not
	// been successfully associated with a route. For each route there is a
	// submap that is keyed by vehicle.
	private Map<String, Map<String, IpcVehicleComplete>> vehiclesByRouteMap = new ConcurrentHashMapNullKeyOk<String, Map<String, IpcVehicleComplete>>();

	// So can determine vehicles associated with a block ID. Keyed on
	// block ID. Each block can have a list of vehicle IDs. Though rare
	// there are situations where multiple vehicles might have the
	// same assignment, such as for unscheduled assignments.
	private Map<String, List<String>> vehicleIdsByBlockMap = new ConcurrentHashMapNullKeyOk<String, List<String>>();

	// Keeps track of vehicle static config info. If new vehicle encountered
	// in AVL feed then this map is updated and the new VehicleConfig is also
	// written to the database. Using HashMap instead of ConcurrentHashMap
	// since synchronizing puts anyways.
	private ConcurrentHashMap<String, VehicleConfig> vehicleConfigsMap = new ConcurrentHashMap<String, VehicleConfig>();

	// So can quickly look up vehicle config using tracker ID
	private Map<String, VehicleConfig> vehicleConfigByTrackerIdMap = new HashMap<String, VehicleConfig>();

	// So can determine how long since data was read from db
	private long dbReadTime;

	// For filtering out info more than MAX_AGE since it means that the AVL info
	// is
	// obsolete and shouldn't be displayed.
	private static final int MAX_AGE_MSEC = 15 * Time.MS_PER_MIN;

	private static final Logger logger = LoggerFactory.getLogger(VehicleDataCache.class);

	/********************** Member Functions **************************/

	/**
	 * Gets the singleton instance of this class.
	 * 
	 * @return
	 */
	public static VehicleDataCache getInstance() {
		return singleton;
	}

	/*
	 * Constructor declared private to enforce only access to this singleton
	 * class being getInstance()
	 */
	private VehicleDataCache() {
	}

	/**
	 * Reads in vehicle config data from db. Unsynchronized since the calling
	 * methods are expected to sync.
	 */
	private void readVehicleConfigFromDb() {
		Session session = HibernateUtils.getSession(AgencyConfig.getAgencyId());
		try {
			// Read VehicleConfig data from database
			List<VehicleConfig> vehicleConfigs = VehicleConfig.getVehicleConfigs(session);

			// Convert list to the maps
			for (VehicleConfig vehicleConfig : vehicleConfigs) {
				vehicleConfigsMap.put(vehicleConfig.getId(), vehicleConfig);
				vehicleConfigByTrackerIdMap.put(vehicleConfig.getTrackerId(), vehicleConfig);
				dbReadTime = System.currentTimeMillis();
			}
		} catch (HibernateException e) {
			logger.error("Exception reading in VehicleConfig data. {}", e.getMessage(), e);
		} finally {
			// Always close the session
			session.close();
		}
	}

	/**
	 * Reads in vehicle config data from db if haven't done so yet.
	 */
	private void readVehicleConfigFromDbIfNeedTo() {
		synchronized (vehicleConfigsMap) {
			if (vehicleConfigsMap.isEmpty()) {
				readVehicleConfigFromDb();
			}
		}
	}

	/**
	 * Reads in vehicle config data from db if more than 5 minutes since last db
	 * read. Useful for when vehicle data has been updated in db.
	 */
	private void readVehicleConfigFromDbIfOld() {
		synchronized (vehicleConfigsMap) {
			// Read db if more than 5 minutes since last read
			if (System.currentTimeMillis() > dbReadTime + 5 * Time.MIN_IN_MSECS)
				readVehicleConfigFromDb();
		}
	}

	/**
	 * To be called when vehicle encountered in AVL feed. Adds the vehicle to
	 * the cache and stores in database if haven't done so yet.
	 * 
	 * @param avlReport
	 */
	public void cacheVehicleConfig(AvlReport avlReport) {
		// If a schedule based vehicle then don't need it to be
		// part of the cache
		if (avlReport.isForSchedBasedPreds())
			return;

		// Make sure go initial data from database
		readVehicleConfigFromDbIfNeedTo();

		// If new vehicle...
		String vehicleId = avlReport.getVehicleId();
		String vehicleName = avlReport.getVehicleName();
		VehicleConfig vehicleConfig = new VehicleConfig(vehicleId, vehicleName);
		VehicleConfig absent = vehicleConfigsMap.putIfAbsent(vehicleId, vehicleConfig);
		if (absent == null) {
			logger.info("Encountered new vehicle where vehicleId={} so " + "updating vehicle cache and writing the "
					+ "VehicleConfig to database.", vehicleId);
			// Write the vehicle to the database
			Core.getInstance().getDbLogger().add(vehicleConfig);
		} else {
			if (vehicleName != null) {
				if (!vehicleName.equals(absent.getName())) {
					Session session = HibernateUtils.getSession(AgencyConfig.getAgencyId());
					Transaction tx = session.beginTransaction();
					try {
						absent.setName(vehicleName);
						VehicleConfig.updateVehicleConfig(absent, session);
						tx.commit();
					} catch(Exception ex) {
						tx.rollback();
					}
				}
			}
		}
	}

	/**
	 * Returns the VehicleConfig for the specified trackerId. Useful for when
	 * getting a GPS feed that has a tracker ID, like an IMEI or phone #,
	 * instead of a vehicle ID. Allows the corresponding vehicleId to be
	 * determined from the VehicleConfig object.
	 * <p>
	 * If trackerId not found in VehicleConfig data then the VehicleData is
	 * reread from the db. This way if a new vehicle is configured don't need to
	 * restart the core system in order to determine the vehicle ID.
	 * 
	 * @param trackerId
	 * @return VehicleConfig for the trackerId, or null if there isn't one
	 *         configured.
	 */
	public VehicleConfig getVehicleConfigByTrackerId(String trackerId) {
		VehicleConfig vehicleConfig = vehicleConfigByTrackerIdMap.get(trackerId);

		// If specified trackerId not found then reread VehicleConfig data
		// from db again to see if it has been updated.
		if (vehicleConfig == null) {
			readVehicleConfigFromDbIfOld();
			vehicleConfig = vehicleConfigByTrackerIdMap.get(trackerId);
		}
		return vehicleConfig;
	}

	/**
	 * Returns an unmodifiable collection of the static vehicle configurations.
	 * 
	 * @return Unmodifiable collection of VehicleConfig objects
	 */
	public Collection<VehicleConfig> getVehicleConfigs() {
		readVehicleConfigFromDbIfNeedTo();
		return Collections.unmodifiableCollection(vehicleConfigsMap.values());
	}

	/**
	 * Filters out vehicle info if last GPS report is too old. Doesn't filter
	 * out vehicles at layovers though because for those won't get another
	 * report for a long time. This includes schedule based vehicles.
	 * 
	 * @param vehicles
	 * @return
	 */
	private Collection<IpcVehicleComplete> filterOldAvlReports(Collection<IpcVehicleComplete> vehicles) {
		Collection<IpcVehicleComplete> filteredVehicles = new ArrayList<IpcVehicleComplete>(vehicles.size());

		long timeCutoff = Core.getInstance().getSystemTime() - MAX_AGE_MSEC;
		for (IpcVehicleComplete vehicle : vehicles) {
			if (vehicle.isLayover() || vehicle.getAvl().getTime() > timeCutoff) {
				filteredVehicles.add(vehicle);
			}
		}

		// Return only vehicles whose AVL report not too old.
		return filteredVehicles;
	}

	/**
	 * This is intended to be used, when the vehicle maps are read, in order to
	 * remove schedule based vehicles from a collection.
	 * 
	 * @param vehicles
	 *            collection of vehicles to investigate
	 * @return filtered collection of vehicles
	 */
	private Collection<IpcVehicleComplete> filterSchedBasedVehicle(Collection<IpcVehicleComplete> vehicles) {
		Collection<IpcVehicleComplete> filteredVehicles = new ArrayList<IpcVehicleComplete>(vehicles.size());

		for (IpcVehicleComplete vehicle : vehicles) {
			// Return the vehicle info unless it is a schedule based vehicle
			if (!vehicle.isForSchedBasedPred()) {
				filteredVehicles.add(vehicle);
			}
		}

		// Return results
		return filteredVehicles;
	}

	/**
	 * Returns Collection of Vehicles currently associated with specified route.
	 * Filters out info more than MAX_AGE_MSEC since it means that the info is
	 * obsolete and shouldn't be displayed. Returns null if no vehicles for
	 * specified route.
	 * 
	 * @param routeIdOrShortName
	 *            Specifies which route to return vehicle data for. Can be a
	 *            route_short_name or a route_id. Can also be null or empty
	 *            string to retrieve vehicles that are not assigned to a route.
	 * @return Collection of IpcExtVehicle for vehicles on route, or null if no
	 *         vehicles for the route.
	 */
	public Collection<IpcVehicleComplete> getVehiclesForRoute(String routeIdOrShortName) {
		// Try getting vehicles using routeShortName
		String routeShortName = routeIdOrShortName;
		// If want vehicles not associated with route then need to use null
		// as the route short name instead of an empty string.
		if (routeShortName != null && routeShortName.isEmpty())
			routeShortName = null;
		Map<String, IpcVehicleComplete> vehicleMapForRoute = vehiclesByRouteMap.get(routeShortName);

		// If couldn't get vehicles by route short name try using
		// the route ID.
		if (vehicleMapForRoute == null) {
			Route route = Core.getInstance().getDbConfig().getRouteById(routeIdOrShortName);
			if (route != null) {
				vehicleMapForRoute = vehiclesByRouteMap.get(route.getShortName());
			}
		}

		if (vehicleMapForRoute != null)
			return filterSchedBasedVehicle(filterOldAvlReports(vehicleMapForRoute.values()));
		else
			return null;
	}

	/**
	 * Returns Collection of vehicles currently associated with specified
	 * routes. Filters out info more than MAX_AGE_MSEC since it means that the
	 * info is obsolete and shouldn't be displayed. Needs to return a collection
	 * because there are situations, such as unscheduled assignments, where
	 * multiple vehicles can be assigned to a block.
	 * 
	 * @param routeIdsOrShortNames
	 * @return Collection of vehicles for the route. Empty collection if there
	 *         are none.
	 */
	public Collection<IpcVehicleComplete> getVehiclesForRoute(Collection<String> routeIdsOrShortNames) {
		// If there is just a single route specified then use a shortcut
		if (routeIdsOrShortNames.size() == 1) {
			String routeIdOrShortName = routeIdsOrShortNames.iterator().next();
			return getVehiclesForRoute(routeIdOrShortName);
		}

		Collection<IpcVehicleComplete> vehicles = new ArrayList<IpcVehicleComplete>();
		for (String routeIdOrShortName : routeIdsOrShortNames) {
			Collection<IpcVehicleComplete> vehiclesForRoute = getVehiclesForRoute(routeIdOrShortName);
			if (vehiclesForRoute != null)
				vehicles.addAll(vehiclesForRoute);
		}
		return vehicles;
	}

	/**
	 * Returns collection of vehicles whose vehicleIds were specified using the
	 * vehiclesIds parameter. No filtering of old vehicles is done since
	 * requesting info on specific vehicles.
	 * 
	 * @param vehicleIds
	 *            Specifies which vehicles should return.
	 * @return
	 */
	public Collection<IpcVehicleComplete> getVehicles(Collection<String> vehicleIds) {
		Collection<IpcVehicleComplete> vehicles = new ArrayList<IpcVehicleComplete>();
		for (String vehicleId : vehicleIds) {
			IpcVehicleComplete vehicle = vehiclesMap.get(vehicleId);
			if (vehicle != null)
				vehicles.add(vehicle);
		}

		// Return results but filter out schedule based vehicles if past
		// trip start time
		return vehicles;
	}

	/**
	 * Returns Vehicle info for the vehicleId specified. No filtering of old
	 * vehicles is done since requesting info on specific vehicle.
	 * 
	 * @param vehicleId
	 * @return
	 */
	public IpcVehicleComplete getVehicle(String vehicleId) {
		return vehiclesMap.get(vehicleId);
	}

	/**
	 * Returns Vehicle info for all vehicles. Filter out schedule based
	 * predictions if trip time has already passed since should always filter
	 * out such vehicles. But don't filter out stale vehicles since this command
	 * could be useful to see all vehicles, including ones in the bus yard that
	 * have been turned off for a while.
	 * 
	 * @return
	 */
	public Collection<IpcVehicleComplete> getVehicles() {
		return filterSchedBasedVehicle(vehiclesMap.values());
	}

	/**
	 * Returns all vehicles, even schedule based ones
	 * 
	 * @return all vehicles, even schedule based ones
	 */
	public Collection<IpcVehicleComplete> getVehiclesIncludingSchedBasedOnes() {
		return vehiclesMap.values();
	}

	/**
	 * Returns copy of list of vehicle IDs that are currently assigned to the
	 * specified block. A copy is returned since the list is quite small and
	 * will often need to iterate over it while calling methods that modify the
	 * underlying list. Using a copy makes sure that don't get a
	 * ConcurrentModificationException. Will return empty list if no vehicles
	 * assigned to that block (won't return null). Usually there will only be a
	 * single vehicle associated with a block assignment but there are cases,
	 * such as unscheduled assignments, where there could be multiple vehicles.
	 * Therefore this method returns a List. No filtering of vehicles is done
	 * since dealing with vehicle IDs, not IpcExtVehicle objects, and therefore
	 * harder to tell if vehicle is stale.
	 * 
	 * @param blockId
	 * @return Copy of list of vehicle IDs associated with the specified block
	 *         Id. Returns empty list instead of null if no vehicles associated
	 *         with the block ID.
	 */
	public Collection<String> getVehiclesByBlockId(String blockId) {
		List<String> vehicleIds = vehicleIdsByBlockMap.get(blockId);
		if (vehicleIds != null)
			// Return copy of collection
			return new ArrayList<String>(vehicleIds);
		else
			return new ArrayList<String>(0);
	}

	/**
	 * Updates the vehiclesByBlockMap
	 * 
	 * @param originalVehicle
	 *            For getting the previous block ID for the vehicle. Can be null
	 *            if there was no previous vehicle info
	 * @param vehicle
	 *            For getting the current block ID for the vehicle.
	 */
	private void updateVehicleIdsByBlockMap(IpcVehicleComplete originalVehicle, IpcVehicleComplete vehicle) {
		// Handle old assignment
		if (originalVehicle != null) {
			// If block assignment is same as before don't need to update the
			// block map
			if (Objects.equals(originalVehicle.getBlockId(), vehicle.getBlockId()))
				return;

			// Block assignment has changed for vehicle so remove the old one
			// from the map
			List<String> vehicleIdsForOldBlock = vehicleIdsByBlockMap.get(originalVehicle.getBlockId());
			if (vehicleIdsForOldBlock != null)
				vehicleIdsForOldBlock.remove(originalVehicle.getId());
		}

		// Add the new block assignment to the map
		List<String> vehiclesForNewBlock = vehicleIdsByBlockMap.get(vehicle.getBlockId());
		if (vehiclesForNewBlock == null) {
			vehiclesForNewBlock = new ArrayList<String>(1);
			vehicleIdsByBlockMap.put(vehicle.getBlockId(), vehiclesForNewBlock);
		}
		vehiclesForNewBlock.add(vehicle.getId());
	}

	/**
	 * Updates vehiclesByRouteMap containing the vehicle info.
	 * 
	 * @param originalVehicle
	 * @param vehicle
	 */
	private void updateVehiclesByRouteMap(IpcVehicleComplete originalVehicle, IpcVehicleComplete vehicle) {
		// If the route has changed then remove the vehicle from the old map for
		// that route. Watch out for getRouteShortName() sometimes being null
		if (originalVehicle != null && originalVehicle.getRouteShortName() != vehicle.getRouteShortName()
				&& (originalVehicle.getRouteShortName() == null
						|| !originalVehicle.getRouteShortName().equals(vehicle.getRouteShortName()))) {
			Map<String, IpcVehicleComplete> vehicleMapForRoute = vehiclesByRouteMap
					.get(originalVehicle.getRouteShortName());
			vehicleMapForRoute.remove(vehicle.getId());
		}

		// Add IpcExtVehicle to the vehiclesByRouteMap
		Map<String, IpcVehicleComplete> vehicleMapForRoute = vehiclesByRouteMap.get(vehicle.getRouteShortName());
		if (vehicleMapForRoute == null) {
			vehicleMapForRoute = new HashMap<String, IpcVehicleComplete>();
			String routeMapKey = vehicle.getRouteShortName();
			vehiclesByRouteMap.put(routeMapKey, vehicleMapForRoute);
		}
		vehicleMapForRoute.put(vehicle.getId(), vehicle);
	}

	/**
	 * Updates vehiclesMap. Usually will add the IpcExtVehicle to the
	 * vehiclesMap. But there is a special case where a schedule based vehicle
	 * is being made unpredictable. For this situation actually need to remove
	 * the vehicle from the vehicles map so that it won't show up requesting
	 * vehicles for the API.
	 * 
	 * @param vehicle
	 */
	private void updateVehiclesMap(IpcVehicleComplete vehicle) {
		if (!vehicle.isForSchedBasedPred() || vehicle.isPredictable()) {
			// Normal situation. Add vehicle to vehiclesMap
			vehiclesMap.put(vehicle.getId(), vehicle);
		} else {
			// Special case where vehicle is schedule based and it is not
			// predictable. This means that should get rid of the vehicle
			// from the vehiclesMap since it was just a temporary fake
			// vehicle.
			vehiclesMap.remove(vehicle.getId());
		}
	}

	/**
	 * Updates the maps containing the vehicle info. Should be called every time
	 * vehicle state changes.
	 * 
	 * @param vehicleState
	 *            The current VehicleState
	 */
	public void updateVehicle(VehicleState vehicleState) {
		IpcVehicleComplete vehicle = new IpcVehicleComplete(vehicleState);
		IpcVehicleComplete originalVehicle = vehiclesMap.get(vehicle.getId());

		logger.debug("Adding to VehicleDataCache vehicle={}", vehicle);

		updateVehiclesByRouteMap(originalVehicle, vehicle);
		updateVehicleIdsByBlockMap(originalVehicle, vehicle);
		updateVehiclesMap(vehicle);
	}

	/**
	 * Removes a vehicle from the vehiclesMap
	 * 
	 * @param vehicleId
	 *            The id of the vehicle to remove from the vehiclesMap
	 */
	public void removeVehicle(String vehicleId) {
		logger.debug("Removing from VehicleDataCache vehiclesMap vehicleId={}", vehicleId);
		vehiclesMap.remove(vehicleId);
	}
}
