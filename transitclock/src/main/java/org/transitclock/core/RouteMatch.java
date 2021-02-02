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

import java.util.List;

import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.ScheduleTime;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.Vector;
import org.transitclock.db.structs.VectorWithHeading;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;
import org.slf4j.Logger;

/**
 * Describes where an AVL report matches to an assignment spatially.
 * 
 * @author SkiBu Smith
 * 
 */
public class RouteMatch extends SpatialMatch {

	protected final int stopPathIndex;
	protected final int segmentIndex;
	protected final double distanceToSegment;
	protected final double distanceAlongSegment;
	protected final VehicleAtStopInfo atStop;

	
	private static final Logger logger = 
			LoggerFactory.getLogger(RouteMatch.class);


	/********************** Member Functions **************************/

	public RouteMatch(long avlTime, Block block,
			int tripIndex, int stopPathIndex, int segmentIndex,
			double distanceToSegment, double distanceAlongSegment) {
		
		super(avlTime, block, tripIndex);
		
		this.stopPathIndex = stopPathIndex;
		this.segmentIndex = segmentIndex;
		this.distanceToSegment = distanceToSegment;
		this.distanceAlongSegment = distanceAlongSegment;
		
		// Determine whether at stop
		this.atStop = atStop();
	
	}

	/**
	 * based on the current trip/stop path/sgement compute the predicted 
	 * vehicle location.
	 */
	protected Location computeLocation() {
		Trip trip = block.getTrip(tripIndex);
		if (trip != null) {
			StopPath stopPath = trip.getStopPath(stopPathIndex);
			if (stopPath != null) {
				VectorWithHeading vector = stopPath.getSegmentVector(segmentIndex);
				if (vector != null) {
					return vector.locAlongVector(distanceAlongSegment);
				}
			}
		}
		return null;
	}

	/**
	 * based on the indices and distance along segment compute the predicted
	 * vehicle location.
	 */
	public Location computeLocation(Indices i, double distanceAlongSegment) {
		if (i != null) {
			VectorWithHeading segment = i.getSegment();
			if (segment != null) {
				return segment.locAlongVector(distanceAlongSegment);
			}
		}
		return null;
	}

	/**
	 * For making a copy of the SpatialMatch for the same trip pattern but for a
	 * new trip/block. This is useful when doing things by TripPattern, such as
	 * spatial matching. Can then make copies for the other trips that use that
	 * TripPattern.
	 * 
	 * @param toCopy
	 *            The SpatialMatch to copy (except for the trip/block info)
	 * @param newTrip
	 *            The new trip to use for the copy. It is assumed to be for the
	 *            same trip pattern. It can be for a separate block.
	 */
	public RouteMatch(RouteMatch toCopy, Trip newTrip) {
		// Use the new block and trip index info
		super(toCopy.avlTime, newTrip.getBlock(), newTrip.getBlock().getTripIndex(newTrip));
		if (toCopy.getTrip().getTripPattern() != newTrip.getTripPattern())
			logger.error("Trying to create a copy of a SpatialMatch using a "
					+ "new trip but they have different trip patterns. "
					+ "toCopy={} toCopy.tripPattern={} newTrip.tripPattern={}",
					toCopy, toCopy.getTrip().getTripPattern().toShortString(),
					newTrip.getTripPattern().toShortString());			
		this.stopPathIndex = toCopy.stopPathIndex;
		this.segmentIndex = toCopy.segmentIndex;
		this.distanceToSegment = toCopy.distanceToSegment;
		this.distanceAlongSegment = toCopy.distanceAlongSegment;
		// Make a copy of the atStop. Can't simply use the toCopy
		// atStop because it will be for the wrong tripIndex. Therefore
		// need to create a new object.
		if (toCopy.atStop() == null) {
			this.atStop = null;
		} else {
			this.atStop = new VehicleAtStopInfo(newTrip.getBlock(),
					this.tripIndex, 
					toCopy.atStop().getStopPathIndex());
		}
		// recomupte predictedLocation for above reasons as well
		this.predictedLocation = toCopy.computeLocation(toCopy.getIndices(), toCopy.distanceAlongSegment);
	}
	
