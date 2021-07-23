<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%@page import="org.transitclock.web.WebConfigParams"%>

<!-- 
 Query String parameters:
   a=AGENCY (required)
   r=ROUTE (optional, if not specified then a route selector is created)
   s=STOP_ID (optional, for specifying which stop interested in)
   tripPattern=TRIP_PATTERN (optional, for specifying which stop interested in).
   verbose=true (optional, for getting additional info in vehicle popup window)
   showUnassignedVehicles=true (optional, for showing unassigned vehicles)
-->
<html>
<head>
  <!-- So that get proper sized map on iOS mobile device -->
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  
  <link rel="stylesheet" href="css/mapUi.css" />
 
  <!-- Load javascript and css files -->
  <%@include file="/template/includes.jsp" %>
  <link rel="stylesheet" href="http://cdn.leafletjs.com/leaflet-0.7.3/leaflet.css" />
  <script src="http://cdn.leafletjs.com/leaflet-0.7.3/leaflet.js"></script>
  <script type="text/javascript" src="//leaflet.github.io/Leaflet.label/leaflet.label.js"></script>
  <link rel="stylesheet" href="//leaflet.github.io/Leaflet.label/leaflet.label.css" />
  <script src="javascript/leafletRotatedMarker.js"></script>
  <script src="javascript/mapUiOptions.js"></script>
  <script src="<%= request.getContextPath() %>/javascript/jquery-dateFormat.min.js"></script>

  <%-- MBTA wants some color customization. Load in options file if mbta --%>
  <% if (request.getParameter("a").startsWith("mbta")) { %>
    <link rel="stylesheet" href="css/mbtaMapUi.css" />
    <script src="javascript/mbtaMapUiOptions.js"></script>
  <% } %>
  
  <!-- Load in Select2 files so can create fancy selectors -->
  <link href="//cdnjs.cloudflare.com/ajax/libs/select2/4.0.0/css/select2.min.css" rel="stylesheet" />
  <script src="//cdnjs.cloudflare.com/ajax/libs/select2/4.0.0/js/select2.min.js"></script>

  <style>
    /* Override the body style from the includes.jsp/general.css files */
    body {
	  margin: 0px;
    }
    
    /* Set width of route selector. For smaller displays use smaller width */
    #routes {
      width: 400px;
    }
    
    @media (max-width:600px) {
      #routes {
        width: 300px;
      }
    }
    
  </style>
  
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  
  <title><fmt:message key="div.transitimemaps" /></title>
</head>

<body>
  <div id="map"></div>
  <!-- To center successfully in all situations tried to use a div within a div.
       The title text is set in css so that it is easily configurable -->
  <div id="titleContainer">
    <div id="mapTitle"></div>
  </div>
  
  <!--  Wanted to center the routes selector horizontally but couldn't get
        it to work. Got problems with the map not fitting page properly
        when tried to use fancy position absolute and relative css.
        Also, found that only way to set width of route selector is
        to set the css width here. Yes, strange! -->  
  <div id="routesContainer">
    <div id="routesDiv">
      <select id="routes"></select>	
    </div>
  </div>
  
</body>

<script>



var agencyTimezoneOffset;
/**
 * For formating epoch times to human readable times, possibly including
 * a timezone offest. The time param in epoch time in seconds (not msec).
 */
function dateFormat(time) {
	var localTimezoneOffset = (new Date()).getTimezoneOffset();
	var timezoneDiffMinutes = localTimezoneOffset - agencyTimezoneOffset;
	
	var offsetDate = new Date(parseInt(time)*1000 + timezoneDiffMinutes*60*1000);
	// Use jquery-dateFormat javascript library
	return $.format.date(offsetDate, 'HH:mm:ss');
}

/**
 * Handle the route specification
 */
var routeQueryStrParam;

function getRouteQueryStrParam() {
	return routeQueryStrParam;
}

function setRouteQueryStrParam(param) {
	routeQueryStrParam = param;
}

/**
 * Returns query string param to be used for API for specifying the route.
 * It can be either "r=routeId" or "r=routeShortName" depending on whether route
 * ID or route short name were used when bringing up the page.
 */
function setRouteQueryStrParamViaQueryStr() {
	// If route not set in query string then return null
	if (!getQueryVariable("r")) {
	 	routeQueryStrParam =  null;
		return;
	}
	 
	if (getQueryVariable("r"))
		routeQueryStrParam = "r=" + getQueryVariable("r");	
}

// For keeping track the predictions popup so can update content
var predictionsPopup = null;
var predictionsTimeout = null;

//Start dynamic AVL polling rate at 1 sec and give it a max of 20 sec
var MIN_AVL_POLLING_RATE = 1000;
var avlPollingRate = MIN_AVL_POLLING_RATE;
var MAX_AVL_POLLING_RATE = 20000;
var avlTimer = null;

/**
 * Called when prediction read from API. Updates the content of the
 * predictionsPopup with the new prediction info.
 */
