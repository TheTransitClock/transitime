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
package org.transitclock.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.config.LongConfigValue;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.VehicleStateManager;

import org.transitclock.core.diversion.cache.DiversionsCacheFactory;
import org.transitclock.core.diversion.cache.DiversionsKey;
import org.transitclock.core.diversion.cache.DiversionsList;
import org.transitclock.core.holdingmethod.HoldingTimeGeneratorFactory;
import org.transitclock.core.predictiongenerator.PredictionComponentElementsGenerator;
import org.transitclock.core.predictiongenerator.bias.BiasAdjuster;
import org.transitclock.core.predictiongenerator.bias.BiasAdjusterFactory;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.HoldingTime;
import org.transitclock.db.structs.PredictionForStopPath;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.ipc.data.IpcPrediction;
import org.transitclock.ipc.data.IpcPrediction.ArrivalOrDeparture;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

/**
 * When a new match based on AVL data is made for a vehicle the methods in this
 * class are used to generate the corresponding predictions.
 * <p>
 * Layovers are the most complicated part. At a layover a vehicle is expected to
 * leave at the scheduled departure time. But if a vehicle is late then it will
 * stop just for the max of the stop time or the break time.
 * <p>
 * The stop time should be based on historic AVL data but I'm not sure how that
 * can be determined for a layover. Perhaps just need to use a default time and
 * not update it via historic AVL data? 
 * <p>
 * The break time is a value per stop, though it will default to a system wide
 * setting since most of the time won't have specific break time data per stop.
 * <p>
 * Also need to take into account that vehicles frequently don't leave right at
 * the scheduled departure time. The AVL data can be used to determine when they
 * really depart. This could be stored as the stop time for the stop. This
 * overloads the meaning of stop time because then it is used both for
 * determining 1) how late relative to the schedule time a vehicle departs a
 * layover; and 2) how long a vehicle will stop for if vehicle arrives late and
 * the stop time is greater than the break time.
 * 
 * @author SkiBu Smith
 * 
 */
public class PredictionGeneratorDefaultImpl extends PredictionGenerator implements PredictionComponentElementsGenerator{

	private static IntegerConfigValue maxPredictionsTimeSecs =
			new IntegerConfigValue("transitclock.core.maxPredictionsTimeSecs", 
					45 * Time.SEC_PER_MIN,
					"How far forward into the future should generate " +
					"predictions for.");
	public static int getMaxPredictionsTimeSecs() {
		return maxPredictionsTimeSecs.getValue();
	}
			
	private static LongConfigValue generateHoldingTimeWhenPredictionWithin =
			new LongConfigValue("transitclock.core.generateHoldingTimeWhenPredictionWithin",
					0L,
			"If the prediction is less than this number of milliseconds from current time then use it to generate a holding time");
			
			
	private static BooleanConfigValue useArrivalPredictionsForNormalStops =
			new BooleanConfigValue("transitclock.core.useArrivalPredictionsForNormalStops", 
					true,
					"For specifying whether to use arrival predictions or " +
					"departure predictions for normal, non-wait time, stops.");
	
	private static IntegerConfigValue maxLateCutoffPredsForNextTripsSecs =
			new IntegerConfigValue("transitclock.core.maxLateCutoffPredsForNextTripsSecs",
					Integer.MAX_VALUE,
					"If a vehicle is further behind schedule than this amount "
					+ "then predictions for subsequent trips will be marked as "
					+ "being uncertain. This is useful for when another vehicle "
					+ "might take over the next trip for the block due to "
					+ "vehicle being late.");
	
	private static BooleanConfigValue useExactSchedTimeForWaitStops =
			new BooleanConfigValue("transitclock.core.useExactSchedTimeForWaitStops", 
					true,
					"The predicted time for wait stops includes the historic "
					+ "wait stop time. This means it will be a bit after the "
					+ "configured schedule time. But some might not want to "
					+ "see such adjusted times. Plus just showing the schedule "
					+ "time is more conservative, and therefore usually better. "
					+ "If this value is set to true then the actual schedule "
					+ "time will be used. If false then the schedule time plus "
					+ "the wait stop time will be used.");
	
	private static BooleanConfigValue useHoldingTimeInPrediction =
			new BooleanConfigValue("useHoldingTimeInPrediction",
					false,
					"Add holding time to prediction.");
	
	private static final Logger logger = 
			LoggerFactory.getLogger(PredictionGeneratorDefaultImpl.class);

