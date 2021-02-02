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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.config.BooleanConfigValue;
import org.transitclock.configData.AvlConfig;
import org.transitclock.configData.CoreConfig;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Extent;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.VectorWithHeading;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

/**
 * For determining possible spatial matches. A spatial match is when the AVL
 * report location is within allowable distance of the path segment and the
 * heading is OK and the distance to the segment is a local minimum (it is a
 * best match).
 * 
 * @author SkiBu Smith
 * 
 */
public class SpatialMatcher {

	// So that know where to start searching from
	private RouteMatch startSearchSpatialMatch = null;
	
	// For keeping track of whether getting closer or further away
	private double previousDistanceToSegment = Double.MAX_VALUE;
	
	private int previousSegmentIndex = -1;

	// For keeping track of potential matches where heading and
	// distance to segment are acceptable.
	private RouteMatch previousPotentialSpatialMatch = null;

	// For keeping track of match with best distance. This is useful for
	// logging in case something goes wrong. It lets one determine if
	// need to make the system more lenient.
	private RouteMatch smallestDistanceSpatialMatch = null;

	// For keeping track of what kind of spatial matching being done
	public enum MatchingType {STANDARD_MATCHING, AUTO_ASSIGNING_MATCHING};
	
	private static final Logger logger = 
			LoggerFactory.getLogger(SpatialMatcher.class);

	private static BooleanConfigValue spatialMatchToLayoversAllowedForAutoAssignment=new BooleanConfigValue("transitclock.core.spatialMatchToLayoversAllowedForAutoAssignment", false, "Allow auto assigner consider spatial matches to layovers. Experimental.");
	/********************** Member Functions **************************/

	/**
	 * Declared private because only the public static members should be
	 * creating a SpatialMatcher. This is because need a new SpatialMatcher
	 * every time doing a match. Only the public static members can enforce this
	 * requirement.
	 */
	private SpatialMatcher() {
	}

	/**
	 * Specifies where to start when searching for spatial matches. This
	 * is important because due to the noise of GPS and also the flexibility
	 * of layovers a best spatial match might be determined to be before the
	 * previous match. But that would be a problem when determining arrivals/
	 * departures and such. Therefore need to only search starting from
	 * previous match.
	 * 
	 * @param startSearchSpatialMatch
	 */
	private void setStartOfSearch(RouteMatch startSearchSpatialMatch) {
		this.startSearchSpatialMatch = startSearchSpatialMatch;
	}

	/**
	 * Goes through entire TripPattern for specified Trip and determines spatial
	 * matches. Matches must be within getMaxAllowableDistanceFromSegment()
	 * except layovers are always included since vehicle are allowed to be away
	 * from the route path during layovers. First checks to see if the avlReport
	 * location is near the extent of the trip pattern. If it is not then can
	 * save processing power and return immediately.
	 * 
	 * @param avlReport
	 * @param trip
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 * @return List of potential SpatialMatches. Can be empty but will not be
	 *         null.
	 */
	private List<RouteMatch> getSpatialMatchesForTrip(AvlReport avlReport,
			Trip trip, MatchingType matchingType) {
		Block block = trip.getBlock();
		
		// The matches to be returned
		List<RouteMatch> spatialMatches = new ArrayList<RouteMatch>();
		
		// Start looking for matches at the beginning of the trip.
		Indices indices = new Indices(block, block.getTripIndex(trip), 
				0, // stopPathIndex
				0); // segmentIndex

		// Loop through stopPaths and segments until reach end of trip and
		// add them to spatialMatches member
		do {
			processPossiblePotentialMatch(avlReport, indices, spatialMatches,
					matchingType);

			// For next iteration through while loop
			indices.increment(avlReport.getTime());
		} while (!indices.atBeginningOfTrip());

		// Need to handle boundary condition. Done looking ahead but
		// the end match might be a potential one even if was continuing
		// to improve the match. Therefore if there was a potential
		// match then should store it.
		if (previousPotentialSpatialMatch != null) {
			// There was a potential match and now things are getting
			// worse so that was a local minimum. Therefore this is
			// one of the spatial matches to be returned.
			spatialMatches.add(previousPotentialSpatialMatch);
		}

		// Return the list of local matches
		return spatialMatches;
	}