function predictionCallback(preds, status) {
	// If predictions popup was closed then don't do anything
	if (predictionsPopup == null) 
		return;
	
	// There will be predictions for just a single route/stop
	var routeStopPreds = preds.predictions[0];
	
	// Set timeout to update predictions again in few seconds
	predictionsTimeout = setTimeout(getPredictionsJson, 20000, routeStopPreds.routeShortName, routeStopPreds.stopId);

	// Add route and stop info
	var stopName = routeStopPreds.stopName;
	if (routeStopPreds.stopCode)
		stopName += " (" + routeStopPreds.stopCode + ")";
    var content = '<b><fmt:message key="div.droute" />:</b> ' + routeStopPreds.routeName + '<br/>' 
		+ '<b><fmt:message key="div.dstop" /></b> ' + stopName + '<br/>';
	if (verbose)
		content += '<b><fmt:message key="div.dstop" /> Id:</b> ' + routeStopPreds.stopId + '<br/>';
		
	// For each destination add predictions
	for (var i in routeStopPreds.dest) {
		// If there are several destinations then add a horizontal rule
		// to break predictions up by destination
		if (routeStopPreds.dest.length > 1)
			content += '<hr/>';
		
		// Add the destination/headsign info
		if (routeStopPreds.dest[i].headsign)
			content += '<b><fmt:message key="div.ddestination" />:</b> ' + routeStopPreds.dest[i].headsign + '<br/>';
		
		// Add each prediction for the current destination
		if (routeStopPreds.dest[i].pred.length > 0) {
			content += '<span class="prediction">';
			
			for (var j in routeStopPreds.dest[i].pred) {
				// Separators between the predictions
				if (j == 1)
					content += ', ';
				else if (j ==2)
					content += ' & '
					
				// Add the actual prediction
				var pred = routeStopPreds.dest[i].pred[j];
				content += pred.min;
				
				// Added any special indicators for if schedule based,
				// delayed, or not yet departed from terminal
				/*
				if (pred.scheduleBased)
					content += '<sup>sched</sup>';
				else {
					if (pred.notYetDeparted)
						content += '<sup>not yet left</sup>';
					else
						if (pred.delayed) 
							content += '<sup>delayed</sup>';
				}
				*/
				// If in verbose mode add vehicle info
				if (verbose)
					content += ' <span class="vehicle">(<fmt:message key="div.Vehicle" /> ' + pred.vehicle + ')</span>';
			}
			content += ' <fmt:message key="div.dminutes" />';
			
			content += '</span>';
		} else {
			// There are no predictions so let user know
			content += "<fmt:message key="div.dnopredictions" />";
		}
	}
	
	// Now update popup with the wonderful prediction info
	predictionsPopup.setContent(content);
}

/**
 * Initiates API call to get prediction data.
 */
function getPredictionsJson(routeShortName, stopId) {
	// JSON request of predicton data
	var url = apiUrlPrefix + "/command/predictions?rs=" + routeShortName 
			+ encodeURIComponent("|") + stopId;
	$.getJSON(url, predictionCallback);	
}

/**
 * Called when user clicks on stop. Request predictions from API and calls
 * predictionCallback() when data received.
 */
function showStopPopup(stopMarker) {
	// JSON request of predicton data
	getPredictionsJson(stopMarker.routeShortName, stopMarker.stop.id);
    
	// Create popup in proper place but content will be added in predictionCallback()
	predictionsPopup = L.popup(stopPopupOptions)
		.setLatLng(stopMarker.getLatLng())
		.openOn(map);
}

var routeFeatureGroup = null;

/**
 * Reads in route data obtained via AJAX and draws route and stops on map.
 */