	/********************** Member Functions **************************/

	/**
	 * Generates prediction for the stop specified by the indices parameter. It
	 * will be an arrival prediction if at the end of the trip or the
	 * useArrivalTimes parameter is set to true, and it is not at a waitStop
	 * (since at waitStop always want a departure prediction. For departures the
	 * prediction time will be the sum of the parameters predictionTime and
	 * timeStoppedMsec.
	 * 
	 * @param avlReport
	 *            So can get information such as vehicleId and AVL time
	 * @param indices
	 *            Describes the stop that should generate prediction for
	 * @param predictionTime
	 *            The expected arrival time at the stop.
	 * @param useArrivalTimes
	 *            For when not at end of trip or at a waitStop we have the
	 *            choice of either generating arrival or departure times. For
	 *            such stops this parameter whether to generate an arrival or a
	 *            departure time for such a stop.
	 * @param affectedByWaitStop
	 *            to indicate if prediction is not as accurate because it
	 *            depends on driver leaving waitStop according to schedule.
	 * @param isDelayed
	 *            The vehicle has not been making forward progress
	 * @param lateSoMarkAsUncertain
	 *            Indicates that vehicle is late and now generating predictions
	 *            for a subsequent trip. Use to indicate that the predictions
	 *            are less certain.
	 * @return The generated Prediction
	 */
	 protected IpcPrediction generatePredictionForStop(AvlReport avlReport,  
			Indices indices, long predictionTime, boolean useArrivalTimes,
			boolean affectedByWaitStop, boolean isDelayed,

	  boolean lateSoMarkAsUncertain, int tripCounter, Integer scheduleDeviation) {	 

		// Determine additional parameters for the prediction to be generated
		
		StopPath path = indices.getStopPath();
		String stopId = path.getStopId();
		int gtfsStopSeq = path.getGtfsStopSeq();
		
		if(BiasAdjusterFactory.getInstance()!=null)
		{
			BiasAdjuster adjuster = BiasAdjusterFactory.getInstance();
			predictionTime=avlReport.getTime()+adjuster.adjustPrediction(predictionTime-avlReport.getTime());			
		}
		
		Trip trip = indices.getTrip();
		
		long freqStartTime=-1;		
		VehicleState vehicleState = VehicleStateManager.getInstance().getVehicleState(avlReport.getVehicleId());		
		if(trip.isNoSchedule()) {													
			if(vehicleState.getTripStartTime(tripCounter)!=null)
				freqStartTime=vehicleState.getTripStartTime(tripCounter).longValue();			
		}
						
		// If should generate arrival time...
		if ((indices.atEndOfTrip() || useArrivalTimes) && !indices.isWaitStop()) {	
			// Create and return arrival time for this stop
			return new IpcPrediction(avlReport, stopId, gtfsStopSeq, trip, 
					predictionTime,	predictionTime, indices.atEndOfTrip(),
					affectedByWaitStop, isDelayed, lateSoMarkAsUncertain,
					ArrivalOrDeparture.ARRIVAL, scheduleDeviation, freqStartTime, tripCounter,vehicleState.isCanceled());

		} else {
			
			// Generate a departure time
						int expectedStopTimeMsec = 
								(int) getStopTimeForPath(indices, avlReport, vehicleState);
			// If at a wait stop then need to handle specially...
			if (indices.isWaitStop()) {
				
				
				logger.debug("For vehicleId={} the original arrival time " +
						"for waitStop stopId={} is {}",
						avlReport.getVehicleId(), path.getStopId(),
						Time.dateTimeStrMsec(predictionTime));
				
				// At layover so need to look at if it has enough time to get 
				// to the waitStop, the scheduled departure time, the expected
				// stop time since vehicles often don't depart on time, and 
				// whether driver getting enough break time.
				long arrivalTime = predictionTime;
				boolean deadheadingSoNoDriverBreak = false;
				
				// Make sure have enough time to get to the waitStop for the
				// departure.
				double distanceToWaitStop = Geo.distance(avlReport.getLocation(), 
						indices.getStopPath().getStopLocation());
				int crowFliesTimeToWaitStop = 
						TravelTimes.travelTimeAsTheCrowFlies(distanceToWaitStop);
				if (avlReport.getTime() + crowFliesTimeToWaitStop > arrivalTime) {
					arrivalTime = avlReport.getTime() + crowFliesTimeToWaitStop;
					deadheadingSoNoDriverBreak = true;
					logger.debug("For vehicleId={} adjusted the arrival time "
							+ "for layover stopId={} tripId={} blockId={} "
							+ "since crowFliesTimeToLayover={} "
							+ "but predictionTime-avlReport.getTime()={}msec. The "
							+ "arrival time is now {}",
							avlReport.getVehicleId(), path.getStopId(),
							trip.getId(), trip.getBlockId(),
							crowFliesTimeToWaitStop, 
							predictionTime-avlReport.getTime(),
							Time.dateTimeStrMsec(arrivalTime));
				}
				
				// If it is currently before the scheduled departure time then use
				// the schedule time. But if after the scheduled time then use
				// the prediction time, which indicates when it is going to arrive
				// at the stop, but also adjust for stop wait time.
				long scheduledDepartureTime = TravelTimes
								.scheduledDepartureTime(indices, arrivalTime);
				long expectedDepartureTime =
						Math.max(arrivalTime + expectedStopTimeMsec,
								scheduledDepartureTime);
				if (expectedDepartureTime > scheduledDepartureTime) {
					logger.info("For vehicleId={} adjusted departure time "
							+ "for wait stop stopId={} tripId={} blockId={} to "
							+ "expectedDepartureTimeWithStopWaitTime={} "
							+ "because arrivalTime={} but "
							+ "scheduledDepartureTime={} and "
							+ "expectedStopTimeMsec={}", 
							avlReport.getVehicleId(), path.getStopId(), 
							trip.getId(), trip.getBlockId(),
							Time.dateTimeStrMsec(expectedDepartureTime),
							Time.dateTimeStrMsec(arrivalTime),
							Time.dateTimeStrMsec(scheduledDepartureTime),
							expectedStopTimeMsec);
				}
				
				// Make sure there is enough break time for the driver to get
				// their break. But only giving drivers a break if vehicle not
				// limited by deadheading time. Thought is that if deadheading
				// then driver just starting assignment or already got break
				// elsewhere, so won't take a break at this layover.
				if (!deadheadingSoNoDriverBreak) {
					if (expectedDepartureTime < 
							predictionTime + path.getBreakTimeSec()*Time.MS_PER_SEC) {
						expectedDepartureTime = 
								predictionTime + path.getBreakTimeSec()*Time.MS_PER_SEC;
						logger.info("For vehicleId={} adjusted departure time " 
								+ "for wait stop stopId={} tripId={} blockId={} "
								+ "to expectedDepartureTime={} to ensure "
								+ "driver gets break of path.getBreakTimeSec()={}",
								avlReport.getVehicleId(), path.getStopId(),
								trip.getId(), trip.getBlockId(),
								Time.dateTimeStrMsec(expectedDepartureTime), 
								path.getBreakTimeSec());
					}					
				}
								
				// Create and return the departure prediction for this wait stop.
				// If supposed to use exact schedule times for the end user for
				// the wait stops then use the special IpcPrediction() 
				// constructor that allows both the 
				// predictionForNextStopCalculation and the predictionForUser 
				// to be specified.
				if (useExactSchedTimeForWaitStops.getValue()) {
					// Configured to use schedule times instead of prediction
					// times for wait stops. So need to determine the prediction
					// time without taking into account the stop wait time.
					long expectedDepartureTimeWithoutStopWaitTime = 
							Math.max(arrivalTime, scheduledDepartureTime);
					if (!deadheadingSoNoDriverBreak) {
						expectedDepartureTimeWithoutStopWaitTime =
								Math.max(
										expectedDepartureTimeWithoutStopWaitTime,
										path.getBreakTimeSec()
												* Time.MS_PER_SEC);
					}
								
					long predictionForNextStopCalculation = expectedDepartureTime;
					long predictionForUser = expectedDepartureTimeWithoutStopWaitTime;
					return new IpcPrediction(avlReport, stopId, gtfsStopSeq,
							trip, predictionForUser,
							predictionForNextStopCalculation,
							indices.atEndOfTrip(), affectedByWaitStop,
							isDelayed, lateSoMarkAsUncertain,

							ArrivalOrDeparture.DEPARTURE, scheduleDeviation, freqStartTime, tripCounter,vehicleState.isCanceled());

				} else {
					// Use the expected departure times, possibly adjusted for 
					// stop wait times
					return new IpcPrediction(avlReport, stopId, gtfsStopSeq,
							trip, expectedDepartureTime, expectedDepartureTime,
							indices.atEndOfTrip(), affectedByWaitStop,
							isDelayed, lateSoMarkAsUncertain,

							ArrivalOrDeparture.DEPARTURE, scheduleDeviation, freqStartTime, tripCounter,vehicleState.isCanceled());

				}
			} else {
				// Create and return the departure prediction for this 
				// non-wait-stop stop
				return new IpcPrediction(avlReport, stopId, gtfsStopSeq, trip,
						predictionTime + expectedStopTimeMsec, 
						predictionTime + expectedStopTimeMsec, 
						indices.atEndOfTrip(),
						affectedByWaitStop, isDelayed, lateSoMarkAsUncertain,
						ArrivalOrDeparture.DEPARTURE, scheduleDeviation, freqStartTime, tripCounter,vehicleState.isCanceled());


			}
		}			
	}