	/**
	 * For list of spatial matches passed in returns the first non-layover
	 * one. This is needed because for a trip always get a layover match
	 * in addition to the possible real spatial matches. But the layover
	 * match doesn't need heading to be correct.
	 * 
	 * @param spatialMatchesForTrip
	 * @return
	 */
	private static RouteMatch getFirstNonLayoverSpatialMatch(
			List<RouteMatch> spatialMatchesForTrip) {
		for (RouteMatch match : spatialMatchesForTrip) {
			if (!match.isLayover())
				return match;
		}
		
		return null;
	}
	
	/**
	 * Checks to see if for a non-layover match if can verify that the vehicle
	 * is moving in the proper direction. This is intended to avoid matching
	 * vehicle to wrong trip. Only concerned with non-layover matches because
	 * for layovers heading doesn't matter since vehicle is supposed to be able
	 * to move around on indeterminate path to the layover stop.
	 * <p>
	 * If cannot confirm that the heading of the vehicle matches then should
	 * reject all matches so that won't wrongly match to a layover for another
	 * trip. When get another AVL report for the vehicle will then be able to
	 * see if heading proper using two AVL reports.
	 * 
	 * @param spatialMatch
	 *            The spatial match that should be investigated
	 * @param vehicleState
	 *            For determining previous AVL reports
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 * @return True if for a non-layover match that couldn't verify that vehicle
	 *         heading in proper direction for the match.
	 */
	public static boolean problemMatchDueToLackOfHeadingInfo(
			RouteMatch spatialMatch,
			VehicleState vehicleState, 
			MatchingType matchingType) {
		// If there was no spatial match then there can't be a problem
		// with the heading.
		if (spatialMatch == null)
			return false;
		
		// Convenience variables
		AvlReport avlReport = vehicleState.getAvlReport();
		Trip trip = spatialMatch.getTrip();

		// If a layover stop then heading doesn't matter so there
		// is no problem with the match
		if (spatialMatch.isLayover())
			return false;
		
		// If heading is valid then don't need to check previous AvlReport
		// to see if it is valid so simply return false.
		if (!Float.isNaN(avlReport.getHeading())) {
			return false;
		}
		
		// Heading for the current AVL report is not valid and the match 
		// is not a layover. So check the previous fix.
		// If there was no valid previous AVL report then can't tell if
		// heading in proper direction for the match so return true.
		double minDistance = CoreConfig.
				getDistanceBetweenAvlsForInitialMatchingWithoutHeading();
		AvlReport previousAvlReport = 
				vehicleState.getPreviousAvlReport(minDistance);
		if (previousAvlReport == null) 
			return true;
		
		// Determine matches for the previous AvlReport
		List<RouteMatch> spatialMatchesForPreviousReport =
				(new SpatialMatcher()).getSpatialMatchesForTrip(
						previousAvlReport, trip, matchingType);

		// There can be multiple matches, but only look at first 
		// non-layover ones for the previous report
		RouteMatch previousNonLayoverSpatialMatch =
				getFirstNonLayoverSpatialMatch(
						spatialMatchesForPreviousReport);
		
		// If no previous non-layover spatial matches then can't tell if
		// moving in proper direction so return true. Note: always can get
		// a match to a layover since the distance to the layover doesn't 
		// matter. That is why if only get a previous match to a layover
		// then don't really know if the previous match is valid, and therefore
		// can't tell if heading in proper direction.
		if (previousNonLayoverSpatialMatch == null)
			return true;
		
		
		// If vehicle heading in right direction then return false
		// since there is no problem with the heading for the match
		if (previousNonLayoverSpatialMatch.lessThanOrEqualTo(spatialMatch))
			return false;

		// Couldn't verify that vehicle making forward progress
		// for the spatial matches for the trip so return true.
		return true;
	}
		
