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
package org.transitclock.gtfs.gtfsStructs;

import org.apache.commons.csv.CSVRecord;
import org.transitclock.utils.Time;
import org.transitclock.utils.csv.CsvBase;

/**
 * A GTFS stop_times object
 * 
 * @author SkiBu Smith
 * 
 */
public class GtfsStopTime extends CsvBase implements Comparable<GtfsStopTime> {

	private final String tripId;
	// arrivalTimeSecs is in seconds into day. Can be null
	private final Integer arrivalTimeSecs;
	// departureTimeSecs is in seconds into day. Can be null
	private final Integer departureTimeSecs;
	private final String stopId;
	private final Integer stopSequence;
	private final String stopHeadsign;
	private final String pickupType;
	private final String dropOffType;
	private final Boolean timepointStop;
	// For when a special GtfsStopTime is created using special constructor. 
	// Currently not configured in GTFS file
	private final Boolean isWaitStop;
	
	// Can be null
	private final Double shapeDistTraveled; 

	// For deleting a stop time via a supplemental stop_times.txt file
	private final Boolean delete;
	
	private final Double maxDistance;
	
	/* This is the max speed for using in calculating how far the spatial matcher 
	 * is to look along the route when the vehicle is at this point. */
	private final Double maxSpeed;

	/********************** Member Functions **************************/


	/**
	 * For creating a GtfsStopTime object from scratch
	 * 
	 * @param tripId
	 * @param arrivalTimeStr
	 * @param departureTimeStr
	 * @param stopId
	 * @param stopSequence Index of stop in trip
	 * @param timepointStop
	 * @param shapeDistTraveled
	 */
	public GtfsStopTime(String tripId, String arrivalTimeStr,
			String departureTimeStr, String stopId, int stopSequence,
			Boolean timepointStop) {
		this.tripId = tripId;
		this.arrivalTimeSecs = 
				(arrivalTimeStr != null && !arrivalTimeStr.isEmpty()) ? 
						Time.parseTimeOfDay(arrivalTimeStr) : null;
		this.departureTimeSecs = 
				(departureTimeStr != null && !departureTimeStr.isEmpty()) ?
						Time.parseTimeOfDay(departureTimeStr) : null;
		this.stopId = stopId;
		this.stopSequence = stopSequence;
		this.timepointStop = timepointStop;

		// Following values are usually not used so simply using null for them
		this.stopHeadsign = null;
		this.pickupType = null;
		this.dropOffType = null;
		this.shapeDistTraveled = null;
		
		this.delete = false;
		
		this.isWaitStop = null;
		
		this.maxDistance = null;
		this.maxSpeed = null;
		
	}
	
	/**
	 * Creates a GtfsStopTime object by reading the data from the CSVRecord.
	 * 
	 * @param record
	 * @param supplemental
	 * @param fileName
	 *            for logging errors
	 */
	public GtfsStopTime(CSVRecord record, boolean supplemental, String fileName)
			throws NumberFormatException {
		super(record, supplemental, fileName);

		tripId = getRequiredValue(record, "trip_id");

		// Convert arrival_time to seconds in day
		String arrivalTimeStr = getOptionalValue(record, "arrival_time");
		if (arrivalTimeStr != null)
			arrivalTimeSecs = Time.parseTimeOfDay(arrivalTimeStr);
		else
			arrivalTimeSecs = null;

		// Convert departure_time to seconds in day
		String deparatureTimeStr = getOptionalValue(record, "departure_time");
		if (deparatureTimeStr != null)
			departureTimeSecs = Time.parseTimeOfDay(deparatureTimeStr);
		else
			departureTimeSecs = null;

		stopId = getRequiredValue(record, "stop_id");

		String stopSequenceStr =
				getRequiredUnlessSupplementalValue(record, "stop_sequence");
		stopSequence = stopSequenceStr == null ? null : Integer.parseInt(stopSequenceStr);

		stopHeadsign = getOptionalValue(record, "stop_headsign");
		pickupType = getOptionalValue(record, "pickup_type");
		dropOffType = getOptionalValue(record, "drop_off_type");
		
		String shapeDistTraveledStr = getOptionalValue(record, "shape_dist_traveled");
		shapeDistTraveled = shapeDistTraveledStr == null || shapeDistTraveledStr.trim().isEmpty() ? 
				null : Double.parseDouble(shapeDistTraveledStr);
		
		timepointStop = getOptionalBooleanValue(record, "timepoint");
		
		delete = getOptionalBooleanValue(record, "delete");
		isWaitStop = null;
		
		maxDistance = getOptionalDoubleValue(record, "max_distance");
		
		maxSpeed= getOptionalDoubleValue(record, "max_speed");
		
		
	}

