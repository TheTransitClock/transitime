package org.transitclock.api.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.transitclock.db.structs.Location;
import org.transitclock.ipc.data.IpcDiversion;
import org.transitclock.ipc.data.IpcDiversionStopPath;
import org.transitclock.ipc.data.IpcLocation;
@XmlRootElement
public class ApiDiversion implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2524022174620441284L;
	@XmlAttribute
	private String routeId;
	@XmlAttribute
	private String tripId;
	@XmlAttribute
	private String shapeId;
	@XmlAttribute
	private int startStopSeq;
	@XmlAttribute
	private int distanceStartAlongSegment;
	@XmlAttribute
	private int returnStopSeq;
	@XmlAttribute
	private int distanceEndAlongSegment;	
	@XmlElement(name="stopPaths")
	private List<ApiDiversionStopPath> diversionStopPaths;
	@XmlAttribute
	private Date startTime;
	@XmlAttribute
	private Date  endTime;
	
	/**
	 * Need a no-arg constructor for Jersey. Otherwise get really obtuse
	 * "MessageBodyWriter not found for media type=application/json" exception.
	 */
	protected ApiDiversion() {
		
	}

	public ApiDiversion(IpcDiversion diversion)
	{
		this.startStopSeq=diversion.getStartStopSeq();
		this.returnStopSeq=diversion.getReturnStopSeq();
		this.routeId=diversion.getRouteId();
		this.shapeId=diversion.getShapeId();
		this.tripId=diversion.getTripId();	
		this.distanceStartAlongSegment=diversion.getDistanceStartAlongSegment();
		this.distanceStartAlongSegment=diversion.getDistanceStartAlongSegment();			
		
		this.startTime=diversion.getStartTime();
		this.endTime=diversion.getEndTime();
		
		this.diversionStopPaths=new ArrayList<ApiDiversionStopPath>();		
		for( IpcDiversionStopPath ipcStopPath:diversion.getDiversionStopPaths())
		{
			this.diversionStopPaths.add(new ApiDiversionStopPath(ipcStopPath));			
		}
	}

	public String getRouteId() {
		return routeId;
	}

	public String getTripId() {
		return tripId;
	}

	public String getShapeId() {
		return shapeId;
	}

	public int getStartStopSeq() {
		return startStopSeq;
	}

	public int getDistanceStartAlongSegment() {
		return distanceStartAlongSegment;
	}

	public int getReturnStopSeq() {
		return returnStopSeq;
	}

	public int getDistanceEndAlongSegment() {
		return distanceEndAlongSegment;
	}

	public List<ApiDiversionStopPath> getDiversionStopPaths() {
		return diversionStopPaths;
	}

	public Date getStartTime() {
		return startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	
}
