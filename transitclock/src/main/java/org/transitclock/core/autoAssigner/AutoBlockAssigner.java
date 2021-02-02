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

package org.transitclock.core.autoAssigner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.DoubleConfigValue;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.configData.CoreConfig;
import org.transitclock.core.BlocksInfo;
import org.transitclock.core.RouteMatch;
import org.transitclock.core.SpatialMatcher;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TemporalMatch;
import org.transitclock.core.TemporalMatcher;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleState;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStateManager;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Trip;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.Time;

/**
 * For automatically assigning a vehicle to an available block by determining
 * both spatial and temporal match. When there is a match for an AVL report then
 * a previous AVL report is also investigated to make sure that the vehicle
 * really is moving along a trip.
 * <p>
 * Tries to match to every non-assigned block. Looks only at the trips that are
 * currently active so that doesn't try to look at all possibilities. Caches
 * matches for trip patterns so that doesn't need to do a spatial match to a
 * trip pattern multiple times. Stationary vehicles are not matched because
 * system requires a previous AVL report that is a minimum distance away from
 * the current report. Even with all of this optimization it can take a while to
 * match a vehicle since have to look at every stop path for each available trip
 * pattern. For an agency with ~250 available blocks this can take about 1/2 a
 * second.
 *
 * @author SkiBu Smith
 *
 */
public class AutoBlockAssigner {

	/*********************** members *****************************/
	
	// The vehicle state is repeatedly used so it is a member so it doesn't
	// have to be passed around to various methods.
	private VehicleState vehicleState;
	
	// Contains the results of spatial matching the avl report to the 
	// specified trip pattern. Keyed on trip pattern ID. Note: since the spatial 
	// matches are cached and reused the block member will not be correct
	private Map<String, RouteMatch> spatialMatchCache = 
			new HashMap<String, RouteMatch>();
	
	/****************************** Config params **********************/
	
	private static BooleanConfigValue autoAssignerEnabled =
			new BooleanConfigValue(
					"transitclock.autoBlockAssigner.autoAssignerEnabled", 
					false, 
					"Set to true to enable the auto assignment feature where "
					+ "the system tries to assign vehicle to an available block");
	
	private static BooleanConfigValue ignoreAvlAssignments =
			new BooleanConfigValue(
					"transitclock.autoBlockAssigner.ignoreAvlAssignments", 
					false, 
					"For when want to test automatic assignments. When set to "
					+ "true then system ignores assignments from AVL feed so "
					+ "vehicles need to be automatically assigned instead");
	public static boolean ignoreAvlAssignments() {
		return ignoreAvlAssignments.getValue();
	}
	
	private static DoubleConfigValue minDistanceFromCurrentReport =
			new DoubleConfigValue(
					"transitclock.autoBlockAssigner.minDistanceFromCurrentReport", 
					100.0, 
					"AutoBlockAssigner looks at two AVL reports to match "
					+ "vehicle. This parameter specifies how far away those "
					+ "AVL reports need to be sure that the vehicle really "
					+ "is moving and in service. If getting incorrect matches "
					+ "then this value should likely be increased.");
	
	private static IntegerConfigValue allowableEarlySeconds =
			new IntegerConfigValue(
					"transitclock.autoBlockAssigner.allowableEarlySeconds",
					3*Time.SEC_PER_MIN,
					"How early a vehicle can be in seconds and still be "
					+ "automatically assigned to a block");
	
	private static IntegerConfigValue allowableLateSeconds =
			new IntegerConfigValue(
					"transitclock.autoBlockAssigner.allowableLateSeconds",
					5*Time.SEC_PER_MIN,
					"How late a vehicle can be in seconds and still be "
					+ "automatically assigned to a block");
		
	private static IntegerConfigValue minTimeBetweenAutoAssigningSecs =
			new IntegerConfigValue(
					"transitclock.autoBlockAssigner.minTimeBetweenAutoAssigningSecs", 
					30,
					"Minimum time per vehicle that can do auto assigning. Auto "
					+ "assigning is computationally expensive, especially when "
					+ "there are many blocks. Don't need to do it that "
					+ "frequently. Especially important for agencies with high "
					+ "reporting rates. So this param allows one to limit how "
					+ "frequently auto assigner called for vehicle");
	
	// For keeping track of last time vehicle auto assigned so that can limit 
	// how frequently it is done. Keyed on vehicleId
	private static HashMap<String, Long> timeVehicleLastAutoAssigned =
			new HashMap<String, Long>();
	
