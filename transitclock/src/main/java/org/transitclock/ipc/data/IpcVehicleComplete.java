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
import java.util.Date;
import java.util.List;

import org.transitclock.core.BlockAssignmentMethod;
import org.transitclock.core.SpatialMatch;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.VehicleState;
import org.transitclock.db.structs.OccupancyStatus;
import org.transitclock.db.structs.Trip;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

/**
 * Extension of IpcVehicle class so that all info describing vehicle is
 * included. All this info is put into the VehicleDataCache so that can get
 * whatever data is needed be the remote clients. In particular, includes
 * additional info for SIRI feed. Made a separate class from IpcVehicle since
 * the SIRI feed is not expected to get significant use (it is a poor interface)
 * and don't want to bog down all the other IpcVehicle requests with extraneous
 * data.
 *
 * @author SkiBu Smith
 *
 */
public class IpcVehicleComplete extends IpcVehicleGtfsRealtime {

	private final String originStopId;
	private final String destinationId;
	private final Double distanceToNextStop;
	private final Double distanceOfNextStopFromTripStart;
	private final Double distanceAlongTrip;
	private double headway;
	private final IpcOccupancyStatus occupancyStatus;
	
	private static final long serialVersionUID = 8154105842499551462L;

	/********************** Member Functions **************************/

	/**
	 * The constructor.
	 * 
	 * @param vs The current vehicle state. Must not be null.
	 */
	public IpcVehicleComplete(VehicleState vs) {
		super(vs);
		
		// If vehicle assigned then can set the parameters
		Trip trip = vs.getTrip();
		if (trip != null) {
			List<String> stopIdsForTrip = 
					vs.getTrip().getTripPattern().getStopIds();
			this.originStopId = stopIdsForTrip.get(0);
			this.destinationId = stopIdsForTrip.get(stopIdsForTrip.size()-1);
			
			// Get the match. If match is just after a stop then adjust
			// it to just before the stop so that can determine proper 
			// stop ID and such.
			SpatialMatch match = vs.getMatch().getMatchBeforeStopIfAtStop();
			// If vehicle is at a stop then "next" stop will actually be
			// the current stop.
			this.distanceToNextStop = match.getDistanceRemainingInStopPath();

			// Determine how far from beginning of trip to the next stop
			double sumOfStopPathLengths = 0.0;
			for (int spIdx=0; spIdx <= match.getStopPathIndex(); ++spIdx) {
				sumOfStopPathLengths += trip.getStopPath(spIdx).length();
			}
			this.distanceOfNextStopFromTripStart = sumOfStopPathLengths;
			this.distanceAlongTrip = 
					sumOfStopPathLengths - this.distanceToNextStop;
			this.occupancyStatus = toIpcOccupancyStatus(vs.getAvlReport().getOccupancyStatus());
			if(vs.getHeadway()!=null)
			{
				this.headway=vs.getHeadway().getHeadway();
			}
			else
				this.headway=-1;
		} else {
			// Vehicle not assigned to trip so null out parameters
			this.originStopId = null;
			this.destinationId = null;
			this.distanceToNextStop =null; //Double.NaN;
			this.distanceOfNextStopFromTripStart =null;//  Double.NaN;
			this.distanceAlongTrip =null; // Double.NaN;
			this.occupancyStatus = null;
		}
	}

