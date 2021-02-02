package org.transitclock.ipc.data;

import java.io.Serializable;
import java.util.ArrayList;
import org.transitclock.core.diversion.model.DiversionStopPath;
import org.transitclock.db.structs.Location;

public class IpcDiversionStopPath implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5887640144411684439L;
	private String stopId;
	private String stopName;
	private Integer stopSequence;
	private Location stopLocation;
	private String directionId;
	private ArrayList<IpcLocation> path = new ArrayList<IpcLocation>();
	
	public IpcDiversionStopPath(String stopId, String stopName, Integer stopSequence, Location stopLocation,
			String directionId, ArrayList<IpcLocation> path) {
		super();
		this.stopId = stopId;
		this.stopName = stopName;
		this.stopSequence = stopSequence;
		this.stopLocation = stopLocation;
		this.directionId = directionId;
		this.path = path;
	}
	public IpcDiversionStopPath(DiversionStopPath diversionStopPath)
	{
		this.stopId=diversionStopPath.getStopId();
		this.stopName=diversionStopPath.getStopName();
		this.stopSequence=diversionStopPath.getStopSequence();
		this.stopLocation=diversionStopPath.getStopLocation();
		this.directionId=diversionStopPath.getDirectionId();
		copyPath(diversionStopPath);
	}
	private void copyPath(DiversionStopPath diversionStopPath)
	{
		for(Location point:diversionStopPath.getPath()) {
			path.add(new IpcLocation(point));
		}
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
	public String getDirectionId() {
		return directionId;
	}
	public void setDirectionId(String directionId) {
		this.directionId = directionId;
	}
	public ArrayList<IpcLocation> getPath() {
		return path;
	}
	public void setPath(ArrayList<IpcLocation> path) {
		this.path = path;
	}
	@Override
	public String toString() {
		return "IpcDiversionStopPath [stopId=" + stopId + ", stopName=" + stopName + ", stopSequence=" + stopSequence
				+ ", stopLocation=" + stopLocation + ", directionId=" + directionId + ", path=" + path + "]";
	}
	
}