	/*********************** Logging **********************************/
	
	private static final Logger logger = LoggerFactory
			.getLogger(AutoBlockAssigner.class);

	/********************** Member Functions **************************/

	/**
	 * Constructor
	 *
	 * @param vehicleState
	 *            Info on the vehicle to match
	 */
	public AutoBlockAssigner(VehicleState vehicleState) {
		this.vehicleState = vehicleState;
	}
	
	/**
	 * @return the current AVL report from vehicleState member
	 */
	private AvlReport getAvlReport() {
		return vehicleState.getAvlReport();
	}

	/**
	 * The previousAvlReport should be a good distance away from the current AVL
	 * report in order to really be sure that vehicle is traveling along the
	 * trip.
	 * 
	 * @return the previous AVL report, at least min distance away from current
	 *         AVL report, from vehicleState member
	 */
	private AvlReport getPreviousAvlReport() {
		double minDistance = minDistanceFromCurrentReport.getValue();
		return vehicleState.getPreviousAvlReport(minDistance);
	}
	
	/**
	 * Determines if a block doesn't have a non-schedule based vehicle
	 * associated with it. This means that the block assignment is available for
	 * trying to automatically assign a vehicle to it. Schedule based vehicles
	 * don't count because even when have a schedule based vehicle still want to
	 * assign a real vehicle to that assignment when possible.
	 * 
	 * @param blockId
	 *            The block assignment to examine
	 * @return True if block is available to be assigned (doesn't have a regular
	 *         vehicle assigned to it.
	 */
	private boolean isBlockUnassigned(String blockId) {
		Collection<String> vehicleIdsForBlock = 
				VehicleDataCache.getInstance().getVehiclesByBlockId(blockId);
		// If no vehicles associated with the block then it is definitely
		// unassigned.
		if (vehicleIdsForBlock.isEmpty())
			return true;

		// There are vehicles assigned to the block but still need to see if
		// they are schedule based vehicles or not
		for (String vehicleId : vehicleIdsForBlock) {
			// If a regular vehicle instead of one for schedule based
			// predictions then the block has a vehicle assigned to it,
			// meaning it is not unassigned
			VehicleState vehiclestate = VehicleStateManager.getInstance()
					.getVehicleState(vehicleId);
			if (!vehiclestate.isForSchedBasedPreds())
				return false;
		}
		
		// Block doesn't have a non-schedule based vehicle so it is unassigned
		return true;
	}

	/**
	 * Determines which blocks are currently active and are not assigned to a
	 * vehicle, meaning that they are available for assignment.
	 * 
	 * @return List of blocks that are available for assignment. Can be empty
	 *         but not null
	 */
	private List<Block> unassignedActiveBlocks() {
		List<Block> currentlyUnassignedBlocks = new ArrayList<Block>();
		List<Block> activeBlocks = BlocksInfo.getCurrentlyActiveBlocks();
		for (Block block : activeBlocks) {
			if (isBlockUnassigned(block.getId())) {
				// No vehicles assigned to this active block so should see
				// if the vehicle currently trying to assign can match to it
				currentlyUnassignedBlocks.add(block);
			}
		}

		return currentlyUnassignedBlocks;
	}
	
