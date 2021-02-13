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

package org.transitclock.core.predAccuracy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.core.VehicleState;
import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.db.structs.ArrivalDeparture;
import org.transitclock.db.structs.PredictionAccuracy;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.TripPattern;
import org.transitclock.ipc.data.IpcPrediction;
import org.transitclock.ipc.data.IpcPredictionsForRouteStopDest;
import org.transitclock.modules.Module;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.MapKey;
import org.transitclock.utils.Time;

/**
 * Reads internal predictions every transitclock.predAccuracy.pollingRateMsec and
 * stores the predictions into memory. Then when arrivals/departures occur the
 * prediction accuracy can be determined and stored.
 *
 *
 * @author SkiBu Smith
 *
 */
public class PredictionAccuracyModule extends Module {

	// The map that contains all of the predictions to be used for prediction 
	// accuracy analysis. Each value is a list of predictions because can have
	// more than a single prediction stored in memory for a vehicle/stop.
	// Declared static because want to be able to access it from another
	// class by using the static method handleArrivalDeparture().
	private static ConcurrentHashMap<PredictionKey, List<PredAccuracyPrediction>> predictionMap =
			new ConcurrentHashMap<PredictionAccuracyModule.PredictionKey, List<PredAccuracyPrediction>>();
	
	private static final Logger logger = LoggerFactory
			.getLogger(PredictionAccuracyModule.class);

	/********************** Config Params **************************/
	
	private static final IntegerConfigValue timeBetweenPollingPredictionsMsec = 
			new IntegerConfigValue("transitclock.predAccuracy.pollingRateMsec", 
					2 * Time.MS_PER_MIN,
					"How frequently to query predictions for determining "
					+ "prediction accuracy.");
	
	protected static int getTimeBetweenPollingPredictionsMsec() {
		return timeBetweenPollingPredictionsMsec.getValue();
	}

	private static final IntegerConfigValue maxPredTimeMinutes = 
			new IntegerConfigValue("transitclock.predAccuracy.maxPredTimeMinutes", 
					15,
					"Maximum time into the future for a pediction for it to "
					+ "be stored in memory for prediction accuracy analysis.");
	
	private static int getMaxPredTimeMinutes() {
		return maxPredTimeMinutes.getValue();
	}

	private static final IntegerConfigValue maxPredStalenessMinutes = 
			new IntegerConfigValue("transitclock.predAccuracy.maxPredStalenessMinutes", 
					15,
					"Maximum time in minutes a prediction cam be into the "
					+ "past before it is removed from memory because no "
					+ "corresponding arrival/departure time was determined.");
	
	private static int getMaxPredStalenessMinutes() {
		return maxPredStalenessMinutes.getValue();
	}

	private static final IntegerConfigValue stopsPerTrip = 
			new IntegerConfigValue("transitclock.predAccuracy.stopsPerTrip", 
					5,
					"Number of stops per trip pattern that should collect "
					+ "prediction data for each polling cycle.");
	
	private static final IntegerConfigValue maxRandomStopSelectionsPerTrip = 
			new IntegerConfigValue("transitclock.predAccuracy.maxRandomStopSelectionsPerTrip", 
					100,
					"Max number of random stops to look at to get the stopsPerTrip.");

	
	private static int getStopsPerTrip() {
		return stopsPerTrip.getValue();
	}

	private static final IntegerConfigValue maxLatenessComparedToPredictionMsec = 
			new IntegerConfigValue("transitclock.predAccuracy.maxLatenessComparedToPredictionMsec", 
					25 * Time.MS_PER_MIN,
					"How late in msec a vehicle can arrive/departure a stop "
					+ "compared to the prediction and still have the prediction "
					+ "be considered a match.");
	
	private static int getMaxLatenessComparedToPredictionMsec() {
		return maxLatenessComparedToPredictionMsec.getValue();
	}

	private static final IntegerConfigValue maxEarlynessComparedToPredictionMsec = 
			new IntegerConfigValue("transitclock.predAccuracy.maxEarlynessComparedToPredictionMsec", 
					15 * Time.MS_PER_MIN,
					"How early in msec a vehicle can arrive/departure a stop "
					+ "compared to the prediction and still have the prediction "
					+ "be considered a match.");
	
	private static int getMaxEarlynessComparedToPredictionMsec() {
		return maxEarlynessComparedToPredictionMsec.getValue();
	}