	/**
	 * Constructs a new SpatialMatch but at the new indices specified. Useful
	 * for create a match that is just before or after a stop, which is useful
	 * for determining travel time from stop to the next match.
	 * 
	 * @param toCopy
	 * @param newIndices
	 * @param distanceAlongSegment
	 */
	public RouteMatch(RouteMatch toCopy, Indices newIndices, 
			double distanceAlongSegment) {		
		super(toCopy.avlTime, toCopy.block, newIndices.getTripIndex());
		this.stopPathIndex = newIndices.getStopPathIndex();
		this.segmentIndex = newIndices.getSegmentIndex();
		this.distanceToSegment = toCopy.distanceToSegment;
		this.distanceAlongSegment = distanceAlongSegment;
		this.atStop = toCopy.atStop;
		this.predictedLocation = computeLocation(newIndices, distanceAlongSegment);
	}

	/**
	 * For subclasses to create an object using this superclass.
	 * 
	 * @param toCopy
	 */
	protected RouteMatch(RouteMatch toCopy) {
		super(toCopy.getAvlTime(), toCopy.getBlock(), toCopy.getTripIndex());
		this.stopPathIndex = toCopy.stopPathIndex;
		this.segmentIndex = toCopy.segmentIndex;
		this.distanceToSegment = toCopy.distanceToSegment;
		this.distanceAlongSegment = toCopy.distanceAlongSegment;
		this.atStop = toCopy.atStop;
		this.predictedLocation = toCopy.predictedLocation;
	}

	/**
	 * Returns distance of this match from the beginning of the trip.
	 * 
	 * @return
	 */
	public double distanceFromBeginningOfTrip() {
		// Determine how far match is from terminal at beginning of trip
		double distanceFromFirstTerminal = 0.0;
		Trip trip = getTrip();
		for (int index=1; index<stopPathIndex; ++index) {
			distanceFromFirstTerminal += trip.getStopPath(index).getLength();		
		}
		if (stopPathIndex != 0)
			distanceFromFirstTerminal += getDistanceAlongStopPath();
		return distanceFromFirstTerminal;
	}
	
	/**
	 * Determines if this SpatialMatch is further away then the specified
	 * distance from the terminal at the beginning and end of the trip. This can
	 * be useful when doing things like matching a vehicle to a route where one
	 * needs to first be sure that the vehicle is not at a terminal because at
	 * the terminal one cannot determine appropriate spatial match.
	 * 
	 * @param distance
	 * @return True if match is further away than distance away from terminals
	 *         of trip
	 */
	public boolean awayFromTerminals(double distance) {
		// Determine how far match is from terminal at beginning of trip
		double distanceFromFirstTerminal = distanceFromBeginningOfTrip();
		
		// If too close to beginning of trip return false
		if (distanceFromFirstTerminal < distance)
			return false;
		
		// If too close to end of trip return false
		double distanceFromLastTerminal = 
				getTrip().getLength() - distanceFromFirstTerminal;
		if (distanceFromLastTerminal < distance)
			return false;
		
		// Somewhere in the middle of trip so return true
		return true;
	}
	
	/**
	 * Returns whether this SpatialMatch is within the specified distance along
	 * the path from the end of the trip.
	 * 
	 * @param distance
	 * @return True if within distance of end of trip.
	 */
	public boolean withinDistanceOfEndOfTrip(double distance) {
		// Determine how far match is from terminal at beginning of trip
		double distanceFromFirstTerminal = distanceFromBeginningOfTrip();
		
		// Return if within specified distance of end of trip
		double tripLength = getTrip().getLength();
		double distanceFromLastTerminal = 
				tripLength - distanceFromFirstTerminal;
		return distanceFromLastTerminal < distance;
	}
	
	/**
	 * Returns whether the trip for this match is the last trip of the block.
	 */
	public boolean isLastTripOfBlock() {
		return tripIndex == block.getTrips().size()-1;
	}
	
	/**
	 * Returns true if the match is to the stop at the end of the stop path.
	 * This can be important because atStop can also be set to the previous
	 * stop, which is something very different. If match is not for at a stop
	 * then null is returned.
	 * 
	 * @return True if match is for at stop at end of stop path
	 */
	public boolean atEndOfPathStop() {
		return atStop != null && atStop.getTripIndex() == tripIndex
				&& atStop.getStopPathIndex() == stopPathIndex;
	}
	
