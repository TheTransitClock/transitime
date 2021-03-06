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
package org.transitclock.ipc.data;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;

import org.transitclock.applications.Core;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.OccupancyStatus;
import org.transitclock.db.structs.Trip;
import org.transitclock.ipc.data.IpcPrediction.ArrivalOrDeparture;
import org.transitclock.utils.StringUtils;
import org.transitclock.utils.Time;

/**
 * Contains information on a single prediction. For providing info to client.
 * <p>
 * Declared serializable since using RMI to pass back Prediction objects and RMI
 * uses serialization.
 * 
 * @author SkiBu Smith
 * 
 */
public class IpcPrediction implements Serializable {

	private final String vehicleId;
	// Ideally routeId and stopId wouldn't need to be here since they are
	// are already in PredictionsForRouteStopDest but for GTFS-realtime feed
	// need to provide all predictions by trip, not by stop. This means that
	// don't have the PredictionsForRouteStopDest. But still need routeId and
	// stopId so they are stored here as well.
	private final String routeId;
	private final String stopId;
	
	private final int gtfsStopSeq;
	private final String tripId;
	private final String tripPatternId;
	private final boolean isTripUnscheduled;
	private final String blockId;
	// The prediction to present to the user. Can be different from
	// actualPredictionTime in that for wait stops might want to show
	// user the scheduled time even if the actual prediction time,
	// including the stop wait time, might be greater.
	private final long predictionTime;
	// The prediction time including all factors, including the stop
	// wait time for wait stops presented to the user is different
	// than the actual prediction time. Only used on server side when
	// calculating predictions.
	private final long actualPredictionTime;
	// True if prediction for last stop of trip
	private final boolean atEndOfTrip;
	private final boolean schedBasedPred;
	// The time of the fix so can tell how stale prediction is
	private final long avlTime;
	// The time the AVL data was processed and the prediction was created.
	private final long creationTime;
	private final long tripStartEpochTime;
	private final boolean affectedByWaitStop;
	private final String driverId;
	private final short passengerCount;
	private final float passengerFullness;
	private final boolean isDelayed;
	private final boolean lateAndSubsequentTripSoMarkAsUncertain;
	private final boolean isArrival;
	private final Integer delay;
	private boolean isCanceled;
	private final IpcOccupancyStatus occupancyStatus;
	
	public boolean isCanceled() {
		return isCanceled;
	}


	private final long freqStartTime;
	private final int tripCounter;
	// Want to store trip on server side so that can determine route info
	// when creating PredictionsForRouteStop object.
	private final Trip trip;

	private static final long serialVersionUID = 7264507678733060174L;

	public enum ArrivalOrDeparture {ARRIVAL, DEPARTURE};
	
	/********************** Member Functions **************************/

