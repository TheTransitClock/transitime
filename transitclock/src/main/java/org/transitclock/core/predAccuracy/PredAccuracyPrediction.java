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

package org.transitclock.core.predAccuracy;

import java.util.Date;

/**
 * For prediction accuracy analysis. For keeping a prediction in memory so
 * that it can be compared to its corresponding arrival/departure to determine
 * the accuracy of the prediction and store that info into the database.
 *
 * @author SkiBu Smith
 *
 */
public class PredAccuracyPrediction {

	private final String routeId;
	private final String directionId;
	private final String stopId;
	// Note: tripId might not always be available from a prediction API
	private final String tripId;
	private final String vehicleId;
	private final Date predictedTime;
	// The time the prediction was read. This allows us to determine
	// how far out into the future the prediction is for.
	private final Date predictionReadTime;
	private final Date avlTime;
	private final String scheduledTime;
	private final boolean isArrival;
	// affectedByWaitStop is a Boolean so that null can represent "don't know"
	private final Boolean affectedByWaitStop;
	private final String source;
	private final String algorithm;
	private final Long totalPredictedDwellTime;
	private final Long totalPredictedTravelTime;
	

	/********************** Member Functions **************************/

	/**
	 * Constructor
	 * 
	 * @param routeId
	 * @param directionId
	 *            Might not always be available from prediction API so can be
	 *            set to null.
	 * @param stopId
	 * @param tripId
	 *            Might not always be available from prediction API so can be
	 *            set to null.
	 * @param vehicleId
	 * @param predictedTime
	 * @param predictionReadTime
	 * @param isArrival
	 * @param affectedByWaitStop
	 *            Set to null if this information not available
	 * @param source
	 *            Description of the feed, especially useful if have couple of
	 *            sources. Can be a value such as "MBTA_epoch" or "NextBus".
	 * @param algorithm 
	 * 			  This is the algorithm used to generate prediction.
	 */
	public PredAccuracyPrediction(String routeId, String directionId,
			String stopId, String tripId, String vehicleId, Date predictedTime, 
			Date predictionReadTime, Date avlTime, boolean isArrival,
			Boolean affectedByWaitStop, String source, String algorithm, String scheduledTime, Long totalPredictedDwellTime, Long totalPredictedTravelTime) {
		super();
		this.routeId = routeId;
		this.directionId = directionId;
		this.stopId = stopId;
		this.tripId = tripId;
		this.vehicleId = vehicleId;
		this.predictedTime = predictedTime;
		this.predictionReadTime = predictionReadTime;
		this.isArrival = isArrival;
		this.affectedByWaitStop = affectedByWaitStop;
		this.source = source;
		this.scheduledTime = scheduledTime;
		this.algorithm = algorithm;
		this.totalPredictedDwellTime=totalPredictedDwellTime;
		this.totalPredictedTravelTime=totalPredictedTravelTime;
		this.avlTime=avlTime;
	}
	
	public String getAlgorithm() {
		return algorithm;
	}

	public String getRouteId() {
		return routeId;
	}
	
	public String getDirectionId() {
		return directionId;
	}
	
	public String getStopId() {
		return stopId;
	}
	
	public String getTripId() {
		return tripId;
	}
	
	public String getVehicleId() {
		return vehicleId;
	}
	
	public Date getPredictedTime() {
		return predictedTime;
	}
	
	/**
	 * The time the prediction was read. This allows us to determine
	 * how far out into the future the prediction is for.
	 * @return
	 */
	public Date getPredictionReadTime() {
		return predictionReadTime;
	}
	
	public String getSource() {
		return source;
	}
	
	public boolean isArrival() {
		return isArrival;
	}
	
	public Boolean isAffectedByWaitStop() {
		return affectedByWaitStop;
	}
	
	
	public Long getTotalPredictedDwellTime() {
		return totalPredictedDwellTime;
	}

	public Long getTotalPredictedTravelTime() {
		return totalPredictedTravelTime;
	}

	public Date getAvlTime() {
		return avlTime;
	}
	
	@Override
	public String toString() {
		return "PredAccuracyPrediction [routeId=" + routeId + ", directionId=" + directionId + ", stopId=" + stopId
				+ ", tripId=" + tripId + ", vehicleId=" + vehicleId + ", predictedTime=" + predictedTime
				+ ", predictionReadTime=" + predictionReadTime + ", avlTime=" + avlTime + ", scheduledTime="
				+ scheduledTime + ", isArrival=" + isArrival + ", affectedByWaitStop=" + affectedByWaitStop
				+ ", source=" + source + ", algorithm=" + algorithm + ", totalPredictedDwellTime="
				+ totalPredictedDwellTime + ", totalPredictedTravelTime=" + totalPredictedTravelTime + "]";
	}

	public String getScheduledTime() {
		return scheduledTime;
	}

	
}
