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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.DoubleConfigValue;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.configData.AgencyConfig;
import org.transitclock.configData.AvlConfig;
import org.transitclock.configData.CoreConfig;
import org.transitclock.core.SpatialMatcher.MatchingType;
import org.transitclock.core.autoAssigner.AutoBlockAssigner;
import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStateManager;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.Stop;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.VectorWithHeading;
import org.transitclock.db.structs.VehicleEvent;
import org.transitclock.db.structs.AvlReport.AssignmentType;
import org.transitclock.logging.Markers;
import org.transitclock.monitoring.CloudwatchService;
import org.transitclock.utils.Geo;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.StringUtils;
import org.transitclock.utils.Time;

/**
 * This is a very important high-level class. It takes the AVL data and
 * processes it. Matches vehicles to their assignments. Once a match is made
 * then MatchProcessor class is used to generate predictions, arrival/departure
 * times, headway, etc.
 * 
 * @author SkiBu Smith
 * 
 */
public class AvlProcessor {

	// For keeping track of how long since received an AVL report so
	// can determine if AVL feed is up.
	private AvlReport lastRegularReportProcessed;

	// Singleton class
	private static AvlProcessor singleton = new AvlProcessor();

	/*********** Configurable Parameters for this module ***********/

	private static double getTerminalDistanceForRouteMatching() {
		return terminalDistanceForRouteMatching.getValue();
	}

	private static DoubleConfigValue terminalDistanceForRouteMatching = 
			new DoubleConfigValue(
			"transitclock.core.terminalDistanceForRouteMatching", 
			100.0,
			"How far vehicle must be away from the terminal before doing "
			+ "initial matching. This is important because when vehicle is at "
			+ "terminal don't know which trip it it should be matched to until "
			+ "vehicle has left the terminal.");

	private static IntegerConfigValue allowableBadAssignments = 
			new IntegerConfigValue(
			"transitclock.core.allowableBadAssignments", 0,
			"If get a bad assignment, such as no assignment, but no "
					+ "more than allowableBadAssignments then will use the "
					+ "previous assignment. Useful for when assignment part "
					+ "of AVL feed doesn't always provide a valid assignment.");

	private static BooleanConfigValue emailMessagesWhenAssignmentGrabImproper =
			new BooleanConfigValue(
					"transitclock.core.emailMessagesWhenAssignmentGrabImproper", 
					false, 
					"When one vehicle gets assigned by AVL feed but another "
					+ "vehicle already has that assignment then sometimes the "
					+ "assignment to the new vehicle would be incorrect. Could "
					+ "be that vehicle was never logged out or simply got bad "
					+ "assignment. For this situation it can be useful to "
					+ "receive error message via e-mail. But can get too many "
					+ "such e-mails. This property allows one to control those "
					+ "e-mails.");
	
	private static DoubleConfigValue maxDistanceForAssignmentGrab =
			new DoubleConfigValue(
					"transitclock.core.maxDistanceForAssignmentGrab",
					10000.0,
					"For when another vehicles gets assignment and needs to "
					+ "grab it from another vehicle. The new vehicle must "
					+ "match to route within maxDistanceForAssignmentGrab in "
					+ "order to grab the assignment.");

	 private static DoubleConfigValue maxMatchDistanceFromAVLRecord =
	      new DoubleConfigValue(
	          "transitclock.core.maxMatchDistanceFromAVLRecord",
	          500.0,
	          "For logging distance between spatial match and actual AVL assignment ");
	 
	 private static BooleanConfigValue ignoreInactiveBlocks =
			 new BooleanConfigValue(
					 "transitclock.core.ignoreInactiveBlocks",
					 true,
					 "If the block isn't active at this time then ignore it. This way " 
					 + "don't look at each trip to see if it is active which is important " 
					 + "because looking at each trip means all the trip data including "
					 + "travel times needs to be lazy loaded, which can be slow.");

	
  private double getMaxMatchDistanceFromAVLRecord() {
    return maxMatchDistanceFromAVLRecord.getValue();
  }


	
	/************************** Logging *******************************/

	private static final Logger logger = LoggerFactory
			.getLogger(AvlProcessor.class);

	/********************** Member Functions **************************/

	/*
	 * Singleton class so shouldn't use constructor so declared private
	 */
	private AvlProcessor() {
	}

	/**
	 * Returns the singleton AvlProcessor
	 * 
	 * @return
	 */
	public static AvlProcessor getInstance() {
		return singleton;
	}

	/**
	 * Removes predictions and the match for the vehicle and marks it as
	 * unpredictable. Updates VehicleDataCache. Creates and logs a VehicleEvent
	 * explaining the situation.
	 * 
	 * @param vehicleId
	 *            The vehicle to be made unpredictable
	 * @param eventDescription
	 *            A longer description of why vehicle being made unpredictable
	 * @param vehicleEvent
	 *            A short description from VehicleEvent class for labeling the
	 *            event.
	 */
	public void makeVehicleUnpredictable(String vehicleId,
			String eventDescription, String vehicleEvent) {
		logger.info("Making vehicleId={} unpredictable. {}", vehicleId,
				eventDescription);

		VehicleState vehicleState = VehicleStateManager.getInstance()
				.getVehicleState(vehicleId);

		// Create a VehicleEvent to record what happened
		AvlReport avlReport = vehicleState.getAvlReport();
		TemporalMatch lastMatch = vehicleState.getMatch();
		boolean wasPredictable = vehicleState.isPredictable();
		VehicleEvent.create(avlReport, lastMatch, vehicleEvent,
				eventDescription, false, // predictable
				wasPredictable, // becameUnpredictable
				null); // supervisor

		// Update the state of the vehicle
		vehicleState.setMatch(null);

		// Remove the predictions that were generated by the vehicle
		PredictionDataCache.getInstance().removePredictions(vehicleState);

		// Update VehicleDataCache with the new state for the vehicle
		VehicleDataCache.getInstance().updateVehicle(vehicleState);
	}

	/**
	 * Removes predictions and the match for the vehicle and marks is as
	 * unpredictable. Also removes block assignment from the vehicleState. To be
	 * used for situations such as assignment ended or vehicle was reassigned.
	 * Creates and logs a VehicleEvent explaining the situation.
	 * 
	 * @param vehicleState
	 *            The vehicle to be made unpredictable
	 * @param eventDescription
	 *            A longer description of why vehicle being made unpredictable
	 * @param vehicleEvent
	 *            A short description from VehicleEvent class for labeling the
	 *            event.
	 */
	public void makeVehicleUnpredictableAndTerminateAssignment(
			VehicleState vehicleState, String eventDescription,
			String vehicleEvent) {
		makeVehicleUnpredictable(vehicleState.getVehicleId(), eventDescription,
				vehicleEvent);

		vehicleState.unsetBlock(BlockAssignmentMethod.ASSIGNMENT_TERMINATED);
	}

	/**
	 * Marks the vehicle as not being predictable and that the assignment has
	 * been grabbed. Updates VehicleDataCache. Creates and logs a VehicleEvent
	 * explaining the situation.
	 * 
	 * @param vehicleState
	 *            The vehicle to be made unpredictable
	 * @param eventDescription
	 *            A longer description of why vehicle being made unpredictable
	 * @param vehicleEvent
	 *            A short description from VehicleEvent class for labeling the
	 *            event.
	 */
	public void makeVehicleUnpredictableAndGrabAssignment(
			VehicleState vehicleState, String eventDescription,
			String vehicleEvent) {
		makeVehicleUnpredictable(vehicleState.getVehicleId(), eventDescription,
				vehicleEvent);

		vehicleState.unsetBlock(BlockAssignmentMethod.ASSIGNMENT_GRABBED);
	}

	/**
	 * Removes the vehicle from the VehicleDataCache.
	 * 
	 * @param vehicleId
	 *            The vehicle to remove
	 */
	public void removeFromVehicleDataCache(String vehicleId) {
		VehicleDataCache.getInstance().removeVehicle(vehicleId);
	}
	