function routeConfigCallback(routesData, status) {
	// If there is an old route then remove it
	if (routeFeatureGroup) {
		map.removeLayer(routeFeatureGroup);
	}
	// Use a FeatureGroup to contain all paths and stops so that can use
	// bringToBack() on the whole group at once in  order to make sure that
	// the paths & stops are drawn below the vehicles even if the vehicles
	// happen to be drawn first.
	routeFeatureGroup = L.featureGroup();

	// Only working with single route at a time for now
	var route = routesData.routes[0];
	
	// Draw stops for the route. Do stops before paths so that when call  
	// bringToBack() the stops will end up being on top.
	var locsToFit = [];
	var firstNonMinorStop = true;
	for (var i=0; i<route.direction.length; ++i) {
		var direction = route.direction[i];
		for (var j=0; j<direction.stop.length; ++j) {
			var stop = direction.stop[j];
			var options = stop.minor ? minorStopOptions : stopOptions;
			// Draw first non-minor stop differently to highlight it
			/*
			if (!stop.minor && firstNonMinorStop) {
				options = firstStopOptions;
				firstNonMinorStop = false;
			}
			*/
			// Keep track of non-minor stop locations so can fit map to show them all
			if (!stop.minor)
				locsToFit.push(L.latLng(stop.lat, stop.lon));
			
			// Create the stop Marker
			var stopMarker = L.circleMarker([stop.lat,stop.lon], options).addTo(map);
			
			routeFeatureGroup.addLayer(stopMarker);
			
			// Store stop data obtained via AJAX with stopMarker so it can be used in popup
			stopMarker.stop = stop;
			
			// Store routeShortName obtained via AJAX with stopMarker so can be 
			// used to get predictions for stop/route
			stopMarker.routeShortName = route.shortName;
			
			// When user clicks on stop popup information box
			stopMarker.on('click', function(e) {
				showStopPopup(this);
			}).addTo(map);
		}
	}

	// Draw the paths for the route
	for (var i=0; i<route.shape.length; ++i) {
		var shape = route.shape[i];
		var options = shape.minor ? minorShapeOptions : shapeOptions;
		
		var latLngs = [];		
		for (var j=0; j<shape.loc.length; ++j) {
			var loc = shape.loc[j];			
			latLngs.push(L.latLng(loc.lat, loc.lon));
		}
		var polyline = L.polyline(latLngs, options).addTo(map);
		
		routeFeatureGroup.addLayer(polyline);
		
		// Store shape data obtained via AJAX with polyline so it can be used in popup
		polyline.shape = shape;
		
		// Popup trip pattern info when user clicks on path
		if (verbose) {
			polyline.on('click', function(e) {
				var content = "<b><fmt:message key="div.dtrippattern" />:</b> " + this.shape.tripPattern 
					+ "<br/><b><fmt:message key="div.Headsign" />:</b> " + this.shape.headsign;
				L.popup(tripPatternPopupOptions)
					.setLatLng(e.latlng)
					.setContent(content)
					.openOn(map);}
						 );
		}
		
	}
	
	// Add all of the paths and stops to the map at once via the FeatureGroup
	routeFeatureGroup.addTo(map);

	// If stop was specified for getting route then locationOfNextPredictedVehicle
	// is also returned. Use this vehicle location when fitting bounds of map
	// so that user will always see the next vehicle coming.
	if (route.locationOfNextPredictedVehicle) {
		locsToFit.push(L.latLng(route.locationOfNextPredictedVehicle.lat, 
				route.locationOfNextPredictedVehicle.lon));
	}
	
	// Get map to fit route
	map.fitBounds(locsToFit);
	
	// It can happen that vehicles get drawn before the route paths & stops.
	// In this case need call bringToBack() on the paths and stops so that
	// the vehicles will be drawn on top.
	// Note: bringToBack() must be called after map is first specified 
	// via fitBounds() or other such method.
	routeFeatureGroup.bringToBack();
}

var vehicleMarkers = [];

/**
 * Gets vehicle marker from the array vehicleIcons 
 */
function getVehicleMarker(vehicleId) {
	for (var i=0; i<vehicleMarkers.length; ++i) {
		if (vehicleMarkers[i].vehicleData.id == vehicleId)
			return vehicleMarkers[i];
	}	
	
	// Don't yet have marker for that vehicle
	return null;
}

/**
 * So that can easily output speed in km/hr instead of m/s
 */
function formatSpeed(speedInMetersPerSec) {
	// If not a number then just return blank string
	if (speedInMetersPerSec == "NaN")
		return "";
	
	// Convert m/s to km/hr and truncate to 1 decimal place to make
	// output pretty
	return (parseFloat(speedInMetersPerSec) * 3.6).toFixed(1) + " <fmt:message key="div.dkmhr" />";
}

/**
 * Takes in vehicleData info from API and determines the content
 * to be displayed for the vehicles popup.
 */
function getVehiclePopupContent(vehicleData) {
    var layoverStr = verbose && vehicleData.layover ? 
			 ("<br/><b><fmt:message key="div.dlayover" />:</b> " + vehicleData.layover) : "";
    var layoverDepartureStr = vehicleData.layover ? 
    		 ("<br/><b><fmt:message key="div.ddeparture" />:</b> " + 
    				 dateFormat(vehicleData.layoverDepTime)) : "";
    var nextStopNameStr = vehicleData.nextStopName ? 
    		 ("<br/><b><fmt:message key="div.dnextstop" />:</b> " + vehicleData.nextStopName) : "";
    if (verbose && vehicleData.nextStopId)
    	nextStopNameStr += "<br/><b><fmt:message key="div.dnextstop" /> Id:</b> " + vehicleData.nextStopId;
    var driver = vehicleData.driver ? 
    		"<br/><b><fmt:message key="div.ddriver" />:</b> " + vehicleData.driver : "";
    var latLonHeadingStr = verbose ? "<br/><b><fmt:message key="div.dlat" />:</b> " + vehicleData.loc.lat
    			+ "<br/><b><fmt:message key="div.dlon" />:</b> " + vehicleData.loc.lon 
    			+ "<br/><b><fmt:message key="div.dheading" />:</b> " + vehicleData.loc.heading 
    			+ "<br/><b><fmt:message key="div.dspeed" />:</b> " + formatSpeed(vehicleData.loc.speed)
    			: "";
	var gpsTimeStr = dateFormat(vehicleData.loc.time);
    var directionStr = verbose ? "<br/><b><fmt:message key="div.ddirection" />:</b> " + vehicleData.direction : ""; 
    var tripPatternStr = verbose ? "<br/><b><fmt:message key="div.dtrippattern" />:</b> " + vehicleData.tripPattern : "";
    var startTimeStr = vehicleData.isScheduledService ? "" : "<br/><b><fmt:message key="div.dstarttime" />:</b> "+dateFormat(vehicleData.freqStartTime/1000);
    var schAdhStr = vehicleData.isScheduledService ? "<br/><b><fmt:message key="div.dschadh" />:</b> " + vehicleData.schAdhStr : "";
    var vehicleName = (vehicleData.vehicleName == '' || vehicleData.vehicleName == 'undefined'  || 
			vehicleData.vehicleName === undefined) ? vehicleData.id : vehicleData.vehicleName;
    var content = "<b><fmt:message key="div.Vehicle" />:</b> " + vehicleName
    	+ "<br/><b><fmt:message key="div.droute" />: </b> " + vehicleData.routeShortName
		+ latLonHeadingStr
		+ "<br/><b><fmt:message key="div.dgpstime" />:</b> " + gpsTimeStr
		+ "<br/><b><fmt:message key="div.Headsign" />:</b> " + vehicleData.headsign
		+ directionStr 
		+ schAdhStr 
		+ "<br/><b><fmt:message key="div.dblock" />:</b> " + vehicleData.block
		+ "<br/><b><fmt:message key="div.dtrip" />:</b> " + vehicleData.trip
		+ tripPatternStr
		+ startTimeStr
		+ layoverStr
		+ layoverDepartureStr
		+ nextStopNameStr
		+ driver;	
	return content;
}