	/********************** Internal Classes **************************/
	
	/**
	 * For keeping track of which routes and stops to get predictions for.
	 */
	public static class RouteAndStops {
		public String routeId;
		// Keyed on direction ID
		public Map<String, Collection<String>> stopIds = 
				new HashMap<String, Collection<String>>();
		
		@Override
		public String toString() {
			return "RouteAndStops [" 
					+ "routeId=" + routeId + ", stopIds=" + stopIds
					+ "]";
		}
	}
	
	/**
	 * Key for map of predictions
	 */
	protected static class PredictionKey extends MapKey {
		private PredictionKey(String vehicleId, String directionId, String stopId) {
			super(vehicleId, directionId, stopId);
		}

		@Override
		public String toString() {
			return "PredictionKey [" + "vehicleId=" + o1 + ", directionId=" + o2
					+ ", stopId=" + o3 + "]";
		}
	}

	/********************** Member Functions **************************/

	/**
	 * The constructor for the module. Called automatically if the module
	 * is configured.
	 * 
	 * @param agencyId
	 */
	public PredictionAccuracyModule(String agencyId) {
		super(agencyId);
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		// Log that module successfully started
		logger.info("Started module {} for agencyId={}", 
				getClass().getName(), getAgencyId());
		
		// No need to run at startup since internal predictions won't be
		// generated yet. So sleep a bit first.
		Time.sleep(getTimeBetweenPollingPredictionsMsec());
		
		// Run forever
		while (true) {
      IntervalTimer timer = null;

		  try {
		    timer = new IntervalTimer();
				// Process data

				

		    logger.info("processing prediction accuracy....");
				getAndProcessData(getRoutesAndStops(), Core.getInstance().getSystemDate());
				logger.info("processing prediction accuracy complete.");

				// Make sure old predictions that were never matched to an
				// arrival/departure don't stick around taking up memory.
				clearStalePredictions();
				
			} catch (Exception e) {

				logger.error("Error accessing predictions feed {}", e, e);
				logger.debug("execption details {}", e, e);
			} catch (Throwable t) {
			  logger.error("possible sql exception {}", t, t);
			} finally {
			  // if we have an exception, we still need to wait to be nice to the cpu
	       // Wait appropriate amount of time till poll again
        long elapsedMsec = timer.elapsedMsec();
        long sleepTime = 
            getTimeBetweenPollingPredictionsMsec() - elapsedMsec;
        if (sleepTime > 0)
          Time.sleep(sleepTime);

			}
		}
	}

	/**
	 * Returns the routes and stops that should store predictions in memory for.
	 * Usually will be all routes for an agency, with a sampling of stops.
	 * 
	 * @return
	 */
	protected List<RouteAndStops> getRoutesAndStops() {
		// The value to be returned
		List<RouteAndStops> list = new ArrayList<RouteAndStops>();
		
		// For each route...
		List<Route> routes = Core.getInstance().getDbConfig().getRoutes();
		for (Route route : routes) {
			RouteAndStops routeStopInfo = new RouteAndStops();
			list.add(routeStopInfo);
			
			routeStopInfo.routeId = route.getId();
			
			// For each direction for the route...
			List<TripPattern> tripPatterns = 
					route.getLongestTripPatternForEachDirection();
			for (TripPattern tripPattern : tripPatterns) {
				List<String> stopIdsForTripPattern = tripPattern.getStopIds();
				
				// If not that many stops for the trip then use all of them.
				if (getStopsPerTrip() >= stopIdsForTripPattern.size()) {
					// Use all stops for this trip pattern
					routeStopInfo.stopIds.put(tripPattern.getDirectionId(), 
							stopIdsForTripPattern);
				} else {
					// Get stops for direction randomly
					Set<String> stopsSet = new HashSet<String>();
					int tries=0;
					while (stopsSet.size() < getStopsPerTrip() && tries < maxRandomStopSelectionsPerTrip.getValue()) {
						// Randomly get a stop ID for the trip pattern
						int index = (int) (stopIdsForTripPattern.size() * 
								Math.random());
						String stopId = stopIdsForTripPattern.get(index);
						if (!stopsSet.contains(stopId)) {
							stopsSet.add(stopId);
						}
						tries++;
					}
					routeStopInfo.stopIds.put(tripPattern.getDirectionId(), 
							stopsSet);
				}
			}
		}
		
		// Return the routes/stops that predictions should be stored in 
		// memory for
		logger.debug("getRoutesAndStops() returning {}", list);
		return list;		
	}
	