	/**
	 * Looks at the previous AVL reports to determine if vehicle is actually
	 * moving. If it is not moving then the vehicle is made unpredictable. Uses
	 * the system properties transitclock.core.timeForDeterminingNoProgress and
	 * transitclock.core.minDistanceForNoProgress
	 * 
	 * @param bestTemporalMatch
	 * @param vehicleState
	 * @return True if vehicle not making progress, otherwise false. If vehicle
	 *         doesn't currently match or if there is not enough history for the
	 *         vehicle then false is returned.
	 */
	private boolean handleIfVehicleNotMakingProgress(
			TemporalMatch bestTemporalMatch, VehicleState vehicleState) {
		// If there is no current match anyways then don't need to do anything
		// here.
		if (bestTemporalMatch == null)
			return false;

		// If this feature disabled then return false 
		int noProgressMsec = CoreConfig.getTimeForDeterminingNoProgress();
		if (noProgressMsec <= 0)
			return false;
		
		// If no previous match then cannot determine if not making progress
		TemporalMatch previousMatch = vehicleState.getPreviousMatch(noProgressMsec);
		if (previousMatch == null)
			return false;

		// Determine distance traveled between the matches
		double distanceTraveled = previousMatch
				.distanceBetweenMatches(bestTemporalMatch);

		double minDistance = CoreConfig.getMinDistanceForNoProgress();
		if (distanceTraveled < minDistance) {
			// Determine if went through any wait stops since if did then
			// vehicle wasn't stuck in traffic. It was simply stopped at
			// layover.
			boolean traversedWaitStop = previousMatch
					.traversedWaitStop(bestTemporalMatch);
			if (!traversedWaitStop) {
				// Determine how much time elapsed between AVL reports
				long timeBetweenAvlReports =
						vehicleState.getAvlReport().getTime()
								- previousMatch.getAvlTime();

				// Create message indicating why vehicle being made
				// unpredictable because vehicle not making forward progress.
				String eventDescription = "Vehicle only traveled "
						+ StringUtils.distanceFormat(distanceTraveled)
						+ " over the last "
						+ Time.elapsedTimeStr(timeBetweenAvlReports)
						+ " which is below minDistanceForNoProgress of "
						+ StringUtils.distanceFormat(minDistance)
						+ " so was made unpredictable.";

				// Make vehicle unpredictable and do associated logging
				AvlProcessor.getInstance().makeVehicleUnpredictable(
						vehicleState.getVehicleId(), eventDescription,
						VehicleEvent.NO_PROGRESS);

				// Return that vehicle indeed not making progress
				return true;
			}
		}

		// Vehicle was making progress so return such
		return false;
	}

	/**
	 * Looks at the previous AVL reports to determine if vehicle is actually
	 * moving. If it is not moving then the vehicle should be marked as being
	 * delayed. Uses the system properties
	 * transitclock.core.timeForDeterminingDelayed and
	 * transitclock.core.minDistanceForDelayed
	 * 
	 * @param vehicleState
	 *            For providing the temporal match and the AVL history. It is
	 *            expected that the new match has already been set.
	 * @return True if vehicle not making progress, otherwise false. If vehicle
	 *         doesn't currently match or if there is not enough history for the
	 *         vehicle then false is returned.
	 */
	private boolean handlePossibleVehicleDelay(VehicleState vehicleState) {
		// Assume vehicle is not delayed
		boolean wasDelayed = vehicleState.isDelayed();
		vehicleState.setIsDelayed(false);
		
		// Determine the new match
		TemporalMatch currentMatch = vehicleState.getMatch();
		
		// If there is no current match anyways then don't need to do anything
		// here.
		if (currentMatch == null)
			return false;

		// If this feature disabled then return false 
		int maxDelayedSecs = CoreConfig.getTimeForDeterminingDelayedSecs();
		if (maxDelayedSecs <= 0)
			return false;
		
		// If no previous match then cannot determine if not making progress
		TemporalMatch previousMatch =
				vehicleState.getPreviousMatch(maxDelayedSecs * Time.MS_PER_SEC);
		if (previousMatch == null)
			return false;

		// Determine distance traveled between the matches
		double distanceTraveled = previousMatch
				.distanceBetweenMatches(currentMatch);

		double minDistance = CoreConfig.getMinDistanceForDelayed();
		if (distanceTraveled < minDistance) {
			// Determine if went through any wait stops since if did then
			// vehicle wasn't stuck in traffic. It was simply stopped at
			// layover.
			boolean traversedWaitStop = previousMatch
					.traversedWaitStop(currentMatch);
			if (!traversedWaitStop) {
				// Mark vehicle as being delayed
				vehicleState.setIsDelayed(true);

				// Create description of event
				long timeBetweenAvlReports = vehicleState.getAvlReport().getTime()
						- previousMatch.getAvlTime();
				String description =
						"Vehicle vehicleId="
								+ vehicleState.getVehicleId()
								+ " is delayed. Over "
								+ timeBetweenAvlReports
								+ " msec it "
								+ "traveled only "
								+ Geo.distanceFormat(distanceTraveled)
								+ " while "
								+ "transitclock.core.timeForDeterminingDelayedSecs="
								+ maxDelayedSecs + " and "
								+ "transitclock.core.minDistanceForDelayed="
								+ Geo.distanceFormat(minDistance);
				
				// Log the event
				logger.info(description);
				
				// If vehicle newly delayed then also create a VehicleEvent 
				// indicating such
				if (!wasDelayed) {
					VehicleEvent.create(vehicleState.getAvlReport(), 
							vehicleState.getMatch(), VehicleEvent.DELAYED,
							description, 
							true, // predictable
							false, // becameUnpredictable
							null); // supervisor
				}
				
				// Return that vehicle indeed delayed
				return true;
			}
		}

		// Vehicle was making progress so return such
		return false;
	}
	