/**
 * Determines options for drawing the vehicle marker icon based on uiType
 */
function getVehicleMarkerOptions(vehicleData) {
	if (!vehicleData.uiType || vehicleData.uiType == "normal")
		return vehicleMarkerOptions;
	else if (vehicleData.uiType == "secondary")
		return secondaryVehicleMarkerOptions;
	else
		return minorVehicleMarkerOptions;
}

/**
 * Determines options for drawing the vehicle background circle based on uiType
 */
function getVehicleMarkerBackgroundOptions(vehicleData) {
	// Handle unassigned vehicles
	if (!vehicleData.block)
		return unassignedVehicleMarkerBackgroundOptions;
	else if (!vehicleData.uiType || vehicleData.uiType == "normal")
		return vehicleMarkerBackgroundOptions;
	else if (vehicleData.uiType == "secondary")
		return secondaryVehicleMarkerBackgroundOptions;
	else
		return minorVehicleMarkerBackgroundOptions;
}

/**
 * Determines which icon to use to represent vehicle, such as a streetcar icon,
 * a bus icon, or an icon to represent a layover.
 */
 function getIconForVehicle(vehicleData) {
	// Determine which icon to use. Vehicles types defined per route
	// as indicated in the GTFS spec at https://developers.google.com/transit/gtfs/reference#routes_fields
	var vehicleIcon = busIcon;
	if (vehicleData.vehicleType == "0")
		vehicleIcon = streetcarIcon;
	else if (vehicleData.vehicleType == "1")
		vehicleIcon = subwayIcon;
	else if (vehicleData.vehicleType == "2")
		vehicleIcon = railIcon;
	else if (vehicleData.vehicleType == "4")
		vehicleIcon = ferryIcon;

	// Indicate layovers specially
	/*
	if (vehicleData.layover)
		vehicleIcon = layoverIcon;
	*/
	
	// Return the result
	return vehicleIcon;
}

/**
 * Removes the specified marker from the map
 */
function removeVehicleMarker(vehicleMarker) {
	// Close stop predictions popup if there is one
	if (vehicleMarker.popup)
		map.closePopup(vehicleMarker.popup);
	
	map.removeLayer(vehicleMarker.background);
	map.removeLayer(vehicleMarker.headingArrow);
	map.removeLayer(vehicleMarker);
}

/**
 * Removes all vehicle markers from map
 */
function removeAllVehicles() {
	// Remove each vehicleMarker
	for (var i in vehicleMarkers) {
		var vehicleMarker = vehicleMarkers[i];
		removeVehicleMarker(vehicleMarker);
	}
	
	// Clear out the vehicleMarkers array
	vehicleMarkers.length = 0;
}

/*
 * For determining if vehicles and other markers are stale.
 * This can happen if laptop or tablet with map already running
 * is turned on again. 
 */
var lastVehiclesUpdateTime = new Date();

function hideThingsIfStale() {
	// If no vehicle update for 30 seconds then...
	if (new Date() - lastVehiclesUpdateTime > 30000) {
		// Remove all the vehicle icons. Should also remove
		// predictions as well but that would be more work
		// to implement.
		removeAllVehicles();
		
		console.log("Removing all vehicle because no update in a while.");
		
		// Update lastVehiclesUpdateTime so that don't keep
		// calling removeAlVehicles().
		lastVehiclesUpdateTime = new Date();
	}	
}

