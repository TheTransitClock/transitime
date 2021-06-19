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
package org.transitclock.gtfs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Stop;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.TripPattern;
import org.transitclock.db.structs.Vector;
import org.transitclock.gtfs.gtfsStructs.GtfsShape;
import org.transitclock.utils.Geo;
import org.transitclock.utils.IntervalTimer;

/**
 * Part of GtfsData class. Processes the shapes.txt data and converts
 * into the segments for the associated stopPaths between the stops.
 * 
 * @author SkiBu Smith
 * 
 */
public class StopPathProcessor {

	// Data passed in to constructor
	private final Map<String, List<GtfsShape>> gtfsShapesMap;  // Keyed on shapeId
	private final Map<String, Stop> stopsMap;
	private final Collection<TripPattern> tripPatterns;
	private final double offsetDistance;
	private final double maxStopToPathDistance;
	private final double maxDistanceForEliminatingVertices;
	private final boolean trimPathBeforeFirstStopOfTrip;
	private double maxDistanceBetweenStops;
	private boolean disableSpecialLoopBackToBeginningCase;
	
	private static final Logger logger = 
			LoggerFactory.getLogger(StopPathProcessor.class);

	/********************** Member Functions **************************/

	/**
	 * Constructor. Stores parameters.
	 * 
	 * @param gtfsShapes
	 * @param stopsMap
	 * @param tripPatterns
	 * @param offsetDistance
	 *            How much to the right the stopPaths should be offset. If
	 *            negative then the stopPaths are offset to the left. If 0.0
	 *            then of course the stopPaths are not offset at all.
	 * @param maxStopToPathDistance
	 * @param maxDistanceForEliminatingVertices
	 *            For getting rid of really small segments
	 * @param disableSpecialLoopBackToBeginningCase 
	 * @param Indicates
	 *            that shouldn't use the shape before the first stop of the
	 *            trip. This can be useful because sometimes a single shape is
	 *            used for multiple trip patterns or the shapes simply have some
	 *            problem points at the beginning (like sfmta 21-Hayes.
	 */
	public StopPathProcessor(Collection<GtfsShape> gtfsShapes, 
			Map<String, Stop> stopsMap, 
			Collection<TripPattern> tripPatterns,
			double offsetDistance,
			double maxStopToPathDistance,
			double maxDistanceForEliminatingVertices,
			boolean trimPathBeforeFirstStopOfTrip,double maxDistanceBetweenStops, boolean disableSpecialLoopBackToBeginningCase) {
		// Create a GtfsShapes Map where can look up
		// GtfsShapes by shapeId.
		gtfsShapesMap = new HashMap<String, List<GtfsShape>>(gtfsShapes.size());
		for (GtfsShape gtfsShape : gtfsShapes) {
			String shapeIdKey = gtfsShape.getShapeId();
			List<GtfsShape> shapesList = gtfsShapesMap.get(shapeIdKey);
			if (shapesList == null) {
				shapesList = new ArrayList<GtfsShape>();
				gtfsShapesMap.put(shapeIdKey, shapesList);
			}				
			shapesList.add(gtfsShape);
		}
		// The shapes might not be in the right order so need to sort them.
		// This way can step through the shape points in the proper order.
		for (String shapeIdKey : gtfsShapesMap.keySet()) {
			List<GtfsShape> shapesList = gtfsShapesMap.get(shapeIdKey);
			Collections.sort(shapesList);
		}
		
		this.stopsMap = stopsMap;
		this.tripPatterns = tripPatterns;
		this.offsetDistance = offsetDistance;
		this.maxStopToPathDistance = maxStopToPathDistance;
		this.maxDistanceForEliminatingVertices = maxDistanceForEliminatingVertices;
		this.trimPathBeforeFirstStopOfTrip = trimPathBeforeFirstStopOfTrip;
		this.maxDistanceBetweenStops=maxDistanceBetweenStops;
		this.disableSpecialLoopBackToBeginningCase=disableSpecialLoopBackToBeginningCase;
	}
	