	/**
	 * For vehicles that were already predictable but then got a new AvlReport.
	 * Determines where in the block assignment the vehicle now matches to.
	 * Starts at the previous match and then looks ahead from there to find good
	 * spatial matches. Then determines which spatial match is best by looking
	 * at temporal match. Updates the vehicleState with the resulting best
	 * temporal match.
	 * 
	 * @param vehicleState
	 *            the previous vehicle state
	 */
	public void matchNewFixForPredictableVehicle(VehicleState vehicleState) {
		// Make sure state is coherent
		if (!vehicleState.isPredictable() || vehicleState.getMatch() == null) {
			throw new RuntimeException("Called AvlProcessor.matchNewFix() "
					+ "for a vehicle that was not already predictable. "
					+ vehicleState);
		}

		logger.debug("STARTOFMATCHING");
		logger.debug("Matching already predictable vehicle using new AVL "
				+ "report. The old spatial match is {}", vehicleState);

		// Find possible route matches
		List<RouteMatch> spatialMatches = SpatialMatcher
				.getSpatialMatches(vehicleState);
		logger.debug("For vehicleId={} found the following {} spatial "
				+ "matches: {}", vehicleState.getVehicleId(),
				spatialMatches.size(), spatialMatches);
		
		// Find possible diversion matches.
		List<DiversionMatch> divesionMatches = DiversionMatcher.getDiversionMatches(vehicleState);
		logger.debug("For vehicleId={} found the following {} diversion "
				+ "matches: {}", vehicleState.getVehicleId(),
				divesionMatches.size(), divesionMatches);
		

		// Find best temporal match of the spatial matches
		TemporalMatch bestTemporalMatch = TemporalMatcher.getInstance()
				.getBestTemporalMatch(vehicleState, spatialMatches, divesionMatches);
								
		// Log this as info since matching is a significant milestone
		logger.info("For vehicleId={} the best match is {}",
				vehicleState.getVehicleId(), bestTemporalMatch);

		// If didn't get a match then remember such in VehicleState
		if (bestTemporalMatch == null)
			vehicleState.incrementNumberOfBadMatches();

		// If vehicle not making progress then return
		boolean notMakingProgress = handleIfVehicleNotMakingProgress(
				bestTemporalMatch, vehicleState);
		if (notMakingProgress)
			return;

		// Record this match unless the match was null and haven't
		// reached number of bad matches.
		if (bestTemporalMatch != null || vehicleState.overLimitOfBadMatches()) {
			// If not over the limit of bad matches then handle normally
			if (bestTemporalMatch != null
					|| !vehicleState.overLimitOfBadMatches()) {
				// Set the match of the vehicle. 
				vehicleState.setMatch(bestTemporalMatch);				
			} else {
				// Exceeded allowable number of bad matches so make vehicle 
				// unpredictable due to bad matches log that info.
				// Log that vehicle is being made unpredictable as a
				// VehicleEvent
				String eventDescription = "Vehicle had "
						+ vehicleState.numberOfBadMatches()
						+ " bad spatial matches in a row"
						+ " and so was made unpredictable.";

				logger.warn("For vehicleId={} {}", vehicleState.getVehicleId(),
						eventDescription);

				// Remove the predictions for the vehicle
				makeVehicleUnpredictable(vehicleState.getVehicleId(), eventDescription,
						VehicleEvent.NO_MATCH);

				// Remove block assignment from vehicle
				vehicleState.unsetBlock(BlockAssignmentMethod.COULD_NOT_MATCH);
			}
		} else {
			logger.info("For vehicleId={} got a bad match, {} in a row, so "
					+ "not updating match for vehicle",
					vehicleState.getVehicleId(),
					vehicleState.numberOfBadMatches());
		}
			
		// If schedule adherence is bad then try matching vehicle to assignment
		// again. This can make vehicle unpredictable if can't match vehicle to 
		// assignment.
		if (vehicleState.isPredictable() && vehicleState.lastMatchIsValid())
			verifyRealTimeSchAdh(vehicleState);
		
		logger.debug("ENDOFMATCHING");

	}

	/**
	 * When matching a vehicle to a route we are currently assuming that we
	 * cannot make predictions or match a vehicle to a specific trip until after
	 * vehicle has started on its trip. This is because there will be multiple
	 * trips per route and we cannot tell which one the vehicle is on time wise
	 * until the vehicle has started the trip.
	 * 
	 * @param match
	 * @return True if the match can be used when matching vehicle to a route
	 */
	private static boolean matchOkForRouteMatching(RouteMatch match) {
		return match.awayFromTerminals(getTerminalDistanceForRouteMatching());
	}

	/**
	 * When assigning a vehicle to a block then this method should be called to
	 * update the VehicleState and log a corresponding VehicleEvent. If block
	 * assignments are to be exclusive then any old vehicle on that assignment
	 * will be have its assignment removed.
	 * 
	 * @param bestMatch
	 *            The TemporalMatch that the vehicle was matched to. If set then
	 *            vehicle will be made predictable and if block assignments are
	 *            to be exclusive then any old vehicle on that assignment will
	 *            be have its assignment removed. If null then vehicle will be
	 *            configured to be not predictable.
	 * @param vehicleState
	 *            The VehicleState for the vehicle to be updated
	 * @param possibleBlockAssignmentMethod
	 *            The type of assignment, such as
	 *            BlockAssignmentMethod.AVL_FEED_ROUTE_ASSIGNMENT or
	 *            BlockAssignmentMethod.AVL_FEED_BLOCK_ASSIGNMENT
	 * @param assignmentId
	 *            The ID of the route or block that getting assigned to
	 * @param assignmentType
	 *            A string for logging and such indicating whether assignment
	 *            made to a route or to a block. Should therefore be "block" or
	 *            "route".
	 */
	private void updateVehicleStateFromAssignment(TemporalMatch bestMatch,
			VehicleState vehicleState,
			BlockAssignmentMethod possibleBlockAssignmentMethod,
			String assignmentId, String assignmentType) {
		// Convenience variables
		AvlReport avlReport = vehicleState.getAvlReport();
		String vehicleId = avlReport.getVehicleId();
		
		// Make sure no other vehicle is using that assignment
		// if it is supposed to be exclusive. This needs to be done before
		// the VehicleDataCache is updated with info from the current
		// vehicle since this will affect all vehicles assigned to the
		// block.
		if (bestMatch != null) {
			unassignOtherVehiclesFromBlock(bestMatch.getBlock(), vehicleId);
		}

		// If got a valid match then keep track of state
		BlockAssignmentMethod blockAssignmentMethod = null;
		boolean predictable = false;
		Block block = null;
		if (bestMatch != null) {
			blockAssignmentMethod = possibleBlockAssignmentMethod;
			predictable = true;
			block = bestMatch.getBlock();
			logger.info("vehicleId={} matched to {}Id={}. "
					+ "Vehicle is now predictable. Match={}",
					vehicleId, assignmentType, assignmentId, bestMatch);

			// Record a corresponding VehicleEvent
			String eventDescription = "Vehicle successfully matched to "
					+ assignmentType + " assignment and is now predictable.";
			VehicleEvent.create(avlReport, bestMatch, VehicleEvent.PREDICTABLE,
					eventDescription, true, // predictable
					false, // becameUnpredictable
					null); // supervisor
		} else {
			logger.debug("For vehicleId={} could not assign to {}Id={}. "
					+ "Therefore vehicle is not being made predictable.",
					vehicleId, assignmentType, assignmentId);
		}

		// Update the vehicle state with the determined block assignment
		// and match. Of course might not have been successful in
		// matching vehicle, but still should update VehicleState.
		vehicleState.setMatch(bestMatch);
		vehicleState.setBlock(block, blockAssignmentMethod, assignmentId,
				predictable);
		
		if (bestMatch != null) {
		  logConflictingSpatialAssigment(bestMatch, vehicleState);
		}
	}

	/**
	 * compare the match to the avl location and log if they differ greatly.
	 * Note we just log this, we do not make the vehicle unpredictable.
	 * @param bestMatch
	 * @param vehicleState
	 */
	private void logConflictingSpatialAssigment(TemporalMatch bestMatch,
      VehicleState vehicleState) {
	  if (vehicleState == null || vehicleState.getAvlReport() == null) return;

    // avl location
    double avlLat = vehicleState.getAvlReport().getLat();
    double avlLon = vehicleState.getAvlReport().getLon();
    Location avlLocation = new Location(avlLat, avlLon);

    // match location
    VectorWithHeading segment = bestMatch.getIndices().getSegment();
    double distanceAlongSegment = bestMatch.getDistanceAlongSegment();
    Location matchLocation = segment.locAlongVector(distanceAlongSegment);

    long tripStartTime = bestMatch.getTrip().getStartTime() * 1000 + Time.getStartOfDay(new Date());
    // ignore future trips as we are deadheading
    if (tripStartTime > Core.getInstance().getSystemTime())
    	return;
    
    // difference
	  double deltaDistance = Math.abs(Geo.distance(avlLocation, matchLocation));
	  
	  if (vehicleState.isPredictable() && deltaDistance > getMaxMatchDistanceFromAVLRecord()) {
      String eventDescription = "Vehicle match conflict from AVL report of " 
	    + Geo.distanceFormat(deltaDistance) + " from match " + matchLocation; 
	    
      VehicleEvent.create(vehicleState.getAvlReport(), bestMatch, VehicleEvent.AVL_CONFLICT,
          eventDescription, true, // predictable
          false, // becameUnpredictable
          null); // supervisor
	  }    
    
  }