/**
 * Creates a new marker for the vehicle. The marker actually consists of
 * the main icon marker, a background circle, and an arrow marker to indicate
 * heading. 
 */
function createVehicleMarker(vehicleData) {
	var vehicleLoc = L.latLng(vehicleData.loc.lat, vehicleData.loc.lon);
	
	// Create new icon. First create the background marker as
	// a simple colored circle.
	var vehicleBackground = L.circleMarker(vehicleLoc,
			getVehicleMarkerBackgroundOptions(vehicleData)).addTo(
			map);

	// Add a arrow to indicate the heading of the vehicle
	var headingArrow = L.rotatedMarker(vehicleLoc,
			getVehicleMarkerOptions(vehicleData))
			.setIcon(arrowIcon).addTo(map);
	headingArrow.options.angle = vehicleData.loc.heading;
	headingArrow.setLatLng(vehicleLoc);
	// If heading is NaN then don't show arrow at all
	if (isNaN(parseFloat(vehicleData.loc.heading))) {
		headingArrow.setOpacity(0.0);
	}

	// Create the actual vehicle marker. This needs to be created
	// last and on top since the popup callback for the vehicle is
	// attached to this marker. Otherwise won't get click events.
	var vehicleName = (vehicleData.vehicleName == '' || 
			vehicleData.vehicleName == 'undefined' || 
			vehicleData.vehicleName === undefined) ? vehicleData.id : vehicleData.vehicleName;
	if (vehicleData.schAdhStr)
		vehicleName += " " + vehicleData.schAdhStr;
	var vehicleMarker = L.marker(vehicleLoc,
			getVehicleMarkerOptions(vehicleData))
			.setIcon(getIconForVehicle(vehicleData))
			.bindLabel(vehicleName, {noHide: true, className: "my-label", offset: [12, -14] })
			.addTo(map);


	// Add the background and the heading arrow markers to 
	// vehicleIcon so they can all be updated when the vehicle moves.
	vehicleMarker.background = vehicleBackground;
	vehicleMarker.headingArrow = headingArrow;

	// When user clicks on vehicle popup shows additional info
	vehicleMarker.on('click', function(e) {
		var content = getVehiclePopupContent(this.vehicleData);
		var latlng = L.latLng(this.vehicleData.loc.lat,
				this.vehicleData.loc.lon);
		// Create popup and associate it with the vehicleMarker
		// so can later update the content.
		this.popup = L.popup(vehiclePopupOptions, this)
			.setLatLng(latlng)
			.setContent(content).openOn(map);
	});	
	
	// Return the new marker
	return vehicleMarker;
}

/**
 * Updates the vehicle markers plus the popup based on the vehicleData read
 * in from the API.
 */
function updateVehicleMarker(vehicleMarker, vehicleData) {
	// If changing its minor status then need to redraw it with new options.
	if (vehicleMarker.vehicleData.uiType != vehicleData.uiType) {
		// Change from minor to non-minor, or visa versa so update icon
		vehicleMarker
				.setOpacity(getVehicleMarkerOptions(vehicleData).opacity);
		vehicleMarker.background
				.setStyle(getVehicleMarkerBackgroundOptions(vehicleData));
	}

	// Set to proper icon, which changes depending not just on vehicle
	// type but also on whether currently on layover.
	vehicleMarker.setIcon(getIconForVehicle(vehicleData));
	
	// Update orientation of the arrow for when setLatLng() is called
	vehicleMarker.headingArrow.options.angle = vehicleData.loc.heading;

	// Set opacity of arrow icon depending on state and on whether
	// the heading is valid
	if (isNaN(parseFloat(vehicleData.loc.heading))) {
		vehicleMarker.headingArrow.setOpacity(0.0);
	} else {
		vehicleMarker.headingArrow
				.setOpacity(getVehicleMarkerOptions(vehicleData).opacity);
	}

	// Update content in vehicle popup, if it has changed, in case it 
	// is actually popped up
	if (vehicleMarker.popup) {
		var content = getVehiclePopupContent(vehicleData);
		// If the content has actually changed then update the popup
		if (content != vehicleMarker.popup.getContent())
			vehicleMarker.popup.setContent(content).update();
	}

	// Update markers location on the map if vehicle has actually moved.
	if (vehicleMarker.vehicleData.loc.lat != vehicleData.loc.lat
			|| vehicleMarker.vehicleData.loc.lon != vehicleData.loc.lon) {
		animateVehicle(vehicleMarker, 
				vehicleMarker.vehicleData.loc.lat, 
				vehicleMarker.vehicleData.loc.lon, 
				vehicleData.loc.lat, vehicleData.loc.lon);
	}
}

/**
 * Reads in vehicle data obtained via AJAX. Called for each vehicle in API
 * whether vehicle changed or not.
 */