	/**
	 * For when need to convert a GtfsStopTime to a subclass. Copies the
	 * originalValues but uses newArrivalTime and newDepartureTime if they are
	 * not null. If they are null then will use the arrivalTimeSecs and
	 * departureTimeSecs from originalValues.
	 * 
	 * @param originalValues
	 *            The original values from the stop_times.txt file
	 * @param newArrivalTime
	 *            The new arrival time. If null passed in then the original time
	 *            is used.
	 * @param newDepartureTime
	 *            The new departure time. If null passed in then the original
	 *            time is used.
	 */
	protected GtfsStopTime(GtfsStopTime originalValues, Integer newArrivalTime,
			Integer newDepartureTime) {
		super(originalValues);

		tripId = originalValues.tripId;
		stopId = originalValues.stopId;
		stopSequence = originalValues.stopSequence;
		stopHeadsign = originalValues.stopHeadsign;
		pickupType = originalValues.pickupType;
		dropOffType = originalValues.dropOffType;
		shapeDistTraveled = originalValues.shapeDistTraveled;
		timepointStop = originalValues.timepointStop;
		
		// Set the appropriate arrival/departure times. If null passed in
		// then use the original values.
		Integer newArrivalTimeSecs = newArrivalTime!=null ? 
				newArrivalTime : originalValues.arrivalTimeSecs;
		Integer newDepartureTimeSecs = newDepartureTime!=null ? 
				newDepartureTime : originalValues.departureTimeSecs;
		// Make sure that the arrivalTime is not after the departure time.
		// This can happen when have weird data. Since it looks really
		// peculiar in a schedule and doesn't make any sense use the 
		// average arrival/departure time for this situation.
		if (newArrivalTimeSecs > newDepartureTimeSecs) {
			newArrivalTimeSecs = newDepartureTimeSecs = 
					(newArrivalTimeSecs + newDepartureTimeSecs)/2;
		}
		arrivalTimeSecs = newArrivalTimeSecs;
		departureTimeSecs = newDepartureTimeSecs;
		
		delete = false;
		isWaitStop = null;
		maxDistance=originalValues.maxDistance;
		maxSpeed=originalValues.maxSpeed;
	}

	/**
	 * For when need to create a GtfsStopTime that is a wait stop. Uses the
	 * values from originalValues but uses the new arrival time and sets
	 * isWaitStop to true.
	 * 
	 * @param originalValues
	 *            The original values from the stop_times.txt file
	 * @param newArrivalTimeSecs
	 *            The new arrival time.
	 */
	public GtfsStopTime(GtfsStopTime originalValues, Integer newArrivalTimeSecs) {
		super(originalValues);

		tripId = originalValues.tripId;
		stopId = originalValues.stopId;
		stopSequence = originalValues.stopSequence;
		stopHeadsign = originalValues.stopHeadsign;
		pickupType = originalValues.pickupType;
		dropOffType = originalValues.dropOffType;
		shapeDistTraveled = originalValues.shapeDistTraveled;
		timepointStop = originalValues.timepointStop;
		
		departureTimeSecs = originalValues.departureTimeSecs;
		
		delete = false;
		
		// Handle the special members
		arrivalTimeSecs = newArrivalTimeSecs;
		isWaitStop = true;
		maxDistance=originalValues.maxDistance;
		maxSpeed=originalValues.maxSpeed;
	}

