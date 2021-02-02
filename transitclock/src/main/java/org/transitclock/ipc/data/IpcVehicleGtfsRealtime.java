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
import java.util.Date;

import org.transitclock.applications.Core;
import org.transitclock.core.BlockAssignmentMethod;
import org.transitclock.core.RouteMatch;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TemporalMatch;
import org.transitclock.core.VehicleState;
import org.transitclock.db.structs.HoldingTime;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.utils.Time;


/**
 * Extension of IpcVehicle class so that additional info can be provided for
 * GTFS-realtime feed. Made a separate class from IpcVehicle since the expect
 * other interfaces, such as the JSON/XML interface, to be dominant and don't
 * want to bog down all the other IpcVehicle requests with extraneous data.
 *
 * @author SkiBu Smith
 *
 */
public class IpcVehicleGtfsRealtime extends IpcVehicle {

	// True if vehicle at stop. Otherwise false to indicate in transit
	private final boolean atStop; 
	
	// Slightly different from nextStopId of super class IpcVehicle since if
	// matching to just beyond a stop it should still be considered at the
	// stop by nextStopId will indicate the next stop.
	private final String atOrNextStopId;
	
	// Set if vehicle is predictable. Otherwise null
	private final Integer atOrNextGtfsStopSeq;
	
	// For GTFS-rt to disambiguate trips
	private final long tripStartEpochTime;


	private boolean isCanceled; 

	public boolean isCanceled() {
		return isCanceled;
	}

	public void setCanceled(boolean isCanceled) {
		this.isCanceled = isCanceled;
	}
	
	// For GTFS-rt to set scheduled relationship
	private final boolean isTripUnscheduled;


	private static final long serialVersionUID = -6611046660260490100L;

	/********************** Member Functions **************************/

	/**
	 * The constructor used on the server side
	 * 
	 * @param vs
	 */
	public IpcVehicleGtfsRealtime(VehicleState vs) {
		super(vs);

		// Get the match. If match is just after a stop then adjust
		// it to just before the stop so that can determine proper 
		// stop ID and gtfs stop sequence.
		TemporalMatch temporalMatch = vs.getMatch();
		if (temporalMatch != null) {
			RouteMatch match = vs.getMatch().getMatchBeforeStopIfAtStop();			
			atStop = match.getAtStop() != null;
			
			// Determine stop info depending on whether it is at a stop or in 
			// transit to a stop.
			StopPath stopPath = atStop ? 
					match.getAtStop().getStopPath() : match.getStopPath();
			atOrNextStopId = stopPath.getStopId();
			atOrNextGtfsStopSeq = stopPath.getGtfsStopSeq();
			this.isCanceled =vs.isCanceled();
			// Note: the trip start date is created on server side so that
			// proper timezone is used. This unfortunately is a bit expensive.
			int time = vs.getTrip().getStartTime();
			Date currentTime = Core.getInstance().getSystemDate();
			this.tripStartEpochTime =
					Core.getInstance().getTime()
							.getEpochTime(time, currentTime);
			Trip trip = vs.getTrip();
			this.isTripUnscheduled = trip != null && trip.isNoSchedule() && !trip.isExactTimesHeadway();
		} else {
			atStop = false;
			atOrNextStopId = null;
			atOrNextGtfsStopSeq = null;
			tripStartEpochTime = 0;
			isTripUnscheduled = false;
		}
	}