	/**
	 * Goes through the Block assignment data and determines the closest spatial
	 * matches. For first matching a vehicle to a block assignment. Matches must
	 * be within getMaxAllowableDistanceFromSegment() except layovers are always
	 * included since vehicle are allowed to be away from the route path during
	 * layovers.
	 * 
	 * @param avlReport
	 *            The AVL report to match to the block
	 * @param block
	 *            The block being investigated
	 * @param tripsToInvestigate
	 *            List of trips that should bother investigating. The calling
	 *            function can determine which trips are currently active and
	 *            pass that list in such that this method doesn't need to look
	 *            through all trips.
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 * @return non-null possibly empty list of spatial matches
	 */
	public static List<RouteMatch> getSpatialMatches(
			AvlReport avlReport,
			Block block, List<Trip> tripsToInvestigate,
			MatchingType matchingType) {
		List<RouteMatch> spatialMatchesForAllTrips = 
				new ArrayList<RouteMatch>();

		// If no trips to investigate then done
		if (tripsToInvestigate == null || tripsToInvestigate.isEmpty())
			return spatialMatchesForAllTrips;

		// So can reuse spatial matches if looking at same trip pattern
		Set<String> tripPatternIdsCovered = new HashSet<String>();

		for (Trip trip : tripsToInvestigate) {
			if (tripPatternIdsCovered.contains(trip.getTripPattern().getId())) {
				// Already found spatial matches for this trip pattern
				// so use them instead of going through the whole trip
				// pattern again.
				boolean foundTripPattern = false;
				// So can determine when to stop copying. Need to stop
				// when starting to look at another trip.
				String tripIdThatFoundTripPatternFor = null;
				List<RouteMatch> matchListForIteration = 
						new ArrayList<RouteMatch>(spatialMatchesForAllTrips);
				for (RouteMatch spatialMatch : matchListForIteration) {
					String spatialMatchTripPatternId = spatialMatch.getTrip()
							.getTripPattern().getId();
					String currentTripPatternId = trip.getTripPattern().getId();
					if (spatialMatchTripPatternId.equals(currentTripPatternId)
							&& (tripIdThatFoundTripPatternFor == null || tripIdThatFoundTripPatternFor
									.equals(spatialMatch.getTrip().getId()))) {
						foundTripPattern = true;
						RouteMatch spatialMatchCopy = new RouteMatch(
								spatialMatch, trip);
						spatialMatchesForAllTrips.add(spatialMatchCopy);
						tripIdThatFoundTripPatternFor = spatialMatch.getTrip()
								.getId();
					} else {
						// If trip pattern or trip ID for spatial matches is now
						// different
						// then have finished copying all the spatial matches
						// for
						// this trip pattern. Therefore done with the for loop.
						if (foundTripPattern)
							break;
					}
				}
			} else {
				// Haven't already examined this trip pattern for spatial
				// matches so do so now.
				List<RouteMatch> spatialMatchesForTrip =
						(new SpatialMatcher()).getSpatialMatchesForTrip(
								avlReport, trip, matchingType);
				
				// Use these spatial matches for the trip
				spatialMatchesForAllTrips.addAll(spatialMatchesForTrip);
				tripPatternIdsCovered.add(trip.getTripPattern().getId());
			}
		}

		// Don't want to match to just before end of block because that could
		// cause a vehicle that has just finished its block to become reassigned
		// again. But only do this if a schedule based assignment because for
		// a no schedule based assignment the vehicle loops around the trip and
		// will frequently be at the end of the trip, which should be considered
		// fine.
		if (!block.isNoSchedule()) {
			Iterator<RouteMatch> iterator =
					spatialMatchesForAllTrips.iterator();
			while (iterator.hasNext()) {
				RouteMatch match = iterator.next();
				if (block.nearEndOfBlock(match, CoreConfig
						.getDistanceFromEndOfBlockForInitialMatching())) {
					// The match is too close to end of block so don't use it
					logger.debug(
							"vehicleId={} match was within {}m of the end "
									+ "of the block so not using that spatial match.",
							avlReport.getVehicleId(),
							CoreConfig
									.getDistanceFromEndOfBlockForInitialMatching(),
							match);
					iterator.remove();
				}
			}
		}
		
		// Return results
		logger.debug("Finished determining spatial matches for vehicleId={} "				
				+ "location={} and blockId={}. The list of spatial "
				+ "matches is {}", avlReport.getVehicleId(),
				avlReport.getLocation(), block.getId(),
				spatialMatchesForAllTrips);

		return spatialMatchesForAllTrips;
	}

