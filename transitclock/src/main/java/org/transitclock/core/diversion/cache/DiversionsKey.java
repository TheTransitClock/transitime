package org.transitclock.core.diversion.cache;

import java.io.Serializable;

import org.transitclock.core.diversion.model.Diversion;

public class DiversionsKey implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5427491805221655172L;
	String tripId;
	
	
	public DiversionsKey(Diversion diversion) {
		this.tripId=diversion.getTripId();
						
	}		
	
	public DiversionsKey(String tripId, String routeId) {
		super();
		this.tripId = tripId;
	}

	

	public String getTripId() {
		return tripId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		DiversionsKey other = (DiversionsKey) obj;
		if (tripId == null) {
			if (other.tripId != null)
				return false;
		} else if (!tripId.equals(other.tripId))
			return false;
		return true;
	}



	


}