	private boolean isDiverted(Indices indices, DiversionsList diversions) {
		if(diversions==null)
			return false;
		for( org.transitclock.core.diversion.model.Diversion diversion:diversions.getDiversions())
		{
			if(indices.getStopPath().getGtfsStopSeq()>=diversion.getStartStopSeq() && indices.getStopPath().getGtfsStopSeq()<=diversion.getReturnStopSeq())
				return true;
		}
		return false;
	}
	/**
	 * Generates the predictions for the vehicle. 
	 * 
	 * @param vehicleState
	 *            Contains the new match for the vehicle that the predictions
	 *            are to be based on.
	 * @return List of Predictions. Can be empty but will not be null.
	 */
	@Override
	public List<IpcPrediction> generate(VehicleState vehicleState) {	
		// For layovers always use arrival time for end of trip and
		// departure time for anything else. But for non-layover stops
		// can use either arrival or departure times, depending on what
		// the agency wants. Therefore make this configurable.
		
		
		boolean useArrivalPreds = useArrivalPredictionsForNormalStops.getValue();
		
		// If prediction is based on scheduled departure time for a layover
		// then the predictions are likely not as accurate. Therefore this
		// information needs to be part of a prediction.
		boolean affectedByWaitStop = false;
		
		// For storing the new predictions
		List<IpcPrediction> newPredictions = new ArrayList<IpcPrediction>();

		// Get the new match for the vehicle that predictions are to be based on
		TemporalMatch match = vehicleState.getMatch();
		Indices indices = match.getIndices();
		
		
		// Get info from the AVL report.
		AvlReport avlReport = vehicleState.getAvlReport();
		long avlTime = avlReport.getTime();
		boolean schedBasedPreds = avlReport.isForSchedBasedPreds();
		
		logger.debug("Calling prediction algorithm for {} with a match {}.", avlReport, match);
		
		// Get time to end of first path and thereby determine prediction for 
		// first stop.
		
		long predictionTime = avlTime + expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match);
		
