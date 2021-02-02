package org.transitclock.core.diversion.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.transitclock.core.diversion.model.Diversion;

public class DiversionsList implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6633598451497950140L;
	
	List<Diversion> diversions=new ArrayList<Diversion>();
	
	public List<Diversion> getDiversions() {
		return diversions;
	}

	public void setEvents(List<Diversion> diversions) {
		this.diversions = diversions;	
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((diversions == null) ? 0 : diversions.hashCode());
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
		DiversionsList other = (DiversionsList) obj;
		if (diversions == null) {
			if (other.diversions != null)
				return false;
		} else if (!diversions.equals(other.diversions))
			return false;
		return true;
	}
	void addDiversion(Diversion diversion)
	{
		if(this.diversions==null)
			diversions=new ArrayList<Diversion>();
		else
			diversions.add(diversion);
	}
}
