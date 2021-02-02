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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.configData.CoreConfig;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Trip;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

import java.util.Date;
import java.util.List;

/**
 * Singleton class that does the temporal matching to determine where 
 * an AVL report matches to the block assignment. Looks at spatial
 * matches and determines which one makes the most sense temporally.
 * 
 * @author SkiBu Smith
 */
public class TemporalMatcher {

	// Singleton class
	private static TemporalMatcher singleton = new TemporalMatcher();
	
	private static final Logger logger = 
			LoggerFactory.getLogger(TemporalMatcher.class);

	/********************** Member Functions **************************/

	/**
	 * Declaring constructor as private since singleton class
	 */
	private TemporalMatcher() {}
	
	/**
	 * Returns the TemporalMatcher singleton
	 * @return
	 */
	public static TemporalMatcher getInstance() {
		return singleton;
	}
	
	/**
	 * For the spatial match, determines how far off in time the vehicle is from
	 * what is expected based on the start time of the trip plus the travel time
	 * from the beginning of the trip. This can be used to determine which
	 * spatial match has the best temporal match. Intended to be used when first
	 * matching a vehicle to an assignment.
	 * 
	 * @param for logging messages
	 * @param date
	 * @param spatialMatch
	 * @param isFirstSpatialMatch
	 *            Set to true if this is the first of the spatial matches. This
	 *            is needed because for layovers want to make them less likely
	 *            to match, but only if not the first spatial match. This way if
	 *            matching a vehicle to middle of trip then will more likely
	 *            match to the middle instead of the layover at the end of the
	 *            trip. This is important when starting up the system while
	 *            vehicles are already running.
	 * @return The TemporalDifference between the AVL time and when the vehicle
	 *         is expected to be at that match. Returns null if the temporal
	 *         difference is beyond the allowable bounds.
	 */
	private static TemporalDifference determineHowFarOffScheduledTime(
			String vehicleId, Date date, RouteMatch spatialMatch,
			boolean isFirstSpatialMatch) {

	  // check to see if we are frequency based
    if (spatialMatch.getTrip().isNoSchedule()) {
      // if there is no schedule, we really can't be late!
      logger.debug("frequency trip has no schedule adherence, using temporalDifference of 0 {}", spatialMatch.getTrip());
      return new TemporalDifference(0);
    }
	  
		// Determine how long it should take to travel along trip to the match. 
		// Can add this time to the trip scheduled start time to determine
		// when the vehicle is predicted to be at the match. 
		RouteMatch beginningOfTrip = new RouteMatch(
				0,    // AVL time doesn't matter
				spatialMatch.getBlock(), 
				spatialMatch.getTripIndex(), 
				0,    // stopPathIndex 
				0,    // segmentIndex 
				0.0,  // distanceToSegment
				0.0); // distanceAlongSegment
		int tripStartTimeSecs = spatialMatch.getTrip().getStartTime();
		int travelTimeForCurrentTrip = 
				TravelTimes.getInstance().expectedTravelTimeBetweenMatches(vehicleId, 
						tripStartTimeSecs, 
						beginningOfTrip, spatialMatch);
		
		int msecIntoDayVehicleExpectedToBeAtMatch = 
				tripStartTimeSecs * 1000 + travelTimeForCurrentTrip;
				
		// Need to convert everything to Date or to secsInDay so can do comparison
		int avlTimeIntoDayMsec = Core.getInstance().getTime().getMsecsIntoDay(date);		
		int earlyMsec = msecIntoDayVehicleExpectedToBeAtMatch - avlTimeIntoDayMsec;

		// Also check if 24 hours early late so that can work even for when 
		// trip starts before midnight or goes after midnight.
		TemporalDifference deltaFromSchedule = 
				new TemporalDifference(earlyMsec);
		TemporalDifference beforeMidnightExpectedTimeDelta = 
				new TemporalDifference(earlyMsec - Time.MS_PER_DAY);
		TemporalDifference afterMidnightExpectedTimeDelta = 
				new TemporalDifference(earlyMsec + Time.MS_PER_DAY);
		if (beforeMidnightExpectedTimeDelta.betterThan(deltaFromSchedule))
			deltaFromSchedule = beforeMidnightExpectedTimeDelta;
		if (afterMidnightExpectedTimeDelta.betterThan(deltaFromSchedule))
			deltaFromSchedule = afterMidnightExpectedTimeDelta;
			
		// Return adherence but return null if adherence is beyond limits
		if (deltaFromSchedule.isWithinBoundsForInitialMatching()) {
			// Want to favor regular matches over layovers when can
			// match a vehicle both to a middle to the trip and to a layover. 
			// This is important when starting up the system while vehicles 
			// are already running, which is pretty much always true when
			// system is restarted. The regular matches are favored by
			// adding time to layover matches, but only if the layover match
			// is not the very first match. Otherwise wouldn't correctly
			// match to the layover at the beginning of the trip if vehicle
			// is getting assigned before actually starting that trip.
			if (spatialMatch.isLayover() && !isFirstSpatialMatch) {
				deltaFromSchedule.addTime(CoreConfig
						.getAllowableLateSecondsForInitialMatching()
						* Time.MS_PER_SEC);
			}
			
			logger.debug("For vehicleId={} determineHowFarOffScheduledTime() "
					+ "returning expectedTimeDelta={} for {}", 
					vehicleId, deltaFromSchedule, spatialMatch);
			return deltaFromSchedule;
		} else {
			logger.debug("For vehicleId={} in "
					+ "TemporalMatcher.determineHowFarOffScheduledTime() "
					+ "expectedTimeDelta={} is not within bounds of "
					+ "{}={} and {}={} so returning null for {}",
					vehicleId, deltaFromSchedule, 
					CoreConfig.getAllowableEarlySecondsId(), 
					CoreConfig.getAllowableEarlySeconds(), 
					CoreConfig.getAllowableLateSecondsId(), 
					CoreConfig.getAllowableLateSeconds(),
					spatialMatch);
			return null;
		}
	}
	
