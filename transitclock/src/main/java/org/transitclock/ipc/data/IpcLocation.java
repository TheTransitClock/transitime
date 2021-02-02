package org.transitclock.ipc.data;

import java.io.Serializable;

import org.transitclock.db.structs.Location;

public class IpcLocation implements Serializable{

	private Double  lat;


	private Double lon;


	public Double getLat() {
		return lat;
	}


	public Double getLon() {
		return lon;
	}


	public IpcLocation(Double lat, Double lon) {
		super();
		this.lat = lat;
		this.lon = lon;
	}
	
	public IpcLocation(Location location)
	{
		this.lat=location.getLat();
		this.lon=location.getLon();
	}


	@Override
	public String toString() {
		return "IpcLocation [lat=" + lat + ", lon=" + lon + "]";
	}
	
}
