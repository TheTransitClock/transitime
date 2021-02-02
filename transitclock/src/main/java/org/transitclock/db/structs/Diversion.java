package org.transitclock.db.structs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.classic.Lifecycle;

@Entity @DynamicUpdate 
@Table(name="Diversions")
public class Diversion implements Serializable, Lifecycle {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1926444520257485356L;

	@Column
	private String routeId;
	
	@Column	
	private String tripId;
	
	@Id
	private String shapeId;
	
	@Column
	private Date startTime;
	
	@Column
	private Date  endTime;
	
	@Column
	private int startStopSeq;
	
	@Column
	private int distanceStartAlongSegment;
	
	@Column
	private int returnStopSeq;
			
	@Column
	private int distanceEndAlongSegment;
			 
	
	@OrderColumn	
	private ArrayList<Location> detourPath;
	
	
	@OrderColumn	
	private ArrayList<Location> stopLocations;
	
	
	@Column 
	private ArrayList<Long> expectedTravelTimes;
	
	@Transient
	private List<VectorWithHeading> vectors = null;

	public Diversion() {
		super();		
	}
	public Diversion(String routeId, String tripId, String shapeId, Date startTime, Date endTime, int startStopSeq,
			int distanceStartAlongSegment, int returnStopSeq, int distanceEndAlongSegment, ArrayList<Location> detourPath,
			ArrayList<Location> stopLocations, ArrayList<Long>expectedTravelTimes) {
		super();
		this.routeId = routeId;
		this.tripId = tripId;
		this.shapeId = shapeId;
		this.startTime = startTime;
		this.endTime = endTime;
		this.startStopSeq = startStopSeq;
		this.distanceStartAlongSegment = distanceStartAlongSegment;
		this.returnStopSeq = returnStopSeq;
		this.distanceEndAlongSegment = distanceEndAlongSegment;
		this.detourPath = detourPath;
		this.stopLocations = stopLocations;
		this.expectedTravelTimes = expectedTravelTimes;
		
		vectors = new ArrayList<VectorWithHeading>(detourPath.size()-1);
		for (int segmentIndex=0; segmentIndex<detourPath.size()-1; ++segmentIndex) {
			VectorWithHeading v = 
					new VectorWithHeading(nullSafeLocation(detourPath.get(segmentIndex)), 
					              		  nullSafeLocation(detourPath.get(segmentIndex+1)));
			vectors.add(v);
		}
	}
	private Location nullSafeLocation(Location location) {
		  if (location == null) {
		    location = new Location(0.0, 0.0);
		  }
	    return location;
	  }
	@Override
	public void onLoad(Session arg0, Serializable arg1) {
		vectors = new ArrayList<VectorWithHeading>(detourPath.size()-1);
		for (int segmentIndex=0; segmentIndex<detourPath.size()-1; ++segmentIndex) {
			VectorWithHeading v = 
					new VectorWithHeading(nullSafeLocation(detourPath.get(segmentIndex)), 
					              		  nullSafeLocation(detourPath.get(segmentIndex+1)));
			vectors.add(v);
		}
	}

	public String getRouteId() {
		return routeId;
	}

	public void setRouteId(String routeId) {
		this.routeId = routeId;
	}

	public String getTripId() {
		return tripId;
	}

	public void setTripId(String tripId) {
		this.tripId = tripId;
	}

	public String getShapeId() {
		return shapeId;
	}

	public void setShapeId(String shapeId) {
		this.shapeId = shapeId;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public int getStartStopSeq() {
		return startStopSeq;
	}

	public void setStartStopSeq(int startStopSeq) {
		this.startStopSeq = startStopSeq;
	}

	public int getDistanceStartAlongSegment() {
		return distanceStartAlongSegment;
	}

	public void setDistanceStartAlongSegment(int distanceStartAlongSegment) {
		this.distanceStartAlongSegment = distanceStartAlongSegment;
	}

	public int getReturnStopSeq() {
		return returnStopSeq;
	}

	public void setReturnStopSeq(int returnStopSeq) {
		this.returnStopSeq = returnStopSeq;
	}

	public int getDistanceEndAlongSegment() {
		return distanceEndAlongSegment;
	}

	public void setDistanceEndAlongSegment(int distanceEndAlongSegment) {
		this.distanceEndAlongSegment = distanceEndAlongSegment;
	}

	public List<Location> getDetourPath() {
		return detourPath;
	}

	public void setDetourPath(ArrayList<Location> detourPath) {
		this.detourPath = detourPath;
	}

	public List<Location> getStopLocations() {
		return stopLocations;
	}

	public void setStopLocations(ArrayList<Location> stopLocations) {
		this.stopLocations = stopLocations;
	}
	
	

	public List<VectorWithHeading> getVectors() {
		return vectors;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((detourPath == null) ? 0 : detourPath.hashCode());
		result = prime * result + distanceEndAlongSegment;
		result = prime * result + distanceStartAlongSegment;
		result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
		result = prime * result + returnStopSeq;
		result = prime * result + ((routeId == null) ? 0 : routeId.hashCode());
		result = prime * result + ((shapeId == null) ? 0 : shapeId.hashCode());
		result = prime * result + startStopSeq;
		result = prime * result + ((startTime == null) ? 0 : startTime.hashCode());
		result = prime * result + ((stopLocations == null) ? 0 : stopLocations.hashCode());
		result = prime * result + ((tripId == null) ? 0 : tripId.hashCode());
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
		Diversion other = (Diversion) obj;
		if (detourPath == null) {
			if (other.detourPath != null)
				return false;
		} else if (!detourPath.equals(other.detourPath))
			return false;
		if (distanceEndAlongSegment != other.distanceEndAlongSegment)
			return false;
		if (distanceStartAlongSegment != other.distanceStartAlongSegment)
			return false;
		if (endTime == null) {
			if (other.endTime != null)
				return false;
		} else if (!endTime.equals(other.endTime))
			return false;
		if (returnStopSeq != other.returnStopSeq)
			return false;
		if (routeId == null) {
			if (other.routeId != null)
				return false;
		} else if (!routeId.equals(other.routeId))
			return false;
		if (shapeId == null) {
			if (other.shapeId != null)
				return false;
		} else if (!shapeId.equals(other.shapeId))
			return false;
		if (startStopSeq != other.startStopSeq)
			return false;
		if (startTime == null) {
			if (other.startTime != null)
				return false;
		} else if (!startTime.equals(other.startTime))
			return false;
		if (stopLocations == null) {
			if (other.stopLocations != null)
				return false;
		} else if (!stopLocations.equals(other.stopLocations))
			return false;
		if (tripId == null) {
			if (other.tripId != null)
				return false;
		} else if (!tripId.equals(other.tripId))
			return false;
		return true;
	}
	@Override
	public boolean onSave(Session s) throws CallbackException {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean onUpdate(Session s) throws CallbackException {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean onDelete(Session s) throws CallbackException {
		// TODO Auto-generated method stub
		return false;
	}
	
	
}
