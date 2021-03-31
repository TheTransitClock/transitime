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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.CallbackException;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.collection.internal.PersistentList;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.classic.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.db.hibernate.HibernateUtils;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.gtfs.TitleFormatter;
import org.transitclock.gtfs.gtfsStructs.GtfsTrip;
import org.transitclock.utils.Time;


/**
 * Describes a GTFS trip but also includes travel time information.
 * <p>
 * Serializable since Hibernate requires such.
 * <p>
 * Implements Lifecycle so that can have the onLoad() callback be called when
 * reading in data so that can intern() member strings. In order to do this the
 * String members could not be declared as final since they are updated after
 * the constructor is called. 
 * 
 * @author SkiBu Smith
 *
 */
@Entity @DynamicUpdate @Table(name="Trips")
public class Trip implements Lifecycle, Serializable {

	@Column 
	@Id	
	private final int configRev;

	@Column(length=HibernateUtils.DEFAULT_ID_SIZE) 
	@Id 
	private String tripId;
	
	// The startTime needs to be an Id column because GTFS frequencies.txt
	// file can be used to define multiple trips with the same trip ID. 
	// It is in number of seconds into the day.
	// Not declared as final because only used for frequency based trips.
	@Column
	@Id 
	private Integer startTime;
	
	// Used by some agencies to identify the trip in the AVL feed
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE) 
	private String tripShortName;
	
	// Number of seconds into the day.
	// Not final because only used for frequency based trips.
	@Column
	private Integer endTime;
	
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String directionId;
	
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String routeId;
	
	// Route short name is also needed because some agencies such as SFMTA
	// change the route IDs when making schedule changes. But we need a 
	// consistent route identifier for certain things, such as bookmarking
	// prediction pages or for running schedule adherence reports over
	// time. For where need a route identifier that is consistent over time
	// it can be best to use the routeShortName.
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String routeShortName;
	
	// So can determine all the stops and stopPaths associated with trip
	// Note that needs to be FetchType.EAGER because otherwise get a 
	// Javassist HibernateException.
	@ManyToOne(fetch=FetchType.EAGER)
	@Cascade({CascadeType.SAVE_UPDATE})
	private TripPattern tripPattern;

	// Use FetchType.EAGER so that all travel times are efficiently read in
	// when system is started up.
	// Note that needs to be FetchType.EAGER because otherwise get a Javassist 
	// HibernateException.
	//
	// We are sharing travel times so need a ManyToOne mapping
	@ManyToOne(fetch=FetchType.EAGER)
	@Cascade({CascadeType.SAVE_UPDATE})
	private TravelTimesForTrip travelTimes;
	
	// Contains schedule time for each stop as obtained from GTFS 
	// stop_times.txt file. Useful for determining schedule adherence.
	@ElementCollection
  @OrderColumn
	private final List<ScheduleTime> scheduledTimesList =
			new ArrayList<ScheduleTime>();
	
	// For non-scheduled blocks where vehicle runs a trip as a continuous loop 
	@Column
	private final boolean noSchedule; 

	// For when times are determined via the GTFS frequency.txt file and
	// exact_times for the trip is set to true. Indicates that the schedule
	// times were determined using the trip frequency and start_time.
	@Column
	private final boolean exactTimesHeadway;
	
	// Service ID for the trip
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String serviceId;
	
	// The GTFS trips.txt trip_headsign if set. Otherwise will get from the
	// stop_headsign, if set, from the first stop of the trip. Otherwise null.
	@Column(length=TripPattern.HEADSIGN_LENGTH)
	private String headsign;
	
	// From GTFS trips.txt block_id if set. Otherwise the trip_id.
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String blockId;
	
	// The GTFS trips.txt shape_id
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private String shapeId;

	@Transient
	private Route route;
	
	// Note: though trip_short_name and wheelchair_accessible are available
	// as part of the GTFS spec and in a GtfsTrip object, they are not
	// included here because currently don't understand how best to use them
	// for RTPI. Route and shape can be determined from the TripPattern so don't
	// need to be here.
	
	// Hibernate requires class to be serializable because has composite Id
	private static final long serialVersionUID = -23135771144135812L;

	private static final Logger logger = 
			LoggerFactory.getLogger(Trip.class);

	/********************** Member Functions **************************/

	/**
	 * Constructs Trip object from GTFS data.
	 * <p>
	 * Does not set startTime nor endTime. Those are set separately using
	 * addScheduleTimes(). Also doesn't set travel times. Those are set
	 * separately using setTravelTimes().
	 * 
	 * @param configRev
	 * @param gtfsTrip
	 *            The GTFS data describing the trip
	 * @param properRouteId
	 *            The routeId, but can actually be the ID of the parent route.
	 * @param routeShortName
	 *            Needed to provide a route identifier that is consistent over
	 *            schedule changes.
	 * @param unprocessedHeadsign
	 *            the headsign from the GTFS trips.txt file, or if that is not
	 *            available then the stop_headsign from the GTFS stop_times.txt
	 *            file.
	 * @param titleFormatter
	 *            So can fix titles associated with trip
	 */
	public Trip(int configRev, GtfsTrip gtfsTrip, String properRouteId,
			String routeShortName, String unprocessedHeadsign, TitleFormatter titleFormatter) {
		this.configRev = configRev;
		this.tripId = gtfsTrip.getTripId();
		this.tripShortName = gtfsTrip.getTripShortName();
		this.directionId = gtfsTrip.getDirectionId();
		this.routeId =
				properRouteId != null ? properRouteId : gtfsTrip.getRouteId();
		this.routeShortName = routeShortName;
		this.serviceId = gtfsTrip.getServiceId();
		this.headsign =
				processedHeadsign(unprocessedHeadsign, routeId,
						titleFormatter);
		// Make sure headsign not too long for db
		if (this.headsign.length() > TripPattern.HEADSIGN_LENGTH) {
			this.headsign = 
					this.headsign.substring(0, TripPattern.HEADSIGN_LENGTH);
		}

		// block column is optional in GTFS trips.txt file. Best can do when
		// block ID is not set is to use the trip short name or the trip id 
		// as the block. MBTA uses trip short name in the feed so start with
		// that.
		String theBlockId = gtfsTrip.getBlockId();
		if (theBlockId == null) {
			theBlockId = gtfsTrip.getTripShortName();
			if (theBlockId == null)
				theBlockId = gtfsTrip.getTripId();
		}
		this.blockId = theBlockId;
		this.shapeId = gtfsTrip.getShapeId();
		this.noSchedule = false;

		// Not a frequency based trip with an exact time so remember such
		this.exactTimesHeadway = false;
	}

	/**
	 * Creates a copy of the Trip object but adjusts the startTime, endTime, and
	 * scheduledTimesMap according to the frequenciesBasedStartTime. This is
	 * used when the frequencies.txt file specifies exact_times for a trip.
	 * 
	 * @param tripFromStopTimes
	 * @param frequenciesBasedStartTime
	 */
	public Trip(Trip tripFromStopTimes, int frequenciesBasedStartTime) {
		this.configRev = tripFromStopTimes.configRev;
		this.tripId = tripFromStopTimes.tripId;
		this.tripShortName = tripFromStopTimes.tripShortName;
		this.directionId = tripFromStopTimes.directionId;
		this.routeId = tripFromStopTimes.routeId;
		this.routeShortName = tripFromStopTimes.routeShortName;
		this.serviceId = tripFromStopTimes.serviceId;
		this.headsign = tripFromStopTimes.headsign;
		this.shapeId = tripFromStopTimes.shapeId;
		this.tripPattern = tripFromStopTimes.tripPattern;
		this.travelTimes = tripFromStopTimes.travelTimes;
		
		// Set the updated start and end times by using the frequencies
		// based start time.
		this.startTime = tripFromStopTimes.startTime + frequenciesBasedStartTime;
		this.endTime = tripFromStopTimes.endTime + frequenciesBasedStartTime;
		
		// Since frequencies being used for configuration we will have multiple
		// trips with the same ID. But need a different block ID for each one.
		// Therefore use the original trip's block ID but then append the 
		// start time as a string to make it unique. 
		this.blockId = tripFromStopTimes.blockId + "_" + 
				Time.timeOfDayStr(this.startTime);

		// Set the scheduledTimesMap by using the frequencies based start time
		for (ScheduleTime schedTimeFromStopTimes: tripFromStopTimes.scheduledTimesList) {
			Integer arrivalTime = null;
			if (schedTimeFromStopTimes.getArrivalTime() != null)
				arrivalTime = schedTimeFromStopTimes.getArrivalTime() + 
					frequenciesBasedStartTime;
			Integer departureTime = null;
			if (schedTimeFromStopTimes.getDepartureTime() != null)
				departureTime = schedTimeFromStopTimes.getDepartureTime() + 
					frequenciesBasedStartTime;
			
			ScheduleTime schedTimeFromFrequency = 
					new ScheduleTime(arrivalTime, departureTime); 
			this.scheduledTimesList.add(schedTimeFromFrequency);
		}
		
		// Since this constructor is only for frequency based trips where
		// exact_times is true set the corresponding members to indicate such
		this.noSchedule = false;
		this.exactTimesHeadway = true;		
	}
	
	/**
	 * Creates a copy of the Trip object but adjusts the startTime, endTime, and
	 * scheduledTimesMap according to the frequenciesBasedStartTime. This is
	 * used when the frequencies.txt specifies a time range for a trip but where
	 * exact_times is false. This is for noSchedule routes where vehicle is
	 * expected to continuously run on a route without a schedule.
	 * 
	 * @param tripFromStopTimes
	 * @param frequenciesBasedStartTime
	 * @param frequenciesBasedEndTime
	 */
	public Trip(Trip tripFromStopTimes, int frequenciesBasedStartTime,
			int frequenciesBasedEndTime) {
		this.configRev = tripFromStopTimes.configRev;
		this.tripId = tripFromStopTimes.tripId;
		this.tripShortName = tripFromStopTimes.tripShortName;
		this.directionId = tripFromStopTimes.directionId;
		this.routeId = tripFromStopTimes.routeId;
		this.routeShortName = tripFromStopTimes.routeShortName;
		this.serviceId = tripFromStopTimes.serviceId;
		this.headsign = tripFromStopTimes.headsign;
		this.shapeId = tripFromStopTimes.shapeId;
		this.tripPattern = tripFromStopTimes.tripPattern;
		this.travelTimes = tripFromStopTimes.travelTimes;
		this.blockId = tripFromStopTimes.blockId;
		
		// Set the updated start and end times by using the times from the
		// frequency.txt GTFS file
		this.startTime = frequenciesBasedStartTime;
		this.endTime = frequenciesBasedEndTime;
		
		// Set the scheduledTimesMap by using the frequencies based start time
		for (ScheduleTime schedTimeFromStopTimes : tripFromStopTimes.scheduledTimesList) {
			this.scheduledTimesList.add(schedTimeFromStopTimes);
		}
		
		// Since this constructor is only for frequency based trips where
		// exact_times is false set the corresponding members to indicate such
		this.noSchedule = true;
		this.exactTimesHeadway = false;		
	}
	
	/**
	 * Hibernate requires no-arg constructor
	 */
	@SuppressWarnings("unused")
	private Trip() {
		configRev = -1;
		tripId = null;
		tripShortName = null;
		directionId = null;
		routeId = null;
		routeShortName = null;
		serviceId = null;
		headsign = null;
		blockId = null;
		shapeId = null;
		noSchedule = false;
		exactTimesHeadway = false;
	}

	/**
	 * For refining the headsign. For some agencies like VTA & AC Transit the
	 * headsign includes the route number at the beginning. This is indeed the
	 * headsign but not really appropriate as a destination indicator. Ideally
	 * it might make sense to have an unprocessed headsign and a separate
	 * destination indicator but for now lets just use headsign member. Also
	 * uses TitleFormatter to deal with capitalization.
	 * 
	 * @param gtfsHeadsign
	 * @param routeId
	 * @param titleFormatter
	 * @return Processed headsign with proper formatting, or null if
	 *         gtfsHeadsign passed in is null
	 */
	private String processedHeadsign(String gtfsHeadsign, String routeId,
			TitleFormatter titleFormatter) {
		// Prevent NPE since gtfsHeadsign can be null
		if (gtfsHeadsign == null)
			return null;
		
		String headsignWithoutRouteInfo;
		if (gtfsHeadsign.startsWith(routeId)) {
			// Headsign starts with route ID so trim that off
			headsignWithoutRouteInfo =
					gtfsHeadsign.substring(routeId.length()).trim();
			
			// Handle possibility of having a separator between the route ID
			// and the rest of the headsign. 
			if (headsignWithoutRouteInfo.startsWith(":")
					|| headsignWithoutRouteInfo.startsWith("-"))
				headsignWithoutRouteInfo =
						headsignWithoutRouteInfo.substring(1).trim();
		} else
			// Headsign doesn't start with route ID so use entire string
			headsignWithoutRouteInfo = gtfsHeadsign;

		// Handle capitalization and any other title formatting necessary
		return titleFormatter.processTitle(headsignWithoutRouteInfo);
	}
	
	/**
	 * For adding ScheduleTimes for stops to a Trip. Updates scheduledTimesMap,
	 * startTime, and endTime.
	 * 
	 * @param newScheduledTimesList
	 * @throws ArrayIndexOutOfBoundsException
	 *             If not enough space allocated for serialized schedule times
	 *             in scheduledTimesMap column
	 */
	public void addScheduleTimes(List<ScheduleTime> newScheduledTimesList) {
		// For each schedule time (one per stop path)
		for (ScheduleTime scheduleTime : newScheduledTimesList) {
			// Add the schedule time to the map
			scheduledTimesList.add(scheduleTime);
			
			// Determine the begin and end time. Assumes that times are added in order
			if (startTime == null || 
					(scheduleTime.getDepartureTime() != null && 
					 scheduleTime.getDepartureTime() < startTime))
				startTime = scheduleTime.getDepartureTime();
			if (endTime == null || 
					(scheduleTime.getArrivalTime() != null &&  
					 scheduleTime.getArrivalTime() > endTime))
				endTime = scheduleTime.getArrivalTime();
		}
		
		// If resulting map takes up too much memory throw an exception.
		// Only bother checking if have at least a few schedule times.
		/*if (scheduledTimesList.size() > 5) {
			int serializedSize = HibernateUtils.sizeof(scheduledTimesList);
			if (serializedSize > scheduleTimesMaxBytes) {
				String msg = "Too many elements in "
						+ "scheduledTimesMap when constructing a "
						+ "Trip. Have " + scheduledTimesList.size()
						+ " schedule times taking up " + serializedSize
						+ " bytes but only have " + scheduleTimesMaxBytes
						+ " bytes allocated for the data. Trip="
						+ this.toShortString();
				logger.error(msg);

				// Since this could be a really problematic issue, throw an error
				throw new ArrayIndexOutOfBoundsException(msg);
			}
		}*/
	}
	
	/**
	 * TripPattern is created after the Trip. Therefore it cannot
	 * be set in constructor and instead needs this set method.
	 * @param tripPattern
	 */
	public void setTripPattern(TripPattern tripPattern) {
		this.tripPattern = tripPattern;		
	}
	
	/**
	 * TravelTimesForTrip created after the Trip. Therefore it cannot
	 * be set in constructor and instead needs this set method.
	 * @param travelTimes
	 */
	public void setTravelTimes(TravelTimesForTrip travelTimes) {
		this.travelTimes = travelTimes;
	}
	
	/**
	 * Returns map of Trip objects for the specified configRev. The
	 * map is keyed on the trip IDs.
	 * 
	 * @param session
	 * @param configRev
	 * @return
	 * @throws HibernateException
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Trip> getTrips(Session session, int configRev) 
			throws HibernateException {
		// Setup the query
		String hql = "FROM Trip " +
				"    WHERE configRev = :configRev";
		Query query = session.createQuery(hql);
		query.setInteger("configRev", configRev);

		// Actually perform the query
		List<Trip> tripsList = query.list();

		// Now put the Trips into a map and return it
		Map<String, Trip> tripsMap = new HashMap<String, Trip>();
		for (Trip trip : tripsList) {
			tripsMap.put(trip.getId(), trip);
		}
		return tripsMap;
	}

	/**
	 * Returns specified Trip object for the specified configRev and tripId.
	 * 
	 * @param session
	 * @param configRev
	 * @param tripId
	 * @return
	 * @throws HibernateException
	 */
	public static Trip getTrip(Session session, int configRev, String tripId) 
			throws HibernateException {
		// Setup the query
		String hql = "FROM Trip t " +
                "   left join fetch t.scheduledTimesList " +
                "   left join fetch t.travelTimes " +
				"    WHERE t.configRev = :configRev" +
				"      AND tripId = :tripId";
		Query query = session.createQuery(hql);
		query.setInteger("configRev", configRev);
		query.setString("tripId", tripId);
		
		// Actually perform the query
		Trip trip = (Trip) query.uniqueResult();

		return trip;
	}

	/**
	 * Returns list of Trip objects for the specified configRev and
	 * tripShortName. There can be multiple trips for a tripShortName since can
	 * have multiple service IDs configured. Therefore a list must be returned.
	 * 
	 * @param session
	 * @param configRev
	 * @param tripShortName
	 * @return list of trips for specified configRev and tripShortName
	 * @throws HibernateException
	 */
	public static List<Trip> getTripByShortName(Session session, int configRev,
			String tripShortName) throws HibernateException {
		// Setup the query
		String hql = "FROM Trip t " +
                "   left join fetch t.scheduledTimesList " +
                "   left join fetch t.travelTimes " +
				"    WHERE t.configRev = :configRev" +
				"      AND t.tripShortName = :tripShortName";
		Query query = session.createQuery(hql);
		query.setInteger("configRev", configRev);
		query.setString("tripShortName", tripShortName);
		
		// Actually perform the query
		@SuppressWarnings("unchecked")
		List<Trip> trips = query.list();
		
		return trips;
	}
	
	/**
	 * Deletes rev from the Trips table
	 * 
	 * @param session
	 * @param configRev
	 * @return Number of rows deleted
	 * @throws HibernateException
	 */
	public static int deleteFromRev(Session session, int configRev) 
			throws HibernateException {
		int rowsUpdated = 0;
		rowsUpdated += session.
				createSQLQuery("DELETE FROM Trips WHERE configRev=" 
						+ configRev).
				executeUpdate();
		return rowsUpdated;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// Note: the '\n' at beginning is so that when output list of trips 
		// each will be on new line
		return "\n    Trip [" 
				+ "configRev=" + configRev
				+ ", tripId=" + tripId 
				+ ", tripShortName=" + tripShortName
				+ ", tripPatternId=" 
					+ (tripPattern != null ? tripPattern.getId() : "null")
				+ ", tripIndexInBlock=" + getIndexInBlock()
				+ ", startTime=" + Time.timeOfDayStr(startTime)
				+ ", endTime=" + Time.timeOfDayStr(endTime)
				+ (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
				+ ", directionId=" + directionId
				+ ", routeId=" + routeId
				+ ", routeShortName=" + routeShortName
				+ (noSchedule ? ", noSchedule=" + noSchedule : "")
				+ (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
				+ ", serviceId=" + serviceId
				+ ", blockId=" + blockId
				+ ", shapeId=" + shapeId
//				+ ", scheduledTimesList=" + scheduledTimesList 
				+ "]";
	}
	
	/**
	 * Similar to toString() but also includes full TripPattern and travelTimes
	 * 
	 * @return
	 */
	public String toLongString() {
		return "Trip [" 
				+ "configRev=" + configRev
				+ ", tripId=" + tripId 
				+ ", tripShortName=" + tripShortName
				+ ", tripPatternId=" 
					+ (tripPattern != null ? tripPattern.getId() : "null")
				+ ", tripPattern=" + tripPattern
				+ ", tripIndexInBlock=" + getIndexInBlock()
				+ ", startTime=" + Time.timeOfDayStr(startTime)
				+ ", endTime=" + Time.timeOfDayStr(endTime)
				+ (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
				+ ", directionId=" + directionId
				+ ", routeId=" + routeId
				+ ", routeShortName=" + routeShortName
				+ (noSchedule ? ", noSchedule=" + noSchedule : "")
				+ (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
				+ ", serviceId=" + serviceId
				+ ", blockId=" + blockId
				+ ", shapeId=" + shapeId
				+ ", scheduledTimesList=" + scheduledTimesList
				+ ", travelTimes=" + travelTimes
				+ "]";
	}

	/**
	 * Similar to toString() but doesn't include scheduledTimesMap which
	 * can be quite verbose since it often contains times for many stops.
	 * @return
	 */
	public String toShortString() {
		return "Trip ["
				+ "tripId=" + tripId 
				+ ", tripShortName=" + tripShortName
				+ ", tripPatternId=" 
					+ (tripPattern != null ? tripPattern.getId() : "null")
				+ ", tripIndexInBlock=" + getIndexInBlock()
				+ ", startTime=" + Time.timeOfDayStr(startTime)
				+ ", endTime=" + Time.timeOfDayStr(endTime)
				+ (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
				+ ", directionId=" + directionId
				+ ", routeId=" + routeId
				+ ", routeShortName=" + routeShortName
				+ (noSchedule ? ", noSchedule=" + noSchedule : "")
				+ (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
				+ ", serviceId=" + serviceId
				+ ", blockId=" + blockId
				+ ", shapeId=" + shapeId
				+ "]";
	}

	/**
	 * Required by Hibernate
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((blockId == null) ? 0 : blockId.hashCode());
		result = prime * result + configRev;
		result =
				prime * result
						+ ((directionId == null) ? 0 : directionId.hashCode());
		result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
		result = prime * result + (exactTimesHeadway ? 1231 : 1237);
		result =
				prime * result + ((headsign == null) ? 0 : headsign.hashCode());
		result = prime * result + (noSchedule ? 1231 : 1237);
		result = prime * result + ((route == null) ? 0 : route.hashCode());
		result = prime * result + ((routeId == null) ? 0 : routeId.hashCode());
		result =
				prime
						* result
						+ ((routeShortName == null) ? 0 : routeShortName
								.hashCode());
		result =
				prime
						* result
						+ ((scheduledTimesList == null) ? 0
								: scheduledTimesList.hashCode());
		result =
				prime * result
						+ ((serviceId == null) ? 0 : serviceId.hashCode());
		result = prime * result + ((shapeId == null) ? 0 : shapeId.hashCode());
		result =
				prime * result
						+ ((startTime == null) ? 0 : startTime.hashCode());
		result =
				prime * result
						+ ((travelTimes == null) ? 0 : travelTimes.hashCode());
		result = prime * result + ((tripId == null) ? 0 : tripId.hashCode());
		result =
				prime * result
						+ ((tripPattern == null) ? 0 : tripPattern.hashCode());
		result =
				prime
						* result
						+ ((tripShortName == null) ? 0 : tripShortName
								.hashCode());
		return result;
	}

	/**
	 * Required by Hibernate
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Trip other = (Trip) obj;
		if (blockId == null) {
			if (other.blockId != null)
				return false;
		} else if (!blockId.equals(other.blockId))
			return false;
		if (configRev != other.configRev)
			return false;
		if (directionId == null) {
			if (other.directionId != null)
				return false;
		} else if (!directionId.equals(other.directionId))
			return false;
		if (endTime == null) {
			if (other.endTime != null)
				return false;
		} else if (!endTime.equals(other.endTime))
			return false;
		if (exactTimesHeadway != other.exactTimesHeadway)
			return false;
		if (headsign == null) {
			if (other.headsign != null)
				return false;
		} else if (!headsign.equals(other.headsign))
			return false;
		if (noSchedule != other.noSchedule)
			return false;
		if (route == null) {
			if (other.route != null)
				return false;
		} else if (!route.equals(other.route))
			return false;
		if (routeId == null) {
			if (other.routeId != null)
				return false;
		} else if (!routeId.equals(other.routeId))
			return false;
		if (routeShortName == null) {
			if (other.routeShortName != null)
				return false;
		} else if (!routeShortName.equals(other.routeShortName))
			return false;
		if (scheduledTimesList == null) {
			if (other.scheduledTimesList != null)
				return false;
		} else if (!scheduledTimesList.equals(other.scheduledTimesList))
			return false;
		if (serviceId == null) {
			if (other.serviceId != null)
				return false;
		} else if (!serviceId.equals(other.serviceId))
			return false;
		if (shapeId == null) {
			if (other.shapeId != null)
				return false;
		} else if (!shapeId.equals(other.shapeId))
			return false;
		if (startTime == null) {
			if (other.startTime != null)
				return false;
		} else if (!startTime.equals(other.startTime))
			return false;
		if (travelTimes == null) {
			if (other.travelTimes != null)
				return false;
		} else if (!travelTimes.equals(other.travelTimes))
			return false;
		if (tripId == null) {
			if (other.tripId != null)
				return false;
		} else if (!tripId.equals(other.tripId))
			return false;
		if (tripPattern == null) {
			if (other.tripPattern != null)
				return false;
		} else if (!tripPattern.equals(other.tripPattern))
			return false;
		if (tripShortName == null) {
			if (other.tripShortName != null)
				return false;
		} else if (!tripShortName.equals(other.tripShortName))
			return false;
		return true;
	}

	/********************** Getter Methods **************************/
	
	/**
	 * Returns departure time of first stop of trip. Gtfs requires departure
	 * time of first stop of trip and arrival time of last stop of trip to be
	 * set, even for unscheduled blocks. That way they can determine running
	 * time of trip.
	 * 
	 * @return departure time of first stop of trip. Time is seconds into the
	 *         day. Can be null.
	 */
	public Integer getStartTime() {
		return startTime;
	}
	
	/**
	 * Returns arrival time of last stop of trip. Gtfs requires departure time
	 * of first stop of trip and arrival time of last stop of trip to be set,
	 * even for unscheduled blocks. That way they can determine running time of
	 * trip.
	 * 
	 * @return arrival time of last stop of trip. Time is seconds into the day.
	 *         Can be null.
	 */
	public Integer getEndTime() {
		return endTime;
	}
	
	/**
	 * @return the configRev
	 */
	public int getConfigRev() {
		return configRev;
	}

	/**
	 * @return the tripId
	 */
	public String getId() {
		return tripId;
	}
	
	/**
	 * @return the tripShortName
	 */
	public String getShortName() {
		return tripShortName;
	}
	
	/**
	 * @return the routeId
	 */
	public String getRouteId() {
		return routeId;
	}
	
	/**
	 * Returns the routeShortName. If it is null then returns the full route
	 * name. Causes exception if Core not available, such as when processing
	 * GTFS data.
	 * 
	 * @return the routeShortName
	 */
	public String getRouteShortName() {
		return routeShortName != null ? routeShortName : getRouteName();
	}

	/**
	 * Returns the Route object for this trip. This object is determined and
	 * cached when first accessed. Uses value read in from database using Core,
	 * which means that it won't be available when processing GTFS data since
	 * that doesn't have core object.
	 * 
	 * @return The route or null if no Core object available
	 */
	public Route getRoute() {
		if (route == null) {
			if (Core.isCoreApplication()) {
				DbConfig dbConfig = Core.getInstance().getDbConfig();				
				if (dbConfig == null)
					return null;
				route = dbConfig.getRouteById(routeId);
			} else {
				return null;
			}
		}
		return route;
	}
	
	/**
	 * Returns route name. Gets it from the Core database configuration. If Core
	 * database configuration not available such as when processing GTFS data
	 * then will return null. Will cause exception if core not available
	 * and gtfs data not loaded in db yet since the active revisions will
	 * not be set properly yet.
	 * 
	 * @return The route name or null if Core object not available
	 */
	public String getRouteName() {
		Route route = getRoute();
		if (route == null)
			return null;
		return route.getName();
	}
	
	/**
	 * @return the directionId
	 */
	public String getDirectionId() {
		return directionId;
	}

	/**
	 * @return noSchedule
	 */
	public boolean isNoSchedule() {
		return noSchedule;
	}
	
	/**
	 * @return exactTimesHeadway
	 */
	public boolean isExactTimesHeadway() {
		return exactTimesHeadway;
	}
	
	/**
	 * @return the serviceId
	 */
	public String getServiceId() {
		return serviceId;
	}

	/**
	 * The trip_headsign value from the trips.txt file. But
	 * it has been processed by the TitleFormatter to correct
	 * capitalization and such.
	 * @return the name
	 */
	public String getHeadsign() {
		return headsign;
	}
	
	/**
	 * For modifying the headsign. Useful for when reading in GTFS data and
	 * determine that the headsign should be modified because it is for a
	 * different last stop or such.
	 * 
	 * @param headsign
	 */
	public void setHeadsign(String headsign) {
		this.headsign =	headsign.length() <= TripPattern.HEADSIGN_LENGTH ? 
				headsign : headsign.substring(0, TripPattern.HEADSIGN_LENGTH);
	}

	/**
	 * The block_id is an optional element in GTFS trips.txt file.
	 * If it is available it is used. But if it is not then
	 * the trip_id is used as next best thing.
	 * @return blockId
	 */
	public String getBlockId() {
		return blockId;
	}
	
	/**
	 * Returns the Block that the Trip is associated with. Only valid
	 * when running the core application where can use Core.getInstance().
	 * Otherwise returns null.
	 * 
	 * @return
	 */
	public Block getBlock() {
		// If not part of the core project where DbConfig is available
		// then just return null.
		Core core = Core.getInstance();
		if (core == null)
			return null;
		DbConfig dbConfig = core.getDbConfig();
		if (dbConfig == null)
			return null;
		
		// Part of core project so return the Block
		return dbConfig.getBlock(serviceId, blockId);
	}
	
	/**
	 * Returns the index of the trip in the block. Uses DbConfig from the core
	 * project to determine the Block. If not part of the core project the Block
	 * info is not available and -1 is returned.
	 * 
	 * @return The index of the trip in the block or -1 if block info not
	 *         available.
	 */
	public int getIndexInBlock() {
		// If block info no available then simply return -1
		Block block = getBlock();
		if (block == null)
			return -1;
		
		// Block info available so return the trip index
		return block.getTripIndex(this);
	}
	
	/**
	 * @return the shapeId
	 */
	public String getShapeId() {
		return shapeId;		
	}
	
	/**
	 * @return the tripPattern
	 */
	public TripPattern getTripPattern() {
		return tripPattern;
	}
	
	/**
	 * Returns the ScheduleTime object for the stopPathIndex. Will return null
	 * if there are no schedule times associated with that stop for this trip.
	 * Useful for determining schedule adherence.
	 * 
	 * @param stopPathIndex
	 * @return
	 */
	public ScheduleTime getScheduleTime(int stopPathIndex) {
	  if (scheduledTimesList instanceof PersistentList) {
	    // TODO this is an anti-pattern
	    // instead find a way to manage sessions more consistently 
	    PersistentList persistentListTimes = (PersistentList)scheduledTimesList;
	    SharedSessionContractImplementor session = 
          persistentListTimes.getSession();
	    if (session == null) {
	      Session globalLazyLoadSession = Core.getInstance().getDbConfig().getGlobalSession();
	      globalLazyLoadSession.update(this);
	    }
	  }
		return scheduledTimesList.get(stopPathIndex);
	}
	
	/**
	 * @return list of schedule times for the trip
	 */
	public List<ScheduleTime> getScheduleTimes() {
		return scheduledTimesList;
	}
	
	/**
	 * @return the travelTimes
	 */
	public TravelTimesForTrip getTravelTimes() {
		return travelTimes;
	}
	
	/**
	 * Returns the travel time info for the path specified by the stopPathIndex.
	 * 
	 * @param stopPathIndex
	 * @return
	 */
	public TravelTimesForStopPath getTravelTimesForStopPath(int stopPathIndex) {
		return travelTimes.getTravelTimesForStopPath(stopPathIndex);
	}
	
	/**
	 * Returns length of the trip from the first terminal to the last.
	 * 
	 * @return
	 */
	public double getLength() {
		return getTripPattern().getLength();
	}
	
	/**
	 * Returns the stop ID of the last stop of the trip. This is the destination
	 * for the trip.
	 * 
	 * @return ID of last stop
	 */
	public String getLastStopId() {
		return getTripPattern().getLastStopIdForTrip();
	}

	/**
	 * Returns the List of the stop paths for the trip pattern
	 * 
	 * @return
	 */
	public List<StopPath> getStopPaths() {
		return tripPattern.getStopPaths();
	}
	
	/**
	 * Returns the StopPath for the stopPathIndex specified
	 * 
	 * @param stopPathIndex
	 * @return the path specified or null if index out of range
	 */
	public StopPath getStopPath(int stopPathIndex) {
		return tripPattern.getStopPath(stopPathIndex);
	}
	
	/**
	 * Returns the StopPath specified by the stopId.
	 * 
	 * @param stopId
	 * @return The specified StopPath, or null if the stop is not part of this
	 *         trip pattern.
	 */
	public StopPath getStopPath(String stopId) {
		return tripPattern.getStopPath(stopId);
	}
	
	/**
	 * Returns number of stop paths defined for this trip.
	 * 
	 * @return Number of stop paths
	 */
	public int getNumberStopPaths() {
		return getTripPattern().getStopPaths().size();
	}

	/**
	 * Callback due to implementing Lifecycle interface. Used to compact
	 * string members by interning them.
	 */
	@Override
	public void onLoad(Session s, Serializable id) throws CallbackException {
		if (tripId != null)
			tripId = tripId.intern();
		if (tripShortName != null)
			tripShortName = tripShortName.intern();
		if (directionId != null)
			directionId = directionId.intern();
		if (routeId != null)
			routeId = routeId.intern();
		if (routeShortName != null)
			routeShortName = routeShortName.intern();
		if (serviceId != null)
			serviceId = serviceId.intern();
		if (headsign != null)
			headsign = headsign.intern();
		if (blockId != null)
			blockId = blockId.intern();
		if (shapeId != null)
			shapeId = shapeId.intern();
	}
	
	/**
	 * Implemented due to Lifecycle interface being implemented. Not actually
	 * used.
	 */
	@Override
	public boolean onSave(Session s) throws CallbackException {
		return Lifecycle.NO_VETO;
	}

	/**
	 * Implemented due to Lifecycle interface being implemented. Not actually
	 * used.
	 */
	@Override
	public boolean onUpdate(Session s) throws CallbackException {
		return Lifecycle.NO_VETO;
	}

	/**
	 * Implemented due to Lifecycle interface being implemented. Not actually
	 * used.
	 */
	@Override
	public boolean onDelete(Session s) throws CallbackException {
		return Lifecycle.NO_VETO;
	}

	/**
	 * Query how many travel times for trips entries exist for a given
	 * travelTimesRev.  Used for metrics.
	 * @param session
	 * @param travelTimesRev
	 * @return
	 */
  public static Long countTravelTimesForTrips(Session session,
      int travelTimesRev) {
    String sql = "Select count(*) from TravelTimesForTrips where travelTimesRev=:rev";
    
    Query query = session.createSQLQuery(sql);
    query.setInteger("rev", travelTimesRev);
    Long count = null;
    try {
 
      Integer bcount;  
      if(query.uniqueResult() instanceof BigInteger)
      {
    	  bcount = ((BigInteger)query.uniqueResult()).intValue();
      }else
      {
          bcount = (Integer) query.uniqueResult();
      }
  
      if (bcount != null)
        count = bcount.longValue();
    } catch (HibernateException e) {
      Core.getLogger().error("exception querying for metrics", e);
    }
    return count;
  }
		
}
