package org.transitclock.core.dataCache.ehcache.scheduled;

import java.util.concurrent.TimeUnit;

import org.ehcache.ValueSupplier;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expiry;
import org.transitclock.core.dataCache.TripEvents;
import org.transitclock.core.dataCache.TripKey;

public class TripDataHistoryCacheExpiry implements Expiry<TripKey, TripEvents> {

	@Override
	public Duration getExpiryForCreation(TripKey key, TripEvents value) {
		Duration duration=new Duration(21, TimeUnit.DAYS);
		return duration;
	}

	@Override
	public Duration getExpiryForAccess(TripKey key, ValueSupplier<? extends TripEvents> value) {		
		return null;
	}

	@Override
	public Duration getExpiryForUpdate(TripKey key, ValueSupplier<? extends TripEvents> oldValue, TripEvents newValue) {		
		return null;
	}

}