	/**
	 * Constructs a Prediction object. For use on server side.
	 * 
	 * @param avlReport
	 * @param stopId
	 * @param gtfsStopSeq
	 * @param trip
	 *            Can be set to null for testing but usually will be a valid
	 *            trip
	 * @param predictionTime
	 *            The prediction to present to the user. Can be different from
	 *            actualPredictionTime in that for wait stops might want to show
	 *            user the scheduled time even if the actual prediction time,
	 *            including the stop wait time, might be greater.
	 * @param actualPredictionTime
	 *            The prediction time including all factors, including the stop
	 *            wait time for wait stops presented to the user is different
	 *            than the actual prediction time. Only used on server side when
	 *            calculating predictions.
	 * @param atEndOfTrip
	 *            True if prediction for last stop of trip, which means likely
	 *            not useful to user
	 * @param isDelayed
	 * @param lateAndSubsequentTripSoMarkAsUncertain
	 * @param arrivalOrDeparture
	 * @param delay scheduleDeviation or null
	 * @param freqStartTime 
	 * @param tripCounter
	 * 
	 */
	public IpcPrediction(AvlReport avlReport, String stopId, int gtfsStopSeq,
		      Trip trip, long predictionTime, long actualPredictionTime,
		      boolean atEndOfTrip, boolean affectedByWaitStop,
		      boolean isDelayed, boolean lateAndSubsequentTripSoMarkAsUncertain,
		      ArrivalOrDeparture arrivalOrDeparture, Integer delay, long freqStartTime,
			int tripCounter,boolean isCanceled) {
		this.vehicleId = avlReport.getVehicleId();
	    this.routeId = trip.getRouteId();
	    this.stopId = stopId;
	    this.gtfsStopSeq = gtfsStopSeq;
	    this.trip = trip;
	    // For when trip is null use "" instead of null for the tripId
	    // so that when getting all predictions code for telling when
	    // tripId changes will still work when debugging.
	    this.tripId = trip != null ? trip.getId() : "";
	    this.tripPatternId = trip != null ? trip.getTripPattern().getId() : "";
	    this.blockId = trip != null ? trip.getBlockId() : null;
	    this.isTripUnscheduled = trip != null && trip.isNoSchedule() && !trip.isExactTimesHeadway();
	    this.predictionTime = predictionTime;
	    this.actualPredictionTime = actualPredictionTime;
	    this.atEndOfTrip = atEndOfTrip;
	    this.schedBasedPred = avlReport.isForSchedBasedPreds();
	    this.avlTime = avlReport.getTime();
	    this.creationTime = avlReport.getTimeProcessed();

	    Date currentTime = Core.getInstance().getSystemDate();
	    this.tripStartEpochTime =
	        Core.getInstance().getTime()
	            .getEpochTime(trip.getStartTime(), currentTime);
	    
	    this.affectedByWaitStop = affectedByWaitStop;
	    this.driverId = avlReport.getDriverId();
	    this.passengerCount = (short) avlReport.getPassengerCount();
	    this.passengerFullness = avlReport.getPassengerFullness();
		this.occupancyStatus = toIpcOccupancyStatus(avlReport.getOccupancyStatus());
	    this.isDelayed = isDelayed;
	    this.lateAndSubsequentTripSoMarkAsUncertain = 
	        lateAndSubsequentTripSoMarkAsUncertain;
	    this.isArrival = arrivalOrDeparture == ArrivalOrDeparture.ARRIVAL;
	    this.delay = delay;
	    this.freqStartTime = freqStartTime;
		this.tripCounter =  tripCounter;
		this.isCanceled=isCanceled;
	}

	private IpcOccupancyStatus toIpcOccupancyStatus(OccupancyStatus occupancyStatus) {
		if (occupancyStatus == null) return null;
		return IpcOccupancyStatus.toEnum(occupancyStatus.valueOf());
	}

	/**
	 * Constructor used for when deserializing a proxy object. Declared private
	 * because only used internally by the proxy class.
	 */
	private IpcPrediction(String vehicleId, String routeId, String stopId,
			int gtfsStopSeq, String tripId, String tripPatternId, boolean isTripUnscheduled,
			String blockId, long predictionTime, long actualPredictionTime,
			boolean atEndOfTrip, boolean schedBasedPred, long avlTime,
			long creationTime, long tripStartEpochTime,
			boolean affectedByWaitStop, String driverId, short passengerCount,
			float passengerFullness, boolean isDelayed,

			boolean lateAndSubsequentTripSoMarkAsUncertain, boolean isArrival,  Integer delay, Long freqStartTime, int tripCounter,boolean isCanceled, IpcOccupancyStatus occupancyStatus) {

		this.vehicleId = vehicleId;
		this.routeId = routeId;
		this.stopId = stopId;
		this.gtfsStopSeq = gtfsStopSeq;
		// trip is only for client side
		this.trip = null;
		this.tripId = tripId;
		this.tripPatternId = tripPatternId;
		this.isTripUnscheduled = isTripUnscheduled;
		this.blockId = blockId;
		this.predictionTime = predictionTime;
		this.actualPredictionTime = actualPredictionTime;
		this.atEndOfTrip = atEndOfTrip;
		this.schedBasedPred = schedBasedPred;
		this.avlTime = avlTime;
		this.creationTime = creationTime;
		this.tripStartEpochTime = tripStartEpochTime;
		this.affectedByWaitStop = affectedByWaitStop;
		this.driverId = driverId;
		this.passengerCount = passengerCount;
		this.passengerFullness = passengerFullness;
		this.occupancyStatus = occupancyStatus;
		this.isDelayed = isDelayed;
		this.lateAndSubsequentTripSoMarkAsUncertain = 
				lateAndSubsequentTripSoMarkAsUncertain;
		this.isArrival = isArrival;

		this.freqStartTime = freqStartTime;
		this.tripCounter = tripCounter;

		this.delay = delay;
		this.isCanceled=isCanceled;
	}

	
	/**
	 * SerializationProxy is used so that this class can be immutable and so
	 * that can do versioning of objects.
	 */
	private static class SerializationProxy implements Serializable {
		// Exact copy of fields of Prediction enclosing class object
		private String vehicleId;
		private String routeId;
		private String stopId;
		private int gtfsStopSeq;
		private String tripId;
		private String tripPatternId;
		private boolean isTripUnscheduled;
		private String blockId;
		private long predictionTime;
		private boolean atEndOfTrip;
		private boolean schedBasedPred;
		private long avlTime;
		private long creationTime;
		private long tripStartEpochTime;
		private boolean affectedByWaitStop;
		private String driverId;
		private short passengerCount;
		private float passengerFullness;
		private IpcOccupancyStatus occupancyStatus;
		private boolean isDelayed;
		private boolean lateAndSubsequentTripSoMarkAsUncertain;
		private boolean isArrival;