	/**
	 * For determining the stopPaths for a trip pattern when there is no shape
	 * defined in the GTFS shapes.txt file for that trip pattern. Simply
	 * connects the stops in the trip pattern with a straight line. The first
	 * stop gets a path that has a segment of length 0.
	 * 
	 * @param tripPattern
	 */
	private void connectStopsSinceNoShapes(TripPattern tripPattern) {
		// Create a path segment to the first stop
		StopPath firstPath = tripPattern.getStopPath(0);
		String firstStopIdForTrip = tripPattern.getStopId(0);
		Stop firstStopForTrip = stopsMap.get(firstStopIdForTrip);
		ArrayList<Location> locList = new ArrayList<Location>();
		locList.add(firstStopForTrip.getLoc());
		locList.add(firstStopForTrip.getLoc());		
		firstPath.setLocations(locList);
		
		// Create stopPaths for all the other stops, one to connect each pair of stops
		for (int stopIndex=0; stopIndex<tripPattern.getStopPaths().size()-1; ++stopIndex) {
			// Determine the locations of the two stops that define the path
			String stopId0 = tripPattern.getStopId(stopIndex);
			Stop stop0 = stopsMap.get(stopId0);
			Location loc0 = stop0.getLoc();
			
			String stopId1 = tripPattern.getStopId(stopIndex+1);
			Stop stop1 = stopsMap.get(stopId1);
			Location loc1 = stop1.getLoc();

			locList = new ArrayList<Location>();
			locList.add(loc0);
			locList.add(loc1);
			
			// Filter the segments since some might be longer than desired
			StopPath path = tripPattern.getStopPath(stopIndex+1);
			filterShortSegments(locList, maxDistanceForEliminatingVertices);
			
			path.setLocations(locList);
		}
	}
	
	/**
	 * Offsets the stopPaths to the right the distance specified. Useful for when
	 * shape data is street center line. By offsetting the data the stopPaths in the
	 * different directions won't overlap on the map when zoomed in adequately.
	 * Also, makes debugging easier since won't have confusing overlapping
	 * stopPaths.
	 * 
	 * @param gtfsShapes
	 *            List of GtfsShape objects that represent the original path for
	 *            the shape.
	 * @return
	 */
	private List<Location> getOffsetLocations(List<GtfsShape> gtfsShapes) {
		// The array where the offset path goes
		List<Location> offsetLocations = 
				new ArrayList<Location>(gtfsShapes.size());
		
		// If offsetDistance is 0.0 don't need to go through all the calculations
		if (offsetDistance == 0.0) {
			for (GtfsShape gtfsShape : gtfsShapes) {
				offsetLocations.add(gtfsShape.getLocation());
			}
			return offsetLocations;
		}
		
		// Deal with the first location specially since it isn't a vertex between
		// two vectors.
		GtfsShape gtfsShape0 = gtfsShapes.get(0);
		GtfsShape gtfsShape1 = gtfsShapes.get(1);
		Location beginningOffsetLoc = 
				Geo.rightOffsetBeginningLoc(gtfsShape0.getLocation(), 
						gtfsShape1.getLocation(), 
						offsetDistance);
		offsetLocations.add(beginningOffsetLoc);
		
		// Deal with all of the middle points of the path
		for (int i=0; i<gtfsShapes.size()-2; ++i) {
			GtfsShape g1 = gtfsShapes.get(i);
			GtfsShape g2 = gtfsShapes.get(i+1);
			GtfsShape g3 = gtfsShapes.get(i+2);
			Location offsetVertex = 
					Geo.rightOffsetVertex(g1.getLocation(),  
							g2.getLocation(),  
							g3.getLocation(), 
							offsetDistance);
			offsetLocations.add(offsetVertex);
		}
		
		// Deal with the last location specially since it isn't
		gtfsShape0 = gtfsShapes.get(gtfsShapes.size()-2);
		gtfsShape1 = gtfsShapes.get(gtfsShapes.size()-1);
		Location endOffsetLoc = 
				Geo.rightOffsetEndLoc(gtfsShape0.getLocation(), 
						gtfsShape1.getLocation(), 
						offsetDistance);
		offsetLocations.add(endOffsetLoc);	
		
		// Return the resulting offset stopPaths
		return offsetLocations;
	}