	/**
	 * Goes through the Block assignment data and determines the closest spatial
	 * matches that are not for layovers. For first matching a vehicle to a
	 * block assignment. Matches must be within
	 * getMaxAllowableDistanceFromSegment() except layovers are always included
	 * since vehicle are allowed to be away from the route path during layovers.
	 * 
	 * @param avlReport
	 *            The AVL report to match to the block
	 * @param block
	 *            The block to investigate
	 * @param tripsToInvestigate
	 *            List of trips that should bother investigating. The calling
	 *            function can determine which trips are currently active and
	 *            pass that list in such that this method doesn't need to look
	 *            through all trips.
	 * @return non-null possibly empty list of spatial matches
	 */
	public static List<RouteMatch> getSpatialMatchesForAutoAssigning(
			AvlReport avlReport, Block block,
			List<Trip> tripsToInvestigate) {
		// Get all the spatial matches
		List<RouteMatch> allSpatialMatches =
				getSpatialMatches(avlReport, block, tripsToInvestigate,
						MatchingType.AUTO_ASSIGNING_MATCHING);

		// Filter out the ones that are layovers
		List<RouteMatch> spatialMatches = 
				new ArrayList<RouteMatch>();
		for (RouteMatch spatialMatch : allSpatialMatches) {
			if (!spatialMatch.isLayover() || spatialMatchToLayoversAllowedForAutoAssignment.getValue())
				spatialMatches.add(spatialMatch);
		}		

		return spatialMatches;
	}

	/**
	 * Returns the max distance that an AVL report can be from the segment.
	 * Currently uses the max distance for the route if it is set. If max
	 * distance for route is not set then uses the global
	 * CoreConfig.getMaxDistanceFromSegment().
	 * 
	 * @param route
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 * @return max distance that AVL report is allowed to be from segment
	 */
	private double getMaxAllowableDistanceFromSegment(Route route,
			MatchingType matchingType) {
		if (matchingType == MatchingType.AUTO_ASSIGNING_MATCHING) {
			return CoreConfig.getMaxDistanceFromSegmentForAutoAssigning();
		} else {
			// matchingType is STANDARD_MATCHING
			double maxDistance = route.getMaxAllowableDistanceFromSegment();
			if (Double.isNaN(maxDistance))
				maxDistance = CoreConfig.getMaxDistanceFromSegment();
			return maxDistance;
		}
	}
	
	/**
	 * Returns the max distance that an AVL report can be from the segment.
	 * Currently uses the max distance for the route if it is set. If max
	 * distance for route is not set then uses the global
	 * CoreConfig.getMaxDistanceFromSegment().
	 * 
	 * @param indices
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 * @return max distance that AVL report is allowed to be from segment
	 */
	private double getMaxAllowableDistanceFromSegment(Indices indices, 
			MatchingType matchingType) {
		if(indices.getStopPath().getMaxDistance()!=null)
			return indices.getStopPath().getMaxDistance();
		Route route = indices.getRoute();
		return getMaxAllowableDistanceFromSegment(route, matchingType);
	}
	
