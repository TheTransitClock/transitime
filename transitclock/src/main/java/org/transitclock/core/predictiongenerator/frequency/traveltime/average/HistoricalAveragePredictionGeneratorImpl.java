package org.transitclock.core.predictiongenerator.frequency.traveltime.average;

import java.util.Calendar;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.core.Indices;
import org.transitclock.core.PredictionGeneratorDefaultImpl;
import org.transitclock.core.RouteMatch;
import org.transitclock.core.VehicleState;
import org.transitclock.core.dataCache.HistoricalAverage;
import org.transitclock.core.dataCache.StopPathCacheKey;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.VehicleStateManager;
import org.transitclock.core.dataCache.frequency.FrequencyBasedHistoricalAverageCache;
import org.transitclock.core.dataCache.scheduled.ScheduleBasedHistoricalAverageCache;
import org.transitclock.core.predictiongenerator.PredictionComponentElementsGenerator;
import org.transitclock.core.predictiongenerator.lastvehicle.LastVehiclePredictionGeneratorImpl;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.PredictionForStopPath;
import org.transitclock.ipc.data.IpcPrediction;

/**
 * @author Sean Óg Crudden
 *	This provides a prediction based on the average of historical data for frequency based services.
 *	The average will be based on a trip id and a start time. The average will be based on time segments and previous trips during this time segment on previous days.. Each segment will be the duration of a single trip. 
 *
 */