	private static class BestMatch {
		int shapeIndex;
		double stopToShapeDistance;
		double distanceAlongShape;
		Location matchLocation;
		double distanceAlongPattern;
		@Override
		public String toString() {
			return "BestMatch [shapeIndex=" + shapeIndex + ", stopToShapeDistance=" + stopToShapeDistance
					+ ", distanceAlongShape=" + distanceAlongShape + ", matchLocation=" + matchLocation
					+ ", distanceAlongPattern=" + distanceAlongPattern + "]";
		}
		

	}
	/**
	 * Determines and returns the best match of the stop to a shape.
	 * 
	 * @param tripPattern
	 * @param stopIndex
	 * @param previousShapeIndex
	 * @param previousDistanceAlongShape
	 * @param shapeLocs
	 * @return The BestMatch indicating best match of stop to shape.
	 */
	private BestMatch determineBestMatch(TripPattern tripPattern,
			int stopIndex, int previousShapeIndex,
			double previousDistanceAlongShape, List<Location> shapeLocs,double previousDistanceAlongPattern) {
		// Value to be returned
		BestMatch bestMatch = null;
		
		// Determine the stop for the trip pattern
		String stopId = tripPattern.getStopId(stopIndex);
		Stop stop = stopsMap.get(stopId);
		StopPath stopPath= tripPattern.getStopPath(stopIndex);
		//if(stopId.startsWith("GLN") )
		//	System.out.println("AQUI COMINEZA "+stopId);
		// Determine the previous stop for the trip pattern (can be null)
		Stop previousStop = null;
		if (stopIndex > 0) {
			String previousStopId = tripPattern.getStopId(stopIndex-1);
			previousStop = stopsMap.get(previousStopId);
		}
		
		// Determine distance between stops so can figure when have
		// looked far enough along shapes. If first stop then use MAX_VALUE 
		// so that look through entire shape. This is needed because sometimes,
		// such as when have short trip pattern that is the end part of a 
		// long shape, the shapes include really long path before the first 
		// stop. This is somewhat frequently done by agencies so that they
		// can define a single shape for all trip patterns for a direction.
		double distanceBetweenStopsAsCrowFlies = 
				previousStop==null ? 
						Double.MAX_VALUE :
						(new Vector(previousStop.getLoc(), stop.getLoc())).length();

		// There are shapes defined from shapes.txt so use them.
		// For each shape see if it is the best match for the current stop
		double bestStopToShapeDistance = Double.MAX_VALUE;
		// distanceAlongShapesExamined is for determining when have looked
		// at enough shapes. Need to start at -previousDistanceAlongShape so
		// when add in length of current vector it indeed shows how
		// far along shapes been looking to match current stop.
		double distanceAlongShapesExamined = -previousDistanceAlongShape;
		double dististanceOverPattern = previousDistanceAlongPattern;
		for (int shapeIndex = previousShapeIndex; 
			 shapeIndex < shapeLocs.size()-1; 
			 ++shapeIndex) {
			// Determine Vector that connects the points for this shape 
			Location loc0 = shapeLocs.get(shapeIndex);	
			Location loc1 = shapeLocs.get(shapeIndex+1);
			Vector shapeVector = new Vector(loc0, loc1);
			
			
			dististanceOverPattern+=shapeVector.length();
			//This is the same segment than the previous one
			//so we should only add the part form the last part
			if(previousShapeIndex==shapeIndex)
			{
				dististanceOverPattern-=previousDistanceAlongShape;
			}
			
			
//			stop.get
			// Determine distance of stop to the current shape
			double stopToShapeDistance = stop.getLoc().distance(shapeVector);
			// If this is the best fit so far, but 
			// If this is the best fit so far, remember such.
			// The 0.0001 is to make sure that don't think found 
			// a better match when actually it is the same, but
			// just looks better due to rounding error.
			if (stopToShapeDistance < bestStopToShapeDistance - 0.0001) {
				// Need to avoid special case where a shape loops back to the
				// first stop, as happens with no schedule assignments. Might
				// have a slightly better spatial match to the end of the 
				// shapes but it should not be considered a better match. So
				// If looking at first stop and the stopToShapeDistance is
				// not all that much better (less than 50m better) then don't
				// consider this a better match since most likely the shape has
				// just looped back to the beginning.
				boolean specialLoopBackToBeginningCase =
						bestMatch != null
								&& stopIndex == 0
								&& stopToShapeDistance > bestStopToShapeDistance - 50.0 && !disableSpecialLoopBackToBeginningCase;
				if (!specialLoopBackToBeginningCase) {
					// Remember best distance so far
					bestStopToShapeDistance = stopToShapeDistance;
					// Remember the best match so it can be returned
					bestMatch = new BestMatch();
					bestMatch.distanceAlongShape =
							stop.getLoc().matchDistanceAlongVector(shapeVector);
					bestMatch.stopToShapeDistance = stopToShapeDistance;
					bestMatch.shapeIndex = shapeIndex;
					bestMatch.matchLocation =
							shapeVector
									.locAlongVector(bestMatch.distanceAlongShape);
					
					bestMatch.distanceAlongPattern=dististanceOverPattern-(shapeVector.length()-bestMatch.distanceAlongShape);
				}
			}
			if(stopPath.getShapeDistanceTraveled()!=null && bestMatch!=null)
			{
				
				if(Math.abs(bestMatch.distanceAlongPattern-stopPath.getShapeDistanceTraveled())<10)
					break;
			}
			// Keep track of how far along the shapes have examined. If
			// have looked for much further than the distance between the
			// stops then have looked far enough. Don't want to look too
			// far ahead because routes do weird things like loop back and 
			// such and we don't want to find a closer but inappropriate
			// match that is further along the route. Since sometimes stops
			// might be really close together should add in 600.0m just to 
			// be safe. 
			// Note: the sfmta inbound 38-Geary from Ft Miley is a special case
			// where the first stops are just 92m apart as the crow flies but 
			// the distance along the shape is 760m. Therefore need to be 
			// pretty generous to correctly find the 43rd Ave & Clement stop.
			distanceAlongShapesExamined += shapeVector.length();
			if (distanceAlongShapesExamined >(maxDistanceBetweenStops<600.0?3.0 * distanceBetweenStopsAsCrowFlies + 600.0:maxDistanceBetweenStops))
				break;				
		} // End of for each shape (finding best match)
		
		// Now have the best match of the stop to a shape.
		// If stop really far away from path log an warning
		// but still use the match. Logging a warning instead
		// of an error because the system will still try to
		// create appropriate path by using the stop location.
		// This means that most of the time this will be adequate
		// so not a fatal error.
		if (bestStopToShapeDistance > maxStopToPathDistance) {
			if (bestStopToShapeDistance < 250.0) {
				logger.warn("Stop {} (stop_id={}) at lat={} lon={} for " + 
						"route_id={} route_short_name={} "
						+ "stop_sequence={} is located {} away " +
						"from the shapes for shape_id={}. This is " +
						"further than allowed distance of {} and means " +
						"that either the location of the stop needs to " +
						"be improved, the stop is out of sequence, the " +
						"stop should not be included for the trips in " +
						"stop_times.txt, or the shape needs to be " +
						"improved. The stop path will be modified to go "
						+ "through the stop, which can make the stop path "
						+ "looked jagged and incorrect. {}",
						stop.getName(), 
						stop.getId(),  
						Geo.format(stop.getLoc().getLat()), 
						Geo.format(stop.getLoc().getLon()),
						tripPattern.getRouteId(), 
						tripPattern.getRouteShortName(),
						stopIndex+1,
						Geo.distanceFormat(bestStopToShapeDistance),
						tripPattern.getShapeId(),
						Geo.distanceFormat(maxStopToPathDistance),
						tripPattern.toStringListingTripIds() );
			} else {
				// Really far off so mark it as an error and use a stronger 
				// message
				logger.error("Stop {} (stop_id={}) at lat={} lon={} for " + 
						"route_id={} route_short_name={} "
						+ "stop_sequence={} is located {} away " +
						"from the shapes for shape_id={}. This is MUCH " +
						"further than allowed distance of {} and means " +
						"that either the location of the stop needs to " +
						"be improved, the stop is out of sequence, the " +
						"stop should not be included for the trips in " +
						"stop_times.txt, or the shape needs to be " +
						"improved. The stop path will be modified to go "
						+ "through the stop, which can make the stop path "
						+ "looked jagged and incorrect. {}",
						stop.getName(), 
						stop.getId(),  
						Geo.format(stop.getLoc().getLat()), 
						Geo.format(stop.getLoc().getLon()),
						tripPattern.getRouteId(),  
						tripPattern.getRouteShortName(),
						stopIndex+1,
						Geo.distanceFormat(bestStopToShapeDistance),
						tripPattern.getShapeId(),
						Geo.distanceFormat(maxStopToPathDistance),
						tripPattern.toStringListingTripIds() );
			}
		}
		logger.debug(" bestMatch {} expected distanceAlongPattern {}",bestMatch,stopPath.getShapeDistanceTraveled());
		System.out.println("bestMatch "  +bestMatch+" "+stopPath.getShapeDistanceTraveled());
		// Return results
		return bestMatch;
	}
	
