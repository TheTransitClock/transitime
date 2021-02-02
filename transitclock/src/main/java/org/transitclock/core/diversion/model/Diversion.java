package org.transitclock.core.diversion.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Transient;

import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.VectorWithHeading;
import org.transitclock.ipc.data.IpcDiversion;
import org.transitclock.ipc.data.IpcDiversionStopPath;
import org.transitclock.ipc.data.IpcLocation;

/**
 * @author Sean Óg Crudden This is the starting point of modeling a detour.
 */
public class Diversion implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8573881954740003230L;

	private String routeId;

	private String tripId;

	private String shapeId;

	private int startStopSeq;

	private int distanceStartAlongSegment;

	private int returnStopSeq;

	private int distanceEndAlongSegment;

	private ArrayList<DiversionStopPath> diversionStopPaths = new ArrayList<DiversionStopPath>();

	private Date startTime;

	private Date endTime;

	public Diversion() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Diversion(String routeId, String tripId, String shapeId, int startStopSeq, int distanceStartAlongSegment,
			int returnStopSeq, int distanceEndAlongSegment, ArrayList<DiversionStopPath> diversionStopPaths,Date startTime, Date endTime) {
		super();
		this.routeId = routeId;
		this.tripId = tripId;
		this.shapeId = shapeId;
		this.startStopSeq = startStopSeq;
		this.distanceStartAlongSegment = distanceStartAlongSegment;
		this.returnStopSeq = returnStopSeq;
		this.distanceEndAlongSegment = distanceEndAlongSegment;		
		this.diversionStopPaths = diversionStopPaths;
		this.startTime = startTime;
		this.endTime = endTime;

	}

	public Diversion(IpcDiversion diversion) {
		// TODO Auto-generated constructor stub
		this.routeId = diversion.getRouteId();
		this.tripId = diversion.getTripId();
		this.shapeId = diversion.getShapeId();
		this.startStopSeq = diversion.getStartStopSeq();
		this.returnStopSeq = diversion.getReturnStopSeq();
		this.distanceStartAlongSegment = diversion.getDistanceStartAlongSegment();
		this.distanceEndAlongSegment = diversion.getDistanceEndAlongSegment();
		this.startTime = diversion.getStartTime();
		this.endTime = diversion.getEndTime();
		copyStopPaths(diversion);

	}

	void copyStopPaths(IpcDiversion diversion) {
		for(IpcDiversionStopPath ipcStopPath:diversion.getDiversionStopPaths())
		{			
			DiversionStopPath stopPath=new DiversionStopPath();
			stopPath.setDirectionId(ipcStopPath.getDirectionId());
			stopPath.setStopId(ipcStopPath.getStopId());
			stopPath.setStopName(ipcStopPath.getStopName());
			stopPath.setStopSequence(ipcStopPath.getStopSequence());
			if(ipcStopPath.getStopLocation()!=null)
				stopPath.setStopLocation(new Location(ipcStopPath.getStopLocation().getLat(), ipcStopPath.getStopLocation().getLon()));
			
			for(IpcLocation point:ipcStopPath.getPath())
			{
				stopPath.getPath().add(new Location(point.getLat(), point.getLon()));
			}
						
			diversionStopPaths.add(stopPath);
		}
	}

	public ArrayList<DiversionStopPath> getDiversionStopPaths() {
		return diversionStopPaths;
	}

	public void setDiversionStopPaths(ArrayList<DiversionStopPath> diversionStopPaths) {
		this.diversionStopPaths = diversionStopPaths;
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

	public String getShapeId() {
		return shapeId;
	}

	public void setShapeId(String shapeId) {
		this.shapeId = shapeId;
	}

	@Override
	public String toString() {
		return "Diversion [routeId=" + routeId + ", tripId=" + tripId + ", shapeId=" + shapeId + ", startStopSeq="
				+ startStopSeq + ", distanceStartAlongSegment=" + distanceStartAlongSegment + ", returnStopSeq="
				+ returnStopSeq + ", distanceEndAlongSegment=" + distanceEndAlongSegment + ", diversionStopPaths="
				+ diversionStopPaths + ", startTime=" + startTime + ", endTime=" + endTime + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + distanceEndAlongSegment;
		result = prime * result + distanceStartAlongSegment;
		result = prime * result + ((endTime == null) ? 0 : endTime.hashCode());
		result = prime * result + returnStopSeq;
		result = prime * result + ((routeId == null) ? 0 : routeId.hashCode());
		result = prime * result + ((shapeId == null) ? 0 : shapeId.hashCode());
		result = prime * result + startStopSeq;
		result = prime * result + ((startTime == null) ? 0 : startTime.hashCode());
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
		if (tripId == null) {
			if (other.tripId != null)
				return false;
		} else if (!tripId.equals(other.tripId))
			return false;
		return true;
	}

}
