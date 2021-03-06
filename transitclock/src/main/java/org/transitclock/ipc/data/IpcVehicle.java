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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

import org.transitclock.applications.Core;
import org.transitclock.core.BlockAssignmentMethod;
import org.transitclock.core.SpatialMatch;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.VehicleState;
import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.AvlReport.AssignmentType;
import org.transitclock.utils.Time;

import net.jcip.annotations.Immutable;

/**
 * Contains information on a single vehicle. For providing info to client. This
 * class is Immutable so that it is threadsafe.
 *
 * @author SkiBu Smith
 *
 */
@Immutable
public class IpcVehicle implements Serializable {

	public boolean isAtStop() {
		return isAtStop;
	}

	private final String blockId;
	private final BlockAssignmentMethod blockAssignmentMethod;
	private final IpcAvl avl;
	private final float heading;
	private final String routeId;

	// routeShortName needed because routeId is sometimes not consistent over
	// schedule changes but routeShortName usually is.
	private final String routeShortName;
	private final String routeName;
	private final String tripId;	
	private final String tripPatternId;
	private final String directionId;
	private final String headsign;
	private final boolean predictable;
	// True if vehicle created to generate schedule based predictions
	private final boolean schedBasedPred;
	private final TemporalDifference realTimeSchedAdh;
	private final boolean isDelayed;
	private final boolean isLayover;
	private final long layoverDepartureTime;
	private final String nextStopId;
	private final String nextStopName;
	private final String vehicleType;

	private long freqStartTime;
	private final boolean isAtStop;
	private final IpcHoldingTime holdingTime;

	private final double predictedLatitude;
	private final double predictedLongitude;

	
	private static final long serialVersionUID = -1744566765456572041L;

	/********************** Member Functions **************************/

	/**
	 * Constructs a new Vehicle object from data in a VehicleState object.
	 * 
	 * @param vs
	 */
	public IpcVehicle(VehicleState vs) {
		this.blockAssignmentMethod = vs.getAssignmentMethod();
		this.avl = new IpcAvl(vs.getAvlReport());
		this.heading = vs.getHeading();
		this.routeId = vs.getRouteId();
		this.routeShortName = vs.getRouteShortName();
		this.routeName = vs.getRouteName();
		Trip trip = vs.getTrip();
		if (trip != null) {
			this.blockId = vs.getBlock().getId();
			this.tripId = trip.getId();
			this.tripPatternId = trip.getTripPattern().getId();
			this.directionId = trip.getDirectionId();
			this.headsign = trip.getHeadsign();
			
			// Get the match. If match is just after a stop then adjust
			// it to just before the stop so that can determine proper 
			// stop ID and such.
			SpatialMatch match = vs.getMatch().getMatchBeforeStopIfAtStop();

			// Determine if vehicle is at layover, and if so, what time it 
			// should depart. The departure time isn't necessarily the 
			// scheduled trip start time since if vehicle is late or if
			// driver supposed to get a break then vehicle will leave after
			// the scheduled time. Therefore use the predicted departure time
			// for layover.
			this.isLayover = match.isLayover();
			if (this.isLayover) {
				IpcPrediction predsForVehicle = PredictionDataCache
						.getInstance().getPredictionForVehicle(
								vs.getAvlReport().getVehicleId(),
								vs.getRouteShortName(),
								match.getStopPath().getStopId());
				this.layoverDepartureTime = predsForVehicle!=null ? 
						predsForVehicle.getPredictionTime() : 0;
			} else {
				// Not a layover so departure time not provided
				this.layoverDepartureTime = 0;
			}
			
			// If vehicle is at a stop then "next" stop will actually be
			// the current stop.
			this.nextStopId = match.getStopPath().getStopId();
			this.nextStopName = match.getStopPath().getStopName();
			
			this.vehicleType = match.getRoute().getType();
	
			if(vs.getTripCounter()!=null && vs.getTripStartTime(vs.getTripCounter())!=null)
			{
				this.freqStartTime = vs.getTripStartTime(vs.getTripCounter());
			}
			
		} else {
			// Vehicle not assigned to trip so null out parameters
			this.blockId = null;
			this.tripId = null;
			this.tripPatternId = null;
			this.directionId = null;
			this.headsign = null;
			this.isLayover = false;
			this.layoverDepartureTime = 0;
			this.nextStopId = null;
			this.nextStopName = null;
			this.vehicleType = null;
		}
		this.predictable = vs.isPredictable();
		this.schedBasedPred = vs.isForSchedBasedPreds();
		this.realTimeSchedAdh = vs.getRealTimeSchedAdh();
		this.isDelayed = vs.isDelayed();

		
		if(vs.getMatch()!=null)
			this.isAtStop = vs.getMatch().isAtStop();
		else
			this.isAtStop = false;
		
		if(vs.getHoldingTime()!=null)
		{
			this.holdingTime = new IpcHoldingTime(vs.getHoldingTime());
			this.holdingTime.setCurrentTime(new Date(Core.getInstance().getSystemTime()));
		}
		else
		{
			this.holdingTime = null;
		}

		if (vs.getMatch() != null && vs.getMatch().getLocation() != null) {
			Location matchLocation = vs.getMatch().getLocation();
			this.predictedLatitude = matchLocation.getLat();
			this.predictedLongitude = matchLocation.getLon();
		} else {
			this.predictedLatitude = 0.0;
			this.predictedLongitude = 0.0;
		}		
	}

