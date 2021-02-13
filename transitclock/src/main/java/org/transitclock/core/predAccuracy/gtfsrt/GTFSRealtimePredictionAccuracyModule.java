/*
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */

package org.transitclock.core.predAccuracy.gtfsrt;

import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.ClassConfigValue;
import org.transitclock.config.StringConfigValue;
import org.transitclock.core.predAccuracy.PredAccuracyPrediction;
import org.transitclock.core.predAccuracy.PredictionAccuracyModule;
import org.transitclock.db.structs.ScheduleTime;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.TripPattern;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.utils.Time;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

/**
 * Reads in external prediction data from a GTFS realtime trip updates feed and
 * stores the data in memory. Then when arrivals/departures occur the prediction
 * accuracy can be determined and stored.
 *
 * @author Sean Og Crudden
 *
 */
public class GTFSRealtimePredictionAccuracyModule extends PredictionAccuracyModule {

	private static final Logger logger = LoggerFactory.getLogger(GTFSRealtimePredictionAccuracyModule.class);

	/********************** Config Params **************************/


	private static final StringConfigValue gtfsTripUpdateUrl = new StringConfigValue(
			"transitclock.predAccuracy.gtfsTripUpdateUrl", "http://127.0.0.1:8091/trip-updates",
			"URL to access gtfs-rt trip updates.");

	
  private static ClassConfigValue translatorConfig =
      new ClassConfigValue("transitclock.predAccuracy.RtTranslator", null, 
          "Implementation of GTFSRealtimeTranslator to perform " + 
      "the translation of stopIds and other rt quirks");
	
  // if stopIds needs optional parsing/translation
  private GTFSRealtimeTranslator translator = null;
  

	/**
	 * @return the gtfstripupdateurl
	 */
	public static StringConfigValue getGtfstripupdateurl() {
		return gtfsTripUpdateUrl;
	}

	/********************** Member Functions **************************/

	/**
	 * @param agencyId
	 * @throws Exception 
	 */
	public GTFSRealtimePredictionAccuracyModule(String agencyId) throws Exception {
		super(agencyId);
		if (translatorConfig.getValue() != null) {
		  logger.info("instantiating translator {}", translatorConfig.getValue());
		  translator = (GTFSRealtimeTranslator) translatorConfig.getValue().newInstance();
		}
	}

	/**
	 * Gets GTFS realtime feed all routes from URL and return FeedMessage
	 * 
	 * @return the FeedMessage to be processed
	 */

	private FeedMessage getExternalPredictions() {

		// Will just read all data from gtfs-rt url
		URL url = null;
		logger.info("Getting predictions from API using URL={}", getGtfstripupdateurl().getValue());

		try {
			// Create the connection
			url = new URL(getGtfstripupdateurl().getValue());

			FeedMessage feed = FeedMessage.parseFrom(url.openStream());
			logger.info("Prediction read successfully from URL={}", getGtfstripupdateurl().getValue());

			return feed;
		} catch (Exception e) {
			logger.error("Problem when getting data from GTFS realtime trip updates URL={}", url, e);
			return null;
		}
	}