	/**
	 * For handling complicated special layover case. Special layover case. If
	 * already was at this layover then the expected travel time will be 0. For
	 * this situation need to determine if still within the layover time,
	 * meaning that the time difference is 0, or if the vehicle is instead late
	 * since the layover time has already passed.
	 * 
	 * @param vehicleState
	 * @param spatialMatches
	 * @return
	 */
	private TemporalDifference temporalDifferenceForSpecialLayover(VehicleState vehicleState,
			RouteMatch spatialMatch, int expectedTravelTimeMsec) {
		AvlReport avlReport = vehicleState.getAvlReport();
		Date avlTime = avlReport.getDate();

		// If already was at this layover then the expected travel
		// time will be 0. For this situation need to determine
		// if still within the layover time, meaning that the 
		// time difference is 0, or if the vehicle is instead
		// late since the layover time has already passed.
		if (expectedTravelTimeMsec == 0) {
			// Determine layover stop departure time. If it is before that 
			// time then it is still at layover stop and the temporal
			// difference should be 0. But if it is after the departure
			// time then the vehicle is behind where it should be.
			int departureTimeSecs = 
					spatialMatch.getScheduledWaitStopTimeSecs();
			long scheduledDepartureTime =	
					Core.getInstance().getTime().getEpochTime(
							departureTimeSecs, avlTime);
			if (avlTime.getTime() > scheduledDepartureTime) {
				// Vehicle should have already left so it is late
				logger.debug("For vehicleId={} match at layover stop but " +
						"currently after the layover time so " +
						"indicating that the vehicle is behind where " +
						"it should be. avlTime={}, scheduledDepTime={}",
						vehicleState.getVehicleId(), avlTime, 
						new Date(scheduledDepartureTime));
				return new TemporalDifference(scheduledDepartureTime -
						avlTime.getTime());
			} else {
				// Still at the layover stop so use time difference of 0
				logger.debug("For vehicleId={} match is for layover stop " +
						"but this match is at layover but had enough " +
						"time to get there so temporalDifference=0. ",
						vehicleState.getVehicleId());
				return new TemporalDifference(0);
			}
		} else {
			// Wasn't already at layover stop. But since had enough time to
			// get to the layover the time difference is 0.
			logger.debug("For vehicleId={} wasn't at layover stop but " +
					"this match is at layover yet had enough time to get " +
					"there so temporalDifference=0. expectedTravelTimeMsec={}",
					vehicleState.getVehicleId(), expectedTravelTimeMsec);
			return new TemporalDifference(0);
		}	
	}
	
