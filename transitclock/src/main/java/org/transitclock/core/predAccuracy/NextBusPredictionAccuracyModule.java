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

package org.transitclock.core.predAccuracy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.applications.Core;
import org.transitclock.config.StringConfigValue;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.Trip;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.modules.Module;

/**
 * Reads in external prediction data from NextBus feed and internal Transitime
 * predictions (since inheriting from PredictionAccuracyModule) and stores the
 * data in memory. Then when arrivals/departures occur the prediction accuracy
 * can be determined and stored.
 *
 * @author SkiBu Smith
 *
 */
public class NextBusPredictionAccuracyModule extends PredictionAccuracyModule {

	// For when requesting predictions from external NextBus API
	private static final int timeoutMsec = 20000;
	
	private static final Logger logger = LoggerFactory
			.getLogger(NextBusPredictionAccuracyModule.class);

	/********************** Config Params **************************/
	
	private static final StringConfigValue externalPredictionApiUrl = 
			new StringConfigValue("transitclock.predAccuracy.externalPredictionApiUrl", 
					"http://webservices.nextbus.com/service/publicXMLFeed?",
					"URL to access to obtain external predictions.");
	
	private static String getExternalPredictionApiUrl() {
		return externalPredictionApiUrl.getValue();
	}
	
	private static final StringConfigValue nextBusAgencyIdForApi =
			new StringConfigValue("transitclock.predAccuracy.nextBusAgencyIdForApi",
					 null,
					 "Name of agency for API");
	
	private static String getNextBusAgencyIdForApi() {
		return nextBusAgencyIdForApi.getValue();
	}
	
	/********************** Member Functions **************************/

	/**
	 * @param agencyId
	 */
	public NextBusPredictionAccuracyModule(String agencyId) {
		super(agencyId);
	}

	/**
	 * Determine the URL to use to get data for all the routes/stops
	 * 
	 * @param routeId
	 * @return
	 */
	private String getUrl(RouteAndStops routeAndStops) {
		// Get base part of URL
		String fullUrl = getExternalPredictionApiUrl() 
				+ "command=predictionsForMultiStops"
				+ "&a=" + getNextBusAgencyIdForApi();
		
		// Determine the route short name since that is what NextBus API uses
		DbConfig dbConfig = Core.getInstance().getDbConfig();
		Route route = dbConfig.getRouteById(routeAndStops.routeId);
		if (route == null) {
			logger.error("No route with routeId={}", routeAndStops.routeId);
		}
		// But route_short_name is optional in GTFS and LA Metro doesn't use it
		// for the rail lines. So use route_id if route_short_name not defined,
		// though that might not work either.
		String routeIdForNextBusApi = route.getShortName();
		if (routeIdForNextBusApi == null || routeIdForNextBusApi.isEmpty())
			routeIdForNextBusApi = route.getId();
		
		// For all of the stops for the route, complete the URL
		for (String directionId : routeAndStops.stopIds.keySet()) {
			Collection<String> stopIds = routeAndStops.stopIds.get(directionId);
			for (String stopId : stopIds) {
				fullUrl += "&stops=" + routeIdForNextBusApi + "|" + stopId;
			}
		}
		
		return fullUrl;
	}
	
	/**
	 * Gets XML data for a route from API an returns an XML Document object
	 * containing the resulting data.
	 * 
	 * @param routeAndStops
	 *            Specifies which route and stops to read data for
	 * @return the XML document to be parsed
	 */
	private Document getExternalPredictionsForRoute(
			RouteAndStops routeAndStops) {
		String fullUrl = getUrl(routeAndStops);
		
		logger.info("Getting predictions from API using URL={}", fullUrl);

		try {
			// Create the connection
			URL url = new URL(fullUrl);
			URLConnection con = url.openConnection();
			
			// Set the timeout so don't wait forever
			con.setConnectTimeout(timeoutMsec);
			con.setReadTimeout(timeoutMsec);
			
			// Request compressed data to reduce bandwidth used
			con.setRequestProperty("Accept-Encoding", "gzip,deflate");
			
			// Make sure the response is proper
			int httpResponseCode = ((HttpURLConnection) con).getResponseCode();
			if (httpResponseCode != HttpURLConnection.HTTP_OK) {
				// Got back unexpected error so log it
				logger.error("Error when getting predictions. Response "
						+ "code was {} for URL={}", 
						httpResponseCode, fullUrl);
				return null;
			}
				
			// Create appropriate input stream depending on whether content is 
			// compressed or not
			InputStream in = con.getInputStream();
			if ("gzip".equals(con.getContentEncoding())) {
			    in = new GZIPInputStream(in);
			    logger.debug("Returned XML data is compressed");
			} else {
			    logger.debug("Returned XML data is NOT compressed");			
			}

			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(in);
			in.close();
			
			return doc;
		} catch (IOException | JDOMException e) {
			logger.error("Problem when getting data for route for URL={}", 
					fullUrl, e);
			return null;
		}
	}
	