	/**
	 * Constructor used for when deserializing a proxy object. Declared
	 * protected because only used internally by the proxy class but also for
	 * sub class.
	 * 
	 * @param blockId
	 * @param blockAssignmentMethod
	 * @param avl
	 * @param routeId
	 * @param routeShortName
	 * @param routeName
	 * @param tripId
	 * @param tripPatternId
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
	 * @param freqStartTime
	 */
	protected IpcVehicle(String blockId,
			BlockAssignmentMethod blockAssignmentMethod, IpcAvl avl,
			float heading, String routeId, String routeShortName,
			String routeName, String tripId, String tripPatternId,
			String directionId, String headsign, boolean predictable,
			boolean schedBasedPred, TemporalDifference realTimeSchdAdh,
			boolean isDelayed, boolean isLayover, long layoverDepartureTime,

			String nextStopId, String nextStopName, String vehicleType, long freqStartTime, boolean isAtStop, IpcHoldingTime holdingTime, double predictedLatitude, double predictedLongitude) {

		this.blockId = blockId;
		this.blockAssignmentMethod = blockAssignmentMethod;
		this.avl = avl;
		this.heading = heading;
		this.routeId = routeId;
		this.routeShortName = routeShortName;
		this.routeName = routeName;
		this.tripId = tripId;
		this.tripPatternId = tripPatternId;
		this.directionId = directionId;
		this.headsign = headsign;
		this.predictable = predictable;
		this.schedBasedPred = schedBasedPred;
		this.realTimeSchedAdh = realTimeSchdAdh;
		this.isDelayed = isDelayed;
		this.isLayover = isLayover;
		this.layoverDepartureTime = layoverDepartureTime;
		this.nextStopId = nextStopId;
		this.nextStopName = nextStopName;
		this.vehicleType = vehicleType;

		this.freqStartTime = freqStartTime;
		this.isAtStop = isAtStop;
		this.holdingTime = holdingTime;

		this.predictedLatitude = predictedLatitude;
		this.predictedLongitude = predictedLongitude;

	}

	/*
	 * SerializationProxy is used so that this class can be immutable and so
	 * that can do versioning of objects.
	 */
	protected static class SerializationProxy implements Serializable {
		// Exact copy of fields of IpcVehicle enclosing class object
		protected String blockId;
		protected BlockAssignmentMethod blockAssignmentMethod;
		protected IpcAvl avl;
		protected float heading;
		protected String routeId;
		protected String routeShortName;
		protected String routeName;
		protected String tripId;
		protected String tripPatternId;
		protected String directionId;
		protected String headsign;
		protected boolean predictable;
		protected boolean schedBasedPred;
		protected TemporalDifference realTimeSchdAdh;
		protected boolean isDelayed;
		protected boolean isLayover;
		protected long layoverDepartureTime;
		protected String nextStopId;
		protected String nextStopName;
		protected String vehicleType;

		protected long freqStartTime;
		protected boolean isAtStop;
		protected IpcHoldingTime holdingTime;

		protected double predictedLatitude;
		protected double predictedLongitude;


		private static final long serialVersionUID = -4996254752417270041L;
		private static final short currentSerializationVersion = 0;

		/*
		 * Only to be used within this class.
		 */
		protected SerializationProxy(IpcVehicle v) {
			this.blockId = v.blockId;
			this.blockAssignmentMethod = v.blockAssignmentMethod;
			this.avl = v.avl;
			this.heading = v.heading;
			this.routeId = v.routeId;
			this.routeShortName = v.routeShortName;
			this.routeName = v.routeName;
			this.tripId = v.tripId;
			this.tripPatternId = v.tripPatternId;
			this.directionId = v.directionId;
			this.headsign = v.headsign;
			this.predictable = v.predictable;
			this.schedBasedPred = v.schedBasedPred;
			this.realTimeSchdAdh = v.realTimeSchedAdh;
			this.isDelayed = v.isDelayed;
			this.isLayover = v.isLayover;
			this.layoverDepartureTime = v.layoverDepartureTime;
			this.nextStopId = v.nextStopId;
			this.nextStopName = v.nextStopName;
			this.vehicleType = v.vehicleType;

			this.freqStartTime = v.freqStartTime;
			this.isAtStop = v.isAtStop;
			this.holdingTime = v.holdingTime;

			this.predictedLatitude = v.predictedLatitude;
			this.predictedLongitude = v.predictedLongitude;

		}