	/**
	 * Returns true if the match is to the stop for the previous stop path. This
	 * can be important because atStop can also be set to the stop for the
	 * current stop path, which is very different. If match is not for at a stop
	 * then null is returned.
	 * 
	 * @return True if match is for stop for the previous stop path
	 */
	public boolean atBeginningOfPathStop() {
		return atStop != null 
				&& atStop.getTripIndex() == tripIndex  
				&& atStop.getStopPathIndex() != stopPathIndex;		
	}
	
	/**
	 * For when need to determine arrival time when vehicle matches to a stop.
	 * Returns a SpatialMatch that corresponds to the end of the stop path for
	 * the stop indicated by the current SpatialMatch. If the match is for stop
	 * for the previous stop path (the vehicle is matching to just beyond the
	 * stop) the the previous stop path is used. Only to be called when vehicle
	 * is matched to a stop. Logs error and returns null if match is not for at
	 * a stop.
	 * 
	 * @return SpatialMatch adjusted so it is at end of path for the stop
	 */
	public RouteMatch getMatchAdjustedToEndOfPath() {
		// If not at a stop then we have a problem
		if (atStop == null) {
			logger.error("Wrongly called " +
					"getMatchAdjustedToEndOfPath() when vehicle is not at " +
					"a stop. This is not allowed.");
			return null;
		}
		
		// Determine Indices for the atStop for the Match. If match 
		// is for stop at the beginning of the path instead of the end
		// then decrement the indices so will point to proper stop path.
		Indices indices = getIndices();
		if (!atEndOfPathStop()) {
			indices.decrementStopPath();
		}

		// Determine and return spatial match that is at the end of
		// the StopPath indicates by indices
		StopPath stopPath = indices.getStopPath();
		int segmentIndex = stopPath.getNumberSegments()-1;
		Vector segmentVector = stopPath.getSegmentVector(segmentIndex);
		double distanceAlongSegment = segmentVector.length();
		Indices endOfStopPathIndices = new Indices(block,
				indices.getTripIndex(), indices.getStopPathIndex(),
				segmentIndex);
		return new RouteMatch(this, endOfStopPathIndices,
				distanceAlongSegment);
	}
	
	/**
	 * Determines the next StopPath after this match that has a schedule time.
	 * Useful for determining things such as real-time schedule adherence.
	 * 
	 * @return
	 */
	public RouteMatch getMatchAtNextStopWithScheduleTime() {
		// Determine next stop with a schedule time (arrival or departure)		
		// If there is no such stop then return null. 
		List<StopPath> stopPaths = 
				getTrip().getTripPattern().getStopPaths();
		StopPath stopPathWithScheduleTime = null;
		int stopPathIndex = 0;
		for (int i=getStopPathIndex(); i<stopPaths.size(); ++i) {
			StopPath stopPath = stopPaths.get(i);
			ScheduleTime scheduleTime = getTrip().getScheduleTime(i);
			if (scheduleTime.getTime() != null) {
				stopPathWithScheduleTime = stopPath;
				stopPathIndex = i;
				break;
			}
		}
		if (stopPathWithScheduleTime == null) 
			return null;

		// Determine the appropriate match to use for the upcoming stop where
		// there is a schedule time.		
		Indices indicesAtStopWithScheduleTime = new Indices(getBlock(),
				getTripIndex(), stopPathIndex, 
				stopPathWithScheduleTime.getNumberSegments()-1);		
		StopPath stopPath = indicesAtStopWithScheduleTime.getStopPath();
		int segmentIndex = stopPath.getNumberSegments()-1;
		Vector segmentVector = stopPath.getSegmentVector(segmentIndex);
		double distanceAlongSegment = segmentVector.length();
		RouteMatch matchAtStopWithScheduleTime = new RouteMatch(this, 
				indicesAtStopWithScheduleTime,
				distanceAlongSegment);

		// Return the spatial match at the next stop with a schedule time
		return matchAtStopWithScheduleTime;
	}
	