	/**
	 * Takes data from XML Document object and processes it and calls
	 * storePrediction() on the predictions.
	 * 
	 * @param feed
	 * @param predictionsReadTime
	 */
	private void processExternalPredictions(FeedMessage feed, Date predictionsReadTime) {

		// If couldn't read data from feed then can't process it
		if (feed == null)
			return;

		// So can look up direction in database
		DbConfig dbConfig = Core.getInstance().getDbConfig();

		logger.info("Processing GTFS-rt feed.....");

		for (FeedEntity entity : feed.getEntityList()) {
			if (entity.hasTripUpdate()) {
				TripUpdate update = entity.getTripUpdate();
				List<StopTimeUpdate> stopTimes = update.getStopTimeUpdateList();
				Date eventReadTime = null;
				if (update.hasTimestamp()) {
					/*
					 * this is the best option as it is specific to each trip
					 * update
					 */
					eventReadTime = new Date(update.getTimestamp() * 1000);
				} else {
					/*
					 * can't do anything else? TODO Except maybe look to see if
					 * this has changed and then take the time it has changed as
					 * the read time
					 */
					eventReadTime = new Date(feed.getHeader().getTimestamp() * 1000);
				}

				for (int i = 0; i < stopTimes.size(); i++) {

					StopTimeUpdate stopTime = stopTimes.get(i);

					if (stopTime.hasArrival() || stopTime.hasDeparture()) {

						String direction = null;

						if (update.getTrip().hasDirectionId())
							direction = "" + update.getTrip().getDirectionId();

						if (update.getTrip() != null) {
							Trip trip = dbConfig.getTrip(update.getTrip().getTripId());
							if (trip != null) {
								direction = trip.getDirectionId();
							} else {
								logger.error("Got tripTag={} but no such trip in " + "the configuration.",
										update.getTrip().getTripId());

							}
						}					
						Trip gtfsTrip = dbConfig.getTrip(update.getTrip().getTripId());

						if (gtfsTrip != null) {
							
							logger.debug("Trip loaded.");
							

							logger.debug("Processing : \n" + stopTime);
							/* use stop as means for getting scheduled time */
													
							int stopPathIndex = -1;
							if (stopTime.hasStopSequence()) {
								try {
									stopPathIndex = getStopPathIndex(gtfsTrip, stopTime.getStopSequence());
								} catch (Exception e) {
									logger.error("Not valid stop sequence {} for trip {}.", stopTime.getStopSequence(),
											gtfsTrip.getId());
								}
							} else if (stopTime.hasStopId() && !stopTime.hasStopSequence()) {
								StopPath stopPath = gtfsTrip.getStopPath(stopTime.getStopId());
								// StopPath stopPath = getStopPath(gtfsTrip,
								// stopTime.getStopSequence());

								if (stopPath != null)
									logger.debug(stopPath.toString());

								stopPathIndex = getStopPathIndex(gtfsTrip, stopPath);

							} else {
								logger.error(
										"StopTimeUpdate must have stop id or stop sequence set:" + stopTime.toString());
							}
							if (stopPathIndex >= 0) {

								logger.debug("StopPathIndex : " + stopPathIndex);
								int nextStopIndexIncrement = 0;

								StopTimeUpdate nextStopTime = null;

								if (i + 1 < stopTimes.size())
									nextStopTime = stopTimes.get(i + 1);

								while ((nextStopTime != null && !stopTimeMatchesStopPath(nextStopTime,
										gtfsTrip.getStopPath(stopPathIndex + nextStopIndexIncrement)))
										|| (nextStopTime == null && (stopPathIndex + nextStopIndexIncrement) < gtfsTrip
												.getStopPaths().size())) {
									ScheduleTime scheduledTime = null;

									scheduledTime = gtfsTrip.getScheduleTime(stopPathIndex + nextStopIndexIncrement);

									String stopId = gtfsTrip.getStopPath(stopPathIndex + nextStopIndexIncrement)
											.getStopId();

									nextStopIndexIncrement++;

									if (scheduledTime != null)
										logger.debug(scheduledTime.toString());
									else
										logger.debug("No schedule time found");

									Date eventTime = null;
									if (scheduledTime != null) {
										eventTime = null;
										if (stopTime.hasArrival()) {
											if (stopTime.getArrival().hasTime()) {
												eventTime = new Date(stopTime.getArrival().getTime() * 1000);

												// logger.debug("Event Time : "
												// +
												// eventTime);
											} else if (stopTime.getArrival().hasDelay()) {

												int timeInSeconds = stopTime.getArrival().getDelay();

												if (scheduledTime.getDepartureTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getDepartureTime();
												else if (scheduledTime.getArrivalTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getArrivalTime();

												Calendar calendar = Calendar.getInstance();
												calendar.set(Calendar.HOUR_OF_DAY, 0);
												calendar.set(Calendar.MINUTE, 0);
												calendar.set(Calendar.SECOND, 0);

												calendar.add(Calendar.SECOND, timeInSeconds);

												eventTime = calendar.getTime();
												logger.debug("Event Time : " + eventTime);
												logger.debug("Time in seconds :" + timeInSeconds);

											} else if (update.hasDelay()) {

												int timeInSeconds = update.getDelay();

												if (scheduledTime.getDepartureTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getDepartureTime();
												else if (scheduledTime.getArrivalTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getArrivalTime();

												Calendar calendar = Calendar.getInstance();
												calendar.set(Calendar.HOUR_OF_DAY, 0);
												calendar.set(Calendar.MINUTE, 0);
												calendar.set(Calendar.SECOND, 0);

												calendar.add(Calendar.SECOND, timeInSeconds);

												eventTime = calendar.getTime();
												logger.debug("Event Time : " + eventTime);
												logger.debug("Time in seconds :" + timeInSeconds);
											}
											if (eventTime != null) {
												/*
												 * TODO could be used to cache
												 * read times
												 */
												/*
												 * if (readTimesMap.get(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime())) ==
												 * null) readTimesMap.put(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime()),
												 * eventReadTime.getTime());
												 * else eventReadTime.setTime(
												 * readTimesMap.get(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime())));
												 */
												if (eventTime.after(eventReadTime)) {

													logger.info(
															"Storing external prediction routeId={}, "
																	+ "directionId={}, tripId={}, vehicleId={}, "
																	+ "stopId={}, prediction={}, isArrival={}, scheduledTime={}, readTime={}",
															gtfsTrip.getRouteId(), direction,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															stopId, eventTime, true, scheduledTime.toString(),
															eventReadTime.toString());

													logger.info("Prediction in milliseconds is {} and converted is {}",
															eventTime.getTime(), eventTime);

													PredAccuracyPrediction pred = new PredAccuracyPrediction(
															gtfsTrip.getRouteId(), direction, stopId,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															eventTime, eventReadTime, null, true, new Boolean(false),
															"GTFS-rt", null,scheduledTime.toString(), null, null);

													storePrediction(pred);
												} else {
													logger.info(
															"Discarding as prediction after event. routeId={}, "
																	+ "directionId={}, tripId={}, vehicleId={}, "
																	+ "stopId={}, prediction={}, isArrival={}, scheduledTime={}, readTime={}",
															gtfsTrip.getRouteId(), direction,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															stopId, eventTime, true, scheduledTime.toString(),
															eventReadTime.toString());
												}
											}
										}
										eventTime = null;
										if (stopTime.hasDeparture()) {
											if (stopTime.getDeparture().hasTime()) {
												eventTime = new Date(stopTime.getDeparture().getTime() * 1000);

												logger.debug("Event Time : " + eventTime);
											} else if (stopTime.getDeparture().hasDelay()) {

												int timeInSeconds = stopTime.getDeparture().getDelay();

												if (scheduledTime.getDepartureTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getDepartureTime();
												else if (scheduledTime.getArrivalTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getArrivalTime();

												Calendar calendar = Calendar.getInstance();
												calendar.set(Calendar.HOUR_OF_DAY, 0);
												calendar.set(Calendar.MINUTE, 0);
												calendar.set(Calendar.SECOND, 0);

												calendar.add(Calendar.SECOND, timeInSeconds);

												eventTime = calendar.getTime();
												logger.debug("Event Time : " + eventTime);
												logger.debug("Time in seconds :" + timeInSeconds);
											} else if (update.hasDelay()) {

												int timeInSeconds = update.getDelay();

												if (scheduledTime.getDepartureTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getDepartureTime();
												else if (scheduledTime.getArrivalTime() != null)
													timeInSeconds = timeInSeconds + scheduledTime.getArrivalTime();

												Calendar calendar = Calendar.getInstance();
												calendar.set(Calendar.HOUR_OF_DAY, 0);
												calendar.set(Calendar.MINUTE, 0);
												calendar.set(Calendar.SECOND, 0);

												calendar.add(Calendar.SECOND, timeInSeconds);

												eventTime = calendar.getTime();
												logger.debug("Event Time : " + eventTime);
												logger.debug("Time in seconds :" + timeInSeconds);
											}
											if (eventTime != null) {
												/*
												 * TODO could be used to cache
												 * read times
												 */
												/*
												 * if (readTimesMap.get(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime())) ==
												 * null) readTimesMap.put(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime()),
												 * eventReadTime.getTime());
												 * else eventReadTime.setTime(
												 * readTimesMap.get(new
												 * PredictionReadTimeKey(stopId,
												 * update.getVehicle().getId(),
												 * eventTime.getTime())));
												 */
												if (eventTime.after(eventReadTime)) {
													logger.info(
															"Storing external prediction routeId={}, "
																	+ "directionId={}, tripId={}, vehicleId={}, "
																	+ "stopId={}, prediction={}, isArrival={}, scheduledTime={}, readTime={}",
															gtfsTrip.getRouteId(), direction,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															stopId, eventTime, false, scheduledTime.toString(),
															eventReadTime.toString());

													logger.info("Prediction in milliseonds is {} and converted is {}",
															eventTime.getTime(), eventTime);

													PredAccuracyPrediction pred = new PredAccuracyPrediction(
															gtfsTrip.getRouteId(), direction, stopId,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															eventTime, eventReadTime,null, false, new Boolean(false),
															"GTFS-rt",null, scheduledTime.toString(), null, null);

													storePrediction(pred);
												} else {
													logger.info(
															"Discarding as prediction after event. routeId={}, "
																	+ "directionId={}, tripId={}, vehicleId={}, "
																	+ "stopId={}, prediction={}, isArrival={}, scheduledTime={}, readTime={}",
															gtfsTrip.getRouteId(), direction,
															update.getTrip().getTripId(), update.getVehicle().getId(),
															stopId, eventTime, false, scheduledTime.toString(),
															eventReadTime.toString());
												}
											}
										}
									}
								}
							}
						}
					} else {
						logger.debug("No predictions for vehicleId={} for stop={}", update.getVehicle().getId(),
								stopTime.getStopId());
					}
				}
			}
		}
	}

	private boolean stopTimeMatchesStopPath(StopTimeUpdate stopTimeUpdate, StopPath stopPath) {
		if (stopTimeUpdate.hasStopSequence()) {
			if (stopTimeUpdate.getStopSequence() == stopPath.getGtfsStopSeq())
				return true;
			else
				return false;
		} else if (stopTimeUpdate.hasStopId()) {
			if (stopTimeUpdate.getStopId().equals(stopPath.getStopId()))
				return true;
			else
				return false;
		} else
			return false;
	}

	private int getStopPathIndex(Trip gtfsTrip, int gtfsStopSequence) {
		int index = 0;
		for (StopPath path : gtfsTrip.getStopPaths()) {
			if (path.getGtfsStopSeq() == gtfsStopSequence) {
				return index;
			}
			index++;
		}
		return -1;
	}

	private int getStopPathIndex(Trip gtfsTrip, StopPath stopPath) {

		if (gtfsTrip != null && stopPath != null) {
			for (int i = 0; i < gtfsTrip.getNumberStopPaths(); i++) {
				if (gtfsTrip.getStopPath(i).getStopPathId().equals(stopPath.getStopPathId())) {
					return i;
				}
			}
		}
		return -1;
	}

	/**

	 * Processes both the internal and external predictions
	 * 
	 * @param routesAndStops
	 * @param predictionsReadTime
	 *            For keeping track of when the predictions read in. Used for
	 *            determining length of predictions. Should be the same for all
	 *            predictions read in during a polling cycle even if the
	 *            predictions are read at slightly different times. By using the
	 *            same time can easily see from data in db which internal and
	 *            external predictions are associated with each other.
	 */
	@Override
	protected void getAndProcessData(List<RouteAndStops> routesAndStops, Date predictionsReadTime) {
		// Process internal predictions

		// super.getAndProcessData(routesAndStops, predictionsReadTime);

		logger.info("Calling GTFSRealtimePredictionAccuracyModule." + "getAndProcessData()");

		// Get data for all items in the GTFS-RT trip updates feed
		FeedMessage feed = getExternalPredictions();

		processExternalPredictions(feed, predictionsReadTime);

	}

	class PredictionReadTimeKey {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (int) (predictedTime ^ (predictedTime >>> 32));
			result = prime * result + ((stopId == null) ? 0 : stopId.hashCode());
			result = prime * result + ((vehicleId == null) ? 0 : vehicleId.hashCode());
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
			PredictionReadTimeKey other = (PredictionReadTimeKey) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (predictedTime != other.predictedTime)
				return false;
			if (stopId == null) {
				if (other.stopId != null)
					return false;
			} else if (!stopId.equals(other.stopId))
				return false;
			if (vehicleId == null) {
				if (other.vehicleId != null)
					return false;
			} else if (!vehicleId.equals(other.vehicleId))
				return false;
			return true;
		}

		public PredictionReadTimeKey(String stopId, String vehicleId, long predictedTime) {
			super();
			this.stopId = stopId;
			this.vehicleId = vehicleId;
			this.predictedTime = predictedTime;
		}

		String stopId;
		String vehicleId;
		long predictedTime;

		private GTFSRealtimePredictionAccuracyModule getOuterType() {
			return GTFSRealtimePredictionAccuracyModule.this;
		}
	}

	static HashMap<PredictionReadTimeKey, Long> readTimesMap = new HashMap<PredictionReadTimeKey, Long>();

	Date getDate(int secondsFromMidnight) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);

		calendar.add(Calendar.SECOND, secondsFromMidnight);

		return calendar.getTime();

	}
}