		/*
		 * When object is serialized writeReplace() causes this
		 * SerializationProxy object to be written. Write it in a custom way
		 * that includes a version ID so that clients and servers can have two
		 * different versions of code.
		 */
		protected void writeObject(java.io.ObjectOutputStream stream)
				throws IOException {
		    stream.writeShort(currentSerializationVersion);
		    
			stream.writeObject(blockId);
			stream.writeObject(blockAssignmentMethod);
			stream.writeObject(avl);
			stream.writeFloat(heading);
			stream.writeObject(routeId);
			stream.writeObject(routeShortName);
			stream.writeObject(routeName);
			stream.writeObject(tripId);
			stream.writeObject(tripPatternId);
			stream.writeObject(directionId);
		  stream.writeObject(headsign);
			stream.writeBoolean(predictable);
			stream.writeBoolean(schedBasedPred);
			stream.writeObject(realTimeSchdAdh);
			stream.writeBoolean(isDelayed);

		    stream.writeBoolean(isLayover);
		    stream.writeLong(layoverDepartureTime);
		    stream.writeObject(nextStopId);
		    stream.writeObject(nextStopName);
		    stream.writeObject(vehicleType);
		    stream.writeObject(freqStartTime);
		    stream.writeBoolean(isAtStop);
		    stream.writeObject(holdingTime);

		  stream.writeDouble(predictedLatitude);
		  stream.writeDouble(predictedLongitude);

		}

		/*
		 * Custom method of deserializing a SerializationProy object.
		 */
		protected void readObject(java.io.ObjectInputStream stream)
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
			blockId = (String) stream.readObject();
			blockAssignmentMethod = (BlockAssignmentMethod) stream.readObject();
			avl = (IpcAvl) stream.readObject();
			heading = stream.readFloat();
			routeId = (String) stream.readObject();
			routeShortName = (String) stream.readObject();
			routeName = (String) stream.readObject();
			tripId = (String) stream.readObject();
			tripPatternId = (String) stream.readObject();
			directionId = (String) stream.readObject();
			headsign = (String) stream.readObject();
			predictable = stream.readBoolean();
			schedBasedPred = stream.readBoolean();
			realTimeSchdAdh = (TemporalDifference) stream.readObject();
			isDelayed = stream.readBoolean();
			isLayover = stream.readBoolean();
			layoverDepartureTime = stream.readLong();
			nextStopId = (String) stream.readObject();
			nextStopName = (String) stream.readObject();
			vehicleType = (String) stream.readObject();
			freqStartTime = stream.readLong();
		    isAtStop = stream.readBoolean();
		    holdingTime = (IpcHoldingTime)stream.readObject(); 
			predictedLatitude = stream.readDouble();
			predictedLongitude = stream.readDouble();

		}