	/**
	 * For the trip pattern, goes through the stops and matches them to the
	 * shapes from the shapes.txt file. StopPath segments are created for each
	 * stop and the TripPattern is updated accordingly.
	 * 
	 * @param shapeLocs
	 *            List of Locations that represent the shapes that matching the
	 *            stops to.
	 * @param stopIdsForTripPattern
	 *            List of IDs of the stops that need to match to shapes.
	 * @param tripPattern
	 *            so can get routeId, tripPatternId, and shapeId when creating
	 *            the actual StopPath objects.
	 */
	private void determinePathSegmentsMatchingStopsToShapes(
			List<Location> shapeLocs, TripPattern tripPattern) {
		int previousShapeIndex = 0;
		// How far into the segment the previous match was
		double previousDistanceAlongShape = 0.0; 
		Location previousLocation = null;
		int numberOfStopsTooFarAway = 0;
		double previousDistanceAlongPattern=0.0;
		// For each stop for the trip pattern...
		for (int stopIndex = 0; 
				stopIndex < tripPattern.getStopPaths().size(); 
				++stopIndex) {
			// Determine which shape the stop matches to
			BestMatch bestMatch = determineBestMatch(tripPattern, stopIndex,
					previousShapeIndex, previousDistanceAlongShape, shapeLocs,previousDistanceAlongPattern);
			// Keep track of how many stops too far away from path so can log
			// the number for the entire system
			if (bestMatch.stopToShapeDistance > maxStopToPathDistance) {
				++numberOfStopsTooFarAway;
			}

			// Determine which stop currently working on
			String stopId = tripPattern.getStopId(stopIndex);
			Stop stop = stopsMap.get(stopId);
			
			// The list of locations defining the segments for the stop path
			ArrayList<Location> locList = new ArrayList<Location>();

			// Determine the locations for the stop path
			if (previousLocation == null 
					&& bestMatch.stopToShapeDistance > maxStopToPathDistance) {
				// First stop of trip and stop is away from path so use
				// the stop location.
				locList.add(stop.getLoc());
				locList.add(stop.getLoc());
			} else if (previousLocation == null 
					&& trimPathBeforeFirstStopOfTrip) {
				// The stop is not too far away from the shapes and should trim
				// the shape before the stop so simply use the best match 
				// location.
				locList.add(bestMatch.matchLocation);
				locList.add(bestMatch.matchLocation);
			} else {
				// Add in shape segments from the last match to the current 
				// match.
				
				// Add the previous location as the beginning of the stop path
				Location beginLoc = previousLocation != null ? 
						previousLocation : shapeLocs.get(0);					
				locList.add(beginLoc);
	
				// Now gather together the segments that go from the  
				// previous path to the current one. 
				// If on different shape segment from previous one...
				if (bestMatch.shapeIndex != previousShapeIndex) {
					// Add the intermediate shapes points
					for (int shapeIndex = previousShapeIndex + 1; 
							shapeIndex <= bestMatch.shapeIndex; 
							++shapeIndex) {
						locList.add(shapeLocs.get(shapeIndex));
					}
				}								

				// Handle the end of the stop path, where the stop is closest to
				// the shape. If the stop was too far away from the shapes then
				// also add the stop location to the location list
				// for the path. This way will have hopefully a
				// reasonable path that goes by the stop even if the
				// traces are not adequate.
				if (bestMatch.stopToShapeDistance > maxStopToPathDistance) {
					locList.add(stop.getLoc());
				} else {
					// The stop is not too far away from the shapes so
					// add the match location as the end of the locList.
					locList.add(bestMatch.matchLocation);
				}
			}
			
			// Remember location for when looking at next shape
			previousLocation = locList.get(locList.size() - 1);
			
			// Filter the segments and then associate them with the path 
			filterShortSegments(locList, maxDistanceForEliminatingVertices);
			StopPath stopPath = tripPattern.getStopPath(stopIndex);
			stopPath.setLocations(locList);
			
			// Prepare for looking at next stop
			previousShapeIndex = bestMatch.shapeIndex;
			previousDistanceAlongShape = bestMatch.distanceAlongShape;
			previousDistanceAlongPattern= bestMatch.distanceAlongPattern;
		} // End of for each stop for the trip pattern		
		
		// If there errors with stops being too far away from the stopPaths then
		// log total number of such errors so person responsible for GTFS
		// can see how much work they have to do.
		if (numberOfStopsTooFarAway > 0) {
			logger.warn("Found {} stop{} that {} further than the allowable " +
					"distance of {}m from the shape for shape_id={}", 
					numberOfStopsTooFarAway, 
					numberOfStopsTooFarAway == 1 ? "" : "s",
					numberOfStopsTooFarAway == 1 ? "is" : "are",
					maxStopToPathDistance, 
					tripPattern.getShapeId());
		}
	}
	