		// Determine if vehicle is so late that predictions for subsequent 
		// trips should be marked as uncertain given that another vehicle
		// might substitute in for that block.
		TemporalDifference lateness = vehicleState.getRealTimeSchedAdh();
		boolean lateSoMarkSubsequentTripsAsUncertain = lateness != null ? 
						lateness.isLaterThan(maxLateCutoffPredsForNextTripsSecs
								.getValue()) : false;
		if (lateSoMarkSubsequentTripsAsUncertain)
			logger.info("Vehicle late so marking predictions for subsequent "
					+ "trips as being uncertain. {}", vehicleState);
		int currentTripIndex = indices.getTripIndex();
		
		// For filtering out predictions that are before now, which can
		// happen for schedule based predictions
		long now = Core.getInstance().getSystemTime();
				
		//indices.incrementStopPath(predictionTime);
		
		Integer tripCounter = new Integer(vehicleState.getTripCounter());

		Map<Integer, IpcPrediction> filteredPredictions = new HashMap<Integer, IpcPrediction>();
		

		// Continue through block until end of block or limit on how far
		// into the future should generate predictions reached.
		
		// Stop generating predictions if we reach a diversion.
		DiversionsList diversions=null;
		if(DiversionsCacheFactory.getInstance()!=null)				
			diversions = DiversionsCacheFactory.getInstance().getDiversions(new DiversionsKey(match.getTrip().getId(), match.getRoute().getId()));
								
