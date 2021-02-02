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
package org.transitclock.applications;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.transitclock.config.ConfigFileReader;
import org.transitclock.db.structs.Location;
import org.transitclock.ipc.clients.CommandsInterfaceFactory;
import org.transitclock.ipc.clients.ConfigInterfaceFactory;
import org.transitclock.ipc.clients.DiversionsInterfaceFactory;
import org.transitclock.ipc.clients.PredictionsInterfaceFactory;
import org.transitclock.ipc.clients.VehiclesInterfaceFactory;
import org.transitclock.ipc.data.IpcActiveBlock;
import org.transitclock.ipc.data.IpcBlock;
import org.transitclock.ipc.data.IpcDirectionsForRoute;
import org.transitclock.ipc.data.IpcDiversions;
import org.transitclock.ipc.data.IpcPredictionsForRouteStopDest;
import org.transitclock.ipc.data.IpcRoute;
import org.transitclock.ipc.data.IpcRouteSummary;
import org.transitclock.ipc.data.IpcTrip;
import org.transitclock.ipc.data.IpcTripPattern;
import org.transitclock.ipc.data.IpcVehicle;
import org.transitclock.ipc.data.IpcVehicleComplete;
import org.transitclock.ipc.interfaces.CommandsInterface;
import org.transitclock.ipc.interfaces.ConfigInterface;
import org.transitclock.ipc.interfaces.DiversionsInterface;
import org.transitclock.ipc.interfaces.PredictionsInterface;
import org.transitclock.ipc.interfaces.VehiclesInterface;
import org.transitclock.ipc.interfaces.PredictionsInterface.RouteStop;

/**
 * A command line application that allows user to request data from the
 * server. Such information includes predictions and vehicles.
 *  
 * @author SkiBu Smith
 *
 */
public class RmiQuery {

	private static String agencyId;
	private static Command command;
	private static String routeShortNames[];
	private static String stopIds[];
	private static String vehicleIds[];
	private static double latitude = Double.NaN;
	private static double longitude = Double.NaN;
	// For the config command for getting block, trip, & trip pattern info:
	private static String blockId;
	private static String serviceId;
	private static String tripId;
	
	static {
		ConfigFileReader.processConfig();
	}
	
	private static enum Command {NOT_SPECIFIED, GET_PREDICTIONS, GET_VEHICLES, 
		GET_ROUTE_CONFIG, GET_CONFIG, 
		GET_ACTIVE_BLOCKS, RESET_VEHICLE, GET_DETOURS};

	/********************** Member Functions **************************/

