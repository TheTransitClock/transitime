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

package org.transitclock.api.utils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.transitclock.db.webstructs.ApiKeyManager;
import org.transitclock.ipc.clients.CacheQueryInterfaceFactory;
import org.transitclock.ipc.clients.CommandsInterfaceFactory;
import org.transitclock.ipc.clients.ConfigInterfaceFactory;
import org.transitclock.ipc.clients.DiversionsInterfaceFactory;
import org.transitclock.ipc.clients.HoldingTimeInterfaceFactory;
import org.transitclock.ipc.clients.PredictionAnalysisInterfaceFactory;
import org.transitclock.ipc.clients.PredictionsInterfaceFactory;
import org.transitclock.ipc.clients.ServerStatusInterfaceFactory;
import org.transitclock.ipc.clients.VehiclesInterfaceFactory;
import org.transitclock.ipc.interfaces.CacheQueryInterface;
import org.transitclock.ipc.interfaces.CommandsInterface;
import org.transitclock.ipc.interfaces.ConfigInterface;
import org.transitclock.ipc.interfaces.DiversionsInterface;
import org.transitclock.ipc.interfaces.HoldingTimeInterface;
import org.transitclock.ipc.interfaces.PredictionAnalysisInterface;
import org.transitclock.ipc.interfaces.PredictionsInterface;
import org.transitclock.ipc.interfaces.ServerStatusInterface;
import org.transitclock.ipc.interfaces.VehiclesInterface;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * For getting the standard parameters from the URI used to access the feed.
 * Includes the key, agency, and the media type (JSON or XML). Does not include
 * command specific parameters.
 * 
 * @author SkiBu Smith
 * 
 */
public class StandardParameters {
	@PathParam("key")
	@Parameter(description="Application key to access this api.")
	private String key;

	@PathParam("agency")
	@Parameter(description="Specify the agency the request is intended to.")
	private String agencyId;

	@QueryParam("format")
	private String formatOverride;

	// Note: Specifying a default value so that don't get a
	// 400 bad request when using wget and headers not set. But
	// this isn't enough. Still getting Bad Request. But leaving
	// this in as documentation that it was tried.
	@HeaderParam("accept")
	@DefaultValue("application/json")
	String acceptHeader;

	@Context
	HttpServletRequest request;

	/********************** Member Functions **************************/

	/**
	 * Returns the media type to use for the response based on optional accept
	 * header and the optional format specification in the query string of the
	 * URL. Setting format in query string overrides what is set in accept
	 * header. This way it is always simple to generate a http get for
	 * particular format simply by setting query string.
	 * <p>
	 * If format specification is incorrect then BadRequest
	 * WebApplicationException is thrown.
	 * <p>
	 * The media type is not determined in the constructor because then an
	 * exception would cause an ugly error message because it would be handled
	 * before the root-resource class get method is being called.
	 * 
	 * @return The resulting media type
	 */
	public String getMediaType() throws WebApplicationException {
		// Use default of APPLICATION_JSON
		String mediaType = MediaType.APPLICATION_JSON;

		// If mediaType specified (to something besides "*/*") in accept 
		// header then start with it.
		if (acceptHeader != null && !acceptHeader.contains("*/*")) {
			if (acceptHeader.contains(MediaType.APPLICATION_JSON))
				mediaType = MediaType.APPLICATION_JSON;
			else if (acceptHeader.contains(MediaType.APPLICATION_XML))
				mediaType = MediaType.APPLICATION_XML;
			else
				throw WebUtils.badRequestException("Accept header \"Accept: "
						+ acceptHeader + "\" is not valid. Must be \""
						+ MediaType.APPLICATION_JSON + "\" or \""
						+ MediaType.APPLICATION_XML + "\"");
		}

		// If mediaType format is overridden using the query string format
		// parameter then use it.
		if (formatOverride != null) {
			// Always use lower case
			formatOverride = formatOverride.toLowerCase();

			// If mediaType override set properly then use it
			if (formatOverride.equals("json"))
				mediaType = MediaType.APPLICATION_JSON;
			else if (formatOverride.equals("xml"))
				mediaType = MediaType.APPLICATION_XML;
			else if (formatOverride.equals("human"))
				mediaType = MediaType.TEXT_PLAIN;
			else
				throw WebUtils.badRequestException("Format \"format="
						+ formatOverride + "\" from query string not valid. "
						+ "Format must be \"json\" or \"xml\"");
		}

		return mediaType;
	}

	/**
	 * Makes sure not access feed too much and that the key is valid. If
	 * there is a problem then throws a WebApplicationException.
	 * 
	 * @throws WebApplicationException
	 */
	public void validate() throws WebApplicationException {
		// Make sure not accessing feed too much. This needs to be done
		// early in the handling of the request so can stop processing
		// bad requests before too much effort is expended. Throw exception
		// if usage limits exceeded.
		UsageValidator.getInstance().validateUsage(this);
		
		
		// Make sure the application key is valid
		if (!ApiKeyManager.getInstance().isKeyValid(getKey())) {
			ApiKeyManager manager = ApiKeyManager.getInstance();
			boolean test=manager.isKeyValid(getKey());
			throw WebUtils.badRequestException(
					Status.UNAUTHORIZED.getStatusCode(), "Application key \""
							+ getKey() + "\" is not valid.");			
		}
	}