	/**
	 * Constructor used for when deserializing a proxy object.
	 * 
	 * @param blockId
	 * @param blockAssignmentMethod
	 * @param avl
	 * @param pathHeading
	 * @param routeId
	 * @param routeShortName
	 * @param routeName
	 * @param tripId
	 * @param tripStartDateStr
	 * @param tripPatternId
	 * @param isTripUnscheduled
	 * @param directionId
	 * @param headsign
	 * @param predictable
	 * @param schedBasedPred
	 * @param realTimeSchdAdh
	 * @param isDelayed
	 * @param isLayover
	 * @param layoverDepartureTime
	 * @param nextStopId
	 * @param nextStopName
	 * @param vehicleType
	 * @param atStopId
	 * @param atOrNextStopId
	 * @param atOrNextGtfsStopSeq
	 * @param holdingTime 
	 * @param isCanceled
	 */
	protected IpcVehicleGtfsRealtime(String blockId,
			BlockAssignmentMethod blockAssignmentMethod, IpcAvl avl,
			float pathHeading, String routeId, String routeShortName,
			String routeName, String tripId, String tripPatternId, boolean isTripUnscheduled,
			String directionId, String headsign, boolean predictable,
			boolean schedBasedPred, TemporalDifference realTimeSchdAdh,
			boolean isDelayed, boolean isLayover, long layoverDepartureTime,
			String nextStopId, String nextStopName, String vehicleType,
			long tripStartEpochTime, boolean atStop, String atOrNextStopId,

			Integer atOrNextGtfsStopSeq, long freqStartTime, IpcHoldingTime holdingTime, double predictedLatitude, 
			double predictedLongitude,boolean isCanceled) {

		super(blockId, blockAssignmentMethod, avl, pathHeading, routeId,
				routeShortName, routeName, tripId, tripPatternId, directionId, headsign,
				predictable, schedBasedPred, realTimeSchdAdh, isDelayed,
				isLayover, layoverDepartureTime, nextStopId, nextStopName,

				vehicleType, freqStartTime ,atStop, holdingTime, predictedLatitude, predictedLongitude);

		this.atStop = atStop;
		this.atOrNextStopId = atOrNextStopId;
		this.atOrNextGtfsStopSeq = atOrNextGtfsStopSeq;
		this.tripStartEpochTime = tripStartEpochTime;
		this.isCanceled=isCanceled;
		this.isTripUnscheduled = isTripUnscheduled;

	}
	
	/*
	 * SerializationProxy is used so that this class can be immutable and so
	 * that can do versioning of objects.
	 */
	protected static class GtfsRealtimeVehicleSerializationProxy 
			extends SerializationProxy {
		protected boolean atStop;
		protected String atOrNextStopId; 
		protected Integer atOrNextGtfsStopSeq;
		protected long tripStartEpochTime; 
		protected boolean isCanceled;
		protected boolean isTripUnscheduled;
		private static final short currentSerializationVersion = 0;
		private static final long serialVersionUID = 5804716921925188073L;

		protected GtfsRealtimeVehicleSerializationProxy(IpcVehicleGtfsRealtime v) {
			super(v);
			this.atStop = v.atStop;
			this.atOrNextStopId = v.atOrNextStopId;
			this.atOrNextGtfsStopSeq = v.atOrNextGtfsStopSeq;
			this.tripStartEpochTime = v.tripStartEpochTime;
			this.isCanceled=v.isCanceled;
			this.isTripUnscheduled = v.isTripUnscheduled;
		}
		
		/*
		 * When object is serialized writeReplace() causes this
		 * SerializationProxy object to be written. Write it in a custom way
		 * that includes a version ID so that clients and servers can have two
		 * different versions of code.
		 */
		protected void writeObject(java.io.ObjectOutputStream stream)
				throws IOException {
			// Write the data for IpcVehicle super class
			super.writeObject(stream);
			
			// Write the data for this class
			stream.writeShort(currentSerializationVersion);
			
			stream.writeBoolean(atStop);
			stream.writeObject(atOrNextStopId);
			stream.writeObject(atOrNextGtfsStopSeq);
		    stream.writeLong(tripStartEpochTime);
		    stream.writeBoolean(isCanceled);
		    stream.writeBoolean(isTripUnscheduled);
		}

		/*
		 * Custom method of deserializing a SerializationProy object.
		 */
		protected void readObject(java.io.ObjectInputStream stream)
				throws IOException, ClassNotFoundException {
			// Read the data for IpcVehicle super class
			super.readObject(stream);

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

			// Read in data for this class
			atStop = stream.readBoolean();
			atOrNextStopId = (String) stream.readObject();
			atOrNextGtfsStopSeq = (Integer) stream.readObject();
			tripStartEpochTime = stream.readLong();
			isCanceled=stream.readBoolean();
			isTripUnscheduled = stream.readBoolean();
		}
		
		/*
		 * When an object is read in it will be a SerializatProxy object due to
		 * writeReplace() being used by the enclosing class. When such an object
		 * is deserialized this method will be called and the SerializationProxy
		 * object is converted to an enclosing class object.
		 */
		private Object readResolve() {
			return new IpcVehicleGtfsRealtime(blockId, blockAssignmentMethod,
					avl, heading, routeId, routeShortName, routeName, tripId,
					tripPatternId, isTripUnscheduled, directionId, headsign, predictable,
					schedBasedPred, realTimeSchdAdh, isDelayed, isLayover,
					layoverDepartureTime, nextStopId, nextStopName,
					vehicleType, tripStartEpochTime, atStop, atOrNextStopId,

					atOrNextGtfsStopSeq, freqStartTime, holdingTime, predictedLongitude, predictedLatitude,isCanceled);

		}

	} // End of class GtfsRealtimeVehicleSerializationProxy
	