	/**
	 * Processes all command line options using Apache CLI.
	 * Further info at http://commons.apache.org/proper/commons-cli/usage.html .
	 * Returns the CommandLine object that provides access to each arg.
	 * Exits if there is a parsing problem.
	 * 
	 * @param args The arguments from the command line
	 * @return CommandLine object that provides access to all the args
	 * @throws ParseException
	 */
	@SuppressWarnings("static-access")  // Needed for using OptionBuilder
	private static CommandLine processCommandLineOptions(String[] args) {
		// Specify the options
		Options options = new Options();
		
		options.addOption("h", false, "Display usage and help info."); 
		
		options.addOption(OptionBuilder.withArgName("agencyId")
                .hasArg()
                .isRequired()
                .withDescription("Agency ID.")
                .create("a")
                );
		
		options.addOption(OptionBuilder.withArgName("command")
                .hasArg()
                .isRequired()
                .withDescription("Name of command to execute. Can be "
                		+ "\"preds\", \"vehicles\", \"routeConfig\", \"config\", " 
                		+ "\"resetVehicle\", " 
                		+ "or \"activeBlocks\" .")
                .create("c")
                );

		options.addOption(OptionBuilder.withArgName("routeShortName")
                .hasArg()
                .withDescription("Route short name (not the route_id).")
                .create("r")
                );
		
		options.addOption(OptionBuilder.withArgName("lat")
                .hasArg()
                .withDescription("Latitude, for getting predictions by location.")
                .create("lat")
                );

		options.addOption(OptionBuilder.withArgName("lon")
                .hasArg()
                .withDescription("Longitude, for getting predictions by location.")
                .create("lon")
                );
		
		options.addOption(OptionBuilder.withArgName("stopId")
                .hasArg()
                .withDescription("Stop ID.")
                .create("s")
                );
		
		options.addOption(OptionBuilder.withArgName("vehicleId")
                .hasArg()
                .withDescription("Vehicle ID.")
                .create("v")
                );
		
		options.addOption(OptionBuilder.withArgName("blockId")
                .hasArg()
                .withDescription("block ID.")
                .create("blockId")
                );

		options.addOption(OptionBuilder.withArgName("serviceId")
                .hasArg()
                .withDescription("service ID.")
                .create("serviceId")
                );

		options.addOption(OptionBuilder.withArgName("tripId")
                .hasArg()
                .withDescription("trip ID.")
                .create("tripId")
                );
		
		// Parse the options
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args);
		} catch (Exception e) {
			// There was a parse problem so display the command line options 
			// so user knows what is needed, and exit since can't continue.
			displayCommandLineOptionsAndExit(options);
		}
		
		// Handle help option
		if (cmd.hasOption("h")) {
			displayCommandLineOptionsAndExit(options);
		}

		agencyId = cmd.getOptionValue("a");
		
		// Process the command
		String commandStr = cmd.getOptionValue("c");
		if ("preds".equals(commandStr))
			command = Command.GET_PREDICTIONS;
		else if ("vehicles".equals(commandStr))
			command = Command.GET_VEHICLES;
		else if ("routeConfig".equals(commandStr))
			command = Command.GET_ROUTE_CONFIG;
		else if ("config".equals(commandStr))
			command = Command.GET_CONFIG;		
		else if ("activeBlocks".equals(commandStr))
			command = Command.GET_ACTIVE_BLOCKS;
		else if ("resetVehicle".equals(commandStr))
			command = Command.RESET_VEHICLE;
		else if("detours".equals(commandStr))
			command = Command.GET_DETOURS;
		else {
			System.out.println("Command \"" + commandStr + "\" is not valid.\n");
			displayCommandLineOptionsAndExit(options);
		}
		
		routeShortNames = cmd.getOptionValues("r");
		
		stopIds = cmd.getOptionValues("s");
		
		vehicleIds = cmd.getOptionValues("v");
		
		String latStr = cmd.getOptionValue("lat");
		if (latStr != null) 
			latitude = Double.parseDouble(latStr);
		
		String lonStr = cmd.getOptionValue("lon");
		if (lonStr != null) 
			longitude = Double.parseDouble(lonStr);
		
		blockId = cmd.getOptionValue("blockId");
		serviceId = cmd.getOptionValue("serviceId");
		tripId = cmd.getOptionValue("tripId");
		
		// Return the CommandLine so that arguments can be accessed
		return cmd;
	}
	
	/**
	 * Displays the command line options on stdout
	 * 
	 * @param options Command line options to be displayed
	 */
	private static void displayCommandLineOptionsAndExit(Options options) {
		// Display help
		final String commandLineSyntax = "java transitclock.jar";
		final PrintWriter writer = new PrintWriter(System.out);
		writer.append(
				"A command line application that allows user to request data\n" +
				"from the server. Such information includes predictions and\n" +
				"vehicles.\n\n");
		final HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.printHelp(writer,
								80, // printedRowWidth
								commandLineSyntax,
								"args:", // header
								options,
								2,             // spacesBeforeOption
								2,             // spacesBeforeOptionDescription
								null,          // footer
								true);         // displayUsage
		writer.close();
		System.exit(0);
	}

	/**
	 * For the "preds" command. Gets predictions for specified route/stop.
	 * @throws RemoteException 
	 */
	private static void getPredictions() throws RemoteException {
		// just for debugging
		System.err.println("Getting predictions for agencyId=" + agencyId + 
				" lat=" + latitude + " lon=" + longitude +
				" routeShortNames=" + routeShortNames + " stopIds=" + stopIds);

		// Make sure stops specified
		if (stopIds == null 
				&& (Double.isNaN(latitude) || Double.isNaN(longitude))) {
			System.err.println("Error: must specify stop(s) to get predictions.");
			return;
		}
		
		PredictionsInterface predsInterface = 
				PredictionsInterfaceFactory.get(agencyId);

		// Get predictions depending on the command line options
		if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
			Location loc = new Location(latitude, longitude);
			
			// Getting predictions based on location
			List<IpcPredictionsForRouteStopDest> predictionList = 
					predsInterface.get(loc, 1500.0, 3);
			
			System.out.println("Predictions for agencyId=" + 
					agencyId +	" " + loc + " are:");
			for (IpcPredictionsForRouteStopDest preds : predictionList) {
				System.out.println("  " + preds);
			}
		} else if (stopIds.length == 1) {
			// Getting prediction for single stop...
			String stopId = stopIds[0];
			String routeId = routeShortNames == null ? null : routeShortNames[0];
			
			List<IpcPredictionsForRouteStopDest> predictionList = 
					predsInterface.get(routeId, stopId, 3);
			
			System.out.println("Predictions for agencyId=" + 
					agencyId +	" routeId=" + routeId + " stopId=" + 
					stopId + " are:");
			for (IpcPredictionsForRouteStopDest preds : predictionList) {
				System.out.println("  " + preds);
			}
		} else {
			// Getting predictions for multiple stops
			List<RouteStop> routeStops = new ArrayList<RouteStop>();
			for (int i=0; i<stopIds.length; ++i) {
				String routeId = routeShortNames == null ? null : routeShortNames[i];
				String stopId = stopIds[i];
				RouteStop routeStop = new RouteStop(routeId, stopId);
				routeStops.add(routeStop);
			}
			
			List<IpcPredictionsForRouteStopDest> predictionListList = 
					predsInterface.get(routeStops, 3);
			
			System.out.println("Predictions for agencyId=" + 
					agencyId +	" routeStops=" + routeStops + " are:\n");
			for (IpcPredictionsForRouteStopDest preds : predictionListList) {
				System.out.println("  " + preds + "\n");
			}
		}
	}
	
	/**
	 * For the vehicles commands. Outputs info on each vehicle.
	 *  
	 * @throws RemoteException
	 */
	private static void getVehicles() throws RemoteException {
		// just for debugging
		System.err.println("Getting vehicles for agencyId=" + agencyId + 
				" routeShortNames=" + routeShortNames + " vehicleIds=" + vehicleIds);
		
		VehiclesInterface vehiclesInterface = 
				VehiclesInterfaceFactory.get(agencyId);
		
		Collection<IpcVehicleComplete> vehicles = null;
		if (vehicleIds != null && vehicleIds.length == 1) {
			// Get single vehicle
			IpcVehicleComplete vehicle = vehiclesInterface.getComplete(vehicleIds[0]);
			vehicles = new ArrayList<IpcVehicleComplete>();
			vehicles.add(vehicle);
		} else if (vehicleIds != null && vehicleIds.length > 1) {
			// Get multiple vehicles
			vehicles = vehiclesInterface.getComplete(Arrays.asList(vehicleIds));
		} else if (routeShortNames != null && routeShortNames.length == 1) {
			// Get vehicles for specified route
			vehicles = vehiclesInterface.getCompleteForRoute(routeShortNames[0]);
		} else {
			// Get all vehicles
			vehicles = vehiclesInterface.getComplete();
		}
		
		if (vehicles.size() > 0) {
			System.out.println("Vehicles are:");
			for (IpcVehicle vehicle : vehicles) {
				System.out.println("  " + vehicle);
			}
		} else {
			System.out.println("No vehicles found");
		}
	}
	
	/**
	 * Outputs route config info.
	 * 
	 * @throws RemoteException
	 */
	private static void getRouteConfig() throws RemoteException {
		ConfigInterface configInterface = 
				ConfigInterfaceFactory.get(agencyId);
		Collection<IpcRouteSummary> routes = configInterface.getRoutes();
		System.out.println("Routes are:");
		for (IpcRouteSummary routeSummary : routes) {
			System.out.println(routeSummary);
			
			IpcRoute route = configInterface.getRoute(routeSummary.getShortName(),
					null, null, null);
			System.out.println(route);
			
			IpcDirectionsForRoute stopsForRoute = 
					configInterface.getStops(routeSummary.getShortName());
			System.out.println(stopsForRoute);
		}
	}
	
	/**
	 * Outputs block, trip, or trip pattern info, depending on the
	 * other command line options.
	 * 
	 * @throws RemoteException
	 */
	private static void getConfig() throws RemoteException {
		ConfigInterface configInterface = 
				ConfigInterfaceFactory.get(agencyId);
		if (blockId != null && serviceId != null) {
			System.out.println("Outputting block for blockId=" + blockId + 
					" serviceId=" + serviceId);
			IpcBlock ipcBlock = configInterface.getBlock(blockId, serviceId);
			System.out.println(ipcBlock);
		} else if (tripId != null) {
			System.out.println("Outputting trip for tripId=" + tripId );
			IpcTrip ipcTrip = configInterface.getTrip(tripId);
			System.out.println(ipcTrip);
		} else if (routeShortNames.length > 0) {
			System.out.println("Outputting trip pattern for routeShortName=" 
					+ routeShortNames[0] );
			List<IpcTripPattern> ipcTripPatterns =
					configInterface.getTripPatterns(routeShortNames[0]);
			System.out.println(ipcTripPatterns);
		} else {
			System.err.println("For \"config\" command need to specify " +
					"blockId & serviceId, or tripId, or a route");
		}
	}
	
	/**
	 * Outputs the active blocks, depending on the the routeIds/routeShortNames
	 * specified.
	 * 
	 * @throws RemoteException
	 */
	private static void getActiveBlocks() throws RemoteException {
		Collection<String> routeIds = routeShortNames != null ?
				Arrays.asList(routeShortNames) : null;
		
		VehiclesInterface vehiclesInterface = 
				VehiclesInterfaceFactory.get(agencyId);
		Collection<IpcActiveBlock> activeBlocks = 
				vehiclesInterface.getActiveBlocks(routeIds, 0);
		
		System.out.println("Outputting active blocks for routeIds=" + routeIds);
		for (IpcActiveBlock activeBlock : activeBlocks) {
			IpcTrip activeTrip = activeBlock.getBlock().getTrips().get(activeBlock.getActiveTripIndex());
			System.out.println("\nrouteId=" + activeTrip.getRouteId());
			System.out.println(activeBlock.getBlock().toString());
			System.out.println("activeTrip=" + activeTrip);
			System.out.println("vehicle=" + activeBlock.getVehicles());
			System.out.println(activeBlock);
		}
	}
	
	
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		processCommandLineOptions(args);

		try {
			if (command == Command.GET_PREDICTIONS) {
				getPredictions();
			} else if (command == Command.GET_VEHICLES) {
				getVehicles();
			} else if (command == Command.GET_ROUTE_CONFIG) {
				getRouteConfig();
			} else if (command == Command.GET_CONFIG) {
				getConfig();
			} else if (command == Command.GET_ACTIVE_BLOCKS) {
				getActiveBlocks();
			} else if(command == Command.RESET_VEHICLE) {
				resetVehicles();
			} else if(command == Command.GET_DETOURS)
				getDetours();
			
		} catch (Exception e) {
			// Output stack trace as error message
			e.printStackTrace();
		}
	}

	private static void getDetours() throws Exception {
		// TODO Auto-generated method stub
		DiversionsInterface detourInterface=DiversionsInterfaceFactory.get(agencyId);
		
		IpcDiversions result = detourInterface.getDiversionsForTrip(tripId);
		
		if(result!=null)		
			System.out.println(result.toString());
		
	}

	private static void resetVehicles() throws Exception {		
		CommandsInterface commandsInterface = 
				CommandsInterfaceFactory.get(agencyId);
		if(commandsInterface != null)
		{
			for(String vehicleId:vehicleIds)
			{
				commandsInterface.setVehicleUnpredictable(vehicleId);
			}
		}else 
		{
			throw new Exception("Cannot connect to Command server.");
		}
	}

}
