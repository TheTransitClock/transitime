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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
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
import org.hibernate.classic.Lifecycle;
import org.transitclock.db.hibernate.HibernateUtils;
import org.transitclock.gtfs.GtfsData;
import org.transitclock.gtfs.gtfsStructs.GtfsRoute;


/**
 * A trip pattern, as obtained from stop_times.txt GTFS file.
 * A trip pattern defines what stops are associated with a
 * trip. Trip pattern reduces the amount of data needed to describe
 * an agency since trips usually can share trip patterns.
 * 
 * @author SkiBu Smith
 */
@Entity 
@DynamicUpdate 
@Table(name="TripPatterns")
public class TripPattern implements Serializable, Lifecycle {

	// Which configuration revision used
	@Column 
	@Id
	private final int configRev;
	
	// The ID of the trip pattern
	@Column(length=TRIP_PATTERN_ID_LENGTH) 
	@Id
	private final String id;
	
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	final protected String shapeId;
	
	// For the List of Paths want to use FetchType.EAGER
	// because otherwise need to keep the session open till the Paths
	// are accessed with the default LAZY loading. And use 
	// CascadeType.SAVE_UPDATE so that when the TripPattern is stored the 
	// Paths are automatically stored.
	@OneToMany(fetch=FetchType.EAGER)
	@Cascade({CascadeType.SAVE_UPDATE})
	@JoinTable(name="TripPattern_to_Path_joinTable",
			joinColumns= {
					@JoinColumn(name="trippattern_id", referencedColumnName="id"),
					@JoinColumn(name="trippatterns_configrev", referencedColumnName="configrev")
					},
            inverseJoinColumns= {
            		@JoinColumn(name="stoppaths_trippatternid", referencedColumnName="trippatternid"),
            		@JoinColumn(name="stoppaths_stoppathid", referencedColumnName="stoppathid"),
            		@JoinColumn(name="stoppaths_configrev", referencedColumnName="configRev")
            		})
	
	@OrderColumn( name="listIndex")
	final protected List<StopPath> stopPaths;
	
	@Column(length=HEADSIGN_LENGTH)
	private String headsign;
	
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private final String directionId;
	
	@Column(length=HibernateUtils.DEFAULT_ID_SIZE)
	private final String routeId;
	
	@Column(length=80)
	private final String routeShortName;
	
	// So know lat lon range of the trip pattern 
	@Embedded
	private final Extent extent;
	
	// So know which trips use this trip pattern
	@Transient
	private List<Trip> trips = new ArrayList<Trip>();
	
	// For quickly finding a StopPath using a stop ID.
	// Keyed on stop ID. Since this member is transient this
	// class implements LifeCycle interface so that the
	// member can be initialized using onLoad(). Can't use
	// @PostLoad annotation because that only works when using
	// EntityManager but we are using regular Hibernate sessions.
	@Transient
	final protected Map<String, StopPath> stopPathsMap =
		new HashMap<String, StopPath>();
	
	// For specifying max size of the trip pattern ID
	public static final int TRIP_PATTERN_ID_LENGTH = 120;
	// For specifying max size of headsign
	public static final int HEADSIGN_LENGTH = 255;
	
	// Hibernate requires this class to be serializable because it uses multiple
	// columns for the Id.
	private static final long serialVersionUID = 8002349177548788550L;

	/********************** Member Functions **************************/
	