function vehicleLocationsCallback(vehicles, status) {
	// If no data from the API for vehicle where have created an icon
	// then remove that icon. Count down in for loop since might
	// be deleting some elements.
	for (var i = vehicleMarkers.length - 1; i >= 0; --i) {
		var haveDataForVehicle = false;
		var vehicleMarker = vehicleMarkers[i];
		var markerVehicleId = vehicleMarker.vehicleData.id;
		for (var j = 0; j < vehicles.vehicles.length; ++j) {
			var dataVehicleId = vehicles.vehicles[j].id;
			if (markerVehicleId == dataVehicleId) {
				haveDataForVehicle = true;
				break;
			}
		}

		// If no data from the API for the vehicle associated with the icon...
		if (!haveDataForVehicle) {
			// Delete the marker from the map
			removeVehicleMarker(vehicleMarker);

			// Remove the vehicleIcon from the vehiclesIcon array
			vehicleMarkers.splice(i, 1);
		}
	}

	// Keep track if got updated AVL data. If did then can keep the AVL
	// polling rate the same.
	var gotUpdatedAvlData = false;

	// Go through vehicle data read in for route...
	for (var i = 0; i < vehicles.vehicles.length; ++i) {
		var vehicleData = vehicles.vehicles[i];
		
		// Don't display schedule based vehicles since they are not real and
		// would only serve to confuse people.
		if (vehicleData.scheduleBased)
			continue;
		
		var vehicleLoc = L.latLng(vehicleData.loc.lat, vehicleData.loc.lon);

		// If vehicle icon wasn't already created then create it now
		var vehicleMarker = getVehicleMarker(vehicleData.id);
		if (vehicleMarker == null) {
			// Create the new marker
			vehicleMarker = createVehicleMarker(vehicleData);
			
			// Keep track of vehicle marker so it can be updated
			vehicleMarkers.push(vehicleMarker);
			
			// Definitely got updated data
			gotUpdatedAvlData = true;
		} else {
			// If got new AVL report then remember such
			var oldVehicleData = vehicleMarker.vehicleData;
			if (vehicleData.loc.time != oldVehicleData.loc.time)
				gotUpdatedAvlData = true;
				
			// Vehicle icon already exists, so update it
			updateVehicleMarker(vehicleMarker, vehicleData);
		}

		// Store vehicle data obtained via AJAX with vehicle so it can be used in popup
		vehicleMarker.vehicleData = vehicleData;
	}
	
	// If didn't get any updated AVL data then back off on the polling rate
	if (!gotUpdatedAvlData) {
		avlPollingRate = 2 * avlPollingRate;
		if (avlPollingRate > MAX_AVL_POLLING_RATE)
			avlPollingRate = MAX_AVL_POLLING_RATE;
		console.log("Didn't get new AVL data so increasing polling rate to " 
				+ avlPollingRate + " msec.");
	}

	// Update when vehicles last updated so can determine if update
	// hasn't happened in a long time
	lastVehiclesUpdateTime = new Date();
}

/**
 * Moves the vehicle an increment between its original and new locations.
 * Calls setTimeout() to call this function again in order to continue
 * the animation until finished.
 */
function interpolateVehicle(vehicleMarker, cnt, interpolationSteps, origLat, origLon, newLat, newLon) {
	// Determine the interpolated location
	var interpolatedLat = parseFloat(origLat) + (newLat - origLat) * cnt
			/ interpolationSteps;
	var interpolatedLon = parseFloat(origLon) + (newLon - origLon) * cnt
			/ interpolationSteps;
	var interpolatedLoc = [ interpolatedLat, interpolatedLon ];

//	console.log("interpolating vehicleId=" + vehicleMarker.vehicleData.id + " cnt=" + cnt + 
//			" interpolatedLat=" + interpolatedLat + " interpolatedLon=" + interpolatedLon);

	// Update all markers sto have interpolated location
	vehicleMarker.setLatLng(interpolatedLoc);
	vehicleMarker.background.setLatLng(interpolatedLoc);
	vehicleMarker.headingArrow.setLatLng(interpolatedLoc);

	// If there is a popup for the vehicle then need to move it too
	if (vehicleMarker.popup)
		vehicleMarker.popup.setLatLng(interpolatedLoc);

	if (++cnt <= interpolationSteps) {
		setTimeout(interpolateVehicle, 60, 
				vehicleMarker, cnt, interpolationSteps, 
				origLat, origLon, newLat, newLon);
	}
}

/**
 * Initiates animation of moving vehicle from old location to new location.
 * Determines number of interpolations to use for the animation. Doesn't
 * use animation (though still updates marker position) if vehicle not on
 * visible part of map or moves less than a pixel. Interpolation count is
 * determined such that will move at least a pixel towards the new loc.
 * Trying to avoid situation where moves less than a pixel towards the
 * new loc but then moves a pixel in the other horiz/vert direction due
 * to rounding.
 */