  /**
	 * Attempts to match vehicle to the specified route by finding appropriate
	 * block assignment. Updates the VehicleState with the new block assignment
	 * and match. These will be null if vehicle could not successfully be
	 * matched to route.
	 * 
	 * @param routeId
	 * @param vehicleState
	 * @return True if successfully matched vehicle to block assignment for
	 *         specified route
	 */
	private boolean matchVehicleToRouteAssignment(String routeId,
			VehicleState vehicleState) {
		// Make sure params are good
		if (routeId == null) {
			logger.error("matchVehicleToRouteAssignment() called with null "
					+ "routeId. {}", vehicleState);
		}

		logger.debug("Matching unassigned vehicle to routeId={}. {}", routeId,
				vehicleState);

		// Convenience variables
		AvlReport avlReport = vehicleState.getAvlReport();

		// Determine which blocks are currently active for the route.
		// Multiple services can be active on a given day. Therefore need
		// to look at all the active ones to find out what blocks are active...
		List<Block> allBlocksForRoute = new ArrayList<Block>();
		ServiceUtils serviceUtils = Core.getInstance().getServiceUtils();
		Collection<String> serviceIds =
				serviceUtils.getServiceIds(avlReport.getDate());
		for (String serviceId : serviceIds) {
			List<Block> blocksForService = Core.getInstance().getDbConfig()
					.getBlocksForRoute(serviceId, routeId);
			if (blocksForService != null) {
				allBlocksForRoute.addAll(blocksForService);
			}
		}

		List<RouteMatch> allPotentialSpatialMatchesForRoute = new ArrayList<RouteMatch>();

		// Go through each block and determine best spatial matches
		for (Block block : allBlocksForRoute) {
			// If the block isn't active at this time then ignore it. This way
			// don't look at each trip to see if it is active which is important
			// because looking at each trip means all the trip data including
			// travel times needs to be lazy loaded, which can be slow.
			// Override by setting transitclock.core.ignoreInactiveBlocks to false
			if (!block.isActive(avlReport.getDate()) && ignoreInactiveBlocks.getValue()) {
				if (logger.isDebugEnabled()) {
					logger.debug("For vehicleId={} ignoring block ID {} with "
							+ "start_time={} and end_time={} because not "
							+ "active for time {}", avlReport.getVehicleId(),
							block.getId(),
							Time.timeOfDayStr(block.getStartTime()),
							Time.timeOfDayStr(block.getEndTime()),
							Time.timeStr(avlReport.getDate()));
				}
				continue;
			}

			// Determine which trips for the block are active. If none then
			// continue to the next block
			List<Trip> potentialTrips = block
					.getTripsCurrentlyActive(avlReport);
			if (potentialTrips.isEmpty())
				continue;

			logger.debug("For vehicleId={} examining potential trips for "
					+ "match to block ID {}. {}", avlReport.getVehicleId(),
					block.getId(), potentialTrips);

			// Get the potential spatial matches
			List<RouteMatch> spatialMatchesForBlock = SpatialMatcher
					.getSpatialMatches(vehicleState.getAvlReport(),
							block, potentialTrips, MatchingType.AUTO_ASSIGNING_MATCHING);

			// Add appropriate spatial matches to list
			for (RouteMatch spatialMatch : spatialMatchesForBlock) {
				if (!SpatialMatcher.problemMatchDueToLackOfHeadingInfo(
						spatialMatch, vehicleState, MatchingType.AUTO_ASSIGNING_MATCHING)
						&& matchOkForRouteMatching(spatialMatch))
					allPotentialSpatialMatchesForRoute.add(spatialMatch);
			}
		} // End of going through each block to determine spatial matches

		// For the spatial matches get the best temporal match
		TemporalMatch bestMatch = TemporalMatcher.getInstance()
				.getBestTemporalMatchComparedToSchedule(avlReport,
						allPotentialSpatialMatchesForRoute);
		logger.debug("For vehicleId={} best temporal match is {}",
				avlReport.getVehicleId(), bestMatch);

		// Update the state of the vehicle
		updateVehicleStateFromAssignment(bestMatch, vehicleState,
				BlockAssignmentMethod.AVL_FEED_ROUTE_ASSIGNMENT, routeId,
				"route");

		// Return true if predictable
		return bestMatch != null;
	}

	/**
	 * Attempts to match the vehicle to the new block assignment. Updates the
	 * VehicleState with the new block assignment and match. These will be null
	 * if vehicle could not successfully be matched to block.
	 * 
	 * @param block
	 * @param vehicleState
	 * @return True if successfully matched vehicle to block assignment
	 */
	private boolean matchVehicleToBlockAssignment(Block block,
			VehicleState vehicleState) {
		// Make sure params are good
		if (block == null) {
			logger.error("matchVehicleToBlockAssignment() called with null "
					+ "block. {}", vehicleState);
		}

		logger.debug("Matching unassigned vehicle to block assignment {}. {}",
				block.getId(), vehicleState);

		// Convenience variables
		AvlReport avlReport = vehicleState.getAvlReport();

		// Determine best spatial matches for trips that are currently
		// active. Currently active means that the AVL time is within
		// reasonable range of the start time and within the end time of
		// the trip. Matching type is set to MatchingType.STANDARD_MATCHING,
		// which means the matching can be more lenient than with
		// MatchingType.AUTO_ASSIGNING_MATCHING, because the AVL feed is
		// specifying the block assignment so it should find a match even
		// if it pretty far off.
		List<Trip> potentialTrips = block.getTripsCurrentlyActive(avlReport);
		List<RouteMatch> spatialMatches =
				SpatialMatcher.getSpatialMatches(vehicleState.getAvlReport(),
						block, potentialTrips, MatchingType.STANDARD_MATCHING);
		logger.debug("For vehicleId={} and blockId={} spatial matches={}",
				avlReport.getVehicleId(), block.getId(), spatialMatches);

		// Determine the best temporal match
		TemporalMatch bestMatch = TemporalMatcher.getInstance()
				.getBestTemporalMatchComparedToSchedule(avlReport,
						spatialMatches);
		logger.debug("Best temporal match for vehicleId={} is {}",
				avlReport.getVehicleId(), bestMatch);

		// If best match is a non-layover but cannot confirm that the heading
		// is acceptable then don't consider this a match. Instead, wait till
		// get another AVL report at a different location so can see if making
		// progress along route in proper direction.
		if (SpatialMatcher.problemMatchDueToLackOfHeadingInfo(bestMatch,
				vehicleState, MatchingType.STANDARD_MATCHING)) {
			logger.debug("Found match but could not confirm that heading is "
					+ "proper. Therefore not matching vehicle to block. {}",
					bestMatch);
			return false;
		}

		// If couldn't find an adequate spatial/temporal match then resort
		// to matching to a layover stop at a terminal.
		if (bestMatch == null) {
			logger.debug("For vehicleId={} could not find reasonable "
					+ "match so will try to match to layover stop.",
					avlReport.getVehicleId());

			Trip trip = TemporalMatcher.getInstance()
					.matchToLayoverStopEvenIfOffRoute(avlReport, potentialTrips);
			if (trip != null) {
				// Determine distance to first stop of trip
				Location firstStopInTripLoc = trip.getStopPath(0).getStopLocation();
				double distanceToSegment = 
						firstStopInTripLoc.distance(avlReport.getLocation());

				RouteMatch beginningOfTrip = new RouteMatch(
						avlReport.getTime(),
						block, block.getTripIndex(trip), 0, // stopPathIndex
						0, // segmentIndex
						distanceToSegment,
						0.0); // distanceAlongSegment

				bestMatch = new TemporalMatch(beginningOfTrip,
						new TemporalDifference(0));
				logger.debug("For vehicleId={} could not find reasonable "
						+ "match for blockId={} so had to match to layover. "
						+ "The match is {}", avlReport.getVehicleId(),
						block.getId(), bestMatch);
			} else {
				logger.debug("For vehicleId={} couldn't find match for "
						+ "blockId={}", avlReport.getVehicleId(), block.getId());
			}
		}

		// Sometimes get a bad assignment where there is already a valid vehicle
		// with the assignment and the new match is actually far away from the
		// route, indicating driver might have entered wrong ID or never
		// logged out. For this situation ignore the match.
		if (bestMatch != null
				&& matchProblematicDueOtherVehicleHavingAssignment(bestMatch,
						vehicleState)) {
			logger.error("Got a match for vehicleId={} but that assignment is "
					+ "already taken by another vehicle and the new match "
					+ "doesn't appear to be valid because it is far away from "
					+ "the route. {} {}",
					avlReport.getVehicleId(), bestMatch, avlReport);
			return false;
		}
		
		// Update the state of the vehicle
		updateVehicleStateFromAssignment(bestMatch, vehicleState,
				BlockAssignmentMethod.AVL_FEED_BLOCK_ASSIGNMENT, block.getId(),
				"block");

		// Return true if predictable
		return bestMatch != null;
	}