		private long freqStartTime;
		private int tripCounter;

		private Integer delay;
		private boolean isCanceled;

		private static final long serialVersionUID = -8585283691951746719L;
		private static final short currentSerializationVersion = 0;

		/*
		 * Only to be used within this class.
		 */
		private SerializationProxy(IpcPrediction p) {
			this.vehicleId = p.vehicleId;
			this.routeId = p.routeId;
			this.stopId = p.stopId;
			this.gtfsStopSeq = p.gtfsStopSeq;
			this.tripId = p.tripId;
			this.tripPatternId = p.tripPatternId;
			this.isTripUnscheduled = p.isTripUnscheduled;
			this.blockId = p.blockId;
			this.predictionTime = p.predictionTime;
			this.atEndOfTrip = p.atEndOfTrip;
			this.schedBasedPred = p.schedBasedPred;
			this.avlTime = p.avlTime;
			this.creationTime = p.creationTime;
			this.tripStartEpochTime = p.tripStartEpochTime;
			this.affectedByWaitStop = p.affectedByWaitStop;
			this.driverId = p.driverId;
			this.passengerCount = p.passengerCount;
			this.passengerFullness = p.passengerFullness;
			this.occupancyStatus = p.occupancyStatus;
			this.isDelayed = p.isDelayed;
			this.lateAndSubsequentTripSoMarkAsUncertain = 
					p.lateAndSubsequentTripSoMarkAsUncertain;
			this.isArrival = p.isArrival;

			this.freqStartTime = p.freqStartTime;
			this.tripCounter = p.tripCounter;

			this.delay = p.delay;
			this.isCanceled=p.isCanceled;
		}

		/*
		 * When object is serialized writeReplace() causes this
		 * SerializationProxy object to be written. Write it in a custom way
		 * that includes a version ID so that clients and servers can have two
		 * different versions of code.
		 */
		private void writeObject(java.io.ObjectOutputStream stream)
				throws IOException {
			stream.writeShort(currentSerializationVersion);
			
			stream.writeObject(vehicleId);
			stream.writeObject(routeId);
			stream.writeObject(stopId);
			stream.writeInt(gtfsStopSeq);
			stream.writeObject(tripId);
			stream.writeObject(tripPatternId);
			stream.writeBoolean(isTripUnscheduled);
			stream.writeObject(blockId);
			stream.writeLong(predictionTime);
			stream.writeBoolean(atEndOfTrip);
			stream.writeBoolean(schedBasedPred);
			stream.writeLong(avlTime);
			stream.writeLong(creationTime);
			stream.writeLong(tripStartEpochTime);
			stream.writeBoolean(affectedByWaitStop);
			stream.writeObject(driverId);
			stream.writeShort(passengerCount);
			stream.writeFloat(passengerFullness);
			stream.writeBoolean(isArrival);
			stream.writeBoolean(isDelayed);
			stream.writeBoolean(lateAndSubsequentTripSoMarkAsUncertain);

			stream.writeLong(freqStartTime);
			stream.writeInt(tripCounter);

			stream.writeObject(delay);
			stream.writeBoolean(isCanceled);
			stream.writeObject(occupancyStatus);
		}

