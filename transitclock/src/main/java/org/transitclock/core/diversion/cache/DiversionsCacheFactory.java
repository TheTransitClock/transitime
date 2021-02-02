package org.transitclock.core.diversion.cache;

import org.transitclock.config.StringConfigValue;
import org.transitclock.utils.ClassInstantiator;

public class DiversionsCacheFactory {
	private static StringConfigValue className = 
			new StringConfigValue("transitclock.core.cache.diversionsCache", 
					"org.transitclock.core.diversion.cache.DiversionCacheImpl",
					"Specifies the class used to cache diversions.");
	
	private static DiversionCache singleton = null;
	
	public static DiversionCache getInstance() {
		
		if (singleton == null) {
			singleton = ClassInstantiator.instantiate(className.getValue(), 
					DiversionCache.class);
		}
		
		return singleton;
	}
}