	/**
	 * Returns whether the currentSpatialMatch is a better match than the
	 * bestTemporalMatchSoFar. Uses differenceFromExpectedTime for the current
	 * match to determine how good the new match is temporally.
	 * 
	 * @param bestTemporalMatchSoFar
	 *            Can be null
	 * @param currentSpatialMatch
	 *            Can be null
	 * @param differenceFromExpectedTime
	 *            The TimeDifference for the current match. Can be null if
	 *            currentSpatialMatch is null.
	 * @return True if current temporal match is better and should be used
	 */
	private static boolean currentMatchIsBetter(
			TemporalMatch bestTemporalMatchSoFar, RouteMatch currentSpatialMatch,
			TemporalDifference differenceFromExpectedTime) {
		// If there is no current match then it can't be better
		if (currentSpatialMatch == null || differenceFromExpectedTime == null)
			return false;
		
		// The current match is valid. If there was no previous best match then 
		// the current one must be better. 
		if (bestTemporalMatchSoFar == null)
			return true;
		
		// We have a previous best match and a current match so compare them.
		// If the current match is definitely better than can return true
		if (differenceFromExpectedTime.betterThanOrEqualTo(
						bestTemporalMatchSoFar.getTemporalDifference())) {
			return true;
		}
		
		// The current match does not appear better than the old one. But
		// should see if for the current match the vehicle left early. If it
		// did leave early then want to use the current match even though 
		// the temporal differences show that the vehicle should match to the
		// layover.
		boolean earlyDeparture = 
				bestTemporalMatchSoFar.isLayover()
				&& currentSpatialMatch.distanceFromBeginningOfTrip() > 
					CoreConfig.getDistanceFromLayoverForEarlyDeparture()
				&& differenceFromExpectedTime.early() >= 0
				&& differenceFromExpectedTime.early() <
					CoreConfig.getAllowableEarlyTimeForEarlyDepartureSecs() * Time.MS_PER_SEC;
		return earlyDeparture;
	}

