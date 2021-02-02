package org.transitclock.core.predictiongenerator.scheduled.traveltime.kalman;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.DoubleConfigValue;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.core.HeadwayDetails;
import org.transitclock.core.Indices;
import org.transitclock.core.PredictionGeneratorDefaultImpl;
import org.transitclock.core.RouteMatch;
import org.transitclock.core.SpatialMatch;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TravelTimeDetails;
import org.transitclock.core.VehicleState;
import org.transitclock.core.dataCache.ErrorCache;
import org.transitclock.core.dataCache.ErrorCacheFactory;
import org.transitclock.core.dataCache.KalmanError;
import org.transitclock.core.dataCache.KalmanErrorCacheKey;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.TripDataHistoryCacheFactory;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.VehicleStateManager;
import org.transitclock.core.dataCache.ehcache.scheduled.TripDataHistoryCache;
import org.transitclock.core.dataCache.jcs.KalmanErrorCache;
import org.transitclock.core.predictiongenerator.PredictionComponentElementsGenerator;
import org.transitclock.core.predictiongenerator.kalman.KalmanPrediction;
import org.transitclock.core.predictiongenerator.kalman.KalmanPredictionResult;
import org.transitclock.core.predictiongenerator.kalman.TripSegment;
import org.transitclock.core.predictiongenerator.kalman.Vehicle;
import org.transitclock.core.predictiongenerator.kalman.VehicleStopDetail;
import org.transitclock.core.predictiongenerator.scheduled.average.HistoricalAveragePredictionGeneratorImpl;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.PredictionEvent;
import org.transitclock.db.structs.PredictionForStopPath;
import org.transitclock.db.structs.VehicleEvent;
import org.transitclock.ipc.data.IpcPrediction;

/**
 * @author Sean Óg Crudden This is a prediction generator that uses a Kalman
 *         filter to provide predictions. It uses historical average while waiting on enough data to support a Kalman filter.
 */