	/**
	 * For when need to determine departure time at a stop. Returns a
	 * SpatialMatch that corresponds to the beginning of the next stop path for
	 * the stop indicated by the current SpatialMatch. Only to be called when vehicle
	 * is matched to a stop. Logs error and returns null if match is not for at
	 * a stop.
	 * 
	 * @return SpatialMatch adjusted so it is at beginning of path for the stop
	 */
	public RouteMatch getMatchAdjustedToBeginningOfPath() {
		// If not at a stop then we have a problem
		if (atStop == null) {
			logger.error("Wrongly called " +
					"getMatchAdjustedToBeginningOfPath() when vehicle is " +
					"not at a stop. This is not allowed.");
			return null;
		}

		// Determine Indices for the atStop for the Match. If match 
		// is for stop at the beginning of the path instead of the end
		// then decrement the indices so will point to proper stop path.
		Indices indices = getIndices();
		if (!atBeginningOfPathStop()) {
			indices.incrementStopPath();
		}

		// Create a new SpatialMatch that is at the beginning of
		// stop path
		int segmentIndex = 0;
		double distanceAlongSegment = 0.0;
		Indices beginningOfStopPathIndices = new Indices(block,
				indices.getTripIndex(), indices.getStopPathIndex(),
				segmentIndex);
		return new RouteMatch(this, beginningOfStopPathIndices,
				distanceAlongSegment);
	}
	
	/**
	 * Returns a SpatialMatch that corresponds to the current match but will be
	 * moved to after a stop if the current match is just before the stop. This
	 * way can pass in this modified match to the methods that determine travel
	 * time and the layover/stop time for the stop where the SpatialMatch is
	 * will not be included in the calculations. If the current object is not
	 * just before a stop then the original object is returned.
	 * 
	 * @return This match or one after the stop if at end of stop path
	 */
	private RouteMatch getMatchAfterStopIfAtStop() {
		// If the spatialMatch is not just before a stop then return
		// spatialMatch. The spatialMatch is just before a stop if
		// atStop trip and path indices are the same as for the match.
		if (!atEndOfPathStop())
			return this;
		
		// The spatialMatch is just before a stop so create a new spatial
		// match but use the beginning of the next path.
		Indices nextPathIndices = getIndices().incrementStopPath();
		return new RouteMatch(this, nextPathIndices, 0.0);
	}

	/**
	 * Returns a SpatialMatch that corresponds to just before the next stop.
	 * Useful if need to determine travel time to next stop.
	 * 
	 * @return
	 */
	public RouteMatch getMatchAtJustBeforeNextStop() {
		// First need to get on the proper path. If just before a stop
		// then need to get a match just after that stop.
		RouteMatch m = getMatchAfterStopIfAtStop();
		
		int segmentIndex = 
				block.numSegments(m.getTripIndex(), m.getStopPathIndex())-1;
		Vector segmentVector = block.getSegmentVector(m.getTripIndex(),
				m.getStopPathIndex(), segmentIndex);
		double segmentLength = segmentVector.length();
		
		// Return a match that is at the end of the path
		return new RouteMatch(
				0, // Don't worry about avlTime for this special case
				block, 
				m.getTripIndex(),
				m.getStopPathIndex(),
				segmentIndex, 
				Double.NaN, // distanceToSegment not set to a valid value
				segmentLength);
	}
	
	/**
	 * Returns a SpatialMatch that corresponds to the current match but will be
	 * moved to before a stop if the current match is just after the stop. This
	 * way can pass in this modified match to the methods that determine travel
	 * time and the layover/stop time for the stop where the SpatialMatch is
	 * will not be included in the calculations. If the current object is not
	 * just after a stop then the original object is returned.
	 * 
	 * @return This match or one before the stop if at beginning of stop path
	 */
	public RouteMatch getMatchBeforeStopIfAtStop() {
		// If the spatialMatch is not just after a stop then return
		// spatialMatch. 
		if (!atBeginningOfPathStop())
			return this;
		
		// The spatialMatch is just after a stop so create a new spatial
		// match but use the end of the previous path.
		Indices previousPathIndices = getIndices().decrementStopPath();
		return new RouteMatch(this, previousPathIndices, 
				previousPathIndices.getSegment().length());
	}