	/**
	 * There is a complication with vehicles leaving a layover slightly early
	 * where might not temporally match to a match past the layover due to
	 * system thinking that vehicle first needs to go back to the layover and
	 * then to the spatial match. This leads to an overly long expected travel
	 * time and the result would be that the system thinks the vehicle is at the
	 * match earlier than expected. At the same time it will think that it is
	 * late with respect to the layover, but since late is considered a better
	 * match than early vehicle could be wrongly temporally matched to layover
	 * again. As the vehicle moves further along the trip this only gets worse
	 * since system will expect a longer and longer travel time for vehicle to
	 * go back to layover and then to the spatial match. So could get incorrect
	 * layover match for quiet a while, until the layover distance
	 * transitclock.core.layoverDistance, which can be large, is exceeded.
	 * 
	 * Therefore need to filter out such problematic layover matches. This is
	 * done by seeing if past schedule time for the layover match (before the
	 * schedule time vehicles should be able to roam around), the previous match
	 * for vehicle is same layover as the current spatial match, the distance
	 * from the layover stop is increasing, and there is a subsequent
	 * non-layover spatial match that could be used. If all these conditions are
	 * true then the current layover spatial match is ignored so that will
	 * properly match vehicle to subsequent non-layover spatial match.
	 * 
	 * @param vehicleState
	 *            So can get previous spatial match
	 * @param spatialMatches
	 *            So can get current spatial match being investigated and
	 *            determine if subsequent ones are non-layover matches
	 * @param matchIdx
	 *            So can get current spatial match being investigated
	 * @return true if it is a problematic layover match that should not be used
	 */
	private boolean isProblematicLayover(VehicleState vehicleState,
			List<RouteMatch> spatialMatches, int matchIdx) {
		RouteMatch previousMatch = vehicleState.getMatch();
		RouteMatch spatialMatch = spatialMatches.get(matchIdx);
		
		// If not even a layover then false
		if (!previousMatch.isLayover())
			return false;
		
		// If current spatial match is not the same layover then false
		if (!previousMatch.getIndices().equalStopPath(
							spatialMatch.getIndices()))
			return false;
		
		// If not after the scheduled time then false		
		long avlTime = spatialMatch.getAvlTime();
		long waitStopSchedTime = spatialMatch.getScheduledWaitStopTime();
		if (avlTime < waitStopSchedTime)
			return false;
		
		// If distance from layover not increasing then false
		if (spatialMatch.getDistanceToSegment() <= 
			previousMatch.getDistanceToSegment())
			return false;
		
		// If no subsequent non-layover stops then false
		boolean foundSubsequentNonLayoverMatch = false;
		for (int i=matchIdx+1; i<spatialMatches.size(); ++i) {
			if (!spatialMatches.get(i).isLayover()) {
				foundSubsequentNonLayoverMatch = true;
				break;
			}
		}
		if (!foundSubsequentNonLayoverMatch)
			return false;
		
		// Met all the conditions as a problem layover so return true
		return true;
	}
	