		while (schedBasedPreds
				|| predictionTime < 
					avlTime + maxPredictionsTimeSecs.getValue() * Time.MS_PER_SEC 
					|| !isDiverted(indices, diversions)) {
			// Keep track of whether prediction is affected by layover 
			// scheduled departure time since those predictions might not
			// be a accurate. Once a layover encountered then all subsequent
			// predictions are affected by a layover.
			
			// Increment indices so can generate predictions for next path
			
			
			if (indices.isWaitStop())
				affectedByWaitStop = true;
			
			boolean lateSoMarkAsUncertain =
					lateSoMarkSubsequentTripsAsUncertain
							&& indices.getTripIndex() > currentTripIndex;
			
			
			Integer delay = RealTimeSchedAdhProcessor.generateEffectiveScheduleDifference(vehicleState).getTemporalDifference()/1000;
							
			// Determine the new prediction
			IpcPrediction predictionForStop = generatePredictionForStop(avlReport,
					indices, predictionTime,
					useArrivalPreds, affectedByWaitStop, 
					vehicleState.isDelayed(), lateSoMarkAsUncertain, tripCounter, delay);
												
			
			if((predictionForStop.getPredictionTime()-Core.getInstance().getSystemTime())<generateHoldingTimeWhenPredictionWithin.getValue() &&
					(predictionForStop.getPredictionTime()-Core.getInstance().getSystemTime())>0)
			{							
				if(HoldingTimeGeneratorFactory.getInstance()!=null)
				{							
					HoldingTime holdingTime = HoldingTimeGeneratorFactory.getInstance().generateHoldingTime(vehicleState, predictionForStop);
					if(holdingTime!=null)
					{
						//HoldingTimeCache.getInstance().putHoldingTimeExlusiveByStop(holdingTime, new Date(Core.getInstance().getSystemTime()));
						HoldingTimeCache.getInstance().putHoldingTime(holdingTime);
						vehicleState.setHoldingTime(holdingTime);
					}
				}			
			}

			logger.debug("For vehicleId={} generated prediction {}",
					vehicleState.getVehicleId(), predictionForStop);

			
			// If prediction ended up being too far in the future (which can 
			// happen if it is a departure prediction where the time at the 
			// stop is added to the arrival time) then don't add the prediction
			// and break out of the loop.
			if (!schedBasedPreds
					&& predictionForStop.getPredictionTime() > avlTime
							+ maxPredictionsTimeSecs.getValue() * Time.MS_PER_SEC)
				break;

			// If no schedule assignment then don't want to generate predictions
			// for the last stop of the trip since it is a duplicate of the 			
			// first stop of the trip
			boolean lastStopOfNonSchedBasedTrip = 
					indices.getBlock().isNoSchedule() && indices.atEndOfTrip();
			
			// This is incremented each time the prediction starts a new trip. 
			// The first prediction for the start of a new trip is used as the 
			// start time for a frequency based service 
			if(lastStopOfNonSchedBasedTrip) {						
				tripCounter++;
				vehicleState.putTripStartTime(tripCounter, predictionForStop.getPredictionTime());
				//break;
			}
					

			// The prediction is not too far into the future. Add it to the 
			// list of predictions to be returned. But only do this if
			// it is not last stop of non-schedule based trip since that is a
			// a duplicate of the stop for the next trip. Also, don't add
			// prediction if it is in the past since those are not needed.
			// Can get predictions in the past for schedule based predictions.
			if (!lastStopOfNonSchedBasedTrip
					&& predictionForStop.getPredictionTime() > now) {


				logger.info("Generated prediction {} based on avlreport {}.", predictionForStop, avlReport);


					if(indices.atEndOfTrip() || indices.atBeginningOfTrip()){
						// Deals with case where a vehicle transitions from one trip to another and the lastStop then becomes the firstSTop
						// This occassionally leads to duplicate predictions. This works around the problem by creating a hash of predictions
						// that have the same Prediction information but different trips
						int predictionKey = lastStopPredictionHash(predictionForStop);
						if(filteredPredictions.containsKey(predictionKey) && 
								filteredPredictions.get(predictionKey) != null){
							if(predictionForStop.getTrip().getStartTime() > filteredPredictions.get(predictionKey).getTrip().getStartTime()){
								logger.debug("Found multiple predictions for Prediction with routeId={}, stopId={}, and vehicleId={} ", 
										predictionForStop.getRouteId(), predictionForStop.getStopId(), predictionForStop.getVehicleId());
								filteredPredictions.put(predictionKey, predictionForStop);
							}	
						}
						else{
							filteredPredictions.put(predictionKey, predictionForStop);
						}	
					}
					else{
						newPredictions.add(predictionForStop);
					}
					
					logger.info("Generated prediction {}", predictionForStop);

			}
			
			// Determine prediction time for the departure. For layovers
			// the prediction time can be adjusted by deadhead time,
			// schedule time, break time, etc. For arrival predictions
			// need to add the expected stop time. Need to use 
			// getActualPredictionTime() instead of getPredictionTime() to
			// handle situations where want to display to the user for wait 
			// stops schedule times instead of the calculated prediction time.
			predictionTime = predictionForStop.getActualPredictionTime();			
			if (predictionForStop.isArrival())
			{					
				predictionTime += getStopTimeForPath(indices, avlReport, vehicleState);
				/* TODO this is where we should take account of holding time */
				if(useHoldingTimeInPrediction.getValue() && HoldingTimeGeneratorFactory.getInstance()!=null)
				{
					HoldingTime holdingTime = HoldingTimeGeneratorFactory.getInstance().generateHoldingTime(vehicleState, predictionForStop);
					
					if(holdingTime!=null)
					{
						long holdingTimeMsec = holdingTime.getHoldingTime().getTime()-holdingTime.getArrivalTime().getTime();
						if(holdingTimeMsec>indices.getStopTimeForPath())
						{
							predictionTime += holdingTime.getHoldingTime().getTime()-holdingTime.getArrivalTime().getTime();
						}
					}
					
				}
			}
			indices.incrementStopPath(predictionTime);						
			// If reached end of block then done
			if (indices.pastEndOfBlock(predictionTime)) {
				logger.debug("For vehicleId={} reached end of block when " +
						"generating predictions.", 
						vehicleState.getVehicleId());
				break;
			}
			boolean isCircuitRoute=true;
			// Add in travel time for the next path to get to predicted 
			// arrival time of this stop
			if (!lastStopOfNonSchedBasedTrip && isCircuitRoute) {
				predictionTime += getTravelTimeForPath(indices, avlReport, vehicleState);
			}					
		}
		
