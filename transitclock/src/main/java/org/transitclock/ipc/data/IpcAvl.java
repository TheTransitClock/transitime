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

import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.AvlReport.AssignmentType;
import org.transitclock.db.structs.OccupancyStatus;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

/**
 * A serializable object used by RMI to transfer AVL data to client.
 *
 * @author SkiBu Smith
 *
 */
public class IpcAvl implements Serializable {

	private final String vehicleId;
	private final long time;
	private final float latitude;
	private final float longitude;
	private final float speed;
	private final float heading;
	private final String source;
	private final String assignmentId;
	private final AssignmentType assignmentType;
	private final String driverId;
	private final String licensePlate;
	private final int passengerCount;
	private final IpcOccupancyStatus occupancyStatus;

	private static final long serialVersionUID = 2506303490106709587L;


	/********************** Member Functions **************************/

	/**
	 * @param vehicleId
	 * @param time
	 * @param latitude
	 * @param longitude
	 * @param speed
	 * @param heading
	 * @param source
	 * @param assignmentId
	 * @param assignmentType
	 * @param driverId
	 * @param licensePlate
	 * @param passengerCount
	 */
	public IpcAvl(String vehicleId, long time, float latitude, float longitude,
			float speed, float heading, String source, String assignmentId,
			AssignmentType assignmentType, String driverId,
			String licensePlate, int passengerCount, IpcOccupancyStatus occupancyStatus) {
		this.vehicleId = vehicleId;
		this.time = time;
		this.latitude = latitude;
		this.longitude = longitude;
		this.speed = speed;
		this.heading = heading;
		this.source = source;
		this.assignmentId = assignmentId;
		this.assignmentType = assignmentType;
		this.driverId = driverId;
		this.licensePlate = licensePlate;
		this.passengerCount = passengerCount;
		this.occupancyStatus = occupancyStatus;
	}
	
	/**
	 * copy from AvlReport
	 */
	public IpcAvl(AvlReport a) {
		this.vehicleId = a.getVehicleId();
		this.time = a.getTime();
		this.latitude = (float) a.getLat();
		this.longitude = (float) a.getLon();
		this.speed = a.getSpeed();
		this.heading = a.getHeading();
		this.source = a.getSource();
		this.assignmentId = a.getAssignmentId();
		this.assignmentType = a.getAssignmentType();
		this.driverId = a.getDriverId();
		this.licensePlate = a.getLicensePlate();
		this.passengerCount = a.getPassengerCount();
		this.occupancyStatus = toIpcOccupancyStatus(a.getOccupancyStatus());
	}

	private IpcOccupancyStatus toIpcOccupancyStatus(OccupancyStatus occupancyStatus) {
		if (occupancyStatus == null) return null;
		return IpcOccupancyStatus.toEnum(occupancyStatus.valueOf());
	}

	/*
	 * SerializationProxy is used so that this class can be immutable
	 * and so that can do versioning of objects.
	 */
	private static class SerializationProxy implements Serializable {
		// Exact copy of fields of Vehicle object
		private String vehicleId;
		private long time;
		private float latitude;
		private float longitude;
		private float speed; // in m/s
		private float heading;
		private String source;
		private String assignmentId;
		private AssignmentType assignmentType;
		private String driverId;
		private String licensePlate;
		private int passengerCount;
		private IpcOccupancyStatus occupancyStatus;

		private static final long serialVersionUID = 6220698347690060245L;
		private static final short serializationVersion = 0;

		/*
		 * Only to be used within this class.
		 */
		private SerializationProxy(IpcAvl avl) {
			this.vehicleId = avl.vehicleId;
			this.time = avl.time;
			this.latitude = avl.latitude;
			this.longitude = avl.longitude;
			this.speed = avl.speed;
			this.heading = avl.heading;
			this.source = avl.source;
			this.assignmentId = avl.assignmentId;
			this.assignmentType = avl.assignmentType;
			this.driverId = avl.driverId;
			this.licensePlate = avl.licensePlate;
			this.passengerCount = avl.passengerCount;
			this.occupancyStatus = avl.occupancyStatus;
		}
		