	/**
	 * For the spatial matches passed in determines the one that temporally
	 * makes the most sense. Compares the time elapsed between AVL reports and
	 * compares that to the expected travel time between the previous and the
	 * current spatial match. The spatial match which corresponds most to the
	 * expected travel time is returned as the best temporal match.
	 * 
	 * @param vehicleState
	 * @param spatialMatches
	 * @param divesionMatches 
	 * @return The best temporal match for the spatial matches passed in. If no
	 *         valid temporal match found then returns null.
	 */
	public TemporalMatch getBestTemporalMatch(VehicleState vehicleState,
			List<RouteMatch> spatialMatches, List<DiversionMatch> divesionMatches) {
		// Convenience variables		
		RouteMatch previousMatch = vehicleState.getMatch();
		Date previousAvlTime =
				vehicleState.getPreviousAvlReportFromSuccessfulMatch().getDate();
		AvlReport avlReport = vehicleState.getAvlReport();
		Date avlTime = avlReport.getDate();
		long avlTimeDifferenceMsec = 
				avlTime.getTime() - previousAvlTime.getTime();

		if (spatialMatches.size() == 1 && spatialMatches.get(0).getTrip().isNoSchedule()) {
		  // here we blindly trust avl assignment if its the only match
		  logger.debug("greedily matching to only spatial assigment for frequency based trip {}", spatialMatches.get(0).getTrip());
		  return new TemporalMatch(spatialMatches.get(0), new TemporalDifference(0));
		}
		
		// Find best temporal match of the spatial matches
		TemporalMatch bestTemporalMatchSoFar = null;
		for (int matchIdx = 0; matchIdx < spatialMatches.size(); ++matchIdx) {
			RouteMatch spatialMatch = spatialMatches.get(matchIdx);
			logger.debug("Examining spatial match {}", spatialMatch);
			
			// There is a complication with vehicles leaving a layover slightly 
			// early where might not temporally match to a match past the 
			// layover due to system thinking that vehicle first needs to go
			// back to the layover and then to the spatial match. 
			if (isProblematicLayover(vehicleState, spatialMatches, matchIdx)) {
				// Ignore this problematic layover match
				logger.warn("Ignoring special case layover spatial match "
						+ "because otherwise system would not properly match "
						+ "past the layover for a long time. {}", spatialMatch);
				continue;
			}
			
			// Determine how long would expect it to take to get from previous
			// match to the new match.
			int expectedTravelTimeMsecForward = 
					TravelTimes.getInstance().expectedTravelTimeBetweenMatches(
							vehicleState.getVehicleId(), 
							previousAvlTime, 
							previousMatch, spatialMatch);
			int expectedTravelTimeMsecBackward = 
					TravelTimes.getInstance().expectedTravelTimeBetweenMatches(
							vehicleState.getVehicleId(), 
							previousAvlTime, 
							spatialMatch, previousMatch);

			// TODO - Check why it has to look backwards. Useful for freq based trips. Currently breaks schedule based trips
			//int expectedTravelTimeMsec = Math.min(expectedTravelTimeMsecForward, expectedTravelTimeMsecBackward);

			int expectedTravelTimeMsec = expectedTravelTimeMsecForward;
			
			// If looking at layover match and the match is different from 
			// the previous one then it means we expect that the vehicle has
			// arrived at layover and perhaps gone beyond. For this situation
			// need to add in the travel time as the crow flies from the last
			// stop of the previous trip to the new location to determine how
			// long it should really have taken for vehicle to get to the new
			// location.
			if (spatialMatch.isLayover()
					&& !previousMatch.getIndices().equalStopPath(
							spatialMatch.getIndices())) {
				expectedTravelTimeMsec += TravelTimes
						.travelTimeFromLayoverArrivalToNewLoc(spatialMatch,
								avlReport.getLocation());
			}
			
			// Determine how far off the expected travel time. If match is
			// for a layover stop and the vehicle had enough time to make it from
			// the previous match to the layover then it is a special
			// case. Yes, this part is quite complicated but tried to show
			// clearly in comments what all the important factors are.
			TemporalDifference differenceFromExpectedTime = null;
			if (!spatialMatch.isLayover() || 
					avlTimeDifferenceMsec <= expectedTravelTimeMsec) {
				// Not the special layover case so determine how far off travel
				// time is from expected time the normal way
				differenceFromExpectedTime = new TemporalDifference(
						expectedTravelTimeMsec - avlTimeDifferenceMsec);
				logger.debug("For vehicleId={} not at layover so examining " +
						"normal temporal difference. " +
						"avlTimeDifferenceMsec={}, " +
						"expectedTravelTimeMsec={}",
						vehicleState.getVehicleId(), avlTimeDifferenceMsec, 
						expectedTravelTimeMsec);
			} else {				
				// Special layover case. If already was at this layover then the
				// expected travel time will be 0. For this situation need to
				// determine if still within the layover time, meaning that the
				// time difference is 0, or if the vehicle is instead
				// late since the layover time has already passed.
				differenceFromExpectedTime = temporalDifferenceForSpecialLayover(
						vehicleState, spatialMatch, expectedTravelTimeMsec);
			}
			// If it is before the last one just return the last one. 
			// Something is not right that it thinks it is going backwards.
			if(new Indices(spatialMatch).isEarlierStopPathThan(new Indices(previousMatch)))
			{
				logger.debug("Match {} is before previousMatch {}.", spatialMatch, previousMatch);
				//bestTemporalMatchSoFar=new TemporalMatch(previousMatch,
				//		differenceFromExpectedTime);				
				//break;			
			}
			
			logger.debug("For vehicleId={} temporal match " +
					"differenceFromExpectedTime={}",
					vehicleState.getVehicleId(), differenceFromExpectedTime);
			
			// If the expected travel isn't reasonable then don't use it!
			// Only do this if the expected travel time is significant,
			// as in greater than 2 minutes. Otherwise might throw away
			// times that are noisy just because they are small.
			if (differenceFromExpectedTime != null 
					&& expectedTravelTimeMsec > 2 * Time.MS_PER_MIN 
					&& !differenceFromExpectedTime.isWithinBounds()) {
				differenceFromExpectedTime = null;
				logger.debug("Rejecting temporal match {} because it is not "
						+ "within the allowable bounds.", 
						differenceFromExpectedTime);
			}
					
			// If this temporal match is better than the previous best one
			// then remember it. 
			if (currentMatchIsBetter(bestTemporalMatchSoFar, spatialMatch,
					differenceFromExpectedTime)) {
				bestTemporalMatchSoFar = new TemporalMatch(spatialMatch,
						differenceFromExpectedTime);
			} else {
				// This temporal match was not better. If already found a 
				// temporal match then can stop looking since it will only
				// get worse for the remaining spatial matches.
				if (bestTemporalMatchSoFar != null) {
					logger.debug("For vehicleId={} temporal match is getting " +
							"worse which means that don't need to look at " +
							"additional spatial matches since it would only " +
							"continue to get worse.",
							vehicleState.getVehicleId());
					break;
				}
			}
		} // End of for() loop for looking at each SpatialMatch
		
		logger.debug("For vehicleId={} best temporal match was found to be {}",
				vehicleState.getVehicleId(), bestTemporalMatchSoFar);
		
		
		
		// Return the best temporal match (if there is one)
		return bestTemporalMatchSoFar;
	}
	