	/**
	 * Goes through the locations for the StopPath and filters them to make sure
	 * that they are not too short. Don't want lots of tiny segments for curves
	 * cluttering up the database and mucking travel times more complicated.
	 * 
	 * @param locations
	 *            the locations that are to be filtered
	 * @param maxDistanceForEliminatingVertices
	 *            If a vertex is off by less this distance from the line
	 *            connecting the previous and next vertex then this one can be
	 *            filtered out.
	 */
	private void filterShortSegments(List<Location> locations, 
			double maxDistanceForEliminatingVertices) {	
		// Combine short stopPaths that don't include significant distance
		// orthogonal to travel. Ideally would like to look at just two
		// line segments at a time and filter out those whose vertex is
		// not significantly out of line. But if look at just two segments
		// at a time then could shave a curve and then shave it some more
		// and some more since each two segments wouldn't make much of
		// a difference. But this could lead to some vertexes being taken
		// out even though they would be greater than the max distance
		// away. So instead repeatedly go through the segments
		// to find out which ones can be taken out without any vertex 
		// ending up too far away from the resulting segments.
		for (int beginIndex = 0; beginIndex <locations.size()-2; ++beginIndex) {
			for (int endIndex = beginIndex+2; endIndex < locations.size(); ++endIndex) {
				// Determine the new possible vector that might be able to eliminate
				// some vertices without violating the max distance.
				Vector possibleNewVector = 
						new Vector(locations.get(beginIndex), locations.get(endIndex));
				
				// Check each vertex spanned by the possibleNewVector to see if any
				// would end up being to far away and violating the max distance. Also
				// check to see if checked all vertices and they are OK. Either way
				// need to erase some vertices.
				for (int vertexIndex=beginIndex+1; vertexIndex < endIndex; ++vertexIndex) {
					Location vertex = locations.get(vertexIndex);
					double distanceOfVertexToNewVector = possibleNewVector.distance(vertex);
					// If need to erase vertices...
					if (distanceOfVertexToNewVector >= maxDistanceForEliminatingVertices ||
							vertexIndex == locations.size() - 2) {
						// Things bit complicated here because can be here for two
						// different reasons: 1) because distance violated, meaning
						// have to remove previous vertices; or 2) distance not 
						// violated but got to end locations so need to erase some.
						// The difference is with the last vertex that should be
						// erased.
						int indexToEraseUpTo;
						if (distanceOfVertexToNewVector >= maxDistanceForEliminatingVertices)
							// Current vector doesn't work but previous one might. Therefore
							// back up 2.
							indexToEraseUpTo = endIndex - 2;
						else
							// At end of vector so can erase to end.
							indexToEraseUpTo = locations.size() - 2;
						
						// The current possibleNewVector wouldn't work because then the
						// current vertex would be too far away. But this means that
						// can eliminate previous vertices that were within range.
						for (int eraseIndex = beginIndex + 1; eraseIndex <= indexToEraseUpTo; ++eraseIndex) {
							logger.debug("Removing vertex #{} {}",
									eraseIndex,	locations.get(beginIndex + 1));

							// Erase the vertex. Since all subsequent elements in 
							// locations array then shift over should keep removing
							// the element beginIndex + 1.
							locations.remove(beginIndex + 1);							
						}
						
						// Continue in outer loop by updating endIndex so the endIndex for loop
						// will exit and then breaking out of the inner vertexIndex for loop.
						endIndex = locations.size();
						break;
					}
				}
			}
		}		
	}