	/**
	 * Determines the best match by looking at both the current AVL report and
	 * the previous one. Only for block assignments that do not have a schedule.
	 * 
	 * @param block
	 * @return The best adequate match, or null if there isn't an adequate match
	 */
	private TemporalMatch bestNoScheduleMatch(Block block) {
		if (!block.isNoSchedule()) {
			logger.error("Called bestNoScheduleMatch() on block that has a "
					+ "schedule. {}", block);
			return null;
		}
		
		// Determine all potential spatial matches for the block that are 
		// not layovers. Won't be a layover match anyways since this method
		// is only for use with no schedule assignments.
		AvlReport avlReport = getAvlReport();
		List<Trip> potentialTrips = block.getTripsCurrentlyActive(avlReport);
		List<RouteMatch> spatialMatches = SpatialMatcher
				.getSpatialMatchesForAutoAssigning(getAvlReport(),
						block, potentialTrips);
		if (spatialMatches.isEmpty())
			return null;

		// Determine all possible spatial matches for the previous AVL report so
		// that can make sure that it too matches the assignment.
		AvlReport previousAvlReport = getPreviousAvlReport();
		List<RouteMatch> prevSpatialMatches = SpatialMatcher
				.getSpatialMatchesForAutoAssigning(previousAvlReport,
						block, potentialTrips);
		if (prevSpatialMatches.isEmpty())
			return null;
		
		// Determine params needed in the following for loop
		int timeOfDayInSecs =
				Core.getInstance().getTime()
						.getSecondsIntoDay(previousAvlReport.getTime());
		long avlTimeDifferenceMsec = 
				avlReport.getTime() - previousAvlReport.getTime();
		
		// Go through each spatial match for both the current AVL report and
		// the previous AVL report. Find one where the expected travel time
		// closely matches the time between the AVL reports.
		TemporalDifference bestTemporalDifference = null;
		RouteMatch bestSpatialMatch = null;
		for (RouteMatch prevSpatialMatch : prevSpatialMatches) {
			for (RouteMatch spatialMatch : spatialMatches) {
				// Determine according to the historic travel times how long
				// it was expected to take to travel from the previous match
				// to the current one.
				int expectedTravelTimeMsec =
						TravelTimes.getInstance()
								.expectedTravelTimeBetweenMatches(
										avlReport.getVehicleId(),
										timeOfDayInSecs, prevSpatialMatch,
										spatialMatch);
				TemporalDifference differenceFromExpectedTime =
						new TemporalDifference(expectedTravelTimeMsec
								- avlTimeDifferenceMsec);

				// If the travel time is too far off from the time between the 
				// AVL reports then it is not a good match so continue on to 
				// check out the other spatial matches.
				if (!differenceFromExpectedTime.isWithinBounds(
						allowableEarlySeconds.getValue(),
						allowableLateSeconds.getValue()))
					continue;
				
				// If this match is the best one found temporally then remember it
				if (differenceFromExpectedTime.betterThan(bestTemporalDifference)) {
					bestTemporalDifference = differenceFromExpectedTime;
					bestSpatialMatch = spatialMatch;
				}
			}
		}
		
		// Return the best temporal match if an adequate one was found
		if (bestSpatialMatch == null)
			return null;
		else {
			TemporalMatch bestTemporalMatch =
					new TemporalMatch(bestSpatialMatch, bestTemporalDifference);
			return bestTemporalMatch;
		}
	}
	