	/**
	 * Don't want to always be able to match to a layover because that would
	 * cause vehicles to be wrongly matched and vehicles that are actually still
	 * on the route wouldn't be predicted for. Therefore want to only match to a
	 * layover if reasonable.
	 * <p>
	 * Therefore only match to layover if deadheading, if within 150% of the
	 * distance between the last stop of the previous trip and the layover stop,
	 * or within the configured layover distance.
	 * 
	 * @param vehicleId
	 * @param avlLoc
	 * @param potentialMatchIndices
	 * @return
	 */
	private boolean withinAllowableDistanceOfLayover(String vehicleId,
			Location avlLoc, Indices potentialMatchIndices) {
		// If match not at layover then can't be within allowable distance of 
		// layover
		if (!potentialMatchIndices.isLayover())
			return false;
		
		// Need to treat differently depending on whether vehicle is 
		// deadheading or simply going from one trip to another.
		StopPath previousStopPath = potentialMatchIndices.getPreviousStopPath();
		
		// If first layover of block then deadheading. For this case the vehicle
		// is always considered to spatially match the layover.
		if (previousStopPath == null) {
			logger.debug("Layover at {} is within allowable distance because "
					+ "it is first trip, meaning that it is deadheading", 
					potentialMatchIndices);
			return true;
		}
		
		// Determine how far vehicle is from layover
		Location layoverLoc = 
				potentialMatchIndices.getStopPath().getEndOfPathLocation();
		double distanceToLayover = avlLoc.distance(layoverLoc);
		
		// Not first layover so determine how far vehicle needs to travel from 
		// the end of the previous trip to the layover. The allowable distance
		// is 50% greater than this travel distance or the configured layover
		// distance, whichever is greater.
		Location previousStopLoc = previousStopPath.getEndOfPathLocation();
		double distanceBtwnStops = layoverLoc.distance(previousStopLoc);
		double allowableDistance = 
				Math.max(distanceBtwnStops*1.5, CoreConfig.getLayoverDistance());
		
		boolean withinAllowableDistanceOfLayover = distanceToLayover < allowableDistance;
		if (!withinAllowableDistanceOfLayover && logger.isDebugEnabled()) {
			logger.debug("VehicleId={} not within allowable distance of "
					+ "layover. This indicates to the system that the vehicle "
					+ "isn't actually at the layover and therefore won't match "
					+ "to it. distanceToLayover={} distanceBtwnStops={} "
					+ "CoreConfig.getLayoverDistance()={} allowableDistance={} "
					+ "distanceToLayover < allowableDistance={}", 
					vehicleId, Geo.distanceFormat(distanceToLayover), distanceBtwnStops, 
					CoreConfig.getLayoverDistance(), allowableDistance, 
					withinAllowableDistanceOfLayover);
		}
		
		// Return true if within allowable distance to layover
		return withinAllowableDistanceOfLayover;
	}
	
