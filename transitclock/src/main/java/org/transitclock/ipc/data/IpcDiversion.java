package org.transitclock.ipc.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.transitclock.core.diversion.model.Diversion;
import org.transitclock.core.diversion.model.DiversionStopPath;
import org.transitclock.db.structs.Location;

public class IpcDiversion implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2548744289428304091L;

	private String routeId;

	private String tripId;

	private String shapeId;

	private int startStopSeq;

	private int distanceStartAlongSegment;

	private int returnStopSeq;

	private int distanceEndAlongSegment;

	private ArrayList<IpcDiversionStopPath> diversionStopPaths = new ArrayList<IpcDiversionStopPath>();

	private Date startTime;

	private Date endTime;

	public IpcDiversion() {
		super();
		// TODO Auto-generated constructor stub
	}

	public IpcDiversion(String routeId, String tripId, String shapeId, int startStopSeq, int distanceStartAlongSegment,
			int returnStopSeq, int distanceEndAlongSegment, Date startTime, Date endTime) {
		super();
		this.routeId = routeId;
		this.tripId = tripId;
		this.shapeId = shapeId;
		this.startStopSeq = startStopSeq;
		this.distanceStartAlongSegment = distanceStartAlongSegment;
		this.returnStopSeq = returnStopSeq;
		this.distanceEndAlongSegment = distanceEndAlongSegment;
		
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public IpcDiversion(Diversion diversion) {

		this.routeId = diversion.getRouteId();
		this.tripId = diversion.getTripId();
		this.shapeId = diversion.getShapeId();
		this.startStopSeq = diversion.getStartStopSeq();

		this.distanceStartAlongSegment = diversion.getDistanceStartAlongSegment();
		this.returnStopSeq = diversion.getReturnStopSeq();
		this.distanceEndAlongSegment = diversion.getDistanceEndAlongSegment();
		
		this.startTime = diversion.getStartTime();
		this.endTime = diversion.getEndTime();
		
		for(DiversionStopPath stopPath:diversion.getDiversionStopPaths())
		{
			this.diversionStopPaths.add(new IpcDiversionStopPath(stopPath));
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

	public ArrayList<IpcDiversionStopPath> getDiversionStopPaths() {
		return diversionStopPaths;
	}

	public void setDiversionStopPaths(ArrayList<IpcDiversionStopPath> diversionStopPaths) {
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

	@Override
	public String toString() {
		return "IpcDiversion [routeId=" + routeId + ", tripId=" + tripId + ", shapeId=" + shapeId + ", startStopSeq="
				+ startStopSeq + ", distanceStartAlongSegment=" + distanceStartAlongSegment + ", returnStopSeq="
				+ returnStopSeq + ", distanceEndAlongSegment=" + distanceEndAlongSegment + ", diversionStopPaths="
				+ diversionStopPaths + ", startTime=" + startTime + ", endTime=" + endTime + "]";
	}
	
}
