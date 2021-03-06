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
package org.transitclock.db.structs;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.annotations.DynamicUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.configData.AvlConfig;
import org.transitclock.db.hibernate.HibernateUtils;
import org.transitclock.ipc.data.IpcAvl;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

import net.jcip.annotations.Immutable;

/**
 * An AvlReport is a GPS report with some additional information, such as 
 * vehicleId. 
 * <p>
 * Serializable since Hibernate requires such.
 *
 * @author SkiBu Smith
 * 
 */
@Immutable // From jcip.annoations
@Entity @DynamicUpdate 
@Table(name="AvlReports",  
       indexes = { @Index(name="AvlReportsTimeIndex", 
                          columnList="time" ) })
public class AvlReport implements Serializable {
	// vehicleId is an @Id since might get multiple AVL reports
	// for different vehicles with the same time but need a unique
	// primary key.
	@Id
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE) 
	private final String vehicleId;
	
	// Need to use columnDefinition to explicitly specify that should use 
	// fractional seconds. This column is an Id since shouldn't get two
	// AVL reports for the same vehicle for the same time.
	@Id
	@Column	
	@Temporal(TemporalType.TIMESTAMP)
	private final Date time;
	
	// There is a delay between the time an AVL report is first generated
	// till the time it is actually processed. Therefore it is useful to 
	// also keep track of the time it was processed so that can determine
	// latency. Will be null if AVL report not yet being processed.
	// Need to use columnDefinition to explicitly specify that should use 
	// fractional seconds.
	@Column	
	@Temporal(TemporalType.TIMESTAMP)
	private Date timeProcessed;
	
	@Embedded
	private final Location location;
	
	// Speed of vehicle in m/s.
	// Speed is an optional element since not always available
	// in an AVL feed. Internally it needs to be a Float and
	// be set to null when the value is not valid so that it can be stored
	// in the database. This is because Float.NaN doesn't work with JDBC
	// drivers. Externally though, such as when calling the constructor, 
	// should use Float.NaN. It is converted to a null internally.
	@Column
	private final Float speed;   // optional
	
	// Heading in degrees clockwise from north.
	// Heading is an optional element since not always available
	// in an AVL feed. It is in number of degrees clockwise from 
	// north. Internally it needs to be a Float and
	// be set to null when the value is not valid so that it can be stored
	// in the database. This is because Float.NaN doesn't work with JDBC
	// drivers. Externally though, such as when calling the constructor, 
	// should use Float.NaN. It is converted to a null internally.
	@Column
	private final Float heading; // optional
	
	// Optional text for describing the source of the AVL report
	@Column(length=SOURCE_LENGTH)
	private String source;
	
	// Can be block, trip, or route ID
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String assignmentId;  // optional

	// The type of the assignment received in the AVL feed
	public enum AssignmentType {
		UNSET, 
		BLOCK_ID,
		// For when creating schedule based predictions
		BLOCK_FOR_SCHED_BASED_PREDS,
		
		ROUTE_ID, 
		TRIP_ID, 
		TRIP_SHORT_NAME,
		// For when get bad assignment info from AVL feed
		PREVIOUS
	};
	
	@Column(length=40)
	@Enumerated(EnumType.STRING)
	private AssignmentType assignmentType;

	// Optional. This value is transient because it is usually not set. 
	// Initially only used for San Francisco Muni. Therefore not as
	// worthwhile for storing in the database.
	@Transient
	private final String leadVehicleId;
	
	// Optional
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private final String driverId;
	
	// Optional
	@Column(length=10)
	private final String licensePlate;
	
	// Optional. Set to null if passenger count info is not available
	@Column
	private final Integer passengerCount;
	
	// Optional. How full a bus is as a fraction. 0.0=empty, 1.0=at capacity.
	// This parameter is optional. Set to null if data not available.
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private final Float passengerFullness;

	// Optional.  Mirrors GTFS-RT occupancy status so it can be passed through
	// we choose ordinal sacrificing readability for size!
	@Column
	@Enumerated(EnumType.ORDINAL)
	private OccupancyStatus occupancyStatus;

	// Optional. For containing additional info for a particular feed.
	// Not declared final because setField1() is used to set values.
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String field1Name;
	
	// Optional. For containing additional info for a particular feed.
	// Not declared final because setField1() is used to set values.
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	String field1Value;
	
	
	// How long the AvlReport source field can be in db
	private static final int SOURCE_LENGTH = 10;
	
	private static final Logger logger = 
			LoggerFactory.getLogger(AvlReport.class);	

	// Needed because serializable so can transmit using JMS or RMI
	private static final long serialVersionUID = 92384928349824L;
	
	/********************** Member Functions **************************/

	/**
	 * Hibernate requires a no-args constructor for reading data.
	 * So this is an experiment to see what can be done to satisfy
	 * Hibernate but still have an object be immutable. Since
	 * this constructor is only intended to be used by Hibernate
	 * is is declared protected, since that still works. That way
	 * others won't accidentally use this inappropriate constructor.
	 * And yes, it is peculiar that even though the members in this
	 * class are declared final that Hibernate can still create an
	 * object using this no-args constructor and then set the fields.
	 * Not quite as "final" as one might think. But at least it works.
	 */
	protected AvlReport() {
		vehicleId = null;
		time = null;
		location = null;
		speed = null;
		heading = null;
		source = null;
		assignmentId = null;
		assignmentType = AssignmentType.UNSET;
		leadVehicleId = null;
		driverId = null;
		licensePlate = null;
		timeProcessed = null;
		passengerCount = null;
		passengerFullness = null;
		field1Name = null;
		field1Value = null;
	}
	
	/**
	 * Constructor for an AvlReport object that is not yet being processed.
	 * Since not yet being processed timeProcessed is set to null.
	 * 
	 * @param vehicleId
	 *            ID of the vehicle
	 * @param time
	 *            Epoch time in msec of GPS report (not when processed)
	 * @param lat
	 *            Latitude in decimal degrees
	 * @param lon
	 *            Longitude in decimal degrees
	 * @param speed
	 *            Speed of vehicle in m/s. Should be set to Float.NaN if speed
	 *            not available
	 * @param heading
	 *            Heading of vehicle in degrees clockwise from north. Should be
	 *            set to Float.NaN if speed not available
	 * @param source
	 *            Text describing the source of the report
	 */
	public AvlReport(String vehicleId, long time, double lat, double lon, 
			float speed, float heading, String source) {
		// Store the values
		this.vehicleId = vehicleId;
		this.time = new Date(time);
		this.location = new Location(lat, lon);
		// DB requires null instead of NaN
		this.speed = Float.isNaN(speed) ? null : speed;
		this.heading = Float.isNaN(heading) ? null : heading;
		this.source = sized(source);
		this.assignmentId = null;
		this.assignmentType = AssignmentType.UNSET;
		this.leadVehicleId = null;
		this.driverId = null;
		this.licensePlate = null;
		this.passengerCount = null;
		this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;
		
		// Don't yet know when processed so set timeProcessed to null
		this.timeProcessed = null;
	}
	
	/**
	 * Constructor for an AvlReport object that is not yet being processed.
	 * Since not yet being processed timeProcessed is set to null.
	 * 
	 * @param vehicleId
	 *            ID of the vehicle
	 * @param time
	 *            Epoch time in msec of GPS report (not when processed)
	 * @param location
	 * @param speed
	 *            Speed of vehicle in m/s. Should be set to Float.NaN if speed
	 *            not available
	 * @param heading
	 *            Heading of vehicle in degrees clockwise from north. Should be
	 *            set to Float.NaN if speed not available
	 * @param source
	 *            Text describing the source of the report. Can only be
	 *            SOURCE_LENGTH (10) characters long
	 */
	public AvlReport(String vehicleId, long time, Location location, 
			float speed, float heading, String source) {
		// Store the values
		this.vehicleId = vehicleId;
		this.time = new Date(time);
		this.location = location;
		// DB requires null instead of NaN
		this.speed = Float.isNaN(speed) ? null : speed;
		this.heading = Float.isNaN(heading) ? null : heading;
		this.source = sized(source);
		this.assignmentId = null;
		this.assignmentType = AssignmentType.UNSET;
		this.leadVehicleId = null;
		this.driverId = null;
		this.licensePlate = null;
		this.passengerCount = null;
		this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;
		
		// Don't yet know when processed so set timeProcessed to null
		this.timeProcessed = null;
	}

	/**
	 * @param vehicleId
	 *            ID of the vehicle
	 * @param time
	 *            Epoch time in msec of GPS report (not when processed) For when
	 *            speed and heading are not valid. They are set to Float.NaN .
	 *            Since not yet being processed timeProcessed is set to null.
	 * 
	 * @param vehicleId
	 * @param time
	 *            GPS time, in number of milliseconds since the standard base
	 *            time known as "the epoch", namely January 1, 1970, 00:00:00
	 *            GMT.
	 * @param lat
	 *            Latitude in decimal degrees
	 * @param lon
	 *            Longitude in decimal degrees
	 * @param source
	 *            Text describing the source of the report. Can only be
	 *            SOURCE_LENGTH (10) characters long
	 */
	public AvlReport(String vehicleId, long time, double lat, double lon,
			String source) {
		// Store the values
		this.vehicleId = vehicleId;
		this.time = new Date(time);;
		this.location = new Location(lat, lon);
		this.speed = null;
		this.heading = null;
		this.source = sized(source);
		this.assignmentId = null;
		this.assignmentType = AssignmentType.UNSET;
		this.leadVehicleId = null;
		this.driverId = null;
		this.licensePlate = null;
		this.passengerCount = null;
		this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;

		// Don't yet know when processed so set timeProcessed to null
		this.timeProcessed = null;
	}

	/**
	 * For when speed and heading are not valid. They are set to Float.NaN .
	 * Since not yet being processed timeProcessed is set to null.
	 * 
	 * @param vehicleId
	 * @param time
	 * @param location
	 * @param source
	 *            Text describing the source of the report. Can only be
	 *            SOURCE_LENGTH (10) characters long
	 */
	public AvlReport(String vehicleId, long time, Location location,
			String source) {
		// Store the values
		this.vehicleId = vehicleId;
		this.time = new Date(time);
		this.location = location;
		this.speed = null;
		this.heading = null;
		this.source = sized(source);
		this.assignmentId = null;
		this.assignmentType = AssignmentType.UNSET;
		this.leadVehicleId = null;
		this.driverId = null;
		this.licensePlate = null;
		this.passengerCount = null;
		this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;
		
		// Don't yet know when processed so set timeProcessed to null
		timeProcessed = null;
	}

	/**
	 * Constructor for an AvlReport object that is not yet being processed.
	 * Since not yet being processed timeProcessed is set to null.
	 * 
	 * @param vehicleId
	 *            identifier of vehicle
	 * @param time
	 *            epoch time in msecs since 1970
	 * @param lat
	 *            latitude in decimal degrees
	 * @param lon
	 *            longitude in decimal degrees
	 * @param speed
	 *            Speed of vehicle in m/s. Should be set to Float.NaN if speed
	 *            not available
	 * @param heading
	 *            Heading of vehicle in degrees clockwise from north. Should be
	 *            set to Float.NaN if speed not available
	 * @param source
	 *            Text describing the source of the report. Can only be
	 *            SOURCE_LENGTH (10) characters long
	 * @param leadVehicleId
	 *            Optional value. Set to null if not available.
	 * @param driverId
	 *            Optional value. Set to null if not available.
	 * @param licensePlate
	 *            Optional value. Set to null if not available.
	 * @param passengerCount
	 *            Optional value. Set to the number of passengers on vehicles.
	 *            Set to null if not available.
	 * @param passengerFullness
	 *            Optional Value. Fractional fullness of vehicle. 0.0=empty,
	 *            1.0=full. Set to Float.NaN if data not available.
	 */
	public AvlReport(String vehicleId, long time, double lat, double lon,
			float speed, float heading, String source, String leadVehicleId,
			String driverId, String licensePlate, Integer passengerCount,
			float passengerFullness) {
		// Store the values
		this.vehicleId = vehicleId;
		this.time = new Date(time);
		this.location = new Location(lat, lon);
		// DB requires null instead of NaN
		this.speed = Float.isNaN(speed) ? null : speed;
		this.heading = Float.isNaN(heading) ? null : heading;
		this.source = sized(source);
		this.assignmentId = null;
		this.assignmentType = AssignmentType.UNSET;
		this.leadVehicleId = leadVehicleId;
		this.driverId = driverId;
		this.licensePlate = licensePlate;
		this.passengerCount = passengerCount;
		if (!Float.isNaN(passengerFullness))
			this.passengerFullness = passengerFullness;
		else
			this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;
		
		// Don't yet know when processed so set timeProcessed to null
		this.timeProcessed = null;
	}

	/**
	 * For converting a RMI IpcAvl object to a regular AvlReport.
	 * 
	 * @param ipcAvl
	 */
	public AvlReport(IpcAvl ipcAvl) {
		this.vehicleId = ipcAvl.getVehicleId();
		this.time = new Date(ipcAvl.getTime());
		this.location = new Location(ipcAvl.getLatitude(), ipcAvl.getLongitude());
		this.speed = Float.isNaN(ipcAvl.getSpeed()) ? null : ipcAvl.getSpeed();
		this.heading = Float.isNaN(ipcAvl.getHeading()) ? null : ipcAvl.getHeading();
		this.source = sized(ipcAvl.getSource());
		this.assignmentId = ipcAvl.getAssignmentId();
		this.assignmentType = ipcAvl.getAssignmentType();
		this.leadVehicleId = null;
		this.driverId = null;
		this.licensePlate = null;
		this.passengerCount = null;
		this.passengerFullness = null;
		this.field1Name = null;
		this.field1Value = null;
		
		// Don't yet know when processed so set timeProcessed to null
		this.timeProcessed = null;
	}
	
	/**
	 * Makes a copy of the AvlReport but uses the new time passed in.
	 * Useful for creating a new AvlReport when AVL timeout occurs
	 * when vehicle on a layover.
	 * 
	 * @param toCopy
	 *            The AvlReport to copy (except for the AVL time)
	 * @param newTime
	 *            The AVL time to use
	 */
	public AvlReport(AvlReport toCopy, Date newTime) {
		this.vehicleId = toCopy.vehicleId;
		this.time = newTime;
		this.location = toCopy.location;
		this.speed = toCopy.speed;
		this.heading = toCopy.heading;
		this.source = toCopy.source;
		this.assignmentId = toCopy.assignmentId;
		this.assignmentType = toCopy.assignmentType;
		this.leadVehicleId = toCopy.leadVehicleId;
		this.driverId = toCopy.driverId;
		this.licensePlate = toCopy.licensePlate;
		this.timeProcessed = toCopy.timeProcessed;
		this.passengerCount = toCopy.passengerCount;
		this.passengerFullness = toCopy.passengerFullness;
		this.field1Name = toCopy.field1Name;
		this.field1Value = toCopy.field1Value;
	}
	
	/**
	 * Makes a copy of the AvlReport but uses the new assignment info passed in.
	 * Useful for creating a new AvlReport when a bad assignment is received
	 * since one can simply create a new AvlReport with the new assignment info.
	 * 
	 * @param toCopy
	 *            The AvlReport to copy (except for the AVL time)
	 * @param assignmentId
	 *            The assignment to use
	 * @param assignmentType
	 *            How the assignment was done
	 */
	public AvlReport(AvlReport toCopy, String assignmentId,
			AssignmentType assignmentType) {
		this.vehicleId = toCopy.vehicleId;
		this.time = toCopy.time;
		this.location = toCopy.location;
		this.speed = toCopy.speed;
		this.heading = toCopy.heading;
		this.source = toCopy.source;
		this.assignmentId = assignmentId;
		this.assignmentType = assignmentType;
		this.leadVehicleId = toCopy.leadVehicleId;
		this.driverId = toCopy.driverId;
		this.licensePlate = toCopy.licensePlate;
		this.timeProcessed = toCopy.timeProcessed;
		this.passengerCount = toCopy.passengerCount;
		this.passengerFullness = toCopy.passengerFullness;
		this.field1Name = toCopy.field1Name;
		this.field1Value = toCopy.field1Value;
	}
	
	/**
	 * For truncating the source member to size allowed in db. This way don't
	 * later get an exception when trying to write an AvlReport to the db.
	 * 
	 * @param source
	 * @return The original source string, but truncated to SOURCE_LENGTH
	 */
	private static String sized(String source) {
		if (source == null || source.length() <= SOURCE_LENGTH)
			return source;
		
		return source.substring(0, SOURCE_LENGTH);
	}
	
	/**
	 * Makes sure that the members of this class all have reasonable
	 * values. 
	 * @return null if there are no problems. An error message if 
	 * there are problems with the data.
	 */
	public String validateData() {		
		String errorMsg = "";
		
		// Make sure vehicleId is set
		if (vehicleId == null) 
			errorMsg += "VehicleId is null. ";
		else if (vehicleId.length() == 0)
			errorMsg += "VehicleId is empty string. ";
			
		// Make sure GPS time is OK
		long currentTime = System.currentTimeMillis();
		if (time.getTime() < currentTime - 10 * Time.MS_PER_YEAR)
			errorMsg += "Time of " + Time.dateTimeStr(time) + " is more than 10 years old. ";
		if (time.getTime() > currentTime + 1 * Time.MS_PER_MIN) 
			errorMsg += "Time of " + Time.dateTimeStr(time) + " is more than 1 minute into the future. ";
		
		// Make sure lat/lon is OK
		double lat = location.getLat();
		double lon = location.getLon();
		if (lat < AvlConfig.getMinAvlLatitude()) 
			errorMsg += "Latitude of " + lat + " is less than the parameter " + 
					AvlConfig.getMinAvlLatitudeParamName() + " which is set to " + 
					AvlConfig.getMinAvlLatitude() + " . ";
		if (lat > AvlConfig.getMaxAvlLatitude()) 
			errorMsg += "Latitude of " + lat + " is greater than the parameter " + 
					AvlConfig.getMaxAvlLatitudeParamName() + " which is set to " + 
					AvlConfig.getMaxAvlLatitude() + " . ";
		if (lon < AvlConfig.getMinAvlLongitude()) 
			errorMsg += "Longitude of " + lon + " is less than the parameter " + 
					AvlConfig.getMinAvlLongitudeParamName() + " which is set to " + 
					AvlConfig.getMinAvlLongitude() + " . ";
		if (lon > AvlConfig.getMaxAvlLongitude()) 
			errorMsg += "Longitude of " + lon + " is greater than the parameter " + 
					AvlConfig.getMaxAvlLongitudeParamName() + " which is set to " + 
					AvlConfig.getMaxAvlLongitude() + " . ";
				
		// Make sure speed is OK
		if (isSpeedValid()) {
			if (speed < 0.0f)
				errorMsg += "Speed of " + speed + " is less than zero. ";
			if (speed > AvlConfig.getMaxAvlSpeed()) {
				errorMsg += "Speed of " + speed + 
						"m/s is greater than maximum allowable speed of " + 
						AvlConfig.getMaxAvlSpeed() + "m/s. ";
			}				
		}
		
		// Make sure heading is OK
		if (isHeadingValid()) {
			if (heading < 0.0f)
				errorMsg += "Heading of " + heading + " degrees is less than 0.0 degrees. ";
			if (heading > 360.0f)
				errorMsg += "Heading of " + heading + 
						" degrees is greater than 360.0 degrees. ";			
		}
		
		
		// Return the error message if any
		if (errorMsg.length() > 0)
			return errorMsg;
		else 
			return null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((assignmentId == null) ? 0 : assignmentId.hashCode());
		result = prime * result
				+ ((assignmentType == null) ? 0 : assignmentType.hashCode());
		result = prime * result
				+ ((driverId == null) ? 0 : driverId.hashCode());
		result = prime * result + ((heading == null) ? 0 : heading.hashCode());
		result = prime * result
				+ ((licensePlate == null) ? 0 : licensePlate.hashCode());
		result = prime * result
				+ ((location == null) ? 0 : location.hashCode());
		result = prime * result + ((speed == null) ? 0 : speed.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
		result = prime * result
				+ ((timeProcessed == null) ? 0 : timeProcessed.hashCode());
		result = prime * result
				+ ((vehicleId == null) ? 0 : vehicleId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AvlReport other = (AvlReport) obj;
		if (assignmentId == null) {
			if (other.assignmentId != null)
				return false;
		} else if (!assignmentId.equals(other.assignmentId))
			return false;
		if (assignmentType != other.assignmentType)
			return false;
		if (driverId == null) {
			if (other.driverId != null)
				return false;
		} else if (!driverId.equals(other.driverId))
			return false;
		if (heading == null) {
			if (other.heading != null)
				return false;
		} else if (!heading.equals(other.heading))
			return false;
		if (licensePlate == null) {
			if (other.licensePlate != null)
				return false;
		} else if (!licensePlate.equals(other.licensePlate))
			return false;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		if (passengerCount == null) {
			if (other.passengerCount != null)
				return false;
		} else if (!passengerCount.equals(other.passengerCount))
			return false;
		if (passengerFullness == null) {
			if (other.passengerFullness != null)
				return false;
		} else if (!passengerFullness.equals(other.passengerFullness))
			return false;
		if (speed == null) {
			if (other.speed != null)
				return false;
		} else if (!speed.equals(other.speed))
			return false;
		if (time == null) {
			if (other.time != null)
				return false;
		} else if (!time.equals(other.time))
			return false;
		if (timeProcessed == null) {
			if (other.timeProcessed != null)
				return false;
		} else if (!timeProcessed.equals(other.timeProcessed))
			return false;
		if (vehicleId == null) {
			if (other.vehicleId != null)
				return false;
		} else if (!vehicleId.equals(other.vehicleId))
			return false;
		return true;
	}

	public String getVehicleId() {
		return vehicleId;
	}
	
	/**
	 * @return The GPS time of the AVL report in msec epoch time
	 */
	public long getTime() {
		return time.getTime();
	}
	
	/**
	 * @return A Date object containing the GPS time
	 */
	public Date getDate() {
		return time;
	}
	
	/**
	 * @return The time that the AVL report was received and processed.
	 */
	public long getTimeProcessed() {
		return timeProcessed.getTime();
	}
	
	public Location getLocation() {
		return location;
	}
	
	public double getLat() {
		return location.getLat();
	}
	
	public double getLon() {
		return location.getLon();
	}
	
	/**
	 * @return Speed of vehicle in meters per second. Returns Float.NaN if speed
	 *         is not valid.
	 */
	public float getSpeed() {
		return speed==null? Float.NaN : speed;
	}
	
	/**
	 * Heading of vehicles in degrees. The heading can sometimes
	 * be invalid. Though internally an invalid heading is stored
	 * as null it is returned by this method as NaN so that can
	 * return a float. If speed is below 
	 * AvlConfig.minSpeedForValidHeading() then will also return
	 * NaN.
	 * 
	 * @return Heading of vehicles in degrees. If heading not set
	 * or if speed below minimum then Float.NaN is returned.
	 */
	public float getHeading() {
		// If heading not available then return NaN
		if (heading == null)
			return Float.NaN;

		// The heading is valid. If there is a valid speed available and
		// but  it is not high enough to make the heading valid  
		// then return NaN.
		if (speed != null && speed < AvlConfig.minSpeedForValidHeading()) {
			return Float.NaN;
		} else {
			// Heading is valid so return it
			return heading;
		}
	}
	
	/**
	 * The source of the AVL report
	 * 
	 * @return
	 */
	public String getSource() {
		return source;
	}
	
	
	/**
	 * Override the source set by constructor
	 */
	public void setSource(String source) {
		this.source=sized(source);
	}

	/**
	 * GTFS-RT inspired levels of crowding information.
	 * @return enumeration
	 */
	public OccupancyStatus getOccupancyStatus() {
		return occupancyStatus;
	}

	/**
	 * GTFS-RT inspired levels of crowding information.
	 */
	public void setOccupancyStatus(OccupancyStatus occupancyStatus) {
		this.occupancyStatus = occupancyStatus;
	}



	/**
	 * Returns how many msec elapsed between the GPS fix was generated
	 * to the time it was finally processed. Returns 0 if timeProcessed
	 * was never set.
	 * @return 
	 */
	public long getLatency() {
		// If never processed then return 0.
		if (timeProcessed == null)
			return 0;
		
		return timeProcessed.getTime() - time.getTime();
	}
	
	/**
	 * For some AVL systems speed is not available and therefore cannot be used.  
	 * @return
	 */
	public boolean isSpeedValid() {
		return speed != null;
	}
	
	/**
	 * For some AVL systems heading is not available and therefore cannot be used.  
	 * @return
	 */
	public boolean isHeadingValid() {
		return heading != null;
	}

	/**
	 * Returns the assignment ID if it is set. If it is not set then
	 * null is returned.
	 * 
	 * @return
	 */
	public String getAssignmentId() {
		return assignmentId;
	}
	
	public AssignmentType getAssignmentType() {
		return assignmentType;
	}
	
	/**
	 * Returns true if AVL report indicates that assignment is a block
	 * assignment type such as AssignmentType.BLOCK_ID or
	 * AssignmentType.BLOCK_FOR_SCHED_BASED_PREDS.
	 * 
	 * @return true if block assignment
	 */
	public boolean isBlockIdAssignmentType() {
		return assignmentType == AssignmentType.BLOCK_ID 
				|| assignmentType == AssignmentType.BLOCK_FOR_SCHED_BASED_PREDS;
	}
	
	public boolean isTripIdAssignmentType() {
		return assignmentType == AssignmentType.TRIP_ID;
	}
	
	public boolean isTripShortNameAssignmentType() {
		return assignmentType == AssignmentType.TRIP_SHORT_NAME;
	}
	
	public boolean isRouteIdAssignmentType() {
		return assignmentType == AssignmentType.ROUTE_ID;
	}
	
	private static boolean unpredictableAssignmentsPatternInitialized = false;
	private static Pattern regExPattern = null;
	
	/**
	 * Returns true if the assignment specified matches the regular expression
	 * for unpredictable assignments.
	 * 
	 * @param assignment
	 * @return true if assignment matches regular expression
	 */
	public static boolean matchesUnpredictableAssignment(String assignment) {
		if (unpredictableAssignmentsPatternInitialized == false) {
			String regEx = AvlConfig.getUnpredictableAssignmentsRegEx();
			if (regEx != null && !regEx.isEmpty()) {
				regExPattern = Pattern.compile(regEx);
			}
			unpredictableAssignmentsPatternInitialized = true;
		}
		
		if (regExPattern == null)
			return false;
		
		return regExPattern.matcher(assignment).matches();
	}
	
	/**
	 * Returns whether assignment information was set in the AVL data and that
	 * assignment is valid. An assignment is not valid if it is configured to be
	 * invalid. Examples of such include training vehicles, support vehicles,
	 * and simply vehicles set to a special assignment such as 9999 for sfmta.
	 * 
	 * @return true if has assignment and it is valid. Otherwise false.
	 */
	public boolean hasValidAssignment() {       
		if (assignmentType != AssignmentType.UNSET && 
				matchesUnpredictableAssignment(assignmentId))
			logger.debug("For vehicleId={} was assigned to \"{}\" but that " +
					"assignment is not considered valid due to " +
					"transitclock.avl.unpredictableAssignmentsRegEx being set " +
					"to \"{}\"", 
					vehicleId, assignmentId, 
					AvlConfig.getUnpredictableAssignmentsRegEx());
		
		return assignmentType != AssignmentType.UNSET && 
				!matchesUnpredictableAssignment(assignmentId);
	}
	
	/**
	 * Stores the assignment information as part of this AvlReport. If vehicle
	 * is to not have an assignment need to set assignmentId to null and set
	 * assignmentType to AssignmentType.UNSET.
	 * 
	 * @param assignmentId
	 * @param assignmentType
	 */
	public void setAssignment(String assignmentId, 
			AssignmentType assignmentType) {
		// Make sure don't set to invalid values
		if (assignmentId == null && assignmentType != AssignmentType.UNSET) {
			logger.error("Tried to use setAssignment() to set assignment to "
					+ "null without also setting assignmentType to UNSET");
			return;
		}

		this.assignmentId = assignmentId;
		this.assignmentType = assignmentType;
	}
	
	/**
	 * Returns the ID of the leading vehicle if this is an AVL report for a
	 * non-lead vehicle in a multi-car consist. Otherwise returns null.
	 * 
	 * @return
	 */
	public String getLeadVehicleId() {
		return leadVehicleId;
	}
	
	/**
	 * For containing additional info as part of AVL feed that is
	 * specific to a particular feed or a new element.
	 * 
	 * @param name
	 * @param value
	 */
	public void setField1(String name, String value) {
		field1Name = name;
		field1Value = value;
	}
	
	/**
	 * If this is a vehicle in a multi-car consist and it is not the lead
	 * vehicle then shouldn't generate redundant arrival/departure times,
	 * predictions etc.
	 * 
	 * @return True if non-lead car in multi-car consist
	 */
	public boolean ignoreBecauseInConsist() {
		return leadVehicleId != null;
	}
	
	public String getDriverId() {
		return driverId;
	}
	
	public String getLicensePlate() {
		return licensePlate;
	}
		
	/**
	 * Returns the passenger count, as obtained from AVL feed.
	 * If passenger count not available returns -1.
	 * @return
	 */
	public int getPassengerCount() {
		if (passengerCount != null)
			return passengerCount;
		else
			return -1;
	}
	
	/**
	 * Returns whether the passenger count is valid.
	 * 
	 * @return true if count is valid.
	 */
	public boolean isPassengerCountValid() {
		return passengerCount != null;
	}
	
	/**
	 * Fraction indicating how full a vehicle is. Returns NaN if info not 
	 * available.
	 * 
	 * @return
	 */
	public float getPassengerFullness() {
		if (passengerFullness != null)
			return passengerFullness;
		else
			return Float.NaN;
	}
	
	/**
	 * Returns whether the passenger fullness is valid.
	 * @return true if passenger fullness is valid.
	 */
	public boolean isPassengerFullnessValid() {
		return passengerFullness != null;
	}
	
	/**
	 * Updates the object to record the current time as the time that
	 * the data was actually processed.
	 */
	public void setTimeProcessed() {
		timeProcessed = new Date(Core.getInstance().getSystemTime());
		
	}
	
	public String getField1Name() {
		return field1Name;
	}

	public String getField1Value() {
		return field1Value;
	}

	/**
	 * Returns true if the AVL report is configured to indicate that it was
	 * created to generate schedule based predictions.
	 * 
	 * @return true if for schedule based predictions
	 */
	public boolean isForSchedBasedPreds() {
		return assignmentType == AssignmentType.BLOCK_FOR_SCHED_BASED_PREDS;
	}
	
	@Override
	public String toString() {
		return "AvlReport [" +
				"vehicleId=" + vehicleId +
				", time=" + Time.dateTimeStrMsec(time) + 
				(timeProcessed==null? "" : ", timeProcessed=" + 
					Time.dateTimeStrMsec(timeProcessed)) +
				", location=" + location +
				", speed=" + Geo.speedFormat(getSpeed()) +
				", heading=" + Geo.headingFormat(getHeading()) + 
				", source=" + source +
				", assignmentId=" + assignmentId +
				", assignmentType=" + assignmentType +
				(leadVehicleId==null? "" : ", leadVehicleId=" + leadVehicleId) +
				(driverId==null? "" : ", driverId=" + driverId) +
				(licensePlate==null? "" : ", licensePlate=" + licensePlate) +	
				(passengerCount==null? "" : ", passengerCount=" + passengerCount) +
				(passengerFullness==null? "" : ", passengerFullness=" + passengerFullness) +
				(field1Name==null? "" : ", field1Name=" + field1Name) +
				(field1Value==null? "" : ", field1Value=" + field1Value) +
				"]";
	}

	/**
	 * Gets list of AvlReports from database for the time span specified.
	 * 
	 * @param beginTime
	 * @param endTime
	 * @param vehicleId
	 *            Optional. If not null then will only return results for that
	 *            vehicle
	 * @param clause
	 * 				Optional. If not null then the clause, such as "ORDER BY time"
	 * will be added to the hql statement.
	 * @return List of AvlReports or null if an exception is thrown
	 */
	public static List<AvlReport> getAvlReportsFromDb(
			Date beginTime, 
			Date endTime, 
			String vehicleId,
			String clause) {
		// Sessions are not threadsafe so need to create a new one each time.
		// They are supposed to be lightweight so this should be OK.
		Session session = HibernateUtils.getSession();
		
		// Create the query. Table name is case sensitive!
		String hql = "FROM AvlReport " +
				"    WHERE time >= :beginDate " +
				"      AND time < :endDate"; 
		if (vehicleId != null && !vehicleId.isEmpty())
			hql += " AND vehicleId=:vehicleId";
		if (clause != null)
			hql += " " + clause;
		Query query = session.createQuery(hql);
		
		// Set the parameters
		if (vehicleId != null && !vehicleId.isEmpty())
			query.setString("vehicleId", vehicleId);
		query.setTimestamp("beginDate", beginTime);
		query.setTimestamp("endDate", endTime);
		
		try {
			@SuppressWarnings("unchecked")
			List<AvlReport> avlReports = query.list();
			return avlReports;
		} catch (HibernateException e) {
			// Log error to the Core logger
			Core.getLogger().error(e.getMessage(), e);
			return null;
		} finally {
			// Clean things up. Not sure if this absolutely needed nor if
			// it might actually be detrimental and slow things down.
			session.close();
		}
	}

}