	/**
	 * For determining possible spatial matches. A spatial match is when the AVL
	 * report location is within allowable distance of the path segment and the
	 * heading is OK and the distance to the segment is a local minimum (it is a
	 * best match). Matches must be within getMaxAllowableDistanceFromSegment()
	 * except layovers are always included since vehicle are allowed to be away
	 * from the route path during layovers. To be called for a series of
	 * segments.
	 * <p>
	 * Updates the spatialMatches member with valid matches that are found.
	 * 
	 * @param avlReport
	 *            The new AVL report
	 * @param potentialMatchIndices
	 *            Specifies block/trip/stop path where to look at match
	 * @param spatialMatches The
	 *            list of spatial matches that should add any additional matches
	 *            to
	 * @param matchingType
	 *            for keeping track of what kind of spatial matching being done
	 */
	private void processPossiblePotentialMatch(AvlReport avlReport,
			Indices potentialMatchIndices,
			List<RouteMatch> spatialMatches,
			MatchingType matchingType) {
		// Convenience variables
		VectorWithHeading segmentVector = potentialMatchIndices.getSegment();
		double distanceToSegment = 
				segmentVector.distance(avlReport.getLocation());
		double distanceAlongSegment = 
				segmentVector.matchDistanceAlongVector(avlReport.getLocation());
		boolean atLayover = potentialMatchIndices.isLayover();
	
		// Make sure only searching starting from previous spatial match. 
		// Otherwise would screw up determination of arrivals/departures etc.
		// But only do this for blocks that have a schedule since no-schedule
		// blocks are loops where we don't really have the concept of 
		// before/after for indices.
			
		//&& potentialMatchIndices.getBlock().hasSchedule()
		if (startSearchSpatialMatch != null){
			// If looking at previous index then something is really wrong.
			// Don't need to see if this is a match.
			if (potentialMatchIndices.lessThan(startSearchSpatialMatch.getIndices())&&!startSearchSpatialMatch.getIndices().atEndOfTrip()) {
				logger.error("For vehicleId={} looking at segment that is " +
						"before the segment of the previous match, which " +
						"should not happen. potentialMatchIndices={} " +
						"startSearchSpatialMatch={}",
						avlReport.getVehicleId(), 
						potentialMatchIndices, startSearchSpatialMatch);
				return;
			} else
			// If match is for before the previous one then need to adjust
			// it so that it is the same as the previous one. That way
			// can't end up with a match before the previous one, which
			// would screw up determination of arrivals/departures.
			if (potentialMatchIndices.equals(startSearchSpatialMatch.getIndices())
					&& distanceAlongSegment < 
						startSearchSpatialMatch.getDistanceAlongSegment()) {
				
				// The current match would be before the starting point so
				// adjust it.
				logger.info("For vehicleId={} the spatial match was before " +
						"the starting previous match so will use the previous " +
						"match. original distanceAlongSegment={} and " +
						"startSearchSpatialMatch={}",
						avlReport.getVehicleId(), 
						Geo.distanceFormat(distanceAlongSegment), 
						startSearchSpatialMatch);
				
				distanceAlongSegment = 
						startSearchSpatialMatch.getDistanceAlongSegment();

				previousPotentialSpatialMatch = null;
				
				// return;				
				// TODO CamSys version had this comment and did not set distance to Segment.
				// Do not set distanceToSegment to startSearchSpatialMatch value:
				// - Need to check that it is in bounds
				// - Need accurate comparison with next match's distance.

			}
		}
		
		// If layover then need to set distanceAlongSegment to the length of 
		// the path so that the match is with the actual stop.
		if (atLayover) {
			distanceAlongSegment = potentialMatchIndices.getSegment().length();
		}
		
		// Create the SpatialMatch object for the specified indices
		RouteMatch spatialMatch = new RouteMatch(
				avlReport.getTime(),
				potentialMatchIndices.getBlock(),
				potentialMatchIndices.getTripIndex(),
				potentialMatchIndices.getStopPathIndex(),
				potentialMatchIndices.getSegmentIndex(), 
				distanceToSegment,
				distanceAlongSegment);
		logger.debug("For vehicleId={} examining match to see if it should " +
				"be included in list of spatial matches. {}", 
				avlReport.getVehicleId(), spatialMatch);
		
		// If the match is better than the previous one then it trending 
		// towards a minimum so keep track of it if heading and distance are OK. 
		if (distanceToSegment < previousDistanceToSegment) {
			boolean headingOK = segmentVector.headingOK(avlReport.getHeading(),
					CoreConfig.getMaxHeadingOffsetFromSegment());
			boolean distanceOK =
					distanceToSegment < getMaxAllowableDistanceFromSegment(
							potentialMatchIndices, matchingType);
			
				
			if (headingOK && distanceOK) {
				// Heading and distance OK so store this as a potential match
				previousPotentialSpatialMatch = spatialMatch;
				
				logger.debug("For vehicleId={} distanceToSegment={} is better " +
						"and because heading and distance are " +
						"OK keeping track of this spatial match as a potential " +
						"best spatial match", 
						avlReport.getVehicleId(), 
						Geo.distanceFormat(distanceToSegment));				
			} else {
				// Heading or distance not OK so don't store as potential match.
				// Simply log what is happening.
				logger.debug("For vehicleId={} distanceToSegment={} is better " +
						"than previousDistanceToSegment={} but headingOK={} " +
						"distanceOK={} so not keeping track of this match " +
						"as a potential best spatial match", 
						avlReport.getVehicleId(), 
						Geo.distanceFormat(distanceToSegment), 
						Geo.distanceFormat(previousDistanceToSegment), 
						headingOK, distanceOK);
			}
		} else {
			// This match is not as good as previous one which means that 
			// moving away from a minimum. If have a previous potential
			// match then add it to the list of spatial matches.
			if (previousPotentialSpatialMatch != null) {
				// Match is further away than for previous potential match then
				// have passed by a minimum so store the previous spatial match.
				spatialMatches.add(previousPotentialSpatialMatch);

				logger.debug("For vehicleId={} since there was a previous " +
						"good spatial match and distanceToSegment={} is " +
						"further away than previousDistanceToSegment={}, " +
						"adding the previous spatial match to the list. {}",
						avlReport.getVehicleId(), 
						Geo.distanceFormat(distanceToSegment), 
						Geo.distanceFormat(previousDistanceToSegment), 
						previousPotentialSpatialMatch);

				// Set previousPotentialSpatialMatch to null to indicate 
				// that have already added this match to list
				previousPotentialSpatialMatch = null;
			} else {
				// Moving away from minimum but if there was a valid minimum it
				// was already stored. Therefore simply log what is happening.
				logger.debug("For vehicleId={} distanceToSegment={} is worse " +
						"than previousDistanceToSegment={} but there was no " +
						"previousPotentialSpatialMatch meaning didn't just go " +
						"past a minimum. Therefore no previous match to add " +
						"to list of spatial matches",
						avlReport.getVehicleId(), 
						Geo.distanceFormat(distanceToSegment), 
						Geo.distanceFormat(previousDistanceToSegment));
			}
		}
		
		// Remember the distance to the segment for when checking the
		// next indices for spatial match.
		previousDistanceToSegment = distanceToSegment;
		previousSegmentIndex = potentialMatchIndices.getSegmentIndex();
		
		// A layover is always a spatial match since the vehicle is allowed to
		// be off of the route there. So always add layovers to the list of
		// spatial matches.
		if (atLayover
				&& withinAllowableDistanceOfLayover(avlReport.getVehicleId(),
						avlReport.getLocation(), potentialMatchIndices)) {
			logger.debug("For vehicleId={} segment is at a layover so adding " +
					"it to list of spatial matches. {}",
					avlReport.getVehicleId(), spatialMatch);
			spatialMatches.add(spatialMatch);
		}

		// Keep track of best spatial match even if the distance from vehicle
		// to the match is greater than the allowable distance. This is
		// handy in case there is a problem since can log the best match
		// and see if there is a heading problem or need need make the
		// allowable distance more lenient.
		if (smallestDistanceSpatialMatch == null 
				|| distanceToSegment < 
					smallestDistanceSpatialMatch.getDistanceToSegment()) {
			smallestDistanceSpatialMatch = spatialMatch;
		}
	}
	