	/**
	 * From the list of spatial matches passed in, determines which one has the
	 * best valid temporal match. Intended to be used when first matching a
	 * vehicle to an assignment. Does not use previous AVL report to determine
	 * if there is a match.
	 * 
	 * @param spatialMatches
	 *            The spatial matches to examine
	 * @return The match passed in that best matches temporally where vehicle
	 *         should be. Returns null if no adequate temporal match.
	 */
	public TemporalMatch getBestTemporalMatchComparedToSchedule(
			AvlReport avlReport, List<RouteMatch> spatialMatches) {
		TemporalDifference bestDifferenceFromExpectedTime = null;
		RouteMatch bestSpatialMatch = null;
		
		
		logger.debug("getBestTemporalMatchComparedToSchedule has spatialMatches {}", spatialMatches);
		
	  if (spatialMatches.size() == 1 && spatialMatches.get(0).getTrip().isNoSchedule()) {
	    // again we blindly trust avl assignment if its the only match
      logger.debug("greedily matching to only scheduled spatial assigment for frequency based trip {}", spatialMatches.get(0).getTrip());
      return new TemporalMatch(spatialMatches.get(0), new TemporalDifference(0));
    }
		
		for (int i=0; i<spatialMatches.size(); ++i) {	
			RouteMatch spatialMatch = spatialMatches.get(i);
			
			// If not at wait stop then determine temporal match based on 
			// how long it should take vehicle to travel from the beginning
			// of the trip to the spatial match.
			boolean isFirstSpatialMatch = i==0;
			TemporalDifference differenceFromExpectedTime = 
					determineHowFarOffScheduledTime(avlReport.getVehicleId(), 
							avlReport.getDate(), spatialMatch, isFirstSpatialMatch);			
			
			// If this is the best differenceFromExpectedTime so far then use it.
			if (differenceFromExpectedTime != null && 
					(bestDifferenceFromExpectedTime == null || 
					 differenceFromExpectedTime.betterThan(bestDifferenceFromExpectedTime))) {				
				bestDifferenceFromExpectedTime = differenceFromExpectedTime;
				bestSpatialMatch = spatialMatch;
				logger.debug("For vehicleId={} differenceFromExpectedTime={} " +
						"is best yet so remembering such",
						avlReport.getVehicleId(), differenceFromExpectedTime);
			}
		}
		
		// If found a suitable and best match, return it
		if (bestSpatialMatch != null && bestDifferenceFromExpectedTime != null) {
			TemporalMatch result = 
					new TemporalMatch(bestSpatialMatch, 
							bestDifferenceFromExpectedTime);
			logger.debug("For vehicleId={} getBestTemporalMatch() returning " +
					"temporalMatch={}", 
					avlReport.getVehicleId(), result);
			return result;
		} else {
			logger.debug("For vehicleId={} getBestTemporalMatch() returning " +
					"null because no adequate temporal match was found", 
					avlReport.getVehicleId());
			return null;
		}
	}
	