	/**
	 * Returns a SpatialMatch that corresponds to the previous stop. The
	 * returned match will be just before the previous stop. Useful if need to
	 * determine travel time from previous stop.
	 * 
	 * @return The SpatialMatch for the previous stop, or null if there is no
	 *         previous stop (the current match is for the beginning of the
	 *         block).
	 */
	public RouteMatch getMatchAtPreviousStop() {
		// First need to get on the proper path. If just before a stop
		// then need to get a match just after that stop.
		Indices indices = 
				getMatchBeforeStopIfAtStop().getIndices().decrementStopPath();
		
		// If at beginning of block such that the match before the stop
		// is invalid then simply return null
		if (indices.beforeBeginningOfBlock())
			return null;
		
		// Return a match that is at the end of the path
		Vector segmentVector = indices.getBlock().getSegmentVector(
				indices.getTripIndex(), indices.getStopPathIndex(),
				indices.getSegmentIndex());
		double segmentVectorLength = segmentVector!=null ? 
				segmentVector.length() : Double.NaN;
		return new RouteMatch(
				0, // Don't worry about avlTime for this special case
				block, 
				indices.getTripIndex(),
				indices.getStopPathIndex(),
				indices.getSegmentIndex(),
				Double.NaN,       // distanceToSegment not set to a valid value
				segmentVectorLength); 
	}
	
	/**
	 * Determines if the match means that the vehicle is considered to be at a
	 * stop. Looks forward and back from the match to see if it is within the
	 * allowable distance of the stop. Intended to be called by constructor
	 * to set the atStop member variable so that only have to determine if
	 * SpatialMatch is at the stop once.
	 * 
	 * @return VehicleAtStopInfo object containing trip and path index of stop
	 *         that match is nearby. Returns null if match is not near a stop.
	 */
	private VehicleAtStopInfo atStop() {
		// Determine if just before a stop. Need to look at both how far the
		// match is ahead of the stop and whether the stop is a layover because
		// could have a peculiar config (yes, SFMTA again, this time for route
		// 71) where there is a long path for the first stop, so the distance
		// of the match to the layover stop might be really far but since
		// it is a layover stop should consider the match to be at that stop
		// anyways.
		StopPath stopPath = block.getStopPath(tripIndex, stopPathIndex);
		double distanceRemaining = getDistanceRemainingInStopPath();
		double beforeStopDistance = stopPath.getBeforeStopDistance();
		if (stopPath.isLayoverStop() 
				|| distanceRemaining < beforeStopDistance) {
			// Indeed just before the stop so return the current stop/path index
			return new VehicleAtStopInfo(block, tripIndex, stopPathIndex);
		} 
		
		// Not just before a stop so see if just after a stop
		Indices previousPathIndices = getIndices().decrementStopPath();
 		if (!previousPathIndices.beforeBeginningOfBlock()) {
			StopPath previousStopPath = previousPathIndices.getStopPath();
			if (previousStopPath != null
					&& getDistanceAlongStopPath() < 
						previousStopPath.getAfterStopDistance())
				return new VehicleAtStopInfo(block,
						previousPathIndices.getTripIndex(), 
						previousPathIndices.getStopPathIndex());
		}
		
		// Not at a stop so return null to indicate such
		return null;
	}
	
	/**
	 * A layover stop is when a vehicle can leave route path before departing
	 * this stop since the driver is taking a break.
	 * 
	 * @return true if this spatial match is for a layover
	 */
	public boolean isLayover() {
	  if (block.isNoSchedule()) return false; // Frequency-based unscheduled blocks can't have layovers
		return block.isLayover(tripIndex, stopPathIndex);
	}
	
	/**
	 * Wait stop is when a vehicle is not supposed to depart the stop until the
	 * scheduled departure time.
	 * 
	 * @return true if this spatial match is for a waitStop
	 */
	public boolean isWaitStop() {
		return block.isWaitStop(tripIndex, stopPathIndex);
	}
	
	/**
	 * Returns the scheduled time vehicle is to leave the stop.
	 * 
	 * @return the scheduled time vehicle is to leave the stop. Time is seconds
	 *         into day. Returns -1 if not at a stop or the stop doesn't have a
	 *         scheduled departure time.
	 */
	public int getScheduledWaitStopTimeSecs() { 
		try {
			ScheduleTime scheduleTime = 
					block.getScheduleTime(tripIndex, stopPathIndex);
			if (scheduleTime == null) {
			  // prevent nullpointer
			  logger.error("no scheduled wait stop time for {} {}", tripIndex, stopPathIndex);
			  return -1;
			}
			return scheduleTime.getDepartureTime();
		} catch (Exception e) {
			logger.error("Tried to get wait stop time for a stop that didn't "
					+ "have one. {} {}", this, e, e);
			return -1; 
		}
	}
	