		/*
		 * Custom method of deserializing a SerializationProy object.
		 */
		private void readObject(java.io.ObjectInputStream stream)
				throws IOException, ClassNotFoundException {			
			// If reading from a newer version of protocol then don't
			// know how to handle it so throw exception
			short readVersion = stream.readShort();
			if (currentSerializationVersion < readVersion) {
				throw new IOException("Serialization error when reading "
						+ getClass().getSimpleName()
						+ " object. Read version=" + readVersion 
						+ " but currently using software version=" 
						+ currentSerializationVersion);
			}

			// serialization version is OK so read in object
			vehicleId = (String) stream.readObject();
			routeId = (String) stream.readObject();
			stopId = (String) stream.readObject();
			gtfsStopSeq = stream.readInt();
			tripId = (String) stream.readObject();
			tripPatternId = (String) stream.readObject();
			isTripUnscheduled = stream.readBoolean();
			blockId = (String) stream.readObject();
			predictionTime = stream.readLong();
			atEndOfTrip = stream.readBoolean();
			schedBasedPred = stream.readBoolean();
			avlTime = stream.readLong();
			creationTime = stream.readLong();
			tripStartEpochTime = stream.readLong();
			affectedByWaitStop = stream.readBoolean();
			driverId = (String) stream.readObject();
			passengerCount = stream.readShort();
			passengerFullness = stream.readFloat();
			isArrival = stream.readBoolean();
			isDelayed = stream.readBoolean();
			lateAndSubsequentTripSoMarkAsUncertain = stream.readBoolean();

			freqStartTime=stream.readLong();
			tripCounter=stream.readInt();

			delay = (Integer) stream.readObject();
			isCanceled=stream.readBoolean();
			occupancyStatus = (IpcOccupancyStatus) stream.readObject();
		}

		/*
		 * When an object is read in it will be a SerializatProxy object due to
		 * writeReplace() being used by the enclosing class. When such an object
		 * is deserialized this method will be called and the SerializationProxy
		 * object is converted to an enclosing class object.
		 */
		private Object readResolve() {
			return new IpcPrediction(vehicleId, routeId, stopId, gtfsStopSeq,
					tripId, tripPatternId, isTripUnscheduled, blockId, predictionTime, 0,
					atEndOfTrip, schedBasedPred, avlTime, creationTime,
					tripStartEpochTime, affectedByWaitStop, driverId,
					passengerCount, passengerFullness, isDelayed,
					lateAndSubsequentTripSoMarkAsUncertain, isArrival, delay, freqStartTime, tripCounter,isCanceled, occupancyStatus);

		}
	}

	/*
	 * Needed as part of using a SerializationProxy. When Vehicle object is
	 * serialized the SerializationProxy will instead be used.
	 */
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	/*
	 * Needed as part of using a SerializationProxy. Makes sure that Vehicle
	 * object cannot be deserialized without using proxy, thereby eliminating
	 * possibility of such an attack as described in "Effective Java".
	 */
	private void readObject(ObjectInputStream stream)
			throws InvalidObjectException {
		throw new InvalidObjectException("Must use proxy instead");
	}

