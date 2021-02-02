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

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.ScheduleTime;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.utils.Time;

/**
 * For determining the real-time schedule adherence for a predictable vehicle.
 *
 * @author SkiBu Smith
 *
 */
public class RealTimeSchedAdhProcessor {

	private static final Logger logger = 
			LoggerFactory.getLogger(RealTimeSchedAdhProcessor.class);

	/********************** Member Functions **************************/

	/**
	 * Determines the current schedule adherence for the vehicle. If vehicle at
	 * a stop with a scheduled departure time then the schedule adherence for
	 * that stop is returned. Otherwise will look at when the vehicle is
	 * expected to be at the next stop with a scheduled time and provide the
	 * expected schedule adherence for that stop. Doing it this way is useful
	 * because it allows the schedule adherence to be updated while the vehicle
	 * is in between stops.
	 * 
	 * @param vehicleState
	 * @return The real-time schedule adherence for the vehicle, or null if
	 *         vehicle is not predictable or there are no upcoming stops with a
	 *         schedule time.
	 */
	public static TemporalDifference generate(VehicleState vehicleState) {
		// If vehicle not matched/predictable then cannot provide schedule
		// adherence
		if (!vehicleState.isPredictable())
			return null;
		
		// Convenience variables
		TemporalMatch match = vehicleState.getMatch();		
		Trip trip = match.getTrip();
		Date avlTime = vehicleState.getAvlReport().getDate();
		String vehicleId = vehicleState.getVehicleId();
		
		// If vehicle at a stop with a scheduled departure time then the 
		// schedule adherence is either 0 because the departure time hasn't 
		// been reached yet or the vehicle is late.
		VehicleAtStopInfo stopInfo = match.getAtStop();
		if (stopInfo != null) {
			ScheduleTime schedTime = stopInfo.getScheduleTime();	
			
			if (schedTime != null && schedTime.getDepartureTime() != null) {
				// Determine the scheduled departure time in epoch time
				long departureEpochTime = Core.getInstance().getTime()
						.getEpochTime(schedTime.getDepartureTime(), avlTime);
				
				// Wait stops are handled specially since if before the 
				// departure time then schedule adherence is 0. The scheduled
				// arrival time doesn't matter.
				if (stopInfo.isWaitStop()) {
					// If departure time hasn't been reached yet...
					if (avlTime.getTime() < departureEpochTime) {
						// Departure time not yet reached so perfectly on time!
						logger.debug("For vehicleId={} vehicle at wait stop " +
								"but haven't reached departure time yet so " +
								"returning 0 as the schedule adherence. " +
								"avlTime={} and scheduled departure time={}",
								vehicleId, avlTime, schedTime);
						return new TemporalDifference(0);
					} else {
						TemporalDifference scheduleAdherence = 
								new TemporalDifference(departureEpochTime - 
										avlTime.getTime());
	
						// Already past departure time so return that vehicle 
						// is late
						logger.debug("For vehicleId={} vehicle at wait stop " +
								"but have reached departure time so returning " +
								"schedule adherence={}. avlTime={} and " +
								"scheduled departure time={}",
								vehicleId, scheduleAdherence, avlTime, 
								schedTime);
						return scheduleAdherence;
					}					
				} else { 
					// Not a wait stop where vehicle is supposed to wait
					// to depart until scheduled time. Therefore simply
					// return difference between scheduled departure
					// time and the AVL time.
					TemporalDifference scheduleAdherence = 
							new TemporalDifference(departureEpochTime - 
									avlTime.getTime());

					// Already past departure time so return that vehicle 
					// is late
					logger.debug("For vehicleId={} vehicle at stop but " +
							"have reached departure time so returning " +
							"schedule adherence={}. avlTime={} and " +
							"scheduled time={}",
							vehicleId, scheduleAdherence, avlTime, 
							schedTime);
					return scheduleAdherence;
				}
			}
		}
		
		// Vehicle wasn't at a stop with a schedule time so determine the
		// schedule adherence by looking at when it is expected to arrive
		// at the next stop with a scheduled time. Determine the 
		// appropriate match to use for the upcoming stop where there is a 
		// schedule time.		
		RouteMatch matchAtStopWithScheduleTime = 
				match.getMatchAtNextStopWithScheduleTime();
		if (matchAtStopWithScheduleTime == null)
			return null;
		
		// Determine how long it is expected to take for vehicle to get to 
		// that stop
		int travelTimeToStopMsec = TravelTimes.getInstance()
				.expectedTravelTimeBetweenMatches(vehicleId, avlTime,
						match, matchAtStopWithScheduleTime);
		
		// If using departure time then add in expected stop wait time
		int stopPathIndex = matchAtStopWithScheduleTime.getStopPathIndex();
		ScheduleTime scheduleTime = trip.getScheduleTime(stopPathIndex);
		if (scheduleTime.getDepartureTime() != null) {
			//TravelTimesForStopPath 
			int stopTime = trip.getTravelTimesForStopPath(stopPathIndex)
					.getStopTimeMsec();
			travelTimeToStopMsec += stopTime;
		}
		
		// Return the schedule adherence
		long expectedTime = avlTime.getTime() + travelTimeToStopMsec;
		long departureEpochTime = Core.getInstance().getTime()
				.getEpochTime(scheduleTime.getTime(), avlTime);
		TemporalDifference scheduleAdherence = 
				new TemporalDifference(departureEpochTime - expectedTime);
		logger.debug("For vehicleId={} vehicle not at stop returning " +
				"schedule adherence={}. avlTime={} and scheduled time={}",
				vehicleId, scheduleAdherence, avlTime, scheduleTime);
		return scheduleAdherence;
	}
	