	/**
	 * Stores prediction in memory so that when arrival/departure generated
	 * can compare with the stored prediction. Will only store prediction
	 * if it is less then transitclock.predAccuracy.maxPredTimeMinutes into
	 * the future.
	 * 
	 * @param pred
	 */
	protected void storePrediction(PredAccuracyPrediction pred) {
		// If prediction too far into the future then don't store it in
		// memory. This is important because need to limit how much 
		// memory is used for prediction accuracy data collecting.
		if (pred.getPredictedTime().getTime() > 
			Core.getInstance().getSystemTime() + getMaxPredTimeMinutes()*Time.MS_PER_MIN) {
			logger.debug("Prediction is too far into future so not storing "
					+ "it in memory for prediction accuracy analysis. {}", 
					pred);
			return;
		}
		
		PredictionKey key = new PredictionKey(pred.getVehicleId(), 
				pred.getDirectionId(), pred.getStopId());
		List<PredAccuracyPrediction> predsList = predictionMap.get(key);
		if (predsList == null) {
			predictionMap.putIfAbsent(key,
					new ArrayList<PredAccuracyPrediction>(1));
			predsList = predictionMap.get(key);
		}
		logger.debug("Adding prediction to memory for prediction accuracy "
				+ "analysis. {}", pred);
		predsList.add(pred);
	}
	
	/**
	 * This method should be called every once in a while need to clear out old
	 * predictions that were never matched to an arrival/departure. This is
	 * needed because sometimes a vehicle will never arrive at a stop and so
	 * will not be removed from memory. In order to prevent memory use from
	 * building up need to clear out the old predictions.
	 * 
	 * Synchronized as multiple subclasses exist.
	 */

	protected synchronized void clearStalePredictions() {

		int numPredictionsInMemory = 0;
		int numPredictionsRemoved = 0;
		
		// Go through all predictions in memory...
		Collection<List<PredAccuracyPrediction>> allPreds = 
				predictionMap.values();
		for (List<PredAccuracyPrediction> predsForVehicleStop : allPreds) {
			Iterator<PredAccuracyPrediction> iter = predsForVehicleStop.iterator();
			while (iter.hasNext()) {
				PredAccuracyPrediction pred = iter.next();
				if (pred.getPredictedTime().getTime() < 
						Core.getInstance().getSystemTime() - 
						getMaxPredStalenessMinutes()*Time.MS_PER_MIN) {
					// Prediction was too old so remove it from memory
					++numPredictionsRemoved;
					logger.info("Removing prediction accuracy prediction "
							+ "from memory because it is too old. {}", pred);
					iter.remove();
					
					// Store prediction accuracy info so can note that 
					// a bad prediction was made
					storePredictionAccuracyInfo(pred, null, null);
				} else {
					++numPredictionsInMemory;		
					logger.debug("Prediction currently held in memory. {}",pred);
				}
			}
		}
		
		
		
		logger.debug("There are now {} predictions in memory after removing {}.",
				numPredictionsInMemory, numPredictionsRemoved);
	}
	