function animateVehicle(vehicleMarker, origLat, origLon, newLat, newLon) {
	//console.log("animating vehicleId=" + vehicleMarker.vehicleData.id + 
	//		" origLat=" + origLat + " origLon=" + origLon +
	//		" newLat=" + newLat + " newLon=" + newLon);
	
	// Use default interpolationSteps of 1 so that the marker location
	// will be updated no matter what. This is important because even
	// if vehicle only moves slightly or is off the map, still need
	// to update vehicle position.
	var interpolationSteps = 1;
	
	// Determine if vehicle is visile since no need to animate vehicles
	// that aren't visible
	var bounds = map.getBounds();
	var origLatLng = L.latLng(origLat, origLon);
	var newLatLng = L.latLng(newLat, newLon);
	if (bounds.contains(origLatLng) && bounds.contains(newLatLng)) {
		// Vehicle is visible. Determine number of pixels moving vehicle.
		// Don't want to look at sqrt(x^2 + y^2) since trying to avoid 
		// making the animation jagged. If use the sqrt distance traveled
		// then would sometimes have the marker move sideways instead of
		// forward since would be moving less than 1 pixel at a time
		// horizontally or vertically.
		var origPoint = map.latLngToLayerPoint(origLatLng) ;
		var newPoint = map.latLngToLayerPoint(newLatLng) ;
		var deltaX = origPoint.x - newPoint.x;
		var deltaY = origPoint.y - newPoint.y;
		var pixelsToMove = Math.max(Math.abs(deltaX), Math.abs(deltaY));

		//console.log("origPoint=" + origPoint + " newPoint=" + newPoint);
		//console.log("pixelsToMove=" + pixelsToMove);
		
		// Set interpolationSteps to number of pixels that need to move. This
		// provides smoothest possible animation. But limit interpolationSteps
		// to be at least 1 and at most 10.
		interpolationSteps = Math.max(pixelsToMove, 1);
		interpolationSteps = Math.min(interpolationSteps, 10);
	}
		
	// Start the interpolation process to update marker position
	interpolateVehicle(vehicleMarker, 1, interpolationSteps, origLat, origLon, 
			newLat, newLon);
}

/**
 * Should actually read in new vehicle positions and adjust all vehicle icons.
 */
function updateVehiclesUsingApiData() {
	// If route not yet configured then simply return. Don't want to read
	// in all vehicles for agency!
	// FIXME
	//if (!getRouteQueryStrParam())
	//	return;
	
	var url = apiUrlPrefix + "/command/vehiclesDetails?" + getRouteQueryStrParam();
	// If stop specified as query str param to this page pass it to the 
	// vehicles request such that all but the next 2 predicted vehicles
	// will be labled as minor ones and can therefore be drawn in UI to not
	// attract as much attention.
	if (getQueryVariable("s"))
		url += "&s=" + getQueryVariable("s") + "&numPreds=2";

	// Handle being able to show unassigned vehicles
	if (getQueryVariable("showUnassignedVehicles"))
		url += "&r=";

	// Use ajax() instead of getJSON() so that can set timeout since
	// will be polling vehicle info every 10 seconds and don't want there
	// to be many simultaneous requests.
	$.ajax(url, {
		  dataType: 'json',
		  success: vehicleLocationsCallback,
		  timeout: 6000 // 6 second timeout
		});
	
	// Call this function again at the appropriate time. This can't be done
	// in vehicleLocationsCallback() because it won't be called if there is
	// an error.
	avlTimer = setTimeout(updateVehiclesUsingApiData, avlPollingRate);
}

/************** Executable statements **************/

// Setup some global parameters
var verbose = getQueryVariable("verbose");
var agencyId = getQueryVariable("a");
if (!agencyId)
	alert("You must specify agency in URL using a=agencyId parameter");
 
// Create the map with a scale and zoom control in bottomleft so
// doesn't interfere with route selector
var map = L.map('map', {zoomControl: false});
L.control.scale({metric: false}).addTo(map);
L.control.zoom({position: 'bottomleft'}).addTo(map);

var mapTileUrl = '<%= WebConfigParams.getMapTileUrl() %>'; 
L.tileLayer(mapTileUrl, {
	// Specifying a shorter version of attribution. Original really too long.
    //attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery � <a href="http://mapbox.com">Mapbox</a>',
    attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> &amp; <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery �<%= WebConfigParams.getMapTileCopyright() %>',
    maxZoom: 19
}).addTo(map);

// Set the CLIP_PADDING to a higher value so that when user pans on map
// the route path doesn't need to be redrawn. Note: leaflet documentation
// says that this could decrease drawing performance. But hey, it looks
// better.
L.Path.CLIP_PADDING = 0.8;

// Initiate event handler to be called when a popup is closed. Sets 
// predictionsPopup to null to indicate that don't need to update predictions 
// anymore since stop popup not displayed anymore.
map.on('popupclose', function(e) {
	predictionsPopup = null;
	clearTimeout(predictionsTimeout);
	
	if (e.popup.parent)
		e.popup.parent.popup = null;
});

