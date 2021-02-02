package org.transitclock.api.rootResources;

import java.util.ArrayList;

import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.beanutils.BeanUtils;
import org.transitclock.api.data.ApiDiversion;
import org.transitclock.api.data.ApiDiversionStopPath;
import org.transitclock.api.data.ApiDiversions;
import org.transitclock.api.data.ApiLocation;
import org.transitclock.api.utils.StandardParameters;
import org.transitclock.api.utils.WebUtils;
import org.transitclock.core.diversion.model.Diversion;
import org.transitclock.core.diversion.model.DiversionStopPath;
import org.transitclock.db.structs.Location;
import org.transitclock.ipc.data.IpcDiversion;
import org.transitclock.ipc.data.IpcDiversionStopPath;
import org.transitclock.ipc.data.IpcDiversions;
import org.transitclock.ipc.interfaces.DiversionsInterface;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@Path("/key/{key}/agency/{agency}")
public class DiversionApi {
	@Path("/command/getDiversionsByRoute")
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Operation(summary="Get list of active diversions for a route.",
	description="This is to give the means of manually setting a vehicle unpredictable and unassigned so it will be reassigned quickly.",
	tags= {"command","vehicle"})
	public Response getDiversionsByRouteId(@BeanParam StandardParameters stdParameters,
			@Parameter(description="routeId to get diversions for.",required=true) @QueryParam("routeId") String routeId)  throws WebApplicationException
	{
		stdParameters.validate();
		try {							
			DiversionsInterface diverionsInterface = stdParameters.getDiversionInterface();
			
			IpcDiversions ipcDiversions = diverionsInterface.getDiversionsForRoute(routeId);
			
			ApiDiversions apiDiversions=new ApiDiversions(ipcDiversions.getDiversions());
				
			return stdParameters.createResponse(apiDiversions);
			
		}  catch (Exception e) {
			// If problem getting data then return a Bad Request
			throw WebUtils.badRequestException(e);
		}
	}
	@Path("/command/getDiversionsByTrip")
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Operation(summary="Get a list of active diversions for a trip",
	description="This is to give the means of manually setting a vehicle unpredictable and unassigned so it will be reassigned quickly.",
	tags= {"command","vehicle"})
	public Response getDiversionsByTripId(@BeanParam StandardParameters stdParameters,
			@Parameter(description="tripId to get diversions for.",required=true) @QueryParam("tripId") String tripId) throws WebApplicationException
	{
		stdParameters.validate();
		
		try {
			DiversionsInterface diverionsInterface = stdParameters.getDiversionInterface();
			
			IpcDiversions ipcDiversions = diverionsInterface.getDiversionsForTrip(tripId);
			
			ApiDiversions apiDiversions=new ApiDiversions(ipcDiversions.getDiversions());
			
			Response result = null;
			try {
				result = stdParameters.createResponse(apiDiversions);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return result;
							
			
		}  catch (Exception e) {
			// If problem getting data then return a Bad Request
			e.printStackTrace();
			throw WebUtils.badRequestException(e);			
		}
	}
	@Path("/command/putDiversion")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addDiversion(@BeanParam StandardParameters stdParameters, ApiDiversion diversion)
	{
		String result = "Diversion added : " + diversion;
		stdParameters.validate();
		
		try {
			DiversionsInterface diverionsInterface = stdParameters.getDiversionInterface();
			
			Diversion detour=new Diversion();
			
			copyProperties(detour,diversion);

			diverionsInterface.addDiversion(new IpcDiversion(detour));
			
		}catch(Exception e)
		{
			
			throw WebUtils.badRequestException(e);
		}
				
		return Response.status(201).entity(result).build();
		
	}
	private void copyProperties(Diversion detour, ApiDiversion diversion) {		
		detour.setStartStopSeq(diversion.getStartStopSeq());
		detour.setReturnStopSeq(diversion.getReturnStopSeq());
		detour.setRouteId(diversion.getRouteId());
		detour.setTripId(diversion.getTripId());		
		detour.setShapeId(diversion.getShapeId());
		detour.setDistanceStartAlongSegment(diversion.getDistanceStartAlongSegment());
		detour.setDistanceEndAlongSegment(diversion.getDistanceEndAlongSegment());			
		detour.setStartTime(diversion.getStartTime());
		detour.setEndTime(diversion.getEndTime());
						
		ArrayList<DiversionStopPath> stopPaths = new ArrayList<DiversionStopPath>();	
		
		for(ApiDiversionStopPath apiStopPath:diversion.getDiversionStopPaths())
		{
			DiversionStopPath stopPath = new DiversionStopPath();
			
			stopPath.setDirectionId(apiStopPath.getDirectionId());
			stopPath.setStopId(apiStopPath.getStopId());
			
			if(apiStopPath.getStopLocation()!=null)
				stopPath.setStopLocation(new Location(apiStopPath.getStopLocation().getLat(), apiStopPath.getStopLocation().getLon()));
			
			stopPath.setStopName(apiStopPath.getStopName());
			stopPath.setStopSequence(apiStopPath.getStopSequence());
			
			ArrayList<Location> path=new ArrayList<Location>();
			
			for(ApiLocation point:apiStopPath.getPath())
			{
				path.add(new Location(point.getLat(), point.getLon()));
			}
			stopPath.setPath(path);
			stopPaths.add(stopPath);			
			
		}
		
		detour.setDiversionStopPaths(stopPaths);
	}
	
	
}