		/*
		 * When object is serialized writeReplace() causes this
		 * SerializationProxy object to be written. Write it in a
		 * custom way that includes a version ID so that clients
		 * and servers can have two different versions of code.
		 */
		private void writeObject(java.io.ObjectOutputStream stream)
				throws IOException {
			stream.writeShort(serializationVersion);
			stream.writeObject(vehicleId);
			stream.writeLong(time);
			stream.writeFloat(latitude);
			stream.writeFloat(longitude);
			stream.writeFloat(speed);
			stream.writeFloat(heading);
			stream.writeObject(source);
			stream.writeObject(assignmentId);
			stream.writeObject(assignmentType);
			stream.writeObject(driverId);
			stream.writeObject(licensePlate);
			stream.writeInt(passengerCount);
			stream.writeObject(occupancyStatus);
		}

		/*
		 * When an object is read in it will be a SerializatProxy object
		 * due to writeReplace() being used by the enclosing class. When
		 * such an object is deserialized this method will be called and
		 * the SerializationProxy object is converted to an enclosing
		 * class object.
		 */
		private Object readResolve() {
			return new IpcAvl(vehicleId, time, latitude, longitude, speed,
					heading, source, assignmentId, assignmentType, driverId,
					licensePlate, passengerCount, occupancyStatus);
		}

		/*
		 * Custom method of deserializing a SerializationProy object.
		 */
		private void readObject(java.io.ObjectInputStream stream)
				throws IOException, ClassNotFoundException {
			short readVersion = stream.readShort();
			if (serializationVersion != readVersion) {
				throw new IOException("Serialization error when reading "
						+ getClass().getSimpleName()
						+ " object. Read serializationVersion=" + readVersion);
			}

			// serialization version is OK so read in object
			vehicleId = (String) stream.readObject();
			time = stream.readLong();
			latitude = stream.readFloat();
			longitude = stream.readFloat();
			speed = stream.readFloat();
			heading = stream.readFloat();
			source = (String) stream.readObject();
			assignmentId = (String) stream.readObject();
			assignmentType = (AssignmentType) stream.readObject();
			driverId = (String) stream.readObject();
			licensePlate = (String) stream.readObject();
			passengerCount = stream.readInt();
			occupancyStatus = (IpcOccupancyStatus) stream.readObject();
		}
	}

	/*
	 * Needed as part of using a SerializationProxy. When Vehicle object
	 * is serialized the SerializationProxy will instead be used.
	 */
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	/*
	 * Needed as part of using a SerializationProxy. Makes sure that Vehicle
	 * object cannot be deserialized without using proxy, thereby eliminating
	 * possibility of such an attack as described in "Effective Java".
	 */
	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Must use proxy instead");
	}

	public String getVehicleId() {
		return vehicleId;
	}

	public long getTime() {
		return time;
	}

	public float getLatitude() {
		return latitude;
	}

	public float getLongitude() {
		return longitude;
	}

	/**
	 * @return Speed of vehicle in m/s, or NaN if speed not defined.
	 */
	public float getSpeed() {
		return speed;
	}

	/**
	 * @return Heading of vehicle, or NaN if speed not defined.
	 */
	public float getHeading() {
		return heading;
	}

	public String getSource() {
		return source;
	}
	
	public String getAssignmentId() {
	    return assignmentId;
	}
	
	public AssignmentType getAssignmentType() {
		return assignmentType;
	}
	
	public String getDriverId() {
		return driverId;
	}

	public String getLicensePlate() {
		return licensePlate;
	}

	public int getPassengerCount() {
		return passengerCount;
	}

	public IpcOccupancyStatus getOccupancyStatus() { return occupancyStatus; }

	@Override
	public String toString() {
		return "IpcAvl [vehicleId=" + vehicleId 
				+ ", time=" + Time.dateTimeStr(time) 
				+ ", latitude=" + latitude 
				+ ", longitude=" + longitude 
				+ ", speed=" + Geo.speedFormat(speed)
				+ ", heading=" + Geo.headingFormat(heading) 
				+ ", source=" + source
				+ ", assignmentId=" + assignmentId
				+ ", assignmentType=" + assignmentType
				+ ", driverId=" + driverId 
				+ ", licensePlate=" + licensePlate
				+ ", passengerCount=" + passengerCount
				+ ", occupancyStatus=" + occupancyStatus
				+ "]";
	}
	    

}