	/**
	 * Determines if match is problematic since other vehicle already has
	 * assignment and the other vehicle seems to be more appropriate. Match is
	 * problematic if 1) exclusive matching is enabled, 2) other non-schedule
	 * based vehicle already has the assignment, 3) the other vehicle that
	 * already has the assignment isn't having any problems such as vehicle
	 * being delayed, and 4) the new match is far away from the route which
	 * implies that it might be a mistaken login.
	 * <p>
	 * Should be noted that this issue was encountered with sfmta on 1/1/2016
	 * around 15:00 for vehicle 8660 when avl feed showed it getting block
	 * assignment 573 even though it was far from the route and vehicle 8151
	 * already had that assignment and actually was on the route. This
	 * can happen if driver enters wrong assignment, or perhaps if they
	 * never log out.
	 * 
	 * @param match
	 * @param vehicleState
	 * @return true if the match is problematic and should not be used
	 */
	private boolean matchProblematicDueOtherVehicleHavingAssignment(
			TemporalMatch match, VehicleState vehicleState) {
		// If no match in first place then not a problem
		if (match == null)
			return false;
		
		// If matches don't need to be exclusive then don't have a problem
		Block block = match.getBlock();
		if (!block.shouldBeExclusive())
			return false;
		
		// If no other non-schedule based vehicle assigned to the block then 
		// not a problem
		Collection<String> vehiclesAssignedToBlock = VehicleDataCache
				.getInstance().getVehiclesByBlockId(block.getId());
		if (vehiclesAssignedToBlock.isEmpty())
			// No other vehicle has assignment so not a problem
			return false;
		String otherVehicleId = null;
		for (String vehicleId : vehiclesAssignedToBlock) {
			otherVehicleId = vehicleId;
			VehicleState otherVehicleState =
					VehicleStateManager.getInstance()
							.getVehicleState(otherVehicleId);

			// If other vehicle that has assignment is schedule based then not 
			// a problem to take its assignment away
			if (otherVehicleState.isForSchedBasedPreds())
				return false;
			
			// If that other vehicle actually having any problem then not a 
			// problem to take assignment away
			if (!otherVehicleState.isPredictable() || vehicleState.isDelayed())
				return false;			
		}
		 
		// So far we know that another vehicle has exclusive assignment and
		// there are no problems with that vehicle. This means the new match 
		// could be a mistake. Shouldn't use it if the new vehicle is far
		// away from route (it matches to a layover where matches are lenient),
		// indicating that the vehicle might have gotten wrong assignment while
		// doing something else.
		if (match.getDistanceToSegment() > maxDistanceForAssignmentGrab.getValue()) {			
			// Match is far away from route so consider it to be invalid.
			// Log an error
			logger.error(
					"For agencyId={} got a match for vehicleId={} but that "
					+ "assignment is already taken by vehicleId={} and the new "
					+ "match doesn't appear to be valid because it is more "
					+ "than {}m from the route. {} {}",
					AgencyConfig.getAgencyId(), vehicleState.getVehicleId(), 
					otherVehicleId, maxDistanceForAssignmentGrab.getValue(), 
					match, vehicleState.getAvlReport());

			// Only send e-mail error rarely
			if (shouldSendMessage(vehicleState.getVehicleId(), 
					vehicleState.getAvlReport())) {
				logger.error(Markers.email(),
					"For agencyId={} got a match for vehicleId={} but that "
					+ "assignment is already taken by vehicleId={} and the new "
					+ "match doesn't appear to be valid because it is more "
					+ "than {}m from the route. {} {}",
					AgencyConfig.getAgencyId(), vehicleState.getVehicleId(), 
					otherVehicleId, maxDistanceForAssignmentGrab.getValue(), 
					match, vehicleState.getAvlReport());
			}
			
			return true;
		} else {
			// The new match is reasonably close to the route so should consider
			// it valid
			return false;
		}
	}
	
	// Keyed on vehicleId. Contains last time problem grabbing assignment 
	// message sent for the vehicle. For reducing number of emails sent
	// when there is a problem.
	private Map<String, Long> problemGrabbingAssignmentMap = 
			new HashMap<String, Long>();
	
	/**
	 * For reducing e-mail logging messages when problem grabbing assignment.
	 * Java property transitclock.avl.emailMessagesWhenAssignmentGrabImproper must
	 * be true for e-mail to be sent when there is an error.
	 * 
	 * @param vehicleId
	 * @param avlReport
	 * @return true if should send message
	 */
	private boolean shouldSendMessage(String vehicleId, AvlReport avlReport) {
		Long lastTimeSentForVehicle = problemGrabbingAssignmentMap.get(vehicleId);
		// If message not yet sent for vehicle or it has been more than 10 minutes...
		if (emailMessagesWhenAssignmentGrabImproper.getValue() 
				&& (lastTimeSentForVehicle == null 
				    || avlReport.getTime() > lastTimeSentForVehicle + 30*Time.MS_PER_MIN)) {
			problemGrabbingAssignmentMap.put(vehicleId, avlReport.getTime());
			return true;
		} else 
			return false;
	}
	
	/**
	 * If the block assignment is supposed to be exclusive then looks for any
	 * vehicles assigned to the specified block and removes the assignment from
	 * them. This of course needs to be called before a vehicle is assigned to a
	 * block since *ALL* vehicles assigned to the block will have their
	 * assignment removed.
	 * 
	 * @param block
	 * @param newVehicleId
	 *            for logging message
	 */
	private void unassignOtherVehiclesFromBlock(Block block, String newVehicleId) {
		// Determine vehicles assigned to block
		Collection<String> vehiclesAssignedToBlock = VehicleDataCache
				.getInstance().getVehiclesByBlockId(block.getId());

		// For each vehicle assigned to the block unassign it
		VehicleStateManager stateManager = VehicleStateManager.getInstance();
		for (String vehicleId : vehiclesAssignedToBlock) {
			VehicleState vehicleState = stateManager.getVehicleState(vehicleId);
			if (block.shouldBeExclusive()
					|| vehicleState.isForSchedBasedPreds()) {
				String description = "Assigning vehicleId=" + newVehicleId
						+ " to blockId=" + block.getId() + " but "
						+ "vehicleId=" + vehicleId
						+ " already assigned to that block so "
						+ "removing assignment from vehicleId=" + vehicleId 
						+ ".";
				logger.info(description);
				makeVehicleUnpredictableAndGrabAssignment(vehicleState,
						description, VehicleEvent.ASSIGNMENT_GRABBED);
			}
		}
	}