	/**
	 * Starts at the previous match and goes from that point forward through the
	 * block assignment looking for the best spatial matches. Intended for when
	 * have a predictable vehicle already matched to an assignment and then get
	 * a new AVL report that needs to be matched.
	 * 
	 * @param vehicleState
	 *            the previous vehicle state
	 * @return list of possible spatial matches. If no spatial matches then
	 *         returns empty list (as opposed to null)
	 */
	public static List<RouteMatch>
			getSpatialMatches(VehicleState vehicleState) {
		// Some convenience variables
		TemporalMatch previousMatch = vehicleState.getMatch();
		SpatialMatcher spatialMatcher = new SpatialMatcher();
		
		// The matches to be returned
		List<RouteMatch> spatialMatches = new ArrayList<RouteMatch>();

		// Don't want to waste time search forward too far. So limit distance
		// such that vehicle would have traveled at 30% more than the max speed 
		// plus a couple hundred meters just to be safe.
		long timeBetweenFixesMsec = vehicleState.getAvlReport().getTime()
				- vehicleState.getPreviousAvlReportFromSuccessfulMatch().getTime();
		double distanceAlongPathToSearch = AvlConfig.getMaxAvlSpeed() * 1.2
				* timeBetweenFixesMsec / Time.MS_PER_SEC + 200.0;
		
		// This allows you override the default max distance used to calculate how far along the route to look for a match. 
		// This is set in an additional field (max_speed)in stop_times.txt. 
		if(previousMatch.getTrip().getStopPath(previousMatch.getIndices().getStopPathIndex()).getMaxSpeed()!=null)
		{			
			timeBetweenFixesMsec = vehicleState.getAvlReport().getTime()
					- vehicleState.getPreviousAvlReportFromSuccessfulMatch().getTime();
			distanceAlongPathToSearch = previousMatch.getTrip().getStopPath(previousMatch.getIndices().getStopPathIndex()).getMaxSpeed()
					* timeBetweenFixesMsec / Time.MS_PER_SEC;			
			logger.info("Using alternate max speed {} for vehicle {} on stop path index {} which results in a distance along segment to search of {}.", previousMatch.getTrip().getStopPath(previousMatch.getIndices().getStopPathIndex()).getMaxSpeed(),vehicleState.getVehicleId(),previousMatch.getIndices().getStopPathIndex(),distanceAlongPathToSearch);
		}

		// Since already traveled some along segment should start
		// distanceSearched
		// with minus that distance so that it will be determined correctly.
		double distanceSearched = -previousMatch.getDistanceAlongSegment();
		
		
		// Start at the previous match and search along the block for best
		// spatial matches. Look ahead until distance spanned would mean
		// that vehicle would have had to travel too fast or that end of
		// block reached.
		Indices indices = new Indices(previousMatch);

		
		spatialMatcher.setStartOfSearch(previousMatch);			
		
		while (!indices.pastEndOfBlock(vehicleState.getAvlReport().getTime()) && 
				(vehicleState.isLayover() || distanceSearched < distanceAlongPathToSearch) 
					&& Math.abs(indices.getStopPathIndex()-previousMatch.getIndices().getStopPathIndex()) <= AvlConfig.getMaxStopPathsAhead()) {

			spatialMatcher.processPossiblePotentialMatch(
					vehicleState.getAvlReport(), indices, spatialMatches,
					MatchingType.STANDARD_MATCHING);

			distanceSearched += indices.getSegment().length();

			// For next iteration through while loop
			indices.increment(vehicleState.getAvlReport().getTime());
		}

		// Need to handle boundary condition. Done looking ahead but
		// the end match might be a potential one even if was continuing
		// to improve the match. Therefore if there was a potential
		// match then should store it.
		if (spatialMatcher.previousPotentialSpatialMatch != null) {
			// There was a potential match and now things are getting
			// worse so that was a local minimum. Therefore this is
			// one of the spatial matches to be returned.
			spatialMatches.add(spatialMatcher.previousPotentialSpatialMatch);
		}

		if (spatialMatches.size() > 0) {
			logger.debug("For vehicleId={} with search started at {} there where {} matches and the match with the best " +
					"distance was {}",
					vehicleState.getVehicleId(),
					previousMatch,
					spatialMatches.size(),
					spatialMatcher.smallestDistanceSpatialMatch);
		} else {
			// There were no spatial matches so log this problem
			if(spatialMatcher!=null && spatialMatcher.
							smallestDistanceSpatialMatch!=null)
			{
				logger.warn("For vehicleId={} found no spatial matches within " +
						"allowable distance of segments wtih search started at {}. Best spatial match " +
						"distance was {} for spatial match {}",
						vehicleState.getVehicleId(),
						previousMatch,
						Geo.distanceFormat(spatialMatcher.
								smallestDistanceSpatialMatch.getDistanceToSegment()),
						spatialMatcher.smallestDistanceSpatialMatch);
			}else
			{
				logger.warn("For vehicleId={} found no spatial matches within " +
						"allowable distance of segments. No best match.",
						vehicleState.getVehicleId());
			}
		}
		
		// Need to look at possibility that could match to end of the block if
		// vehicle is near the end. The reason this is important is because 
		// there is a significant chance that won't get a AVL report right at
		// the last stop for the block. And since it is the end of the block
		// there isn't a layover at the beginning of the next trip since there
		// is no next trip. But don't want to always include the last stop of
		// the block as a potential spatial match because need to avoid wrongly
		// matching to it. So only add the final stop for the block as a 
		// potential spatial match if the previous match was reasonably close to
		// it.
		Block block = previousMatch.getBlock();
		if (!block.isNoSchedule()
				&& previousMatch.isLastTripOfBlock()
				&& previousMatch.withinDistanceOfEndOfTrip(
						CoreConfig.getDistanceFromLastStopForEndMatching())) {
			// Create a match that is at the end of the block
			Trip trip = previousMatch.getTrip();
			int indexOfLastStopPath = trip.getNumberStopPaths()-1;
			StopPath lastStopPath = trip.getStopPath(indexOfLastStopPath);
			int indexOfLastSegment = lastStopPath.getNumberSegments()-1;
			double segmentLength = 
					lastStopPath.getSegmentVector(indexOfLastSegment).length();
			RouteMatch matchAtEndOfBlock = new RouteMatch(
					vehicleState.getAvlReport().getTime(),
					block, 
					previousMatch.getTripIndex(),
					indexOfLastStopPath,
					indexOfLastSegment, 
					Double.NaN, // distanceToSegment set to a non-valid value
					segmentLength);

			// Add that match to list of possible SpatialMatches
			logger.debug("Because vehicleId={} within specified distance " +
					"of end of trip adding the very end of the block as a " +
					"potential spatial match. {}", 
					vehicleState.getVehicleId(), matchAtEndOfBlock);
			spatialMatches.add(matchAtEndOfBlock);
		}
		
		
		// Return the list of local matches
		return spatialMatches;
	}

}