	/**
	 * Create a TripPattern. For when processing GTFS data.
	 * 
	 * Note: The name comes from the trip trip_headsign data. If not set then
	 * uses name of last stop for trip.
	 * 
	 * @param configRev
	 * @param shapeId
	 *            Part of what identifies the trip pattern
	 * @param stopPaths
	 *            Part of what identifies the trip pattern
	 * @param trip
	 *            For supplying additional info
	 * @param gtfsData
	 *            So can access stop data for determining extent of trip
	 *            pattern.
	 */
	public TripPattern(int configRev, String shapeId, List<StopPath> stopPaths,
			Trip trip, GtfsData gtfsData) {

		this.shapeId = shapeId;
		this.stopPaths = stopPaths;
		
		// Because will be writing data to the sandbox rev in the db
		this.configRev = configRev;

		// Generate the id . 
		this.id = generateTripPatternId(
				shapeId,
				stopPaths,
				trip,
				gtfsData);
		
		// Now that have the trip pattern ID set it for each StopPath
		for (StopPath path : stopPaths) {
			path.setTripPatternId(id);
		}
		
		// The trip_headsign in trips.txt and therefore the the trip name can be
		// null. For these cases use the last stop as a destination.
		if (trip.getHeadsign() != null) {
			this.headsign = trip.getHeadsign();
		} else {
			// trip_headsign was null so try using final stop name as the destination
			// as a fallback.
			StopPath lastPath = stopPaths.get(stopPaths.size()-1);
			String lastStopIdForTrip = lastPath.getStopId();
			Stop lastStopForTrip = gtfsData.getStop(lastStopIdForTrip);
			this.headsign = lastStopForTrip.getName();
		}
		// Make sure headsign not too long for db
		if (this.headsign.length() > HEADSIGN_LENGTH) {
			this.headsign = this.headsign.substring(0, HEADSIGN_LENGTH);
		}
		
		// Store additional info from this trip
		this.directionId = trip.getDirectionId();
		this.routeId = trip.getRouteId();
		this.routeShortName = getRouteShortName(routeId, gtfsData);
		
		// Remember that this trip pattern refers to this particular 
		// trip. Additional trips will be added as they are processed.
		this.trips.add(trip);
		
		// Determine extent of trip pattern and store it. Also, create
		// the stopPathsMap and fill it in.
		this.extent = new Extent();
		for (StopPath stopPath : stopPaths) {
			// Determine the stop
			Stop stop = gtfsData.getStop(stopPath.getStopId());
			this.extent.add(stop.getLoc());
			this.stopPathsMap.put(stopPath.getStopId(), stopPath);
		}
	}
	
	/**
	 * Hibernate requires a not-arg constructor
	 */
	@SuppressWarnings("unused")
	private TripPattern() {
		super();
		
		configRev = -1;
		id = null;
		shapeId = null;
		stopPaths = null;
		headsign = null;
		directionId = null;
		routeId = null;
		routeShortName = null;
		extent = null;
	}

	/**
	 * Gets the route_short_name from the GTFS route data. If the
	 * route_short_name was not specified in the GTFS data then will use the
	 * route_long_name. This way the route short name returned will always be
	 * something appropriate.
	 * 
	 * @param routeId
	 * @param gtfsData
	 * @return The route_short_name if set, otherwise the route_long_name
	 */
	private static String getRouteShortName(String routeId, GtfsData gtfsData) {
		// Determine GtfsRoute for the route ID
		GtfsRoute gtfsRoute = gtfsData.getGtfsRoute(routeId);		
		if (gtfsRoute == null)
			return null;
		
		// Use route_short_name. If not defined then use route_long_name
		String name = gtfsRoute.getRouteShortName();
		if (name == null)
			name = gtfsRoute.getRouteLongName();
		
		return name;
	}
	