	/**
	 * Gets and processes predictions from Transitime system. To be called every
	 * polling cycle to process internal predictions. To be overridden if
	 * getting predictions from external feed.
	 * 
	 * @param routesAndStops
	 * @param predictionsReadTime
	 *            For keeping track of when the predictions read in. Used for
	 *            determining length of predictions. Should be the same for all
	 *            predictions read in during a polling cycle even if the
	 *            predictions are read at slightly different times. By using the
	 *            same time can easily see from data in db which internal and
	 *            external predictions are associated with each other.
	 */
	protected synchronized void getAndProcessData(List<RouteAndStops> routesAndStops,
			Date predictionsReadTime) {
		logger.debug("Calling PredictionReaderModule.getAndProcessData() "
				+ "to process internal prediction.");

		// Get internal predictions from core and store them in memory
		for (RouteAndStops routeAndStop : routesAndStops) {
			String routeId = routeAndStop.routeId;

			Set<String> directionIds = routeAndStop.stopIds.keySet();
			for (String directionId : directionIds) {
				Collection<String> stopIds = 
						routeAndStop.stopIds.get(directionId);
				for (String stopId : stopIds) {
					List<IpcPredictionsForRouteStopDest> predictions = 
							PredictionDataCache.getInstance().getPredictions(
									routeId, directionId, stopId);
					boolean predictionsFound = false;
					for (IpcPredictionsForRouteStopDest predList : predictions) {
						for (IpcPrediction pred : predList
								.getPredictionsForRouteStop()) {
							if(pred.totalsMatch())
							{
								PredAccuracyPrediction accuracyPred = 
										new PredAccuracyPrediction(
												routeId, directionId, stopId,
												pred.getTripId(), 
												pred.getVehicleId(),
												new Date(pred.getPredictionTime()),
												predictionsReadTime,
												new Date(pred.getAvlTime()),
												pred.isArrival(),
												pred.isAffectedByWaitStop(),
												"TransitClock",null,null, pred.getTotalPredictedDwellTime(), pred.getTotalPredictedTravelTime());
								storePrediction(accuracyPred);
								predictionsFound = true;
							}else
							{
								logger.error("Totals for prediction do {}  not match.", pred);
							}
						}
					}
					
					// Nice to log when predictions for stop not found so can 
					// see if not getting predictions when should be.
					if (!predictionsFound)
						logger.debug("No predictions found for routeId={} "
								+ "directionId={} stopId={}", 
								routeId, directionId, stopId);
				}
			}
		}
	}
	private static void printPredictionsMap(ConcurrentHashMap<PredictionKey, List<PredAccuracyPrediction>>  predictionMap, ArrivalDeparture arrivalDeparture)
	{
		
		logger.debug("Looking for match : " + arrivalDeparture.toString() );
		for (PredictionKey key: predictionMap.keySet())
		{			                      
            List<PredAccuracyPrediction> value = predictionMap.get(key);
            for(PredAccuracyPrediction pred:value)
            {
            	boolean keyprinted=false;
            	if(pred.getVehicleId().equals(arrivalDeparture.getVehicleId()))
            	{
            		if(!keyprinted)
            		{            			
            			logger.debug(key.toString());
            			keyprinted=true;
            		}            			
                    logger.debug(pred.toString());
            	}
            }
            
                        
		} 
	}
	/**
	 * Looks for corresponding prediction in memory. If found then prediction
	 * accuracy information for that prediction is stored in the database.
	 * <p>
	 * This method is to be called when an arrival or a departure is created.
	 * 
	 * @param arrivalDeparture
	 *            The arrival or departure that was generated
	 * @param vehicleState 
	 */
	public static void handleArrivalDeparture(
			ArrivalDeparture arrivalDeparture, VehicleState vehicleState) {
		// Get the List of predictions for the vehicle/direction/stop 
		PredictionKey key = new PredictionKey(arrivalDeparture.getVehicleId(), 
				arrivalDeparture.getDirectionId(), arrivalDeparture.getStopId());
		List<PredAccuracyPrediction> predsList = predictionMap.get(key);
		
		if (predsList == null || predsList.isEmpty())
		{
			logger.debug("No matching predictions for {}", arrivalDeparture);
			//printPredictionsMap(predictionMap, arrivalDeparture);
			return;			
		}
		
		// Go through list of predictions for vehicle, direction, stop and handle
		// the ones that match fully including being appropriate arrival or
		// departure.
		Iterator<PredAccuracyPrediction> predIterator = predsList.iterator();
		while (predIterator.hasNext()) {
			PredAccuracyPrediction pred = predIterator.next();
			
			// If not correct arrival/departure type continue to next prediction
			if (pred.isArrival() != arrivalDeparture.isArrival())
				continue;
			
			// Make sure it is for the proper trip. This is important in case a
			// vehicle is reassigned after a prediction is made. For example, a
			// prediction could be made for a trip to leave at 10am but then the
			// vehicle is reassigned to leave at 9:50am or 10:10am. That 
			// shouldn't be counted against vehicle accuracy since likely 
			// another vehicle substituted in for the original assignment. This 
			// is especially true for MBTA Commuter Rail
			String tripIdOrShortName = pred.getTripId();
			if (!tripIdOrShortName.equals(arrivalDeparture.getTripId()) 
					&& !tripIdOrShortName.equals(arrivalDeparture.getTripShortName()))
				continue;
			
			// Make sure predicted time isn't too far away from the 
			// arrival/departure time so that don't match to something really
			// inappropriate. First determine how late vehicle arrived    
			// at stop compared to the original prediction time.
			long latenessComparedToPrediction = arrivalDeparture.getTime() 
					- pred.getPredictedTime().getTime();
			if (latenessComparedToPrediction > getMaxLatenessComparedToPredictionMsec()
					|| latenessComparedToPrediction < -getMaxEarlynessComparedToPredictionMsec())
				continue;
			
			// There is a match so store the prediction accuracy info into the 
			// database
			storePredictionAccuracyInfo(pred, arrivalDeparture, vehicleState);
			
			// Remove the prediction that was matched
			predIterator.remove();
		}
	}