	@Override
	public String toString() {
		return "IpcPrediction [" 
				+ "vehicleId=" + vehicleId
				+ ", predTime="	+ Time.dateTimeStr(predictionTime)
				+ ", routeId=" + routeId
				+ (trip != null ? ", rteName=" + trip.getRouteShortName() : "")
				+ ", stop="	+ stopId
				// stop name taken out because it is too verbose in the
				// predictions log file
				// + (stopName!=null ? ", stopNm=\"" + stopName + "\"" : "")
				+ ", gtfsStopSeq=" + gtfsStopSeq
				+ ", tripId=" + tripId
				+ ", freqStartTime=" + Time.timeStrMsecNoTimeZone(freqStartTime)
				+ ", tripPatternId=" + tripPatternId
				+ ", blockId=" + blockId
				+ ", avlTime=" + Time.timeStrMsecNoTimeZone(avlTime)
				+ ", createTime=" + Time.timeStrMsecNoTimeZone(creationTime)
				+ ", tripStartEpochTime=" + Time.timeStrMsecNoTimeZone(tripStartEpochTime)
				+ ", atEndOfTrip=" + (atEndOfTrip ? "t" : "f")
				+ ", waitStop="	+ (affectedByWaitStop ? "t" : "f")
				+ (schedBasedPred ? ", schedBasedPred=t" : "")
				+ (isDelayed ? ", delayed=t" : "")
				+ (lateAndSubsequentTripSoMarkAsUncertain ? ", lateAndSubsequentTripSoMarkAsUncertain=t" : "")
				+ ", arrival=" + (isArrival ? "t" : "f") 
				+ (driverId != null ? ", driver=" + driverId : "")
				+ (isPassengerCountValid() ? ", psngrCnt=" + passengerCount
						: "")
				+ (!Float.isNaN(passengerFullness) ? ", psngrFullness="
						+ StringUtils.twoDigitFormat(passengerFullness) : "")
				+ ("isCanceled= "+isCanceled)
				+ ", occupancyStatus=" + occupancyStatus
				+ "]";
	}

	public String getVehicleId() {
		return vehicleId;
	}

	public String getRouteId() {
		return routeId;
	}
	
	public String getStopId() {
		return stopId;
	}

	/**
	 * @return the stop_sequence from the GTFS stop_times.txt file
	 */
	public int getGtfsStopSeq() {
		return gtfsStopSeq;
	}

	public String getTripId() {
		return tripId;
	}

	public String getTripPatternId() {
		return tripPatternId;
	}
	
	public boolean isTripUnscheduled() {
		return isTripUnscheduled;
	}
	
	public String getBlockId() {
		return blockId;
	}

	public long getPredictionTime() {
		return predictionTime;
	}

	public long getActualPredictionTime() {
		return actualPredictionTime;
	}
	
	public boolean isAtEndOfTrip() {
		 return atEndOfTrip;
	}
	
	public boolean isSchedBasedPred() {
		return schedBasedPred;
	}
	
	public boolean isAffectedByWaitStop() {
		return affectedByWaitStop;
	}

	public long getAvlTime() {
		return avlTime;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public long getTripStartEpochTime() {
		return tripStartEpochTime;
	}
	
	/**
	 * Returns the driver ID if it is available. Otherwise returns null.
	 * 
	 * @return
	 */
	public String getDriverId() {
		return driverId;
	}

	/**
	 * Returns the passenger count as obtained from the AVL feed. The value will
	 * not be valid for systems that do not have passenger counting sensors.
	 * Therefore should use isPassengerCountValid() to determine if the value is
	 * valid.
	 * 
	 * @return Passenger count from the AVL feed
	 */
	public short getPassengerCount() {
		return passengerCount;
	}

	/**
	 * Passenger counts only valid for systems where there actually are
	 * passenger counting sensors.
	 * 
	 * @return True if getPassengerCount() returns a valid value
	 */
	public boolean isPassengerCountValid() {
		return passengerCount >= 0;
	}

	public IpcOccupancyStatus getOccupancyStatus() { return occupancyStatus; }

	public boolean isDelayed() {
		return isDelayed;
	}
	
	/**
	 * For when vehicle is quite late and prediction is for a subsequent trip.
	 * 
	 * @return
	 */
	public boolean isLateAndSubsequentTripSoMarkAsUncertain() {
		return lateAndSubsequentTripSoMarkAsUncertain;
	}
	
	public boolean isArrival() {
		return isArrival;
	}
	
	/**
	 * Returns the trip associated with the prediction. Only valid on server
	 * side since trip is not passed to client.
	 * 
	 * @return
	 */
	public Trip getTrip() {
		return trip;
	}
	
	/**
	 * Returns the short name for the route associated with the prediction. Only
	 * valid on server side since uses the trip member and the trip is not
	 * passed to client.
	 * 
	 * @return
	 */
	public String getRouteShortName() {
		if (trip == null)
			return null;
		return trip.getRouteShortName();
	}
	
	public Integer getDelay() {
	  return delay;
	}

	public long getFreqStartTime() {
		return freqStartTime;
	}
	
}