	/**
	 * Deletes rev from the TripPattern_to_Path_joinTable, StopPaths, 
	 * and TripPatterns tables.
	 * 
	 * @param session
	 * @param configRev
	 * @return Number of rows deleted
	 * @throws HibernateException
	 */
	public static int deleteFromRev(Session session, int configRev) 
			throws HibernateException {
		// In a perfect Hibernate world one would simply call on session.delete()
		// for each trip pattern and the join table and the associated trip pattern
		// elements would be automatically deleted by using the magic of Hibernate.
		// But this means that would have to read in all the objects and sub-objects
		// first, which of course takes lots of time and memory, often causing
		// program to crash due to out of memory issue. Therefore
		// using the much, much faster solution of direct SQL calls. Can't use
		// HQL on the join table since it is not a regularly defined table. 
		//
		// Would be great to see if can actually use HQL and delete the 
		// appropriate TripPatterns and have the join table and the trip pattern
		// elements table be automatically updated. I doubt this would work but
		// would be interesting to try if had the time.
		//
		// Delete from TripPattern_to_Path_joinTable first since it has a foreign
		// key to the StopPath table, 
		int rowsUpdated = 0;
		rowsUpdated += session.
				createSQLQuery("DELETE FROM TripPattern_to_Path_joinTable "
						+ "WHERE TripPatterns_configRev=" + configRev).
				executeUpdate();
		rowsUpdated += session.
				createSQLQuery("DELETE FROM StopPaths WHERE configRev=" 
						+ configRev).
				executeUpdate();
		rowsUpdated += session.
				createSQLQuery("DELETE FROM TripPatterns WHERE configRev=" 
						+ configRev).
				executeUpdate();
		return rowsUpdated;
		
//		// Because TripPattern uses a List of Paths things are
//		// complicated because there are multiple tables with foreign keys.
//		// And the join table is not a regular Hibernate table so I don't
//		// believe can use hql to empty it out. Therefore it is best to
//		// read in the objects and then delete them and let Hibernate make
//		// sure it is all done correctly.
//		// NOTE: Unfortunately this is quite slow since have to read in
//		// all the objects first. Might just want to use regular SQL to
//		// delete the items in the TripPattern_to_Path_joinTable
//		List<TripPattern> tripPatternsFromDb = getTripPatterns(session, 0);
//		for (TripPattern tp : tripPatternsFromDb)
//			session.delete(tp);
//		// Need to flush. Otherwise when writing new TripPatterns will get
//		// a uniqueness violation even though already told the session to
//		// delete those objects.
//		session.flush();
//		return tripPatternsFromDb.size();
		
//		int numUpdates = 0;
//		String hql;
//		
//		// Need to first delete the list of Paths
//		hql = "DELETE StopPath WHERE configRev=0";
//		numUpdates += session.createQuery(hql).executeUpdate();
////		hql = "";
////		numUpdates += session.createQuery(hql).executeUpdate();
//		
//		// Note that hql uses class name, not the table name
//		hql = "DELETE TripPattern WHERE configRev=0";
//		numUpdates += session.createQuery(hql).executeUpdate();
//		return numUpdates;
	}
	
	/**
	 * Returns list of TripPattern objects for the specified configRev
	 * 
	 * @param session
	 * @param configRev
	 * @return
	 * @throws HibernateException
	 */
	@SuppressWarnings("unchecked")
	public static List<TripPattern> getTripPatterns(Session session, 
			int configRev) 
			throws HibernateException {
		String hql = "FROM TripPattern " +
				"    WHERE configRev = :configRev";
		Query query = session.createQuery(hql);
		query.setInteger("configRev", configRev);
		return query.list();
	}