public class KalmanPredictionGeneratorImpl extends PredictionGeneratorDefaultImpl
		implements PredictionComponentElementsGenerator {
	
	private String alternative="PredictionGeneratorDefaultImpl";

	/*
	 * TODO I think this needs to be a minimum of three and if just two will use
	 * historical value.
	 */
	private static final IntegerConfigValue minKalmanDays = new IntegerConfigValue(
			"transitclock.prediction.data.kalman.mindays", new Integer(3),
			"Min number of days trip data that needs to be available before Kalman prediciton is used instead of default transiTime prediction.");

	private static final IntegerConfigValue maxKalmanDays = new IntegerConfigValue(
			"transitclock.prediction.data.kalman.maxdays", new Integer(3),
			"Max number of historical days trips to include in Kalman prediction calculation.");

	private static final IntegerConfigValue maxKalmanDaysToSearch = new IntegerConfigValue(
			"transitclock.prediction.data.kalman.maxdaystoseach", new Integer(21),
			"Max number of days to look back for data. This will also be effected by how old the data in the cache is.");

	private static final DoubleConfigValue initialErrorValue = new DoubleConfigValue(
			"transitclock.prediction.data.kalman.initialerrorvalue", new Double(100),
			"Initial Kalman error value to use to start filter.");
	
	/* May be better to use the default implementation as it splits things down into segments. */
	private static final BooleanConfigValue useKalmanForPartialStopPaths = new BooleanConfigValue (
			"transitclock.prediction.data.kalman.usekalmanforpartialstoppaths", new Boolean(true), 
			"Will use Kalman prediction to get to first stop of prediction."
	);
			
	private static final IntegerConfigValue percentagePredictionMethodDifferenceneEventLog=new IntegerConfigValue(
			"transitclock.prediction.data.kalman.percentagePredictionMethodDifferencene", new Integer(50),
			"If the difference in prediction method estimates is greater than this percentage log a Vehicle Event");
	
	private static final IntegerConfigValue tresholdForDifferenceEventLog=new IntegerConfigValue(
			"transitclock.prediction.data.kalman.tresholdForDifferenceEventLog", new Integer(60000),
			"This is the threshold in milliseconds that the difference has to be over before it will consider the percentage difference.");
				
	private static final Logger logger = LoggerFactory.getLogger(KalmanPredictionGeneratorImpl.class);

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.transitclock.core.PredictionGeneratorDefaultImpl#getTravelTimeForPath
	 * (org.transitclock.core.Indices, org.transitclock.db.structs.AvlReport)
	 */
	@Override
	public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState) {

		logger.debug("Calling Kalman prediction algorithm for : "+indices.toString());

		long alternatePrediction = super.getTravelTimeForPath(indices, avlReport, vehicleState);
		
		
		TripDataHistoryCacheInterface tripCache = TripDataHistoryCacheFactory.getInstance();

		ErrorCache kalmanErrorCache = ErrorCacheFactory.getInstance();

		VehicleStateManager vehicleStateManager = VehicleStateManager.getInstance();

		VehicleState currentVehicleState = vehicleStateManager.getVehicleState(avlReport.getVehicleId());

		try {
			TravelTimeDetails travelTimeDetails = this.getLastVehicleTravelTime(currentVehicleState, indices);


			/*
			 * The first vehicle of the day should use schedule or historic data to
			 * make prediction. Cannot use Kalman as yesterdays vehicle will have
			 * little to say about todays.
			 */
			if (travelTimeDetails!=null) {

				logger.debug("Kalman has last vehicle info for : " +indices.toString()+ " : "+travelTimeDetails);

				Date nearestDay = DateUtils.truncate(avlReport.getDate(), Calendar.DAY_OF_MONTH);

				List<TravelTimeDetails> lastDaysTimes = lastDaysTimes(tripCache, currentVehicleState.getTrip().getId(),currentVehicleState.getTrip().getDirectionId(),
						indices.getStopPathIndex(), nearestDay, currentVehicleState.getTrip().getStartTime(),
						maxKalmanDaysToSearch.getValue(), maxKalmanDays.getValue());

				if(lastDaysTimes!=null)
				{
					logger.debug("Kalman has " +lastDaysTimes.size()+ " historical values for : " +indices.toString());
				}
				/*
				 * if we have enough data start using Kalman filter otherwise revert
				 * to extended class for prediction.
				 */
				if (lastDaysTimes != null && lastDaysTimes.size() >= minKalmanDays.getValue().intValue()) {

					logger.debug("Generating Kalman prediction for : "+indices.toString());

					try {

						KalmanPrediction kalmanPrediction = new KalmanPrediction();

						KalmanPredictionResult kalmanPredictionResult;

						Vehicle vehicle = new Vehicle(avlReport.getVehicleId());

						VehicleStopDetail originDetail = new VehicleStopDetail(null, 0, vehicle);
						TripSegment[] historical_segments_k = new TripSegment[lastDaysTimes.size()];
						for (int i = 0; i < lastDaysTimes.size() && i < maxKalmanDays.getValue(); i++) {

							logger.debug("Kalman is using historical value : "+lastDaysTimes.get(i) +" for : " + indices.toString());

							VehicleStopDetail destinationDetail = new VehicleStopDetail(null, lastDaysTimes.get(i).getTravelTime(),
									vehicle);
							historical_segments_k[lastDaysTimes.size()-i-1] = new TripSegment(originDetail, destinationDetail);
						}

						VehicleStopDetail destinationDetail_0_k_1 = new VehicleStopDetail(null, travelTimeDetails.getTravelTime(), vehicle);

						TripSegment ts_day_0_k_1 = new TripSegment(originDetail, destinationDetail_0_k_1);

						TripSegment last_vehicle_segment = ts_day_0_k_1;

						Indices previousVehicleIndices = new Indices(travelTimeDetails.getArrival());

						KalmanError last_prediction_error = lastVehiclePredictionError(kalmanErrorCache, previousVehicleIndices);

						logger.debug("Using error value: " + last_prediction_error +" found with vehicle id "+travelTimeDetails.getArrival().getVehicleId()+ " from: "+new KalmanErrorCacheKey(previousVehicleIndices).toString());

						//TODO this should also display the detail of which vehicle it choose as the last one.
						logger.debug("Using last vehicle value: " + travelTimeDetails + " for : "+ indices.toString());

						kalmanPredictionResult = kalmanPrediction.predict(last_vehicle_segment, historical_segments_k,
								last_prediction_error.getError());

						long predictionTime = (long) kalmanPredictionResult.getResult();

						logger.debug("Setting Kalman error value: " + kalmanPredictionResult.getFilterError() + " for : "+ new KalmanErrorCacheKey(indices).toString());

						kalmanErrorCache.putErrorValue(indices, kalmanPredictionResult.getFilterError());
						
						double percentageDifferecence = Math.abs(100 * ((predictionTime - alternatePrediction) / (double)alternatePrediction));
						
						if(((percentageDifferecence *  alternatePrediction)/100) > tresholdForDifferenceEventLog.getValue())
						{						
							if(percentageDifferecence > percentagePredictionMethodDifferenceneEventLog.getValue())
							{
								String description="Kalman predicts : "+predictionTime+" Super predicts : "+alternatePrediction;

								logger.warn(description);
								
								PredictionEvent.create(avlReport, vehicleState.getMatch(), PredictionEvent.PREDICTION_VARIATION, description, 
										travelTimeDetails.getArrival().getStopId(), 
										travelTimeDetails.getDeparture().getStopId(),
										travelTimeDetails.getArrival().getVehicleId(),
										travelTimeDetails.getArrival().getTime(),
										travelTimeDetails.getDeparture().getTime());
							}
						}

						logger.debug("Using Kalman prediction: " + predictionTime + " instead of "+alternative+" prediction: "
								+ alternatePrediction +" for : " + indices.toString());

						if(storeTravelTimeStopPathPredictions.getValue())
						{
							PredictionForStopPath predictionForStopPath=new PredictionForStopPath(vehicleState.getVehicleId(), new Date(Core.getInstance().getSystemTime()), new Double(new Long(predictionTime).intValue()), indices.getTrip().getId(), indices.getStopPathIndex(), "KALMAN", true, null);
							Core.getInstance().getDbLogger().add(predictionForStopPath);
							StopPathPredictionCache.getInstance().putPrediction(predictionForStopPath);
						}
						return predictionTime;

					} catch (Exception e) {
						logger.error(e.getMessage(), e);
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return alternatePrediction;
	}

	@Override
	public long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, RouteMatch match) {
		
		if(useKalmanForPartialStopPaths.getValue().booleanValue())
		{		
			VehicleStateManager vehicleStateManager = VehicleStateManager.getInstance();
	
			VehicleState currentVehicleState = vehicleStateManager.getVehicleState(avlReport.getVehicleId());	
			
			long fulltime = this.getTravelTimeForPath(match.getIndices(), avlReport, currentVehicleState);
			
			double distanceAlongStopPath = match.getDistanceAlongStopPath();
			
			double stopPathLength =
					match.getStopPath().getLength();
			
			long remainingtime = (long) (fulltime * ((stopPathLength-distanceAlongStopPath)/stopPathLength));
			
			logger.debug("Using Kalman for first stop path {} with value {} instead of {}.", match.getIndices(), remainingtime, super.expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match));
					
			return remainingtime;
		}else
		{
			return super.expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match);
		}
	}

	private KalmanError lastVehiclePredictionError(ErrorCache cache, Indices indices) {

		KalmanError result;
		try {
			result = cache.getErrorValue(indices);
			if(result==null)
			{		
				logger.debug("Kalman Error value set to default: "+initialErrorValue.getValue() +" for key: "+new KalmanErrorCacheKey(indices).toString());
				result=new KalmanError(initialErrorValue.getValue());
			}
			return result;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleState vehicleState) {
		long result=super.getStopTimeForPath(indices, avlReport, vehicleState);
		
		return result;
		
	}
}