	/**
	 * Gets the spatial matches of the AVL report for the specified block. Only
	 * looks at trips that are currently active in order to speed things up.
	 * Checking each active trip is still far too costly. Therefore uses a cache
	 * of spatial matches by trip pattern ID. If a spatial match was already
	 * determine for the trip pattern then the cached value is returned.
	 * 
	 * @param avlReport
	 *            The AVL report to be matched
	 * @param block
	 *            The block to match the AVL report to
	 * 
	 * @return All possible spatial matches
	 */
	private List<RouteMatch> getSpatialMatches(AvlReport avlReport,
			Block block) {
		// Convenience variable
		String vehicleId = avlReport.getVehicleId();
		
		// For returning results of this method
		List<RouteMatch> spatialMatches = new ArrayList<RouteMatch>();
		
		// Determine which trips are currently active so that don't bother 
		// looking at all trips
		List<Trip> activeTrips = block.getTripsCurrentlyActive(avlReport);
		
		// Determine trips that need to look at for spatial matches because 
		// haven't looked at the associated trip pattern yet.
		List<Trip> tripsNeedToInvestigate = new ArrayList<Trip>();
		
		// Go through the activeTrips and determine which ones actually need
		// to be investigated. If the associated trip pattern was already 
		// examined then use the spatial match (or null) previous found
		// and cached. If it is a new trip pattern then add the trip to the
		// list of trips that need to be investigated.
		for (Trip trip : activeTrips) {
			String tripPatternId = trip.getTripPattern().getId();
			
			logger.debug("For vehicleId={} checking tripId={} with "
					+ "tripPatternId={} for spatial "
					+ "matches.", vehicleId, trip.getId(), 
					trip.getTripPattern().getId());
			
			// If spatial match results already in cache...
			if (spatialMatchCache.containsKey(tripPatternId)) {
				// Already processed this trip pattern so use cached results. 
				// Can be null
				RouteMatch previouslyFoundMatch =
						spatialMatchCache.get(tripPatternId);

				// If there actually was a successful spatial match to the 
				// trip pattern in the cache then add it to spatialMatches list
				if (previouslyFoundMatch != null) {
					// The cached match has the wrong trip info so need  
					// to create an equivalent match with the proper trip block 
					// info
					RouteMatch matchWithProperBlock =
							new RouteMatch(previouslyFoundMatch, trip);
					
					// Add to list of spatial matches to return
					spatialMatches.add(matchWithProperBlock);

					logger.debug("For vehicleId={} for tripId={} with "
							+ "tripPatternId={} using previously cached "
							+ "spatial match.", 
							vehicleId, trip.getId(), tripPatternId);
				} else {
					logger.debug("For vehicleId={} for tripId={} with "
							+ "tripPatternId={} found from cache that there "
							+ "is no spatial match.", 
							vehicleId, trip.getId(), tripPatternId);
				}
			} else {
				// New trip pattern so need to investigate it to search for 
				// potential spatial matches
				tripsNeedToInvestigate.add(trip);
				
				logger.debug("For vehicleId={} for tripId={} with "
						+ "tripPatternId={} have not previously determined "
						+ "spatial matches so will do so now.",
						vehicleId, trip.getId(), tripPatternId);
			}
		}

		// Investigate the trip patterns not in the cache. Determine potential 
		// spatial matches that are not layovers. If match is to a layover can 
		// ignore it since layover matches are far too flexible to really be 
		// considered a spatial match
		List<RouteMatch> newSpatialMatches = SpatialMatcher
				.getSpatialMatchesForAutoAssigning(avlReport,
						block, tripsNeedToInvestigate);
		
		// Add newly discovered matches to the cache and to the list of spatial
		// matches to be returned
		for (RouteMatch newSpatialMatch : newSpatialMatches) {
			logger.debug("For vehicleId={} for tripId={} with "
						+ "tripPatternId={} found new spatial match {}.",
						vehicleId, newSpatialMatch.getTrip().getId(), 
						newSpatialMatch.getTrip().getTripPattern().getId(),
						newSpatialMatch);
			
			// Cache it
			spatialMatchCache.put(newSpatialMatch.getTrip().getTripPattern()
					.getId(), newSpatialMatch);
			
			// Add to list of spatial matches to return
			spatialMatches.add(newSpatialMatch);
		}
		
		// Also need to add to the cache the trips patterns that investigated 
		// but did not find a spatial match. This is really important because
		// when don't find a spatial match for a trip pattern don't want to 
		// waste time searching it again to find out again that it doesn't 
		// have a match.
		for (Trip tripInvestigated : tripsNeedToInvestigate) {
			// If the trip that was investigated did not result in spatial
			// match then remember that by storing a null spatial match
			// for the trip pattern
			String tripPatternId = tripInvestigated.getTripPattern().getId();
			boolean spatialMatchFound = false;
			for (RouteMatch newSpatialMatch : newSpatialMatches) {
				String spatialMatchTripPatternId =
						newSpatialMatch.getTrip().getTripPattern().getId();
				if (spatialMatchTripPatternId.equals(tripPatternId)) {
					spatialMatchFound = true;
				}
			}
			// If no spatial match found for the trip pattern that just
			// investigated then mark in cache that no match
			if (!spatialMatchFound) {
				spatialMatchCache.put(tripPatternId, null);
				
				logger.debug("For vehicleId={} for tripId={} with "
						+ "tripPatternId={} no spatial match found so storing "
						+ "that info in cache for investigating next block.",
						vehicleId, tripInvestigated.getId(), 
						tripInvestigated.getTripPattern().getId());
			}
		}
		
		// Return the results
		return spatialMatches;
	}

	/**
	 * Gets the spatial matches of the AVL report for the specified block. Only
	 * looks at trips that are currently active in order to speed things up.
	 * Doesn't use cached value from when investigating the current AVL report.
	 * Therefore this method is useful for checking previous AVL reports.
	 * 
	 * @param avlReport
	 *            The AVL report to be matched
	 * @param block
	 *            The block to match the AVL report to
	 * 
	 * @return list of spatial matches for the avlReport
	 */
	private List<RouteMatch> getSpatialMatchesWithoutCache(
			AvlReport avlReport, Block block) {
		// Determine which trips are currently active so that don't bother 
		// looking at all trips
		List<Trip> activeTrips = block.getTripsCurrentlyActive(avlReport);

		// Get and return the spatial matches
		List<RouteMatch> spatialMatches = SpatialMatcher
				.getSpatialMatchesForAutoAssigning(avlReport,
						block, activeTrips);
		return spatialMatches;
	}
	