public class HistoricalAveragePredictionGeneratorImpl extends
LastVehiclePredictionGeneratorImpl implements PredictionComponentElementsGenerator {
	private String alternative="LastVehiclePredictionGeneratorImpl";
	
	private static final IntegerConfigValue minDays = new IntegerConfigValue(
			"transitclock.prediction.data.average.mindays",
			new Integer(1),
			"Min number of days trip data that needs to be available before historical average prediciton is used instead of default transiTime prediction.");
	
	
	private static final Logger logger = LoggerFactory
			.getLogger(HistoricalAveragePredictionGeneratorImpl.class);

	/* (non-Javadoc)
	 * @see org.transitclock.core.predictiongenerator.KalmanPredictionGeneratorImpl#getTravelTimeForPath(org.transitclock.core.Indices, org.transitclock.db.structs.AvlReport)
	 */
	@Override
	public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState) {
					
		/*
		 * if we have enough data start using historical average otherwise
		 * revert to default. This does not mean that this method of
		 * prediction is better than the default.
		 */				
		if(vehicleState.getTripStartTime(vehicleState.getTripCounter())!=null)
		{																			
									
			Integer time=FrequencyBasedHistoricalAverageCache.secondsFromMidnight(avlReport.getDate(),2);
			
			/* this is what gets the trip from the buckets */
			time=FrequencyBasedHistoricalAverageCache.round(time, FrequencyBasedHistoricalAverageCache.getCacheIncrementsForFrequencyService());
			
			StopPathCacheKey historicalAverageCacheKey=new StopPathCacheKey(indices.getTrip().getId(), indices.getStopPathIndex(), true, time.longValue());
			
			HistoricalAverage average = FrequencyBasedHistoricalAverageCache.getInstance().getAverage(historicalAverageCacheKey);
			
			if(average!=null && average.getCount()>=minDays.getValue())
			{
				if(storeTravelTimeStopPathPredictions.getValue())
				{
					PredictionForStopPath predictionForStopPath=new PredictionForStopPath(vehicleState.getVehicleId(), new Date(Core.getInstance().getSystemTime()), average.getAverage(), indices.getTrip().getId(), indices.getStopPathIndex(), "HISTORICAL AVERAGE", true, time);			
					Core.getInstance().getDbLogger().add(predictionForStopPath);
					StopPathPredictionCache.getInstance().putPrediction(predictionForStopPath);
				}
				
				logger.debug("Using historical average algorithm for prediction : " +average.toString() + " for : " + indices.toString());
				//logger.debug("Instead of transitime value : " + super.getTravelTimeForPath(indices, avlReport));
				return (long)average.getAverage();
			}
		}
		//logger.debug("No historical average found, generating prediction using lastvehicle algorithm: " + historicalAverageCacheKey.toString());
		/* default to parent method if not enough data. This will be based on schedule if UpdateTravelTimes has not been called. */
		return super.getTravelTimeForPath(indices, avlReport,vehicleState);
	}	

	@Override
	public long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport,RouteMatch match) {
				
		Indices indices = match.getIndices();
		Integer time=FrequencyBasedHistoricalAverageCache.secondsFromMidnight(new Date(match.getAvlTime()),2);
		
		/* this is what gets the trip from the buckets */
		time=FrequencyBasedHistoricalAverageCache.round(time, FrequencyBasedHistoricalAverageCache.getCacheIncrementsForFrequencyService());
		
		StopPathCacheKey historicalAverageCacheKey=new StopPathCacheKey(indices.getTrip().getId(), indices.getStopPathIndex(), true, time.longValue());
		
		HistoricalAverage average = FrequencyBasedHistoricalAverageCache.getInstance().getAverage(historicalAverageCacheKey);
		
		if(average!=null && average.getCount()>=minDays.getValue())
		{	
			double fractionofstoppathlefttotravel=(match.getStopPath().getLength()-match.getDistanceAlongStopPath())/match.getStopPath().getLength();
			double value = (double)(average.getAverage() * fractionofstoppathlefttotravel);
			if(storeTravelTimeStopPathPredictions.getValue())
			{
				PredictionForStopPath predictionForStopPath=new PredictionForStopPath(avlReport.getVehicleId(), new Date(Core.getInstance().getSystemTime()), value, indices.getTrip().getId(), indices.getStopPathIndex(), "PARTIAL HISTORICAL AVERAGE", true, time);			
				Core.getInstance().getDbLogger().add(predictionForStopPath);
				StopPathPredictionCache.getInstance().putPrediction(predictionForStopPath);
			}
			return (long)value;
		}else
		{
			return super.expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match);
		}
	}

	@Override
	public long getStopTimeForPath(Indices indices,  AvlReport avlReport, VehicleState vehicleState) {
		
		if(vehicleState.getTripStartTime(vehicleState.getTripCounter())!=null)
		{			
			Integer time=FrequencyBasedHistoricalAverageCache.secondsFromMidnight(avlReport.getDate(),2);
			
			/* this is what gets the trip from the buckets */
			time=FrequencyBasedHistoricalAverageCache.round(time, FrequencyBasedHistoricalAverageCache.getCacheIncrementsForFrequencyService());
			
			StopPathCacheKey historicalAverageCacheKey=new StopPathCacheKey(indices.getTrip().getId(), indices.getStopPathIndex(), false, time.longValue());
			
			HistoricalAverage average = FrequencyBasedHistoricalAverageCache.getInstance().getAverage(historicalAverageCacheKey);
			
			if(average!=null && average.getCount()>=minDays.getValue())
			{				
				if(storeDwellTimeStopPathPredictions.getValue())
				{
					PredictionForStopPath predictionForStopPath=new PredictionForStopPath(vehicleState.getVehicleId(), new Date(Core.getInstance().getSystemTime()), average.getAverage(), indices.getTrip().getId(), indices.getStopPathIndex(), "HISTORICAL AVERAGE", false, time);			
					Core.getInstance().getDbLogger().add(predictionForStopPath);
					StopPathPredictionCache.getInstance().putPrediction(predictionForStopPath);
				}
				
				logger.debug("Using historical average alogrithm for dwell time : "+average.toString() + " for : " + indices.toString());
				return (long)average.getAverage();
			}
		}			
		return super.getStopTimeForPath(indices,  avlReport, vehicleState);
	}
}