	/**
	 * To be called when vehicle doesn't already have a block assignment or the
	 * vehicle is being reassigned. Uses block assignment from the AvlReport to
	 * try to match the vehicle to the assignment. If successful then the
	 * vehicle can be made predictable. The AvlReport is obtained from the
	 * vehicleState parameter.
	 * 
	 * @param avlReport
	 * @param vehicleState
	 *            provides current AvlReport plus is updated by this method with
	 *            the new state.
	 * @return true if successfully assigned vehicle
	 */
	public boolean matchVehicleToAssignment(VehicleState vehicleState) {
		logger.debug("Matching unassigned vehicle to assignment. {}",
				vehicleState);

		// Initialize some variables
		AvlReport avlReport = vehicleState.getAvlReport();

		// Remove old block assignment if there was one
		if (vehicleState.isPredictable()
				&& vehicleState.hasNewAssignment(avlReport)) {
			String eventDescription = "For vehicleId="
					+ vehicleState.getVehicleId()
					+ " the vehicle assignment is being "
					+ "changed to assignmentId="
					+ vehicleState.getAssignmentId();
			makeVehicleUnpredictableAndTerminateAssignment(vehicleState,
					eventDescription, VehicleEvent.ASSIGNMENT_CHANGED);
		}

		// If the vehicle has a block assignment from the AVLFeed
		// then use it.
		Block block = BlockAssigner.getInstance().getBlockAssignment(avlReport);
		if (block != null) {
			// There is a block assignment from AVL feed so use it.
			return matchVehicleToBlockAssignment(block, vehicleState);
		} else {
			// If there is a route assignment from AVL feed us it
			String routeId = BlockAssigner.getInstance().getRouteIdAssignment(
					avlReport);
			if (routeId != null) {
				// There is a route assignment so use it
				return matchVehicleToRouteAssignment(routeId, vehicleState);
			}
		}

		// This method called when there is an assignment from AVL feed. But
		// if that assignment is invalid then will make it here. Try the
		// auto assignment feature in case it is enabled.
		boolean autoAssigned = 
				automaticalyMatchVehicleToAssignment(vehicleState);
		if (autoAssigned)
			return true;
		
		// There was no valid block or route assignment from AVL feed so can't
		// do anything. But set the block assignment for the vehicle
		// so it is up to date. This call also sets the vehicle state
		// to be unpredictable.
		BlockAssignmentMethod blockAssignmentMethod = null;
		vehicleState.unsetBlock(blockAssignmentMethod);
		return false;
	}

	/**
	 * For when vehicle didn't get an assignment from the AVL feed and the
	 * vehicle previously was predictable and was matched to an assignment then
	 * see if can continue to use the old assignment.
	 * 
	 * @param vehicleState
	 */
	private void handlePredictableVehicleWithoutAvlAssignment(
			VehicleState vehicleState) {
		String oldAssignment = vehicleState.getAssignmentId();

		// Had a valid old assignment. If haven't had too many bad
		// assignments in a row then use the old assignment.
		if (vehicleState.getBadAssignmentsInARow() < allowableBadAssignments
				.getValue()) {
			logger.warn("AVL report did not include an assignment for "
					+ "vehicleId={} but badAssignmentsInARow={} which "
					+ "is less than allowableBadAssignments={} so using "
					+ "the old assignment={}", vehicleState.getVehicleId(),
					vehicleState.getBadAssignmentsInARow(),
					allowableBadAssignments.getValue(),
					vehicleState.getAssignmentId());

			// Create AVL report with the old assignment and then use it
			// to update the vehicle state
			AvlReport modifiedAvlReport = new AvlReport(
					vehicleState.getAvlReport(), oldAssignment,
					AssignmentType.PREVIOUS);
			vehicleState.setAvlReport(modifiedAvlReport);
			matchNewFixForPredictableVehicle(vehicleState);

			// Increment the bad assignments count
			vehicleState.setBadAssignmentsInARow(vehicleState
					.getBadAssignmentsInARow() + 1);
		} else {
			// Vehicle was predictable but now have encountered too many
			// problem assignments. Therefore make vehicle unpredictable.
			String eventDescription = "VehicleId="
					+ vehicleState.getVehicleId() + " was assigned to blockId="
					+ oldAssignment
					+ " but received " + vehicleState.getBadAssignmentsInARow() 
					+ " null assignments in a row, which is configured by "
					+ "transitclock.core.allowableBadAssignments to be too many, "
					+ "so making vehicle unpredictable.";
			makeVehicleUnpredictable(vehicleState.getVehicleId(),
					eventDescription, VehicleEvent.ASSIGNMENT_CHANGED);
		}
	}

	/**
	 * For when vehicle is not predictable and didn't have previous assignment.
	 * Since this method is to be called when vehicle isn't assigned and didn't
	 * get a valid assignment through the feed should try to automatically
	 * assign the vehicle based on how it matches to a currently unmatched
	 * block. If it can match the vehicle then this method fully processes the
	 * match, generating predictions and such.
	 * 
	 * @param vehicleState
	 * @return true if auto assigned vehicle
	 */
	private boolean automaticalyMatchVehicleToAssignment(VehicleState vehicleState) {
		// If actually creating a schedule based prediction
		if (vehicleState.isForSchedBasedPreds())
			return false;

		if (!AutoBlockAssigner.enabled()) {
			logger.info("Could not automatically assign vehicleId={} because "
					+ "AutoBlockAssigner not enabled.", 
					vehicleState.getVehicleId());
			return false;
		}
		
		logger.info("Trying to automatically assign vehicleId={}", 
				vehicleState.getVehicleId());
		
		// Try to match vehicle to a block assignment if that feature is enabled
		AutoBlockAssigner autoAssigner = new AutoBlockAssigner(vehicleState);
		TemporalMatch bestMatch = autoAssigner.autoAssignVehicleToBlockIfEnabled();
		if (bestMatch != null) {
			// Successfully matched vehicle to block so make vehicle predictable
			logger.info("Auto matched vehicleId={} to a block assignment. {}",
					vehicleState.getVehicleId(), bestMatch);

			// Update the state of the vehicle
			updateVehicleStateFromAssignment(bestMatch, vehicleState,
					BlockAssignmentMethod.AUTO_ASSIGNER, bestMatch.getBlock()
							.getId(), "block");
			return true;
		}
		
		return false;
	}

	/**
	 * For when don't have valid assignment for vehicle. If have a valid old
	 * assignment and haven't gotten too many bad assignments in a row then
	 * simply use the old assignment. This is handy for when the assignment
	 * portion of the AVL feed does not send assignment data for every report.
	 * 
	 * @param vehicleState
	 */
	private void handleProblemAssignment(VehicleState vehicleState) {
		String oldAssignment = vehicleState.getAssignmentId();
		boolean wasPredictable = vehicleState.isPredictable();

		logger.info("No assignment info for vehicleId={} so trying to assign "
				+ "vehicle without it.", vehicleState.getVehicleId());

		// If the vehicle previously was predictable and had an assignment
		// then see if can continue to use the old assignment.
		if (wasPredictable && oldAssignment != null) {
			handlePredictableVehicleWithoutAvlAssignment(vehicleState);
		} else {
			// Vehicle wasn't predictable and didn't have previous assignment.
			// Since this method is to be called when vehicle isn't assigned
			// and didn't get an assignment through the feed should try to
			// automatically assign the vehicle based on how it matches to
			// a currently unmatched block.
			automaticalyMatchVehicleToAssignment(vehicleState);
		}
	}

	/**
	 * Looks at the last match in vehicleState to determine if at end of block
	 * assignment. Updates vehicleState if at end of block. Note that this will
	 * not always work since might not actually get an AVL report that matches
	 * to the last stop.
	 * 
	 * @param vehicleState
	 * @return True if end of the block was reached with the last match.
	 */
	private boolean handlePossibleEndOfBlock(VehicleState vehicleState) {
		// Determine if at end of block assignment
		TemporalMatch temporalMatch = vehicleState.getMatch();
		if (temporalMatch != null) {
			VehicleAtStopInfo atStopInfo = temporalMatch.getAtStop();
			if (atStopInfo != null && atStopInfo.atEndOfBlock()) {
				logger.info("For vehicleId={} the end of the block={} "
						+ "was reached so will make vehicle unpredictable",
						vehicleState.getVehicleId(), temporalMatch.getBlock()
								.getId());

				// At end of block assignment so remove it
				String eventDescription = "Block assignment "
								+ vehicleState.getBlock().getId()
								+ " ended for vehicle so it was made unpredictable.";
				makeVehicleUnpredictableAndTerminateAssignment(vehicleState,
						eventDescription, VehicleEvent.END_OF_BLOCK);

				// Return that end of block reached
				return true;
			}
		}

		// End of block wasn't reached so return false
		return false;
	}