	public long getTripStartEpochTime() {
		return tripStartEpochTime;
	}
	
	public boolean isTripUnscheduled() {
		return isTripUnscheduled;
	}
	
	/**
	 * Returns true if vehicle at a stop
	 * @return true if at stop
	 */
	public boolean isAtStop() {
		return atStop;
	}
	
	/**
	 * Returns stop ID of stop that vehicle is at or for the next stop if
	 * vehicle is in between stops.
	 * 
	 * @return Stop ID of stop that vehicle is currently at or is transitioning
	 *         to
	 */
	public String getAtOrNextStopId() {
		return atOrNextStopId;
	}

	/**
	 * Returns the GTFS stop sequence of the stop the vehicle is at or is moving
	 * to.
	 * 
	 * @return GTFS stop sequence of stop
	 */
	public Integer getAtOrNextGtfsStopSeq() {
		return atOrNextGtfsStopSeq;
	}
	
	@Override
	public String toString() {
		return "IpcGtfsRealtimeVehicle [" 
				+ "vehicleId=" + getId()
				+ ", blockId=" + getBlockId() 
				+ ", blockAssignmentMethod=" + getBlockAssignmentMethod()
				+ ", routeId=" + getRouteId()
				+ ", routeShortName=" + getRouteShortName()
				+ ", tripId=" + getTripId()
				+ ", tripPatternId=" + getTripPatternId()
				+ ", isTripUnscheduled=" + isTripUnscheduled()
				+ ", directionId=" + getDirectionId()
				+ ", headsign=" + getHeadsign()
				+ ", predictable=" + isPredictable()
				+ ", schedBasedPred=" + isForSchedBasedPred()
				+ ", realTimeSchedAdh=" + getRealTimeSchedAdh() 
				+ ", isDelayed=" + isDelayed()
				+ ", isLayover=" + isLayover()
				+ ", layoverDepartureTime=" 
					+ Time.timeStrNoTimeZone(getLayoverDepartureTime())
				+ ", nextStopId=" + getNextStopId() 
				+ ", nextStopName=" + getNextStopName()
				+ ", avl=" + getAvl()
				+ ", heading=" + getHeading() 
				+ ", vehicleType=" + getVehicleType()
				+ ", atStop=" + atStop
				+ ", atOrNextStopId=" + atOrNextStopId
				+ ", atOrNextGtfsStopSeq=" + atOrNextGtfsStopSeq
				+ ", tripStartEpochTime=" + tripStartEpochTime 
				+ ", tripStartEpochTime=" + new Date(tripStartEpochTime) 
				+ ", isCanceled=" +isCanceled
        + ", isTripUnscheduled" +isTripUnscheduled
				+ "]";
	}
	

}