	/**
	 * Takes data from XML Document object and processes it and calls
	 * storePrediction() on the predictions.
	 * 
	 * @param routeAndStops
	 *            which route and stops getting predictions for
	 * @param doc
	 *            The returned XML document
	 * @param predictionsReadTime
	 */
	private void processExternalPredictionsForRoute(
			RouteAndStops routeAndStops,
			Document doc,
			Date predictionsReadTime) {
		// If couldn't read data from feed then can't process it
		if (doc == null)
			return;
		
		// So can look up info from db
		DbConfig dbConfig = Core.getInstance().getDbConfig();
		
		// Get root of doc
		Element rootNode = doc.getRootElement();

		List<Element> allPredictions = rootNode.getChildren("predictions");
		for (Element predictionsForStop : allPredictions) {
			List<Element> directions = predictionsForStop.getChildren("direction");
			for (Element direction : directions) {
				List<Element> predictions = direction.getChildren("prediction");
				for (Element prediction : predictions) {
					// Determine prediction time
					String epochTimeStr = prediction
							.getAttributeValue("epochTime");
					Date predictedTime = new Date(Long.parseLong(epochTimeStr));

					// Determine other parameters
					String vehicleId = prediction.getAttributeValue("vehicle");
					String stopId = predictionsForStop
							.getAttributeValue("stopTag");
					String tripId = prediction.getAttributeValue("tripTag");
					boolean isArrival = "false".equals(prediction
							.getAttributeValue("isDeparture"));
					boolean affectedByWaitStop = "true".equals(prediction
							.getAttributeValue("affectedByLayover"));
					
					// Direction ID is not available from NextBus API so determine it
					// from the trip ID.
					String directionId = null;
					if (tripId != null) {
						Trip trip = dbConfig.getTrip(tripId);
						if (trip != null) {
							directionId = trip.getDirectionId();
						} else {
							logger.error("Got tripId={} from NextBus feed but "
									+ "no such trip in the configuration.", tripId);
						}
					}
					
					String routeId = routeAndStops.routeId;
					
					logger.debug("Storing external prediction routeId={}, "
							+ "directionId={}, tripId={}, vehicleId={}, "
							+ "stopId={}, prediction={}, isArrival={}",
							routeId, directionId, tripId, vehicleId, stopId,
							predictedTime, isArrival);
					
					// Store in memory the prediction based on absolute time
					PredAccuracyPrediction pred = new PredAccuracyPrediction(
							routeId, directionId, stopId, tripId, vehicleId,
							predictedTime, predictionsReadTime, null,isArrival,
							affectedByWaitStop, "NextBus",null,null, null, null);
					storePrediction(pred);
				}
			}
		}
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
	protected void getAndProcessData(List<RouteAndStops> routesAndStops, 
			Date predictionsReadTime) {
		// Process internal predictions
		super.getAndProcessData(routesAndStops, predictionsReadTime);
		
		logger.debug("Calling NextBusPredictionReaderModule."
				+ "getAndProcessData()");
		
		// Get data for each route and stop
		for (RouteAndStops routeAndStops : routesAndStops) {
			Document doc = getExternalPredictionsForRoute(routeAndStops);
			processExternalPredictionsForRoute(routeAndStops, doc, predictionsReadTime);
		}
	}

	/**
	 * Just for debugging
	 */
	public static void main(String[] args) {
		// Need to start up Core so that can access route & stop info
		Core.createCore();

		// Create a NextBusAvlModue for testing
		Module.start("org.transitclock.core.predAccuracy.NextBusPredictionAccuracyModule");
	}

}