// Get timezone offset and put it into global agencyTimezoneOffset variable
// and set map bounds to the agency extent if route not specified in query string
$.getJSON(apiUrlPrefix + "/command/agencyGroup", 
		function(agencies) {
			// If agency not defined, such as when just testing AVL feed,
			// then set map to United States
			if (agencies.agency.length == 0) {
				map.fitBounds([[25.0, -130.0], [55.0, -70.0]]);
				return;
			}
			
	        agencyTimezoneOffset = agencies.agency[0].timezoneOffsetMinutes;
			
	        // Fit the map initially to the agency, but only if route not
	        // specified in query string. If route specified in query string
	        // then the map will be fit to that route once it is loaded in.
	        if (!getQueryVariable("r")) {
				var e = agencies.agency[0].extent;
				map.fitBounds([[e.minLat, e.minLon], [e.maxLat, e.maxLon]]);
	        }
	});	

// Deal with routes. First determine if route specified by query string
setRouteQueryStrParamViaQueryStr();

// If route not specified in query string then create route selector.
// Otherwise configure for the specified route.
if (!getRouteQueryStrParam()) {
  // Route not specified in query string. Therefore populate the route 
  // selector if route not specified in query string.
  $.getJSON(apiUrlPrefix + "/command/routes?keepDuplicates=true", 
 		function(routes) {
	        // Generate list of routes for the selector
	 		var selectorData = [{id: '', text: 'Select Route'}];
	 		for (var i in routes.routes) {
	 			var route = routes.routes[i];
	 			selectorData.push({id: route.id, text: route.name})
	 		}
	 		
	 		// Configure the selector to be a select2 one that has
	 		// search capability
 			$("#routes").select2({
 				placeholder: "<fmt:message key='div.selectroute'/>", 				
 				data : selectorData})
 				// Called when user selects route. Draws route and associated vehicles on map.
 				.on("select2:select", function(e) {
 					// First remove all old vehicles so that they don't
 					// get moved around when zooming to new route
 					removeAllVehicles();
 					
 					// Remove old predictions popup if there is one
 					if (predictionsPopup) 
 						map.closePopup(predictionsPopup);
 					
 					// Configure map for new route	
 					var selectedRouteId = e.params.data.id;
 					var url = apiUrlPrefix + "/command/routesDetails?r=" + selectedRouteId;
 					$.getJSON(url, routeConfigCallback);

 					// Reset the polling rate back down to minimum value since selecting new route
 					avlPollingRate = MIN_AVL_POLLING_RATE;
 					if (avlTimer)
 						clearTimeout(avlTimer);
 					
 					// Read in vehicle locations now
 					setRouteQueryStrParam("r=" + selectedRouteId); 					
 					updateVehiclesUsingApiData();
 					
 		 			// Disable tooltips. For some reason get an unwanted 
 		 			// tooltip consisting of the current select once a selection
 		 			// has been made. It is really distracting. So have to do
 		 			// this convoluted thing after every selection in order to
 		 			// make sure this annoying tooltip doesn't popup.
 		 			$( "#select2-routes-container" ).tooltip({ content: 'foo' });
 		 			$( "#select2-routes-container" ).tooltip("option", "disabled", true);
				});

	 		// If showing unassigned vehicles then start getting vehicle 
	 		// location data now instead instead of waiting till route selected.
	 		if (getQueryVariable("showUnassignedVehicles")) {
	 			updateVehiclesUsingApiData();
	 		}
	 		
	 		// of waiting 
 			// Set focus to selector so that user can simply start
 			// typing to select a route. Can't use something like
 			// '#routes' since select2 changes  the input element to a
 			// bunch of elements with peculiar and sometimes autogenerated
 			// ids. Therefore simply set focus to the "inpu" element.
 			// Note: only do this if not mobile touch device because on
 			// such a device have input focus on route selector means 
 			// that keyboard pops up when user tries to pan screen.
 			var isMobile = window.matchMedia("only screen and (max-width: 760px)");
            if (!isMobile.matches) {
            	var selector = $(".select2-selection"); // .selection .select2-selection #select2-routes-container
            	// Could not figure out how to set focus successfully.
            	//selector.focus();
            }
 	});	 
} else {
	// Route was specified in query string. 
	// Read in the route info and draw it on map.
	var url = apiUrlPrefix + "/command/routesDetails?" + getRouteQueryStrParam();
	if (getQueryVariable("s"))
		url += "&s=" + getQueryVariable("s");
	if (getQueryVariable("tripPattern"))
		url += "&tripPattern=" + getQueryVariable("tripPattern");
	$.getJSON(url, routeConfigCallback);		
	
	// Read in vehicle locations now (and every few seconds)
	updateVehiclesUsingApiData();

	// Make route selector visible. Haven't tested this!
	$("#routesDiv").css({"visibility":"hidden"});
}

/**
 * Setup timer to determine if haven't updated vehicles in a while.
 * This happens when open up a laptop or tablet that was already
 * displaying the map. For this situation should get rid of the
 * old predictions and vehicles so that they don't scoot around
 * wildly once actually do a vehicle update. This should happen
 * pretty frequently (every 300ms) so that stale vehicles and
 * such are removed as quickly as possible.
 */
setInterval(hideThingsIfStale, 300);
 
/**
 * Fade out the Transitime.org title
 */
setTimeout(function () {
	$('#mapTitle').hide('fade', 1000);
 }, 1000);
	 
</script>