	/**
	 * We define effective schedule time as where the bus currently falls in the schedule based on 
	 * its current position.
	 */
	public static TemporalDifference generateEffectiveScheduleDifference(VehicleState vehicleState) {
	  TemporalMatch match = vehicleState.getMatch();
    Trip trip = match.getTrip();
    long avlTime = match.getAvlTime();
    String vehicleId = vehicleState.getVehicleId();
    
    int nextStopPathIndex = match.getStopPathIndex();
    int previousStopPathIndex = nextStopPathIndex -1;
    
    if (previousStopPathIndex < 0) {
      // we are either before the trip or at the first stop (layover)
      Long departureEpoch = Core.getInstance().getTime().getEpochTime(trip.getScheduleTime(0).getTime(), avlTime);
 
      
      // if trip has not started yet schedule difference = 0,
      // unless previous trip is active in which case schedule difference=early
      long difference = avlTime - departureEpoch;
      
      if (difference < 0) {
    	  difference = 0;
    	  
    	  int tripIndex = match.getTripIndex();
    	  if (tripIndex > 0) {
    		  Trip prevTrip = match.getBlock().getTrip(tripIndex - 1);
        	  Long epochEndTime = Core.getInstance().getTime().getEpochTime(prevTrip.getEndTime(), avlTime);
        	          	  
        	  difference = Math.min(0, avlTime - epochEndTime);
        	  
        	  logger.debug("vehicleId {} has schedDev before trip set by previous trip of {}",
        			  vehicleId,
        			  difference);
    	  }
      }
      
    	  
      logger.debug("vehicleId {} has schedDev before trip start of {}", 
          vehicleId,
          difference); 
      
      return new TemporalDifference(difference);
    }
    if (match.isAtStop()) {
      // If at stop, nextStopPathIndex can be for current stop or next stop depending
      // on match.atEndOfPathStop()
      int departureSecs = match.getAtStop().getScheduleTime().getTime();
      Long departureEpoch = Core.getInstance().getTime()
          .getEpochTime(departureSecs, avlTime);
      if (departureEpoch > avlTime) {
        logger.debug("vehicleId {} has schedDev at stop of 0", 
            vehicleId);
      }
      logger.debug("vehicleId {} has schedDev at stop of {}", 
          vehicleId,
          (avlTime - departureEpoch));
      return new TemporalDifference(avlTime - departureEpoch);
    }
    
    // we must be between stops, interpolate effective schedule
    long fromStopTimeSecs = trip.getScheduleTime(previousStopPathIndex).getTime();
    long toStopTimeSecs = trip.getScheduleTime(nextStopPathIndex).getTime();
    long pathTime = toStopTimeSecs - fromStopTimeSecs;
    
    double ratio = match.getDistanceAlongStopPath() / match.getStopPath().getLength();
    
    int effectiveStopTimeSec = (int) (fromStopTimeSecs + (pathTime * ratio));
    Long effectiveScheduleTimeEpoch = Core.getInstance().getTime().getEpochTime(effectiveStopTimeSec, avlTime);
    
    logger.debug("vehicleId {} has interpolated schedDev of {}, avlTime={}, effective={}", 
        vehicleId, 
        Time.elapsedTimeStr(avlTime - effectiveScheduleTimeEpoch),
        Time.timeStr(avlTime),
        Time.timeStr(effectiveScheduleTimeEpoch));
	  return new TemporalDifference(avlTime - effectiveScheduleTimeEpoch);
	}
}