	/**
	 * If schedule adherence is not within bounds then will try to match the
	 * vehicle to the assignment again. This can be important if system is run
	 * for a while and then paused and then started up again. Vehicle might
	 * continue to match to the pre-paused match, but by then the vehicle might
	 * be on a whole different trip, causing schedule adherence to be really far
	 * off. To prevent this the vehicle is re-matched to the assignment.
	 * <p>
	 * Updates vehicleState accordingly.
	 * 
	 * @param vehicleState
	 */
	private void verifyRealTimeSchAdh(VehicleState vehicleState) {
		// If no schedule then there can't be real-time schedule adherence
		if (vehicleState.getBlock() == null
				|| vehicleState.getBlock().isNoSchedule())
			return;
		
		logger.debug("Confirming real-time schedule adherence for vehicleId={}",
				vehicleState.getVehicleId());

		// Determine the schedule adherence for the vehicle
		TemporalDifference scheduleAdherence = RealTimeSchedAdhProcessor
				.generate(vehicleState);

		// If vehicle is just sitting at terminal past its scheduled departure
		// time then indicate such as an event.
		if (vehicleState.getMatch().isWaitStop()
				&& scheduleAdherence != null
				&& scheduleAdherence.isLaterThan(CoreConfig
						.getAllowableLateAtTerminalForLoggingEvent())
				&& vehicleState.getMatch().getAtStop() != null) {
			// Create description for VehicleEvent
			String stopId = vehicleState.getMatch().getStopPath().getStopId();
			Stop stop = Core.getInstance().getDbConfig().getStop(stopId);
			Route route = vehicleState.getMatch().getRoute();
			VehicleAtStopInfo stopInfo = vehicleState.getMatch().getAtStop();
			Integer scheduledDepartureTime = stopInfo.getScheduleTime()
					.getDepartureTime();

			String description = "Vehicle " + vehicleState.getVehicleId()
					+ " still at stop " + stopId + " \"" + stop.getName()
					+ "\" for route \"" + route.getName() + "\" "
					+ scheduleAdherence.toString()
					+ ". Scheduled departure time was "
					+ Time.timeOfDayStr(scheduledDepartureTime);

			// Create, store in db, and log the VehicleEvent
			VehicleEvent.create(vehicleState.getAvlReport(),
					vehicleState.getMatch(), VehicleEvent.NOT_LEAVING_TERMINAL,
					description, true, // predictable
					false, // becameUnpredictable
					null); // supervisor

		}

		// Make sure the schedule adherence is reasonable
		if (scheduleAdherence != null
				&& !scheduleAdherence.isWithinBounds()) {
			logger.warn(
					"For vehicleId={} schedule adherence {} is not "
							+ "between the allowable bounds. Therefore trying to match "
							+ "the vehicle to its assignmet again to see if get better "
							+ "temporal match by matching to proper trip.",
					vehicleState.getVehicleId(), scheduleAdherence);

			// Log that vehicle is being made unpredictable as a VehicleEvent
			String eventDescription = "Vehicle had schedule adherence of "
					+ scheduleAdherence + " which is beyond acceptable "
					+ "limits. Therefore vehicle made unpredictable.";
		
			// Clear out match, make vehicle event, clear predictions.
			makeVehicleUnpredictable(vehicleState.getVehicleId(), eventDescription, VehicleEvent.NO_MATCH);

			// Schedule adherence not reasonable so match vehicle to assignment
			// again.
			matchVehicleToAssignment(vehicleState);
		}
	}

	/**
	 * Determines the real-time schedule adherence and stores the value in the
	 * vehicleState. To be called after the vehicle is matched.
	 * 
	 * @param vehicleState
	 */
	private void determineAndSetRealTimeSchAdh(VehicleState vehicleState) {
		// If no schedule then there can't be real-time schedule adherence
		if (vehicleState.getBlock() == null
				|| vehicleState.getBlock().isNoSchedule())
			return;
		
		logger.debug("Determining and setting real-time schedule adherence for "
				+ "vehicleId={}", vehicleState.getVehicleId());

		// Determine the schedule adherence for the vehicle
		TemporalDifference scheduleAdherence = RealTimeSchedAdhProcessor
				.generate(vehicleState);
		
		// Store the schedule adherence with the vehicle
		vehicleState.setRealTimeSchedAdh(scheduleAdherence);		
	}
	
	/**
	 * Processes the AVL report by matching to the assignment and generating
	 * predictions and such. Sets VehicleState for the vehicle based on the
	 * results. Also stores AVL report into the database (if not in playback
	 * mode).
	 * 
	 * @param avlReport
	 *            The new AVL report to be processed
	 * @param recursiveCall
	 *            Set to true if this method is calling itself. Used to make
	 *            sure that any bug can't cause infinite recursion.
	 */
	private void lowLevelProcessAvlReport(AvlReport avlReport,
			boolean recursiveCall) {
		// Determine previous state of vehicle
		String vehicleId = avlReport.getVehicleId();
		VehicleState vehicleState = VehicleStateManager.getInstance()
				.getVehicleState(vehicleId);

		// Since modifying the VehicleState should synchronize in case another
		// thread simultaneously processes data for the same vehicle. This
		// would be extremely rare but need to be safe.
		synchronized (vehicleState) {
			// Keep track of last AvlReport even if vehicle not predictable.
			vehicleState.setAvlReport(avlReport);

			// If part of consist and shouldn't be generating predictions
			// and such and shouldn't grab assignment the simply return
			// not that the last AVL report has been set for the vehicle.
			if (avlReport.ignoreBecauseInConsist()) {
				return;
			}
			
			// Do the matching depending on the old and the new assignment
			// for the vehicle.
			boolean matchAlreadyPredictableVehicle = 
					vehicleState.isPredictable()
					&& !vehicleState.hasNewAssignment(avlReport);
			boolean matchToNewAssignment = avlReport.hasValidAssignment()
					&& (!vehicleState.isPredictable() || vehicleState
							.hasNewAssignment(avlReport))
					&& !vehicleState.previousAssignmentProblematic(avlReport);

			if (matchAlreadyPredictableVehicle) {
				// Vehicle was already assigned and assignment hasn't
				// changed so update the match of where the vehicle is
				// within the assignment.
				matchNewFixForPredictableVehicle(vehicleState);
			} else if (matchToNewAssignment) {
				// New assignment from AVL feed so match the vehicle to it
				matchVehicleToAssignment(vehicleState);
			} else {
				// Handle bad assignment where don't have assignment or such.
				// Will try auto assigning a vehicle if that feature is enabled.
				handleProblemAssignment(vehicleState);
			}

			// If the last match is actually valid then generate associated
			// data like predictions and arrival/departure times.
			if (vehicleState.isPredictable() 
					&& vehicleState.lastMatchIsValid()) {
				// Reset the counter
				vehicleState.setBadAssignmentsInARow(0);

				// If vehicle is delayed as indicated by not making forward 
				// progress then store that in the vehicle state
				handlePossibleVehicleDelay(vehicleState);
				
				// Determine and store the schedule adherence. 
				determineAndSetRealTimeSchAdh(vehicleState);
				
				// Only continue processing if vehicle is still predictable
				// since calling checkScheduleAdherence() can make it
				// unpredictable if schedule adherence is really bad.
				if (vehicleState.isPredictable()) {
					// Generates the corresponding data for the vehicle such as
					// predictions and arrival times
					MatchProcessor.getInstance().generateResultsOfMatch(
							vehicleState);

					// If finished block assignment then should remove
					// assignment
					boolean endOfBlockReached = 
							handlePossibleEndOfBlock(vehicleState);

					// If just reached the end of the block and took the block
					// assignment away and made the vehicle unpredictable then
					// should see if the AVL report could be used to assign
					// vehicle to the next assignment. This is needed for
					// agencies like Zhengzhou which is frequency based and
					// where each block assignment is only a single trip and
					// when vehicle finishes one trip/block it can go into the
					// next block right away.
					if (endOfBlockReached) {
						if (recursiveCall) {
							// This method was already called recursively which
							// means unassigned vehicle at end of block but then
							// it got assigned to end of block again. This
							// indicates a bug since vehicles at end of block
							// shouldn't be reassigned to the end of the block
							// again. Therefore log problem and don't try to
							// assign vehicle again.
							logger.error(
									"AvlProcessor.lowLevelProcessAvlReport() "
									+ "called recursively, which is wrong. {}",
									vehicleState);
						} else {
							// Actually process AVL report again to see if can
							// assign to new assignment.
							lowLevelProcessAvlReport(avlReport, true);
						}
					} // End of if end of block reached
				}
			}

			// If called recursively (because end of block reached) but
			// didn't match to new assignment then don't want to store the
			// vehicle state since already did that. 
			if (recursiveCall && !vehicleState.isPredictable())
				return;
			
			// Now that VehicleState has been updated need to update the
			// VehicleDataCache so that when data queried for API the proper
			// info is provided.
			VehicleDataCache.getInstance().updateVehicle(vehicleState);
			
			// Write out current vehicle state to db so can join it with AVL
			// data from db and get historical context of AVL report.
			org.transitclock.db.structs.VehicleState dbVehicleState =
					new org.transitclock.db.structs.VehicleState(vehicleState);
			Core.getInstance().getDbLogger().add(dbVehicleState);
		} // End of synchronizing on vehicleState }
	}