	/**
	 * Combine the arrival/departure with the corresponding prediction and
	 * creates PredictionAccuracy object and stores it in database.
	 * 
	 * @param pred
	 * @param arrivalDeparture
	 *            The corresponding arrival/departure information. Can be null
	 *            to indicate that for a prediction no corresponding
	 *            arrival/departure was ever determined.
	 * @param vehicleState 
	 */
	private static void storePredictionAccuracyInfo(
			PredAccuracyPrediction pred, ArrivalDeparture arrivalDeparture, VehicleState vehicleState) {
		// If no corresponding arrival/departure found for prediction
		// then use null for arrival/departure time to indicate such.
		Date arrivalDepartureTime = arrivalDeparture!=null ? 
				new Date(arrivalDeparture.getTime()) : null;
		Long totalDwellTime=null;
		Long totalTraveTime=null;
		
		if(arrivalDeparture!=null && vehicleState!=null && pred.getAvlTime()!=null )
		{
			try {
				
				logger.debug("Getting times between {} and {}.",pred.getAvlTime(), arrivalDeparture);
				totalDwellTime=getTotalDwellTimeSince(vehicleState, arrivalDeparture, pred.getAvlTime());		
				totalTraveTime=getTotalTravelTimeSince(vehicleState, arrivalDeparture, pred.getAvlTime());
				logger.debug("Total times {} is {} diff from total {}",(totalDwellTime+totalTraveTime),
						(arrivalDeparture.getTime()-pred.getAvlTime().getTime()) - (totalDwellTime+totalTraveTime),
							arrivalDeparture.getTime()-pred.getAvlTime().getTime());
				
				if((arrivalDeparture.getTime()-pred.getAvlTime().getTime()) - (totalDwellTime+totalTraveTime)!=0)
				{
					totalDwellTime=null;
					totalTraveTime=null;					
					logger.error("Sum of individual times should match total therefore failed to calculate totals for dwell and travel times. {}", pred);
				}
			} catch (Exception e) {			
				totalDwellTime=null;
				totalTraveTime=null;
				logger.error("Failed to calculate totals for dwell and travel times. {} {}",pred, e);
			}
		}
		
		// Combine the arrival/departure with the corresponding prediction
		// and create PredictionAccuracy object
		PredictionAccuracy predAccuracy = null;
		try {
			predAccuracy = new PredictionAccuracy(
					pred.getRouteId(), pred.getDirectionId(), pred.getStopId(),
					pred.getTripId(), arrivalDepartureTime,
					pred.getPredictedTime(), pred.getPredictionReadTime(), pred.getAvlTime(),
					pred.getSource(),pred.getAlgorithm(), pred.getVehicleId(), pred.isAffectedByWaitStop(),pred.getTotalPredictedDwellTime(), pred.getTotalPredictedTravelTime(), totalDwellTime, totalTraveTime);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Add the prediction accuracy object to the db logger so that
		// it gets written to database
		logger.debug("Storing prediction accuracy object to db. {}",
				predAccuracy);
		if(predAccuracy!=null)
			Core.getInstance().getDbLogger().add(predAccuracy);
	}

	private static Long getTotalTravelTimeSince(VehicleState vehicleState, ArrivalDeparture arrivalDeparture,
			Date avlTime) {
						
		ArrivalDeparture currentArrival=null;
		ArrivalDeparture currentDeparture=null;
		ArrivalDeparture lastDeparture=null;
		logger.debug("Getting travel time total.");
		ArrivalDeparture currentEvent=arrivalDeparture;
		do
		{
			logger.debug("Getting Event {}.",currentEvent);
		}while((currentEvent=vehicleState.getPreviousEvent(currentEvent))!=null&&
				currentEvent.getTime()>=avlTime.getTime());
		
		Long totalTravelTime=0L;
		
		if(arrivalDeparture!=null)
		{		
			
			if(arrivalDeparture.isArrival())
			{
				currentArrival=arrivalDeparture;
				lastDeparture=null;
			}
			else
			{
				currentDeparture=arrivalDeparture;
				lastDeparture=currentDeparture;
			}
			
			currentEvent=arrivalDeparture;
			
			ArrivalDeparture lastEvent = currentEvent;
			
			while((currentEvent=vehicleState.getPreviousEvent(currentEvent))!=null&&
					currentEvent.getTime()>=avlTime.getTime())
			{
				lastEvent=currentEvent;
				if(currentEvent.isArrival())
				{
					currentArrival=currentEvent;
					lastDeparture=null;
				}
				else
				{
					currentDeparture=currentEvent;
					lastDeparture=currentDeparture;
				}
				
				if(currentDeparture!=null&&currentArrival!=null)
				{
					
					if(!currentDeparture.getStopId().equals(currentArrival.getStopId()))
					{
						if(currentArrival.getTime()>=currentDeparture.getTime())
						{
							totalTravelTime=totalTravelTime+(currentArrival.getTime()-currentDeparture.getTime());
							logger.debug("Getting travel time between {} and {} value {}.", currentDeparture, currentArrival,(currentArrival.getTime()-currentDeparture.getTime()) );
							currentDeparture=null;
							currentArrival=null;
						}		
					}
				}else
				{
					logger.warn("Arrival {} or departure {} is null.",currentArrival,currentDeparture);
				}
			}
			
			if(lastEvent!=null&&lastEvent.isDeparture())
			{
				totalTravelTime=totalTravelTime+(lastEvent.getTime()-avlTime.getTime());
				logger.debug("Getting travel time between {} and {} value {}.",avlTime, lastEvent, (lastEvent.getTime()-avlTime.getTime()));
			}									
		}
		return totalTravelTime;
	}

	private static Long getTotalDwellTimeSince(VehicleState vehicleState, ArrivalDeparture arrivalDeparture,
			Date avlTime) {
		ArrivalDeparture currentArrival=null;
		ArrivalDeparture currentDeparture=null;
		ArrivalDeparture lastArrival=null;
		Long totalDwellTime=0L;
		
		if(arrivalDeparture!=null)
		{		
			if(arrivalDeparture.isArrival())
			{
				currentArrival=arrivalDeparture;
				lastArrival=currentArrival;
			}
			else
			{
				currentDeparture=arrivalDeparture;
				lastArrival=null;
			}
			
			ArrivalDeparture currentEvent=arrivalDeparture;
			
			ArrivalDeparture lastEvent = currentEvent;
			
			while((currentEvent=vehicleState.getPreviousEvent(currentEvent))!=null
					&& currentEvent.getTime()>=avlTime.getTime())
			{
				
				lastEvent=currentEvent;
				if(currentEvent.isArrival())
				{
					currentArrival=currentEvent;
					lastArrival=currentArrival;
				}
				else
				{
					currentDeparture=currentEvent;
					lastArrival=null;
				}
			 
				
				if(currentDeparture!=null&&currentArrival!=null)
				{
					if(currentDeparture.getTime()>=currentArrival.getTime()&&currentDeparture.getStopId().equals(currentArrival.getStopId()))
					{						
						totalDwellTime=totalDwellTime+(currentDeparture.getTime()-currentArrival.getTime());
						logger.debug("Getting dwell time between {} and {} value {}.", currentArrival,currentDeparture, (currentDeparture.getTime()-currentArrival.getTime()));
						currentDeparture=null;
						currentArrival=null;		
					}
					
				}else
				{
					logger.warn("Arrival {} or departure {} is null.",currentArrival,currentDeparture);
				}
				
			}
			
			if(lastEvent!=null&&lastEvent.isArrival())
			{
				totalDwellTime=totalDwellTime+(lastEvent.getTime()-avlTime.getTime());
				logger.debug("Getting dwell time between {} and {} value {}.",avlTime, lastEvent,(lastEvent.getTime()-avlTime.getTime()) );
			}
		
		}
		return totalDwellTime;
	}
}
