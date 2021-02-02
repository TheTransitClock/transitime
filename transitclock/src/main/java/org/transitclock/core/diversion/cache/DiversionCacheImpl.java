package org.transitclock.core.diversion.cache;

import java.util.List;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.core.dataCache.ehcache.CacheManagerFactory;
import org.transitclock.core.diversion.model.Diversion;

public class DiversionCacheImpl implements DiversionCache {

	final private static String cacheName = "diversionsCache";
	private static final Logger logger = LoggerFactory.getLogger(DiversionCacheImpl.class);

	private Cache<DiversionsKey, DiversionsList> cache = null;

	@Override
	public DiversionsList getDiversions(DiversionsKey key) {

		return cache.get(key);
	}

	public DiversionCacheImpl() {
		CacheManager cm = CacheManagerFactory.getInstance();

		cache = cm.getCache(cacheName, DiversionsKey.class,  DiversionsList.class);
	}

	@Override
	public void putDiversion(Diversion diversion) {
		DiversionsList result = cache.get(new DiversionsKey(diversion));
		if(result==null)
			result = new DiversionsList();
				
		if(!inList(result.getDiversions(),diversion))
		{			
			result.addDiversion(diversion);
		
			cache.put(new DiversionsKey(diversion), result);
		}
	}
	private boolean inList(List<Diversion> list, Diversion diversion)
	{					
		for(Diversion item:list)
		{
			if(item.equals(diversion))
			{
				return true;
			}
		}
		return false;
	}

}