	protected IpcOccupancyStatus toIpcOccupancyStatus(OccupancyStatus occupancyStatus) {
		if (occupancyStatus == null) return null;
		return IpcOccupancyStatus.toEnum(occupancyStatus.valueOf());
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
	 * @param tripPatternId
	 * @param isTripUnscheduled
	 * @param directionId
	 * @param headsign
	 * @param predictable
	 * @param realTimeSchdAdh
	 * @param isDelayed
	 * @param isLayover
	 * @param layoverDepartureTime
	 * @param nextStopId
	 * @param nextStopName
	 * @param vehicleType
	 * @param atStop
	 * @param atOrNextStopId
	 * @param atOrNextGtfsStopSeq
	 * @param originStopId
	 * @param destinationId
	 * @param distanceToNextStop
	 * @param distanceOfNextStopFromTripStart
	 * @param distanceAlongTrip
	 */
	private IpcVehicleComplete(String blockId,
			BlockAssignmentMethod blockAssignmentMethod, IpcAvl avl,
			float pathHeading, String routeId, String routeShortName,
			String routeName, String tripId, String tripPatternId, boolean isTripUnscheduled,
			String directionId, String headsign, boolean predictable,
			boolean schedBasedPred, TemporalDifference realTimeSchdAdh,
			boolean isDelayed, boolean isLayover, long layoverDepartureTime,
			String nextStopId, String nextStopName, String vehicleType,
			long tripStartEpochTime, boolean atStop, String atOrNextStopId,
			Integer atOrNextGtfsStopSeq, String originStopId,
			String destinationId, Double distanceToNextStop,
			Double distanceOfNextStopFromTripStart, Double distanceAlongTrip, long freqStartTime, IpcHoldingTime holdingTime, double predictedLatitude, double predictedLongitude,boolean isCanceled,
			double headway, IpcOccupancyStatus occupancyStatus) {

		super(blockId, blockAssignmentMethod, avl, pathHeading, routeId,
				routeShortName, routeName, tripId, tripPatternId, isTripUnscheduled, directionId, headsign,
				predictable, schedBasedPred, realTimeSchdAdh, isDelayed,
				isLayover, layoverDepartureTime, nextStopId, nextStopName,
				vehicleType, tripStartEpochTime, atStop, atOrNextStopId,
				atOrNextGtfsStopSeq, freqStartTime, holdingTime, predictedLatitude, predictedLongitude,isCanceled);


		this.originStopId = originStopId;
		this.destinationId = destinationId;
		this.distanceToNextStop = distanceToNextStop;
		this.distanceOfNextStopFromTripStart = distanceOfNextStopFromTripStart;
		this.distanceAlongTrip = distanceAlongTrip;
		this.headway=headway;
		this.occupancyStatus = occupancyStatus;
	}
	
	/*
	 * SerializationProxy is used so that this class can be immutable and so
	 * that can do versioning of objects.
	 */
	protected static class CompleteVehicleSerializationProxy 
		extends GtfsRealtimeVehicleSerializationProxy {
		// Exact copy of fields of IpcCompleteVehicle enclosing class object
		private String originStopId;
		private String destinationId;
		private Double distanceToNextStop;
		private Double distanceOfNextStopFromTripStart;
		private Double distanceAlongTrip;
		private double headway;
		private IpcOccupancyStatus occupancyStatus;
		private static final short currentSerializationVersion = 0;
		
		private static final long serialVersionUID = 6982458672576764027L;

		private CompleteVehicleSerializationProxy(IpcVehicleComplete v) {
			super(v);
			this.originStopId = v.originStopId;
			this.destinationId = v.destinationId;
			this.distanceToNextStop = v.distanceToNextStop;
			this.distanceOfNextStopFromTripStart = v.distanceOfNextStopFromTripStart;
			this.distanceAlongTrip = v.distanceAlongTrip;
			this.headway=v.headway;
			this.occupancyStatus = v.occupancyStatus;
		}
		
		/*
		 * When object is serialized writeReplace() causes this
		 * SerializationProxy object to be written. Write it in a custom way
		 * that includes a version ID so that clients and servers can have two
		 * different versions of code.
		 */
		protected void writeObject(java.io.ObjectOutputStream stream)
				throws IOException {
			// Write the data for IpcGtfsRealtimeVehicle super class
			super.writeObject(stream);
			
			// Write the data for this class
			stream.writeShort(currentSerializationVersion);
			
		    stream.writeObject(originStopId);
		    stream.writeObject(destinationId);
		    stream.writeObject(distanceToNextStop);
		    stream.writeObject(distanceOfNextStopFromTripStart);
		    stream.writeObject(distanceAlongTrip);
		    stream.writeDouble(headway);
			  stream.writeObject(occupancyStatus);
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
			originStopId = (String) stream.readObject();
			destinationId = (String) stream.readObject();
			distanceToNextStop = (Double)stream.readObject();
			distanceOfNextStopFromTripStart = (Double)stream.readObject();
			distanceAlongTrip =(Double) stream.readObject();
			isCanceled=stream.readBoolean();
			headway=stream.readDouble();
			occupancyStatus = (IpcOccupancyStatus) stream.readObject();
		}
		
		/*
		 * When an object is read in it will be a SerializatProxy object due to
		 * writeReplace() being used by the enclosing class. When such an object
		 * is deserialized this method will be called and the SerializationProxy
		 * object is converted to an enclosing class object.
		 */
		private Object readResolve() {
			return new IpcVehicleComplete(blockId, blockAssignmentMethod, avl,
					heading, routeId, routeShortName, routeName, tripId,
					tripPatternId, isTripUnscheduled, directionId, headsign, predictable,
					schedBasedPred, realTimeSchdAdh, isDelayed, isLayover,
					layoverDepartureTime, nextStopId, nextStopName,
					vehicleType, tripStartEpochTime, atStop, atOrNextStopId,
					atOrNextGtfsStopSeq, originStopId, destinationId,
					distanceToNextStop, distanceOfNextStopFromTripStart,

					distanceAlongTrip, freqStartTime, holdingTime, predictedLatitude, predictedLongitude,isCanceled,
					headway, occupancyStatus);

		}

	} // End of class SiriVehicleSerializationProxy
	
	/*
	 * Needed as part of using a SerializationProxy. When IpcSerialVehicle 
	 * object is serialized the SerializationProxy will instead be used.
	 */
	private Object writeReplace() {
		return new CompleteVehicleSerializationProxy(this);
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

	public String getOriginStopId() {
		return originStopId;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public Double getDistanceToNextStop() {
		return distanceToNextStop;
	}

	public Double getDistanceOfNextStopFromTripStart() {
		return distanceOfNextStopFromTripStart;
	}
	
	public Double getDistanceAlongTrip() {
		return distanceAlongTrip;
	}
	public double getHeadway()
	{
		return headway;
	}
	public IpcOccupancyStatus getOccupancyStatus() { return occupancyStatus; }

	@Override
	public String toString() {
		return "IpcExtVehicle [" 
				+ "vehicleId=" + getId()
				+ ", blockId=" + getBlockId() 
				+ ", blockAssignmentMethod=" + getBlockAssignmentMethod()
				+ ", routeId=" + getRouteId()
				+ ", routeShortName=" + getRouteShortName()
				+ ", routeName=" + getRouteName() 
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
				+ ", avl=" + getAvl()
				+ ", heading=" + getHeading() 
				+ ", vehicleType=" + getVehicleType()
				+ ", atStop=" + isAtStop()
				+ ", atOrNextStopId=" + getAtOrNextStopId()
				+ ", atOrNextGtfsStopSeq=" + getAtOrNextGtfsStopSeq()
				+ ", tripStartEpochTime=" + getTripStartEpochTime()
				+ ", tripStartEpochTime=" + new Date(getTripStartEpochTime())
				+ ", isCanceled="   + isCanceled()
				+ ", originStopId="	+ originStopId 
				+ ", headway=" + headway
				+ ", distanceToNextStop=" 
					+ Geo.distanceFormat(distanceToNextStop)
				+ ", distanceOfNextStopFromTripStart=" 
					+ Geo.distanceFormat(distanceOfNextStopFromTripStart)
				+ ", distanceAlongTrip=" 
					+ Geo.distanceFormat(distanceAlongTrip)
				+ ", occupancyStatus=" + occupancyStatus
				+ "]";
	}

}