	/**
	 * Returns the GPS time of the last regular (non-schedule based) GPS report
	 * processed. Since AvlClient filters out reports that are for a previous
	 * time for a vehicle even if the AVL feed continues to feed old data that
	 * data will be ignored. In other words, the last AVL time will be for that
	 * last valid AVL report.
	 * 
	 * @return The GPS time in msec epoch time of last AVL report, or 0 if no
	 *         last AVL report
	 */
	public long lastAvlReportTime() {
		if (lastRegularReportProcessed == null)
			return 0;

		return lastRegularReportProcessed.getTime();
	}

	/**
	 * For storing the last regular (non-schedule based) AvlReport so can
	 * determine if the AVL feed is working. Makes sure that report is newer
	 * than the previous last regular report so that ignore possibly old data
	 * that might come in from the AVL feed.
	 * 
	 * @param avlReport
	 *            The new report to possibly store
	 */
	private void setLastAvlReport(AvlReport avlReport) {
		// Ignore schedule based predictions AVL reports since those are faked 
		// and don't represent what is going on with the AVL feed
		if (avlReport.isForSchedBasedPreds())
			return;

		// Only store report if it is a newer one. In this way we ignore 
		// possibly old data that might come in from the AVL feed.
		if (lastRegularReportProcessed == null
				|| avlReport.getTime() > lastRegularReportProcessed.getTime()) {
			lastRegularReportProcessed = avlReport;
		}
	}
	
	/**
	 * Returns the last regular (non-schedule based) AvlReport.
	 * 
	 * @return
	 */
	public AvlReport getLastAvlReport() {
		return lastRegularReportProcessed;
	}
	
	/**
	 * Updates the VehicleState in the cache to have the new avlReport. Intended
	 * for when want to update VehicleState AVL report but don't want to
	 * actually process the report, such as for when get data too frequently and
	 * only want to fully process some of it yet still use latest vehicle
	 * location so that vehicles move on map really smoothly.
	 * 
	 * @param avlReport
	 */
	public void cacheAvlReportWithoutProcessing(AvlReport avlReport) {
		VehicleState vehicleState =
				VehicleStateManager.getInstance().getVehicleState(
						avlReport.getVehicleId());
		
		// Since modifying the VehicleState should synchronize in case another
		// thread simultaneously processes data for the same vehicle. This
		// would be extremely rare but need to be safe.
		synchronized (vehicleState) {
			// Update AVL report for cached VehicleState
			vehicleState.setAvlReport(avlReport);
			
			// Let vehicle data cache know that the vehicle state was updated
			// so that new IPC vehicle data will be created and cached and
			// made available to the API.
			VehicleDataCache.getInstance().updateVehicle(vehicleState);
		}
	}
	
	/**
	 * First does housekeeping for the AvlReport (stores it in db, logs it,
	 * etc). Processes the AVL report by matching to the assignment and
	 * generating predictions and such. Sets VehicleState for the vehicle based
	 * on the results. Also stores AVL report into the database (if not in
	 * playback mode).
	 * 
	 * @param avlReport
	 *            The new AVL report to be processed
	 */
	public void processAvlReport(AvlReport avlReport) {
		IntervalTimer timer = new IntervalTimer(); 

		// Handle special case where want to not use assignment from AVL
		// report, most likely because want to test automatic assignment
		// capability
		if (AutoBlockAssigner.ignoreAvlAssignments()
				&& !avlReport.isForSchedBasedPreds()) {
			logger.debug("Removing assignment from AVL report because "
					+ "transitclock.autoBlockAssigner.ignoreAvlAssignments=true. {}",
					avlReport);
			avlReport.setAssignment(null, AssignmentType.UNSET);
		}

		// The beginning of processing AVL data is an important milestone
		// in processing data so log it as info.
		logger.info("===================================================="
				+ "AvlProcessor processing {}", avlReport);

		// Record when the AvlReport was actually processed. This is done here
		// so that the value will be set when the avlReport is stored in the
		// database using the DbLogger.
		avlReport.setTimeProcessed();

		// Keep track of last AVL report processed so can determine if AVL
		// feed is up
		setLastAvlReport(avlReport);

		// Make sure that vehicle configuration is in cache and database
		VehicleDataCache.getInstance().cacheVehicleConfig(avlReport);
		
		// Store the AVL report into the database
		if (!CoreConfig.onlyNeedArrivalDepartures()
				&& !avlReport.isForSchedBasedPreds())
			Core.getInstance().getDbLogger().add(avlReport);

		// If any vehicles have timed out then handle them. This is done
		// here instead of using a regular timer so that it will work
		// even when in playback mode or when reading batch data.
		Core.getInstance().getTimeoutHandlerModule().storeAvlReport(avlReport);

		// Logging to syserr just for debugging.
		if (AvlConfig.shouldLogToStdOut()) {
			System.err.println("Processing avlReport for vehicleId="
					+ avlReport.getVehicleId() +
					// " AVL time=" + Time.timeStrMsec(avlReport.getTime()) +
					" " + avlReport + " ...");
		}

		// Do the low level work of matching vehicle and then generating results
		lowLevelProcessAvlReport(avlReport, false);
		logger.debug("Processing AVL report took {}msec", timer);
        CloudwatchService.getInstance().saveMetric("PredictionProcessingTimeInMillis", Double.valueOf(timer.elapsedMsec()), 1, CloudwatchService.MetricType.AVERAGE, CloudwatchService.ReportingIntervalTimeUnit.MINUTE, false);
        CloudwatchService.getInstance().saveMetric("PredictionTotalLatencyInMillis", Double.valueOf((System.currentTimeMillis() - avlReport.getTime())), 1, CloudwatchService.MetricType.AVERAGE, CloudwatchService.ReportingIntervalTimeUnit.MINUTE, false);
	}

}
