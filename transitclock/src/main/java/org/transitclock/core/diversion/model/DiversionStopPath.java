package org.transitclock.core.diversion.model;

import java.io.Serializable;
import java.util.ArrayList;

import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.VectorWithHeading;

public class DiversionStopPath implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1598813782991216200L;
	private String stopId;
	private String stopName;
	private Integer stopSequence;
	private Location stopLocation;
	private String directionId;
	private ArrayList<Location> path = new ArrayList<Location>();
	
	private ArrayList<VectorWithHeading> vectors = null;
	
	public DiversionStopPath() {
		super();
		// TODO Auto-generated constructor stub
	}
	public DiversionStopPath(String stopId, String stopName, Integer stopSequence, Location stopLocation,
			String directionId, ArrayList<Location> path) {
		super();
		this.stopId = stopId;
		this.stopName = stopName;
		this.stopSequence = stopSequence;
		this.stopLocation = stopLocation;
		this.directionId = directionId;
		this.path = path;
	}
	public String getDirectionId() {
		return directionId;
	}
	public void setDirectionId(String directionId) {
		this.directionId = directionId;
	}
	public String getStopId() {
		return stopId;
	}
	public void setStopId(String stopId) {
		this.stopId = stopId;
	}
	public String getStopName() {
		return stopName;
	}
	public void setStopName(String stopName) {
		this.stopName = stopName;
	}
	public Integer getStopSequence() {
		return stopSequence;
	}
	public void setStopSequence(Integer stopSequence) {
		this.stopSequence = stopSequence;
	}
	public Location getStopLocation() {
		return stopLocation;
	}
	public void setStopLocation(Location stopLocation) {
		this.stopLocation = stopLocation;
	}
	public ArrayList<Location> getPath() {
		return path;
	}
	public void setPath(ArrayList<Location> path) {
		this.path = path;
	}
	public ArrayList<VectorWithHeading> getVectors() {
		if(vectors==null)
			initVectors();
		return vectors;
	}
		
	private void initVectors()
	{
		vectors = new ArrayList<VectorWithHeading>(path.size() - 1);
		for (int segmentIndex = 0; segmentIndex < path.size() - 1; ++segmentIndex) {
			VectorWithHeading v = new VectorWithHeading(nullSafeLocation(path.get(segmentIndex)),
					nullSafeLocation(path.get(segmentIndex + 1)));
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
	public String toString() {
		return "DiversionStopPath [stopId=" + stopId + ", stopName=" + stopName + ", stopSequence=" + stopSequence
				+ ", stopLocation=" + stopLocation + ", directionId=" + directionId + "]";
	}
	
}