	/**
	 * For creating a Response of a single object of the appropriate media type.
	 * 
	 * @param object
	 *            Object to be returned in XML or JSON
	 * @return The created response in the proper media type.
	 */
	public Response createResponse(Object object) {
		// Start building the response
		ResponseBuilder responseBuilder = Response.ok(object);

		// Since this is a truly open API intended to be used by
		// other web pages allow cross-origin requests.
		responseBuilder.header("Access-Control-Allow-Origin", "*");

		// Specify media type of XML or JSON
		responseBuilder.type(getMediaType());

		// Return the response
		return responseBuilder.build();
	}

	/**
	 * Gets the VehiclesInterface for the specified agencyId. If not valid then
	 * throws WebApplicationException.
	 * 
	 * @return The VehiclesInterface
	 */
	public VehiclesInterface getVehiclesInterface()
			throws WebApplicationException {
		VehiclesInterface vehiclesInterface = VehiclesInterfaceFactory
				.get(agencyId);
		if (vehiclesInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return vehiclesInterface;
	}

	/**
	 * Gets the CommandsInterface for the specified agencyId. If not valid then
	 * throws WebApplicationException.
	 * 
	 * @return The CommandsInterface
	 */
	public CommandsInterface getCommandsInterface()
			throws WebApplicationException {
		CommandsInterface commandsInterface = CommandsInterfaceFactory
				.get(agencyId);
		if (commandsInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return commandsInterface;
	}

	/**
	 * Gets the PredictionsInterface for the agencyId specified as part of the
	 * standard parameters. If not valid then throws WebApplicationException.
	 * 
	 * @return The VehiclesInterface
	 */
	public PredictionsInterface getPredictionsInterface()
			throws WebApplicationException {
		PredictionsInterface predictionsInterface = PredictionsInterfaceFactory
				.get(agencyId);
		if (predictionsInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return predictionsInterface;
	}

	/**
	 * Gets the ConfigInterface for the specified agencyId. If not valid then
	 * throws WebApplicationException.
	 * 
	 * @return The VehiclesInterface
	 */
	public ConfigInterface getConfigInterface() throws WebApplicationException {
		ConfigInterface configInterface = ConfigInterfaceFactory.get(agencyId);
		if (configInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return configInterface;
	}

	/**
	 * Gets the ServerStatusInterface for the specified agencyId. If not valid
	 * then throws WebApplicationException.
	 * 
	 * @return The VehiclesInterface
	 */
	public ServerStatusInterface getServerStatusInterface()
			throws WebApplicationException {
		ServerStatusInterface serverStatusInterface = 
				ServerStatusInterfaceFactory.get(agencyId);
		if (serverStatusInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return serverStatusInterface;
	}
	/**
	 * Gets the CacheQueryInterface for the specified agencyId. If not valid
	 * then throws WebApplicationException.
	 * 
	 * @return The CacheQueryInterface
	 */
	public CacheQueryInterface getCacheQueryInterface()
			throws WebApplicationException {
		CacheQueryInterface cachequeryInterface = 
				CacheQueryInterfaceFactory.get(agencyId);
		if (cachequeryInterface == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return cachequeryInterface;
	}
	/**
	 * Gets the PredictionAnalysisInterface for the specified agencyId. If not valid
	 * then throws WebApplicationException.
	 * 
	 * @return The PredictionAnalysisInterface
	 */
	public PredictionAnalysisInterface getPredictionAnalysisInterface()
			throws WebApplicationException {
		PredictionAnalysisInterface predictionAnalysisInterface = 
				PredictionAnalysisInterfaceFactory.get(agencyId);
		if (predictionAnalysisInterface  == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return predictionAnalysisInterface ;
	}
	
	/**
	 * Gets the HoldingTimeInterface for the specified agencyId. If not valid
	 * then throws WebApplicationException.
	 * 
	 * @return The PredictionAnalysisInterface
	 */
	public HoldingTimeInterface getHoldingTimeInterface()
	{
		HoldingTimeInterface holdingTimeInterface = HoldingTimeInterfaceFactory.get(agencyId);
		if (holdingTimeInterface  == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return holdingTimeInterface ;
	}
	
	/**
	 * Gets the DiversionsInterface for the specified agencyId. If not valid
	 * then throws WebApplicationException.
	 * 
	 * @return The PredictionAnalysisInterface
	 */
	public DiversionsInterface getDiversionInterface()
	{
		DiversionsInterface diversionsInterface = DiversionsInterfaceFactory.get(agencyId);
		if (diversionsInterface  == null)
			throw WebUtils.badRequestException("Agency ID " + agencyId
					+ " is not valid");

		return diversionsInterface ;
	}	
	
	/**
	 * Simple getter for the key
	 * 
	 * @return
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Simple getter for the agency ID
	 * 
	 * @return
	 */
	public String getAgencyId() {
		return agencyId;
	}

	/**
	 * Returns the HttpServletRequest.
	 * 
	 * @return
	 */
	public HttpServletRequest getRequest() {
		return request;
	}

}