		for(IpcPrediction prediction : filteredPredictions.values()){
			newPredictions.add(prediction);
			
			
		}
		
		// Return the results
		return newPredictions;
	}
	


	public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState)
	{
		//logger.debug("Using transiTime default algorithm for travel time prediction : " + indices + " Value: "+indices.getTravelTimeForPath());
		if(storeTravelTimeStopPathPredictions.getValue())
		{		
			PredictionForStopPath predictionForStopPath=new PredictionForStopPath(vehicleState.getVehicleId(), new Date(Core.getInstance().getSystemTime()) , new Double(new Long(indices.getTravelTimeForPath()).intValue()), indices.getTrip().getId(), indices.getStopPathIndex(), "TRANSITIME DEFAULT", true, null);		
			Core.getInstance().getDbLogger().add(predictionForStopPath);
			StopPathPredictionCache.getInstance().putPrediction(predictionForStopPath);
		}
		return indices.getTravelTimeForPath();
	}

	
	public long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState) {
		long prediction=TravelTimes.getInstance().expectedStopTimeForStopPath(indices);
		//logger.debug("Using transiTime default algorithm for stop time prediction : "+indices + " Value: "+prediction);
		return prediction;		
	}
	
	public long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, RouteMatch match)
	{
		TravelTimes travelTimes = TravelTimes.getInstance();
		return travelTimes.expectedTravelTimeFromMatchToEndOfStopPath(match);
	}

	
	
	private int lastStopPredictionHash(IpcPrediction prediction){
		final int prime = 31;
		int result = 1;
		
		result = prime * result
				+ ((prediction.getBlockId() == null) ? 0 : prediction.getBlockId().hashCode());
		result = prime * result
				+ ((prediction.getVehicleId() == null) ? 0 : prediction.getVehicleId().hashCode());
		result = prime * result
				+ ((prediction.getStopId() == null) ? 0 : prediction.getStopId().hashCode());
		result = prime * result
				+ ((prediction.getRouteId() == null) ? 0 : prediction.getRouteId().hashCode());
		result = prime * result + Long.valueOf(prediction.getPredictionTime()).hashCode();
		
		return result;
	}

}