	/**
	 * Returns the scheduled time vehicle is to leave the stop.
	 * 
	 * @return the scheduled epoch time vehicle is to leave the stop. Returns -1
	 *         if not at a stop or the stop doesn't have a scheduled departure
	 *         time.
	 */
	public long getScheduledWaitStopTime() {
		int secondsIntoDay = getScheduledWaitStopTimeSecs();
		if (secondsIntoDay < 0)
			return -1;
		
		long scheduledDepartureTime = Core.getInstance().getTime()
				.getEpochTime(secondsIntoDay, avlTime);
		return scheduledDepartureTime;
	}
	
	/**
	 * Travel distance along current path.
	 * 
	 * @return Distance in meters
	 */
	public double getDistanceAlongStopPath() {
		double distance = 0.0;
		for (int segIndex=0; segIndex<segmentIndex; ++segIndex) {
			Vector v = block.getSegmentVector(tripIndex, stopPathIndex,
					segIndex);
			distance += v.length();
		}
		distance += distanceAlongSegment;
		return distance;
	}
	
	/**
	 * How far still to travel to end of path.
	 * 
	 * @return Distance in meters
	 */
	public double getDistanceRemainingInStopPath() {
		double distance = -distanceAlongSegment;
		StopPath stopPath = block.getStopPath(tripIndex, stopPathIndex);
		int numSegments = stopPath.getSegmentVectors().size();
		for (int segIndex=segmentIndex; segIndex<numSegments; ++segIndex) {
			distance += stopPath.getSegmentVector(segIndex).length();
		}
		return distance;
	}
	
	/**
	 * Determines how far it is from this match to otherSpatialMatch.
	 * 
	 * @param otherSpatialMatch
	 * @return Distance in meters between the matches
	 */
	public double distanceBetweenMatches(RouteMatch otherSpatialMatch) {
		// Determine the distances from the beginning of the stop paths
		// and the matches
		double distanceAlongFirstPath = 
				getDistanceAlongStopPath();
		double distanceAlongLastPath = 
				otherSpatialMatch.getDistanceAlongStopPath();
		
		// Determine the lengths of the stop paths. Should include the
		// first one and any intermediate stop paths, but should not include
		// the last one.
		Indices indices = getIndices();
		Indices endIndices = otherSpatialMatch.getIndices();
		double totalStopPathDistances = 0.0;
		while (indices.isEarlierStopPathThan(endIndices)) {
			totalStopPathDistances += indices.getStopPath().getLength();		
			indices.incrementStopPath();
		}

		// Now determine total distance between matches. Note that for
		// the first path the distance is the length of the stop path
		// minus the distance of the first match along the path.
		double distanceBetweenMatches = totalStopPathDistances
				- distanceAlongFirstPath + distanceAlongLastPath;
		return distanceBetweenMatches;
	}
	
	/**
	 * Determines if when going from this spatial match to the otherSpatialMatch
	 * whether a wait stop is traversed. Also returns true if just sitting at a
	 * wait stop.
	 * 
	 * @param otherSpatialMatch
	 * @return true if there is a wait stop between this match and the other
	 *         match
	 */
	public boolean traversedWaitStop(RouteMatch otherSpatialMatch) {
		// If either this match or the other match are at a wait stop then
		// indeed a wait stop is in effect
		VehicleAtStopInfo atStop = getAtStop();
		if (atStop != null && atStop.isWaitStop())
			return true;
		VehicleAtStopInfo endAtStop = otherSpatialMatch.getAtStop();
		if (endAtStop != null && endAtStop.isWaitStop())
			return true;
		
		
		Indices indices = getIndices();
		Indices endIndices = otherSpatialMatch.getIndices();
		
		while (indices.isEarlierStopPathThan(endIndices)) {
			if (indices.isWaitStop())
				return true;
			
			indices.incrementStopPath();
		}
		
		return false;
	}