	/**
	 * Determines best non-layover match for the AVL report to the specified
	 * block. First finds spatial matches and then finds one that best matches
	 * to the schedule. If the match is adequate spatial and temporally then the
	 * match is returned. Intended for schedule based blocks only.
	 * 
	 * @param avlReport
	 *            The AVL report to be matched
	 * @param block
	 *            The block to match the AVL report to
	 * @param useCache
	 *            true if can use match cache. The match cache is useful for
	 *            when matching the current AVL report because it is more
	 *            efficient. But for matching the previous AVL report don't want
	 *            to use the cache because the cache was for the original AVL
	 *            report.
	 * @return The best match if there is one. Null if there is not a valid
	 *         match
	 */
	private TemporalMatch bestTemporalMatch(AvlReport avlReport, Block block,
			boolean useCache) {
		// Determine all potential spatial matches for the block
		List<RouteMatch> spatialMatches = useCache ? 
			getSpatialMatches(avlReport, block) : 
				getSpatialMatchesWithoutCache(avlReport, block);


		// Now that have the spatial matches determine the best temporal match
		TemporalMatch bestMatch = TemporalMatcher.getInstance()
				.getBestTemporalMatchComparedToSchedule(avlReport,
						spatialMatches);

		// Want to be pretty restrictive about matching to avoid false 
		// positives. At the same time, want to not have a temporal match
		// that matches but not all that well cause the auto matcher to
		// think that can't match the vehicle due to the matches being
		// ambiguous. Therefore want to be more restrictive temporally 
		// with respect to the temporal matches used. So throw out the
		// temporal match unless it is pretty close to the scheduled time.
		if (bestMatch != null) {
			TemporalDifference diff = bestMatch.getTemporalDifference();
			boolean isWithinBounds = diff.isWithinBounds(
					allowableEarlySeconds.getValue(),
					allowableLateSeconds.getValue());
			if (!isWithinBounds) {
				// Vehicle is too early or too late to auto match the block so
				// return null.
				logger.info("When trying to automatically assign vehicleId={} "
						+ "to blockId={} got a temporal match but the time "
						+ "difference {} was not within allowed bounds of "
						+ "allowableEarlySeconds={} and "
						+ "allowableLateSeconds={}. {}",
						avlReport.getVehicleId(), block.getId(), diff, 
						allowableEarlySeconds.getValue(),
						allowableLateSeconds.getValue(), bestMatch);
				return null;
			}
		}
		
		// Return the temporal match, which could be null
		return bestMatch;	
	}

	/**
	 * Returns best schedule based match. Only for block assignments that have a
	 * schedule (are not frequency based).
	 * 
	 * @param block
	 *            The block to try to match to
	 * @return Best TemporalMatch to the block assignment, or null if no
	 *         adequate match
	 */
	private TemporalMatch bestScheduleMatch(Block block) {
		IntervalTimer timer = new IntervalTimer();
		AvlReport avlReport = getAvlReport();
		String vehicleId = avlReport.getVehicleId();
		String blockId = block.getId();
		
		// Make sure this method is called appropriately
		if (block.isNoSchedule()) {
			logger.error("Called bestScheduleMatch() on block that does not "
					+ "have a schedule. {}", block.toShortString());
			return null;
		}		

		// Determine best temporal match if there is one. Use cache to speed
		// up processing.
		TemporalMatch bestMatch = bestTemporalMatch(avlReport, block, true);

		logger.debug("For vehicleId={} and blockId={} calling "
				+ "bestTemporalMatch() took {}msec", 
				vehicleId, blockId, timer);

		// If did not find an adequate temporal match then done
		if (bestMatch == null)
			return null;			
		
		// Found a valid temporal match for the AVL report to the block
		logger.debug("Found valid match for vehicleId={} and blockId={} "
				+ "and AVL report={} . Therefore will see if previous AVL "
				+ "report also matches. The bestMatch={}",
				vehicleId, blockId, avlReport, bestMatch);
		
		// Make sure that previous AVL report also matches and 
		// that it matches to block before the current AVL report.
		// Don't use cache since cache contains matches using the
		// current AVL report whereas here we are interested in the
		// previous AVL report.
		AvlReport previousAvlReport = getPreviousAvlReport();
		TemporalMatch previousAvlReportBestMatch = 
				bestTemporalMatch(previousAvlReport, block, false);
		
		logger.debug("For vehicleId={} and blockId={} calling "
				+ "bestTemporalMatch() for previous AVL report took {}msec", 
				vehicleId, blockId, timer);

		if (previousAvlReportBestMatch != null
				&& previousAvlReportBestMatch.lessThanOrEqualTo(bestMatch)) { 
			// Previous AVL report also matches appropriately. 
			// Therefore return this temporal match as appropriate one.
			logger.debug("For vehicleId={} also found appropriate "
					+ "match for previous AVL report {}. Previous "
					+ "match was {}", 
					avlReport.getVehicleId(), previousAvlReport, 
					previousAvlReportBestMatch);	
			return bestMatch;
		} 

		// The previous AVL report did not match the block
		logger.debug("For vehicleId={} did NOT get valid match for "
				+ "previous AVL report {}. Previous match was {} ",
				avlReport.getVehicleId(), previousAvlReport,
				previousAvlReportBestMatch);
		return null;
	}
	