	/**
	 * Determines if can make it to beginning of trip in time.
	 * 
	 * @param avlReport
	 * @param trip
	 * @return
	 */
	private static boolean canDeadheadToBeginningOfTripInTime(
			AvlReport avlReport, Trip trip) {
		long tripStartTimeMsecs = trip.getStartTime() * 1000;
		long msecsIntoDay = 
				Core.getInstance().getTime().getMsecsIntoDay(avlReport.getDate(), 
						tripStartTimeMsecs);
		// If AVL report is from before the start time of the trip
		// then see if have enough time to travel there.
		if (msecsIntoDay < tripStartTimeMsecs) {
			// How much time available to get to layover
			long availableTimeMsec = tripStartTimeMsecs - msecsIntoDay;
			
			// How far as the crow flies to the layover
			Location tripStartLoc = trip.getStopPath(0).getEndOfPathLocation();
			double distance = avlReport.getLocation().distance(tripStartLoc);
			int crowFliesTimeMsec =
					TravelTimes.travelTimeAsTheCrowFlies(distance);
			boolean canDeadhead = crowFliesTimeMsec < availableTimeMsec;
			trip.getBlock().getTripIndex(trip);
			logger.debug("For vehicleId={} determining if can deadhead to "
					+ "beginning of tripIndex={} tripId={}. msecsIntoDay={} "
					+ "tripStartTimeMsecs={} distance={} availableTimeMsec={} "
					+ "crowFliesTimeMsec={} canDeadhead={}",
					avlReport.getVehicleId(), trip.getIndexInBlock(), 
					trip.getId(),
					Time.timeOfDayStr(msecsIntoDay / 1000),
					Time.timeOfDayStr(tripStartTimeMsecs / 1000),
					Geo.distanceFormat(distance), availableTimeMsec, 
					crowFliesTimeMsec, canDeadhead);
			return canDeadhead;
		}
		
		// Can't make it in time 
		logger.debug("For vehicleId={} tripIndex={} tripId={} is not in "
				+ "future so can't deadhead to it. msecsIntoDay={} "
				+ "tripStartTimeMsecs={}", avlReport.getVehicleId(),
				trip.getIndexInBlock(), trip.getId(),
				Time.timeOfDayStr(msecsIntoDay / 1000),
				Time.timeOfDayStr(tripStartTimeMsecs / 1000));
		return false;
	}
	
	/**
	 * Returns the next trip that the vehicle could get to in time. Returns null
	 * if can't get there.
	 * 
	 * Don't want to look through all trips for a block because then might match
	 * to first block the next day, which would be a problem. Instead need to
	 * only look at trips that are considered to be valid. Therefore should only
	 * pass in upcoming trips as the potentialTrips parameter.
	 * 
	 * @param avlReport
	 * @param potentialTrips
	 *            specifies which trips to examine
	 * @return
	 */
	public Trip matchToLayoverStopEvenIfOffRoute(
			AvlReport avlReport, List<Trip> potentialTrips) {
		// Determine upcoming wait stop
		for (Trip trip : potentialTrips) {
			if (canDeadheadToBeginningOfTripInTime(avlReport, trip))
				return trip;
		}
		
		// Didn't find a wait stop that it could get to in time so return null
		return null;
	}
	
}
