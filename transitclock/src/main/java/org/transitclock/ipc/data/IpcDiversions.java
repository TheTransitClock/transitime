package org.transitclock.ipc.data;

import java.io.Serializable;
import java.util.List;

import org.transitclock.core.diversion.cache.DiversionsList;

public class IpcDiversions implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 7884832425433350816L;

	private final List<IpcDiversion> diversions;

	public IpcDiversions(List<IpcDiversion> diversions) {
		super();
		this.diversions = diversions;
	}

	public List<IpcDiversion> getDiversions() {
		return diversions;
	}

	@Override
	public String toString() {
		return "IpcDiversions [diversions=" + diversions + "]";
	}
	
}