	/**
	 * Goes through all the currently active blocks and tries to match the AVL
	 * report to them. Returns list of valid temporal matches. Ignores layover
	 * matches since they are too lenient to indicate a valid match. Also
	 * requires a previous AVL report to match appropriately to make sure that
	 * vehicle really matches and isn't just sitting there and isn't going in
	 * other direction or crossing route and matching only momentarily.
	 * 
	 * @return A non-null list of TemporalMatches. Will be empty if there are no
	 *         valid matches.
	 */
	private List<TemporalMatch> determineTemporalMatches() {
		// Convenience variable for logging
		String vehicleId = vehicleState.getVehicleId();
		
		// The list of matches to return
		List<TemporalMatch> validMatches = new ArrayList<TemporalMatch>();
		
		// Only want to try to auto assign if there is also a previous AVL 
		// report that is significantly away from the current report. This
		// way we avoid trying to match non-moving vehicles which are
		// not in service.
		if (getPreviousAvlReport() == null) {
			// There was no previous AVL report far enough away from the 
			// current one so return empty list of matches
			logger.info("In AutoBlockAssigner.bestMatch() cannot auto "
					+ "assign vehicle because could not find valid previous "
					+ "AVL report in history for vehicleId={} further away "
					+ "than {}m from current AVL report {}",
					vehicleId, minDistanceFromCurrentReport.getValue(),	
					getAvlReport());
			return validMatches;
		}

		// So can see how long the search takes
		IntervalTimer timer = new IntervalTimer();		

		// Determine which blocks to examine. If agency configured such that
		// blocks are to be exclusive then only look at the ones currently
		// not used. But if not to be exclusive, such as for no schedule based
		// routes, then look at all active blocks.
		List<Block> blocksToExamine = CoreConfig.exclusiveBlockAssignments() ? 
				unassignedActiveBlocks() : BlocksInfo.getCurrentlyActiveBlocks();
		
		if (blocksToExamine.isEmpty()) {
			logger.info("No currently active blocks to assign vehicleId={} to.",
					vehicleId);
		} else {
			logger.info("For vehicleId={} examining {} blocks for matches.", 
					vehicleId, blocksToExamine.size());
		}
		
		// For each active block that is currently unassigned...
		for (Block block : blocksToExamine) {
			IntervalTimer blockTimer = new IntervalTimer();
			
			if (logger.isDebugEnabled()) {
				// Note, when auto assignment first done for this block this
				// debug statement will take a while to execute because block
				// info read from db. But that is OK since it is going to happen
				// at some point anyways.
				logger.debug("For vehicleId={} examining blockId={} for match. "
						+ "The block contains the routes {}. {}", 
						vehicleId, block.getId(), block.getRouteIds(), 
						block.toShortString());
			}

			// Determine best match for the block depending on whether the 
			// block is schedule based or not
			TemporalMatch bestMatch = block.isNoSchedule() ? 
					bestNoScheduleMatch(block) :
					bestScheduleMatch(block);					
			if (bestMatch != null)
				validMatches.add(bestMatch);
			
			logger.debug("For vehicleId={} checking blockId={} took {}msec",
					vehicleId, block.getId(), blockTimer);
		}

		// Return the valid matches that were found
		logger.info("Total time for determining possible auto assignment "
				+ "temporal matches for vehicleId={} was {}msec", 
				vehicleId, timer);
		return validMatches;
	}
	