	/**
	 * When combining a regular stop time with a supplemental one need to
	 * create a whole new object since this class is Immutable to make it safer
	 * to use.
	 * 
	 * @param originalStopTime
	 * @param supplementStopTime
	 */
	public GtfsStopTime(GtfsStopTime originalStopTime, GtfsStopTime supplementStopTime) {
		super(originalStopTime);
		
		// Use short variable names
		GtfsStopTime o = originalStopTime;
		GtfsStopTime s = supplementStopTime;

		this.tripId = o.tripId;
		this.maxDistance =
				s.maxDistance == null ? o.maxDistance
						: s.maxDistance;
		
		this.maxSpeed =
				s.maxSpeed == null ? o.maxSpeed
						: s.maxSpeed;
		
		this.arrivalTimeSecs =
				s.arrivalTimeSecs == null ? o.arrivalTimeSecs
						: s.arrivalTimeSecs;
		this.departureTimeSecs =
				s.departureTimeSecs == null ? o.departureTimeSecs
						: s.departureTimeSecs;
		this.stopId = o.stopId;
		this.stopSequence =
				s.stopSequence == null ? o.stopSequence : s.stopSequence;
		this.timepointStop =
				s.timepointStop == null ? o.timepointStop : s.timepointStop;
		this.stopHeadsign =
				s.stopHeadsign == null ? o.stopHeadsign : s.stopHeadsign;
		this.pickupType = s.pickupType == null ? o.pickupType : s.pickupType;
		this.dropOffType =
				s.dropOffType == null ? o.dropOffType : s.dropOffType;
		this.shapeDistTraveled =
				s.shapeDistTraveled == null ? o.shapeDistTraveled
						: s.shapeDistTraveled;
		this.delete = s.delete == null ? o.delete : s.delete;
		this.isWaitStop = s.isWaitStop == null ? o.isWaitStop : s.isWaitStop;
	}

	public Double getMaxDistance() {
		return maxDistance;
	}

	public String getTripId() {
		return tripId;
	}

	/**
	 * @return arrival time in seconds into day
	 */
	public Integer getArrivalTimeSecs() {
		return arrivalTimeSecs;
	}

	/**
	 * @return departure time in seconds into day
	 */
	public Integer getDepartureTimeSecs() {
		return departureTimeSecs;
	}

	public String getStopId() {
		return stopId;
	}

	public int getStopSequence() {
		return stopSequence;
	}

	public String getStopHeadsign() {
		return stopHeadsign;
	}

	public String getPickupType() {
		return pickupType;
	}

	public String getDropOffType() {
		return dropOffType;
	}

	/**
	 * Returns true if the stop_time is configured as a timepoint
	 * stop that should be included with schedule adherence reports.
	 * @return
	 */
	public boolean isTimepointStop() {
		return timepointStop != null && timepointStop;
	}
	
	/**
	 * 
	 * @return shape_dist_traveled or null if not set
	 */
	public Double getShapeDistTraveled() {
		return shapeDistTraveled;
	}

	public boolean shouldDelete() {
		return delete != null && delete;
	}
	
	public boolean isWaitStop() {
		return isWaitStop != null && isWaitStop;
	}
	
	@Override
	public String toString() {
		return "GtfsStopTime ["
				+ "lineNumber="	+ lineNumber + ", "
				+ (tripId != null ? "tripId=" + tripId + ", " : "")
				+ (arrivalTimeSecs != null ? "arrivalTime="
						+ Time.timeOfDayStr(arrivalTimeSecs) + ", " : "")
				+ (departureTimeSecs != null ? "departureTime="
						+ Time.timeOfDayStr(departureTimeSecs) + ", " : "")
				+ (stopId != null ? "stopId=" + stopId + ", " : "")
				+ "stopSequence=" + stopSequence + ", "
				+ (stopHeadsign != null ? "stopHeadsign=" + stopHeadsign
						+ ", " : "")
				+ (pickupType != null ? "pickupType=" + pickupType + ", " : "")
				+ (dropOffType != null ? "dropOffType=" + dropOffType + ", " : "")
				+ (shapeDistTraveled != null ? "shapeDistTraveled="
						+ shapeDistTraveled + ", " : "") 
				+ (timepointStop != null ? "timepointStop=" + timepointStop 
						+ ", ": "")
				+ "delete=" + delete + ", "
				+ (isWaitStop != null ? "isWaitStop=" + isWaitStop : "")
				+ (maxDistance != null ? "maxDistance=" + maxDistance : "")
				+ (maxSpeed != null ? "maxSpeed=" + maxSpeed : "")
				+ "]";
	}

	public Double getMaxSpeed() {
		return maxSpeed;
	}

	/**
	 * So can use Collections.sort() to sort an Array of GtfsStopTime objects by 
	 * stop sequence.
	 * 
	 * @param arg0
	 * @return
	 */
	@Override
	public int compareTo(GtfsStopTime arg0) {
		return getStopSequence() - arg0.getStopSequence();
	}

}