	/**
	 * Determines the indices for the position the specified distance before
	 * this match. Useful for seeing if looking how far past a stop the match
	 * is.
	 * 
	 * @param distanceBeforeMatch
	 * @return
	 */
	public Indices getIndicesForDistanceBeforeMatch(double distanceBeforeMatch) {
		Indices indices = getIndices();
		double distanceExamined = distanceAlongSegment;
		while (distanceExamined < distanceBeforeMatch) {
			indices.decrement();
			if (indices.beforeBeginningOfBlock())
				return null;
			distanceExamined += indices.getSegment().length();			
		}
		
		return indices;
	}
	
	/**
	 * Returns true if this is before or equal to the other SpatialMatch passed
	 * in.
	 * 
	 * @param other
	 *            The Spatial Match to compare to
	 * @return true if this is before or equal to the other SpatialMatch
	 */
	public boolean lessThanOrEqualTo(RouteMatch other) {
		if (tripIndex > other.tripIndex)
			return false;
		if (tripIndex < other.tripIndex)
			return true;
		
		// tripIndex == other.tripIndex
		if (stopPathIndex > other.stopPathIndex)
			return false;
		if (stopPathIndex < other.stopPathIndex)
			return true;
		
		// stopPathIndex == other.pathIndex
		if (segmentIndex > other.segmentIndex)
			return false;
		if (segmentIndex < other.segmentIndex)
			return true;
		
		// segmentIndex == other.segmentIndex
		return distanceAlongSegment <= other.distanceAlongSegment;
	}

	/**
	 * Returns true if this is before the other SpatialMatch passed in.
	 * 
	 * @param other
	 *            The Spatial Match to compare to
	 * @return true if this is before the other SpatialMatch
	 */
	public boolean lessThan(RouteMatch other) {
		if (tripIndex > other.tripIndex)
			return false;
		if (tripIndex < other.tripIndex)
			return true;
		
		// tripIndex == other.tripIndex
		if (stopPathIndex > other.stopPathIndex)
			return false;
		if (stopPathIndex < other.stopPathIndex)
			return true;
		
		// stopPathIndex == other.pathIndex
		if (segmentIndex > other.segmentIndex)
			return false;
		if (segmentIndex < other.segmentIndex)
			return true;
		
		// segmentIndex == other.segmentIndex
		return distanceAlongSegment < other.distanceAlongSegment;
	}

	/**
	 * Returns the total number of stops traversed between the two
	 * matches.
	 * 
	 * @param match1
	 * @param match2
	 * @return Number of stops
	 */
	public static int numberStopsBetweenMatches(RouteMatch match1,
			RouteMatch match2) {
		// Stop index for first trip
		int stopIdxInFirstTrip = match1.getStopPathIndex();

		// Stops for intermediate trips
		Block block = match2.getBlock();
		int numIntermediateTripsStops = 0;
		for (int tripIndex = match1.getTripIndex(); 
				tripIndex < match2.getTripIndex(); 
				++tripIndex) {
			numIntermediateTripsStops += 
					block.getTrip(tripIndex).getNumberStopPaths();
		}

		// Stops for last trip
		int stopIdxInLastTrip = match2.getStopPathIndex();

		// Return total number of stops traversed between matches
		int totalStopsBetweenMatches = stopIdxInLastTrip - stopIdxInFirstTrip
				+ numIntermediateTripsStops;
		return totalStopsBetweenMatches;
	}
	
	@Override
	public String toString() {
		return "SpatialMatch [" 
				+ "avlTime=" + Time.dateTimeStrMsec(avlTime)
				// + ", block=" + block.toShortString() too verbose!
				+ ", blockId=" + block.getId()
				+ ", tripIndex=" + tripIndex
				+ ", gtfsStopSeq=" + getStopPath().getGtfsStopSeq()
				+ ", stopPathIndex=" + stopPathIndex
				+ ", segmentIndex=" + segmentIndex
				+ ", isLayover=" + isLayover()
				+ ", distanceToSegment=" + Geo.distanceFormat(distanceToSegment) 
				+ ", distanceAlongSegment=" + Geo.distanceFormat(distanceAlongSegment) 
				+ ", distanceAlongStopPath=" + Geo.distanceFormat(getDistanceAlongStopPath())
				+ ", atStop=" + atStop
				+ ", trip=" + getTrip().toShortString()
				+ "]";
	}

