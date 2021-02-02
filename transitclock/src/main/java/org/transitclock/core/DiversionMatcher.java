package org.transitclock.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.transitclock.applications.Core;
import org.transitclock.configData.CoreConfig;
import org.transitclock.core.diversion.cache.DiversionsCacheFactory;
import org.transitclock.core.diversion.cache.DiversionsKey;
import org.transitclock.core.diversion.cache.DiversionsList;
import org.transitclock.core.diversion.model.Diversion;
import org.transitclock.core.diversion.model.DiversionStopPath;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.VectorWithHeading;

/**
 * @author scrudden This is to find possible matches to a diversion.
 *
 */

public class DiversionMatcher {

	public static List<DiversionMatch> getDiversionMatches(VehicleState vehicleState) {
		AvlReport avlReport = vehicleState.getAvlReport();
		// The matches to be returned
		List<DiversionMatch> diversionMatches = new ArrayList<DiversionMatch>();

		String tripId = vehicleState.getTrip().getId();
		String routeId = vehicleState.getRouteId();

		DiversionsKey key = new DiversionsKey(tripId, routeId);

		if (DiversionsCacheFactory.getInstance() != null) {
			DiversionsList diversions = DiversionsCacheFactory.getInstance().getDiversions(key);

			if (diversions != null && diversions.getDiversions() != null) {
				for (Diversion diversion : diversions.getDiversions()) {
					/* Check if diversion applies to this trip. */
					if (diversion.getTripId().equals(vehicleState.getTrip().getId())) {

						/*
						 * Check if diversion is currently in place. Null start and end time means in
						 * place all the time.
						 */
						if ((diversion.getStartTime() == null && diversion.getEndTime() == null) || (diversion
								.getStartTime().before(new Date(Core.getInstance().getSystemTime()))
								&& diversion.getEndTime().after(new Date(Core.getInstance().getSystemTime())))) {
							Double minDistanceToSegment = null;
							Integer minIndex = null;
							Integer minVectorIndex = null;
							Integer index = 0;
						
							for(DiversionStopPath diversionStopPath:diversion.getDiversionStopPaths())
							{
								Integer vectorIndex=0;
								for (VectorWithHeading vector : diversionStopPath.getVectors()) {
									Double distanceToSegment = vector.distance(avlReport.getLocation());
									if (minDistanceToSegment == null || distanceToSegment < minDistanceToSegment) {
										minDistanceToSegment = distanceToSegment;
										minIndex = index;	
										minVectorIndex = vectorIndex;
									}
									vectorIndex++;
								}
								index++;
							}
							
							Double distanceAlongDiveresion = 0.0;
							ArrayList<DiversionStopPath> diversionStopPaths=diversion.getDiversionStopPaths();
							for(int i=0;i<minIndex;i++)
							{
								for(int j=0;j<minVectorIndex;j++)
								{
									distanceAlongDiveresion=diversionStopPaths.get(i).getVectors().get(j).length();
								}
								
							}
							distanceAlongDiveresion=distanceAlongDiveresion
									+(diversionStopPaths.get(minIndex).getVectors().get(minVectorIndex).length()
											-diversionStopPaths.get(minIndex).getVectors().get(minVectorIndex).matchDistanceAlongVector(avlReport.getLocation()));
	
							// Check if within acceptable range of diversion to match to it.
							if (minDistanceToSegment < CoreConfig.getMaxDistanceFromSegment()) {
								DiversionMatch diversionMatch = new DiversionMatch(diversion, minIndex, minVectorIndex, minDistanceToSegment, distanceAlongDiveresion,
										avlReport.getTime(), vehicleState.getBlock(),
										vehicleState.getTrip().getIndexInBlock(), diversion.getShapeId(),
										diversion.getTripId(), diversion.getRouteId());

								diversionMatches.add(diversionMatch);
							}
						}
					}
				}
			}
		}
		return diversionMatches;
	}

}