	/**
	 * Determines the path segments for each trip pattern. If shapes.txt
	 * GTFS file has a shape for the trip pattern then that data is used. 
	 * Otherwise will simply connect the stops with straight line stopPaths.
	 * 
	 * When shapes.txt data is used a lot of processing is done. The
	 * path segments are offset orthogonally by the offsetDistance which 
	 * is useful if the shapes.txt info is simply street centerline
	 * data. By offsetting the path segments for the different directions won't
	 * completely overlap on the map. Then the stops are matched to the 
	 * shapes in order to create the Paths. Each path is for a separate
	 * stop and the path ends at the stop. The path segments are also
	 * filtered so that segments that are too short or too long are 
	 * adjusted.
	 */
	public void processPathSegments() {
		// For logging how long things take
		IntervalTimer timer = new IntervalTimer();

		// Let user know what is going on
		logger.info("Processing and filtering path segment data...");
		
		// Need to process stopPaths for every trip pattern...
		for (TripPattern tripPattern : tripPatterns) {
			// Determine the GtfsShape associated with the TripPattern
			String shapeId = tripPattern.getShapeId();
			List<GtfsShape> gtfsShapesForTripPattern = gtfsShapesMap.get(shapeId);
			
			// If no shape defined then simply connect the stops
			if (gtfsShapesForTripPattern == null) {
				// Create stopPaths by connecting the stops
				connectStopsSinceNoShapes(tripPattern);
			} else {
				// Determine list of shapes associated with the trip pattern.
				// The stopPaths are offset to the right by the offsetDistance
				// if needed. This is useful if the shapes.txt data is street
				// centerline data.
				List<Location> offsetLocations = 
						getOffsetLocations(gtfsShapesMap.get(shapeId));
						
				// Create stopPaths by finding best match to shapes
				determinePathSegmentsMatchingStopsToShapes(offsetLocations, 
						tripPattern);
			}
		}
		
		// Let user know what is going on
		logger.info("Finished processing and filtering path segment data. " +
				"Took {} msec.",
				timer.elapsedMsec());		
	}
	
}