	/********************* Getter Methods *****************************/
	
	/**
	 * Returns copy of the indices of the match as an Indices object.
	 * The Indices object is newly created and independent so
	 * it can be changed (incremented and decremented) at will.
	 * @return
	 */
	public Indices getIndices() {
		return new Indices(this);
	}
	
	public Block getBlock() {
		return block;
	}
	
	public Trip getTrip() {
		return block.getTrips().get(tripIndex);
	}

	public Route getRoute() {
		return getTrip().getRoute();
	}
	
	/**
	 * Returns the vector for the segment for this SpatialMatch.
	 * @return
	 */
	public Vector getSegmentVector() {
		int segmentIndex = block.numSegments(tripIndex, stopPathIndex) - 1;
		Vector segmentVector = 
				block.getSegmentVector(tripIndex, stopPathIndex, segmentIndex);
		return segmentVector;
	}
	
	/**
	 * The index of which trip this is within the block.
	 * 
	 * @return
	 */
	public int getTripIndex() {
		return tripIndex;
	}
	
	/**
	 * The index of which stop path this is within the trip.
	 * @return
	 */
	public int getStopPathIndex() {
		return stopPathIndex;
	}

	public StopPath getStopPath() {
		return getTrip().getStopPath(stopPathIndex);
	}
	
	public int getSegmentIndex() {
		return segmentIndex;
	}

	public double getDistanceToSegment() {
		return distanceToSegment;
	}
	
	public double getDistanceAlongSegment() {
		return distanceAlongSegment;
	}
	
	/**
	 * Can be either at stop at beginning of the stop path or at the stop at the
	 * end of the stop path.
	 * 
	 * @return VehicleAtStopInfo object containing trip and path index of stop
	 *         that match is nearby. Returns null if match is not near a stop.
	 */
	public VehicleAtStopInfo getAtStop() {
		return atStop;
	}
	
	/**
	 * Returns the VehicleAtStopInfo if match indicates that vehicle is at the
	 * stop at the end of the path.
	 * 
	 * @return VehicleAtStopInfo if vehicle at stop at end of stop path.
	 *         Otherwise null.
	 */
	public VehicleAtStopInfo getAtEndStop() {
		if (atStop == null)
			return null;
		
		// At a stop so see if it is the end of path stop
		if (atStop.getStopPathIndex() == stopPathIndex) {
			// At end of path stop so return it
			return atStop;
		} else {
			// At a stop but it is the beginning stop so return null
			return null;
		}
	}
	
	/**
	 * Returns the VehicleAtStopInfo if match indicates that vehicle is at the
	 * stop at the beginning of the path.
	 * 
	 * @return VehicleAtStopInfo if vehicle at stop at beginning of stop path.
	 *         Otherwise null.
	 */
	public VehicleAtStopInfo getAtBeginningStop() {
		if (atStop == null)
			return null;
		
		// At a stop so see if it is the end of path stop
		if (atStop.getStopPathIndex() == stopPathIndex) {
			// At end of path stop so return null
			return null;
		} else {
			// At beginning stop so return it
			return atStop;
		}	
	}
	
	/**
	 * Returns true if vehicle is at or near a stop. Can be either at stop at
	 * beginning of the stop path or at the stop at the end of the stop path.
	 * 
	 * @return true if vehicle is at or near a stop.
	 */
	public boolean isAtStop() {
		return atStop != null;
	}
	
	/**
	 * Returns true if this match indicates that the vehicle is at the stop
	 * indicated by the tripIndex and stopPathIndex parameters.
	 * 
	 * @param tripIndex
	 * @param stopPathIndex
	 * @return True if at specified stop
	 */
	public boolean isAtStop(int tripIndex, int stopPathIndex) {
		return atStop != null
				&& atStop.equals(atStop.getBlock().getId(), tripIndex, 
						stopPathIndex);
	}

	/**
	 * Returns the epoch time of the AVL report for which this match was
	 * created.
	 * 
	 * @return Time of the associated AVL report
	 */
	public long getAvlTime() {
		return avlTime;
	}
	
	public Location getLocation() {
		return predictedLocation;
	}
}