	/**
	 * Determines if the auto assigner is being called too recently, as specified by
	 * the transitclock.autoBlockAssigner.minTimeBetweenAutoAssigningSecs property. This
	 * is important because auto assigning is quite costly for agencies with many available
	 * blocks. If have high reporting rate and many available blocks then the system can get bogged
	 * down just doing auto assigning.
	 * 
	 * @param vehicleState
	 * @return true if was too recently called for the vehicle
	 */
	private static boolean tooRecent(VehicleState vehicleState) {
		// Convenience variables
		String vehicleId = vehicleState.getVehicleId();
		long gpsTime = vehicleState.getAvlReport().getTime();
		
		// Determine last time vehicle was auto assigned
		Long lastTime = timeVehicleLastAutoAssigned.get(vehicleId);

		// If first time dealing with the vehicle then it is not too recent
		if (lastTime == null) {
			// Store the time for the vehicle for next time this method is called
			timeVehicleLastAutoAssigned.put(vehicleId, gpsTime);
			return false;
		}
		
		// Return true if not enough time elapsed
		long elapsedSecs = (gpsTime - lastTime) / Time.MS_PER_SEC;
		boolean tooRecent =
				elapsedSecs < minTimeBetweenAutoAssigningSecs.getValue();

		logger.debug("For vehicleId={} tooRecent={} elapsedSecs={} lastTime={} "
				+ "gpsTime={} minTimeBetweenAutoAssigningSecs={} ", 
				vehicleId, tooRecent, elapsedSecs, Time.timeStrMsec(lastTime), 
				Time.timeStrMsec(gpsTime), 
				minTimeBetweenAutoAssigningSecs.getValue());

		if (tooRecent) {
			logger.info("For vehicleId={} too recent to previous time that "
					+ "tried to autoassign. Therefore not autoassigning."
					+ "ElapsedSecs={} lastTime={} gpsTime={} "
					+ "minTimeBetweenAutoAssigningSecs={} ", 
					vehicleId, elapsedSecs, Time.timeStrMsec(lastTime), 
					Time.timeStrMsec(gpsTime), 
					minTimeBetweenAutoAssigningSecs.getValue());
			return true;
		} else {
			// Not too recent so should auto assign. Therefore store the time 
			// for the vehicle for next time this method is called
			timeVehicleLastAutoAssigned.put(vehicleId, gpsTime);
			return false;
		}
			
	}
	
	/**
	 * Returns true if the AutoBlockAssigner is actually enabled.
	 * 
	 * @return true if enabled
	 */
	public static boolean enabled() {
		return autoAssignerEnabled.getValue();
	}
	
	/**
	 * For trying to match vehicle to a active but currently unused block and
	 * the auto assigner is enabled. If auto assigner is not enabled then
	 * returns null. Goes through all the currently active blocks and tries to
	 * match the AVL report to them. Returns a TemporalMatch if there is a
	 * single block that can be successfully matched to. Ignores layover matches
	 * since they are too lenient to indicate a valid match. Also requires a
	 * previous AVL report to match appropriately to make sure that vehicle
	 * really matches and isn't just sitting there and isn't going in other
	 * direction or crossing route and matching only momentarily.
	 * 
	 * @return A TemporalMatch if there is a single valid one, otherwise null
	 */
	public TemporalMatch autoAssignVehicleToBlockIfEnabled() {
		// If the auto assigner is not enabled then simply return null for 
		// the match
		if (!autoAssignerEnabled.getValue())
			return null;
		
		// If auto assigner called too recently for vehicle then return
		if (tooRecent(vehicleState))
			return null;
		
		String vehicleId = vehicleState.getVehicleId();
		logger.info("Determining possible auto assignment match for {}",
				vehicleState.getAvlReport());
		
		// Determine all the valid matches
		List<TemporalMatch> matches = determineTemporalMatches();
		
		// If no matches then not successful
		if (matches.isEmpty()) {
			logger.info("Found no valid matches for vehicleId={}", vehicleId);
			return null;
		}
		
		// If more than a single match then situation is ambiguous and we can't
		// consider that a match 
		if (matches.size() > 1) {
			logger.info("Found multiple matches ({}) for vehicleId={}. "
					+ "Therefore could not auto assign vehicle. {}", 
					matches.size(), vehicleId, matches);
			return null;
		}
		
		// Found a single match so return it
		logger.info("Found single valid match for vehicleId={}. {}", 
				vehicleId, matches.get(0));
		return matches.get(0);
	}
}
