package org.transitclock.core.diversion.cache;

import org.transitclock.core.diversion.model.Diversion;

public interface DiversionCache {	
	DiversionsList getDiversions(DiversionsKey key);	
	void putDiversion(Diversion diversion);	
}
