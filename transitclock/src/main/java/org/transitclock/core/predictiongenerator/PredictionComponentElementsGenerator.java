package org.transitclock.core.predictiongenerator;


import org.transitclock.core.HeadwayDetails;
import org.transitclock.core.Indices;
import org.transitclock.core.RouteMatch;
import org.transitclock.core.VehicleState;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.ipc.data.IpcPrediction;

public interface PredictionComponentElementsGenerator {
	/* this generates a prediction for travel time between stops */
	long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState);
	
	long getStopTimeForPath(Indices indices,  AvlReport avlReport, VehicleState vehicleState);
	
	long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, RouteMatch match);
		
}