		/*
		 * When an object is read in it will be a SerializatProxy object due to
		 * writeReplace() being used by the enclosing class. When such an object
		 * is deserialized this method will be called and the SerializationProxy
		 * object is converted to an enclosing class object.
		 */
		private Object readResolve() {
			return new IpcVehicle(blockId, blockAssignmentMethod, avl, heading,
					routeId, routeShortName, routeName, tripId, tripPatternId,
					directionId, headsign, predictable, schedBasedPred,
					realTimeSchdAdh, isDelayed, isLayover, layoverDepartureTime,
					nextStopId, nextStopName, vehicleType, freqStartTime, isAtStop, holdingTime, predictedLatitude, predictedLongitude);

		}
	} // End of SerializationProxy class

	/*
	 * Needed as part of using a SerializationProxy. When IpcVehicle object is
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

	public String getId() {
		return avl.getVehicleId();
	}

	public String getBlockId() {
		return blockId;
	}

	public BlockAssignmentMethod getBlockAssignmentMethod() {
		return blockAssignmentMethod;
	}

	public IpcAvl getAvl() {
		return avl;
	}

	/**
	 * Returns number of degrees clockwise from due North. Note that this is
	 * very different from angle(). The path heading that the vehicle matched to
	 * is used when it is available. When path heading not available then uses
	 * the AVL heading. That can be NaN as well though.
	 * 
	 * @return Heading of vehicle, or Float.NaN if heading not defined.
	 */
	public float getHeading() {
		return heading;
	}

	public IpcHoldingTime getHoldingTime() {
		return holdingTime;
	}

	/**
	 * @return Speed of vehicle, or Float.NaN if speed not defined.
	 */
	public float getSpeed() {
		return avl.getSpeed();
	}

	public float getLatitude() {
		return avl.getLatitude();
	}

	public float getLongitude() {
		return avl.getLongitude();
	}
	
	public double getPredictedLatitude() {
		return predictedLatitude;
	}

	public double getPredictedLongitude() {
		return predictedLongitude;
	}

	public String getLicensePlate() {
		return avl.getLicensePlate();
	}

	/**
	 * GPS epoch time in msec.
	 * 
	 * @return
	 */
	public long getGpsTime() {
		return avl.getTime();
	}

	public String getRouteId() {
		return routeId;
	}

	public String getRouteShortName() {
		return routeShortName;
	}

	public String getRouteName() {
		return routeName;
	}
	
	public String getTripId() {
		return tripId;
	}

	public String getTripPatternId() {
		return tripPatternId;
	}
	
	public String getDirectionId() {
		return directionId;
	}
	
	public String getHeadsign() {
		return headsign;
	}

	public boolean isPredictable() {
		return predictable;
	}

	public boolean isForSchedBasedPred() {
		return schedBasedPred;
	}
	
	/**
	 * 
	 * @return The real-time schedule adherence for the vehicle, or null if
	 *         vehicle no predictable.
	 */
	public TemporalDifference getRealTimeSchedAdh() {
		return realTimeSchedAdh;
	}

	public boolean isDelayed() {
		return isDelayed;
	}
	
	public boolean isLayover() {
		return isLayover;
	}

	/**
	 * Epoch time of scheduled departure. Only valid if vehicle at layover.
	 * @return
	 */
	public long getLayoverDepartureTime() {
		return layoverDepartureTime;
	}
	
	public String getNextStopId() {
		return nextStopId;
	}
	
	public String getNextStopName() {
		return nextStopName;
	}

	public String getVehicleType() {
		return vehicleType;		
	}

	@Override
	public String toString() {
		return "IpcVehicle [" 
				+ "vehicleId=" + avl.getVehicleId() 
				+ ", blockId=" + blockId 
				+ ", blockAssignmentMethod=" + blockAssignmentMethod
				+ ", routeId=" + routeId 
				+ ", routeShortName=" + routeShortName
				+ ", routeName=" + routeName
				+ ", tripId=" + tripId 
				+ ", tripPatternId=" + tripPatternId
				+ ", directionId=" + directionId
				+ ", headsign=" + headsign
				+ ", predictable=" + predictable
				+ ", schedBasedPred=" + schedBasedPred
				+ ", realTimeSchedAdh=" + realTimeSchedAdh
				+ ", isDelayed=" + isDelayed
				+ ", isLayover=" + isLayover
				+ ", layoverDepartureTime=" 
					+ Time.timeStrNoTimeZone(layoverDepartureTime)
				+ ", nextStopId=" + nextStopId
				+ ", nextStopName=" + nextStopName
				+ ", avl=" + avl
				+ ", heading=" + heading 
				+ ", vehicleType=" + vehicleType
				+ ", holdingTime=" + holdingTime
				+ "]";
	}

	/*
	 * Just for testing.
	 */
	public static void main(String args[]) {
		IpcAvl avl =
				new IpcAvl("avlVehicleId", 10, 1.23f, 4.56f, 0.0f, 0.0f, null,
						"block", AssignmentType.BLOCK_ID, "driver", "license",
						0, null);
		IpcVehicle v =
				new IpcVehicle("blockId",
						BlockAssignmentMethod.AVL_FEED_BLOCK_ASSIGNMENT, avl,
						123.456f, "routeId", "routeShortName", "routeName",
						"tripId", "tripPatternId", "dirId", "headsign", true, 
						false, null, false, false, 0, null, null, null, -1, false, null, 0.0, 0.0);

		try {
			FileOutputStream fileOut = new FileOutputStream("foo.ser");
			ObjectOutputStream outStream = new ObjectOutputStream(fileOut);
			outStream.writeObject(v);
			outStream.close();
			fileOut.close();
		} catch (IOException i) {
			i.printStackTrace();
		}

		try {
			FileInputStream fileIn = new FileInputStream("foo.ser");
			ObjectInputStream in = new ObjectInputStream(fileIn);
			@SuppressWarnings("unused")
			IpcVehicle newVehicle = (IpcVehicle) in.readObject();
			in.close();
			fileIn.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public long getFreqStartTime() {
		return freqStartTime;
	}

}