	/**
	 * Determines the ID of the TripPattern. It is of course important that the
	 * trip pattern be unique. It also needs to be consistent even if the order
	 * of the stop_times file changes or trips are added or removed. Plus it
	 * needs to be understandable. Therefore it consists of appending the
	 * shapeId (if not-null), the from stop ID, the to stop ID, and a hash of
	 * the concatenation of all the stop IDs that make up the trip pattern. It
	 * will be something like "shape_932012_stops_stop1_to_stop2_hash". Long,
	 * but it is readable, unique, and consistent.
	 * 
	 * It is important to not just always use "stop1_to_stop2" since some
	 * agencies might for the same stops have different stopPaths for connecting
	 * them. Therefore should use the shapeId from the Trip passed in.
	 * 
	 * @param shapeId
	 *            Used for the trip pattern id if it is not null
	 * @param stopPaths
	 *            If shapeId null then used as part of ID
	 * @param trip
	 *            In case things get complicated with determining ID and need to
	 *            log message
	 * @param gtfsData
	 *            In case things get complicated with determining ID
	 * @return Unique generated trip pattern ID for the specified trip
	 */
	private static String generateTripPatternId(String shapeId, 
			List<StopPath> stopPaths,
			Trip trip, 
			GtfsData gtfsData) {
		// The ID to be constructed and returned
		String tripPatternId = "";
		
		// If shapeId defined then start with it
		if (shapeId != null) {
			tripPatternId = "shape_" + shapeId + "_";
		}
		
		// Add the from and to stop IDs
		if (stopPaths != null && stopPaths.size() > 1) {
		  StopPath path1 = stopPaths.get(0);
		  StopPath path2 = stopPaths.get(stopPaths.size()-1);
		  tripPatternId += path1.getStopId() + "_to_" + path2.getStopId() + "_";
		} else {
		  GtfsData.logger.info("There was an issue with creating trip " + 
          "pattern for tripId={} for routeId={}, it is missing stops "); 
		  return tripPatternId;
		}
		
		// Determine hash in hexadecimal format of list of stops
		StringBuilder sb = new StringBuilder();
		for (StopPath stopPath : stopPaths) {
			sb.append(stopPath.getStopId());
		}
		String hexOfStopsHash = String.format("%x", sb.toString().hashCode());
		tripPatternId += hexOfStopsHash;
		
		// Make sure not too long for the database column. Include possibility 
		// of needing to include the "_variation N" to make it unique. Otherwise
		// wouldn't notice problem until actually written to db.
		int extraNeededForVariation = "_variation".length() + 2;
		if (tripPatternId.length() + extraNeededForVariation > TRIP_PATTERN_ID_LENGTH)
			tripPatternId =
					tripPatternId.substring(tripPatternId.length()
							+ extraNeededForVariation - TRIP_PATTERN_ID_LENGTH);
		
		// Still need to make sure that tripPatternIds are unique. This should
		// never be a problem due to the rather unique way the trip pattern IDs
		// are determined but still want to be absolutely safe since using a 
		// hash of the list of stop IDs and a hash isn't guaranteed to be 
		// unique.
		boolean problemWithTripPatternId = false;
		int variationCounter = 1;
		String originalTripPatternId = tripPatternId;
		while (gtfsData.isTripPatternIdAlreadyUsed(tripPatternId)) {
			tripPatternId = 
					originalTripPatternId + "_variation" + variationCounter++;
			problemWithTripPatternId = true;
		}
		
		if (problemWithTripPatternId)
			GtfsData.logger.info("There was an issue with creating trip " + 
					"pattern for tripId={} for routeId={} in " +
					"TripPattern.generateTripPatternId(). " + 
					"There already was a trip pattern with the desired name. " + 
					"This likely means that a trip pattern is defined with the " +
					"same shapeId (which is used for the trip pattern ID) but " +
					"with different stop list indicating the trips are not " +
					"consistently defined. Therefore using the special " +
					"tripPatternId={}.",
					trip.getId(), trip.getRouteId(), tripPatternId);
		
		return tripPatternId;
	}
	
	/**
	 * When processing a new trip let the TripPattern know that
	 * this additional Trip refers to it. 
	 * @param gtfsTrip
	 */
	public void addTrip(Trip trip) {
		trips.add(trip);
	}
			
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// Don't want to list full trips array because that is
		// a lot of unneeded data. Only list the tripIds from
		// the trips array.
		String tripsIds = "[";
		for (Trip t : trips) {
			tripsIds += t.getId() + ", ";
		}
		tripsIds += "]";
		
		return "TripPattern ["
				+ "configRev=" + configRev
				+ ", id=" + id
				+ ", headsign=" + headsign
				+ ", routeId=" + routeId
				+ ", directionId=" + directionId
				+ ", shapeId=" + shapeId
				+ ", extent=" + extent
				+ ", trips=" + tripsIds
				+ ", stopPaths=" + stopPaths
				+ "]";
	}	

	/**
	 * For when don't want to display the entire contents of TripPattern,
	 * which can be pretty large because contains list of stops and trips.
	 * @return A short version of the TripPattern object
	 */
	public String toShortString() {
		return "Headsign \"" + headsign + "\"" 
				+ " direction " + directionId
				+ " from stop " + stopPaths.get(0).getStopId() 
				+ " to stop " + stopPaths.get(stopPaths.size()-1).getStopId(); 
	}
		
	/**
	 * A short version of the Trip string. Only includes the name and
	 * a list of the trip ids.
	 * @return
	 */
	public String toStringListingTripIds() {
		String s = "Trip Pattern ["
				+ "id=" + id 
				+ ", headsign=" + headsign 
				+ ", trips=[";
		for (Trip trip : trips) {
			s += trip.getId() + ",";
		}
		s += "] ]";
		return s;
	}
		
	/**
	 * A short version of the Trip string. Only includes the name and
	 * a list of the stop ids.
	 * @return
	 */
	public String toStringListingStopIds() {
		String s = "Trip Pattern ["
				+ "id=" + id 
				+ ", headsign=" + headsign 
				+ ", stopIds=[";
		for (StopPath stopPath : stopPaths) {
			s += stopPath.getStopId() + ",";
		}
		s += "] ]";
		return s;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + configRev;
		result = prime * result
				+ ((directionId == null) ? 0 : directionId.hashCode());
		result = prime * result + ((extent == null) ? 0 : extent.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((headsign == null) ? 0 : headsign.hashCode());
		result = prime * result + ((routeId == null) ? 0 : routeId.hashCode());

//		result = prime * result + ((trips == null) ? 0 : trips.hashCode());  // stack overflow with large db

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		TripPattern other = (TripPattern) obj;
		if (configRev != other.configRev)
			return false;
		if (directionId == null) {
			if (other.directionId != null)
				return false;
		} else if (!directionId.equals(other.directionId))
			return false;
		if (extent == null) {
			if (other.extent != null)
				return false;
		} else if (!extent.equals(other.extent))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (headsign == null) {
			if (other.headsign != null)
				return false;
		} else if (!headsign.equals(other.headsign))
			return false;
		if (routeId == null) {
			if (other.routeId != null)
				return false;
		} else if (!routeId.equals(other.routeId))
			return false;
		if (trips == null) {
			if (other.trips != null)
				return false;
		} else if (!trips.equals(other.trips))
			return false;
		return true;
	}

	/**
	 * Returns true if this trip pattern includes the specified stopId.
	 * 
	 * @param stopId
	 * @return
	 */
	public boolean servesStop(String stopId) {
		// Look through this trip pattern to see if it includes specified stop
		for (StopPath stopPath : stopPaths) {
			if (stopPath.getStopId().equals(stopId))
				return true;
		}
		
		// That stop is not in the trip pattern
		return false;
	}
	
	/**
	 * Returns the StopPath for this TripPattern as specified by the stopId
	 * parameter. Uses a map so is reasonably fast. Synchronized to make sure
	 * that only a single thread can initialize the transient map.
	 * 
	 * @param stopId
	 * @return The StopPath specified by the stop ID, or null if this
	 *         TripPattern does not contain that stop.
	 */
	public synchronized StopPath getStopPath(String stopId) {
		// Return the StopPath specified by the stop ID
		return stopPathsMap.get(stopId);
	}
	
	/**
	 * Returns true if for this TripPattern that stopId2 is after stopId1. If
	 * either stopId1 or stopId2 are not in the trip pattern then false is
	 * returned.
	 * 
	 * @param stopId1
	 * @param stopId2
	 * @return True if stopId2 is after stopId1
	 */
	public boolean isStopAtOrAfterStop(String stopId1, String stopId2) {
		// Short cut
		if (stopId1.equals(stopId2))
			return true;
		
		// Go through list of stops for trip pattern
		boolean stopId1Found = false;
		for (StopPath stopPath : stopPaths) {
			if (stopPath.getStopId().equals(stopId1)) {
				stopId1Found = true;
			}
			
			if (stopId1Found && stopPath.getStopId().equals(stopId2)) {
				return true;
			}
		}
		
		return false;
	}
	
	/************** Getter Methods ****************/
	
	/**
	 * @return the configRev
	 */
	public int getConfigRev() {
		return configRev;
	}
	
	/**
	 * @return the List of the stop paths for the trip pattern
	 */
	public List<StopPath> getStopPaths() {
		return stopPaths;
	}
	
	/**
	 * Returns list of stop IDs for the stop paths for this trip pattern.
	 * 
	 * @return
	 */
	public List<String> getStopIds() {
		List<String> list = new ArrayList<String>(stopPaths.size());
		for (StopPath stopPath : stopPaths)
			list.add(stopPath.getStopId());
		return list;
	}
	
	/**
	 * Returns the stop ID of the last stop of the trip. This is the destination
	 * for the trip.
	 * 
	 * @return ID of last stop
	 */
	public String getLastStopIdForTrip() {
		return stopPaths.get(stopPaths.size()-1).getStopId();
	}
	
	/**
	 * Returns length of the trip from the first terminal to the last.
	 * 
	 * @return
	 */
	public double getLength() {
		double length = 0.0;
		for (int i=1; i<stopPaths.size(); ++i) {
			length += stopPaths.get(i).getLength();
		}
		return length;
	}
	
	/**
	 * @param index
	 * @return The specified StopPath or null if index out of range
	 */
	public StopPath getStopPath(int index) {
		if (index < 0 || index >= stopPaths.size())
			return null;
		
		return stopPaths.get(index);
	}
	
	/**
	 * Returns the number of stopPaths/stops configured.
	 * @return
	 */
	public int getNumberStopPaths() {
		return stopPaths.size();
	}
	
	/**
	 * Gets the stopId of the specified stop
	 * @param i
	 * @return
	 */
	public String getStopId(int i) {
		return stopPaths.get(i).getStopId();
	}
	
	/**
	 * Gets the name of the specified stop as obtained by a Core predictor.
	 * Cannot be used with other applications.
	 * 
	 * @param i
	 * @return
	 */
	public String getStopName(int i) {
		return stopPaths.get(i).getStopName();
	}
	
	/**
	 * Gets the pathId of the specified stop
	 * @param i
	 * @return
	 */
	public String getStopPathId(int i) {
		return stopPaths.get(i).getStopPathId();
	}
	
	/**
	 * @return shapeId which is the shape_id from the trip used to
	 * create this trip pattern.
	 */
	public String getShapeId() {
		return shapeId;
	}
	
	
	/**
	 * @return the id which is of the form "stopId1_to_stopIds"
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * @return routeId, the id of the route
	 */
	public String getRouteId() {
		return routeId;
	}

	/**
	 * @return routeShortName, which varies less across schedule changes than
	 *         the routeId
	 */
	public String getRouteShortName() {
		return routeShortName;
	}
	
	/**
	 * For modifying the headsign. Useful for when reading in GTFS data and
	 * determine that the headsign should be modified because it is for a
	 * different last stop or such.
	 * 
	 * @param headsign
	 */
	public void setHeadsign(String headsign) {
		this.headsign =	headsign.length() <= HEADSIGN_LENGTH ? 
				headsign : headsign.substring(0, HEADSIGN_LENGTH);
	}

	/**
	 * Usually from the trip_headsign from the trips.txt file
	 * @return name, the title of the trip pattern
	 */
	public String getHeadsign() {
		return headsign;
	}
	
	/**
	 * Optional element direction_id specified in trips.txt GTFS file.
	 * @return direction_id. Will be null if not specified in GTFS data
	 */
	public String getDirectionId() {
		return directionId;
	}
	
	/**
	 * NOTE: the Trip List is not available when the object has been read from the
	 * database. It is only available while actually processing the GTFS data.
	 * 
	 * @return trips, the list of the trips that use the trip pattern
	 */
	public List<Trip> getTrips() {
		return trips;
	}
	
	/**
	 * The extent of all of the stops that make up the trip pattern
	 * @return extent
	 */
	public Extent getExtent() {
		return extent;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.classic.Lifecycle#onDelete(org.hibernate.Session)
	 */
	@Override
	public boolean onDelete(Session arg0) throws CallbackException {
		// Don't veto delete
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.hibernate.classic.Lifecycle#onLoad(org.hibernate.Session,
	 * java.io.Serializable) 
	 * <p>
	 * Needed in order to initialize the transient member
	 * stopPathsMap. Can't use @PostLoad since using classic Hibernate sessions
	 * instead of an EntityManager.
	 */
	@Override
	public void onLoad(Session arg0, Serializable arg1) {
		// Initialize the transient member stopPathsMaps
		for (StopPath stopPath : stopPaths) {
			stopPathsMap.put(stopPath.getStopId(), stopPath);
		}

	}

	/* (non-Javadoc)
	 * @see org.hibernate.classic.Lifecycle#onSave(org.hibernate.Session)
	 */
	@Override
	public boolean onSave(Session arg0) throws CallbackException {
		// Don't veto save
		return false;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.classic.Lifecycle#onUpdate(org.hibernate.Session)
	 */
	@Override
	public boolean onUpdate(Session arg0) throws CallbackException {
		// Don't veto update
		return false;
	}
	
}


