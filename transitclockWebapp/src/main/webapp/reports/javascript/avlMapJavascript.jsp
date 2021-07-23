<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="language" value="" scope="session" />
<fmt:setLocale value="${not empty param.language ? param.language : not empty language ? language : pageContext.request.locale}" />
<fmt:requestEncoding value = "UTF-8" />
<fmt:setBundle basename="org.transitclock.i18n.text" />
    
<script>
$("#route").attr("style", "width: 200px");

/* For drawing the route and stops */
var routeOptions = {
	color: '#00ee00',
	weight: 4,
	opacity: 0.4,
	lineJoin: 'round',
	clickable: false
};
				
 var stopOptions = {
    color: '#006600',
    opacity: 0.4,
    radius: 4,
    weight: 2,
    fillColor: '#006600',
    fillOpacity: 0.3,
};

var routePolylineOptions = {clickable: false, color: "#00f", opacity: 0.5, weight: 4};

var stopPopupOptions = {closeButton: false};

function drawAvlMarker(avl) {
	var latLng = L.latLng(avl.lat, avl.lon);
	  
	// Create the marker. Use a divIcon so that can have tooltips
  	var tooltip = avl.time.substring(avl.time.indexOf(' ') + 1);  
  	var avlMarker = L.rotatedMarker(avl, {
          icon: L.divIcon({
        	  className: 'avlMarker_',
        	  html: "<div class='avlTriangle' />",
        	  iconSize: [7,7]
          }),
          angle: avl.heading,
          title: tooltip
      }).addTo(vehicleGroup); 
	
  	// Create popup with detailed info
	
	var labels = ["<fmt:message key='div.Vehicle' />", "<fmt:message key='div.dgpstime' />", "<fmt:message key='TimeProc' />", "Lat/Lon", 
		"<fmt:message key='div.dspeed' />", "<fmt:message key='div.dheading' />", "<fmt:message key='AssigmentId' />"],
		keys = ["vehicleId", "time", "timeProcessed", "latlon", "niceSpeed", "heading", "assignmentId"];
	
	// populate missing keys
	avl.latlon = avl.lat + ", " + avl.lon
	avl.niceSpeed =  Math.round(parseFloat(avl.speed) * 10)/10 + " kph";
	
	var content = $("<table />").attr("class", "popupTable");
	
	for (var i = 0; i < labels.length; i++) {
		var label = $("<td />").attr("class", "popupTableLabel").text(labels[i] + ": ");
		var value = $("<td />").text(avl[keys[i]]);
		content.append( $("<tr />").append(label, value) )
	}
		
	avlMarker.bindPopup(content[0]);
  	
	return avlMarker;
}
  
/* Called when receiving the AVL data via AJAX call */
function processAvlCallback(jsonData) {
	 
	/* Save avl data */ 
	
    // List of all the latLngs
    var latLngs = [];

    // So can draw separate polyline per vehicle
    var previousVehicleId = "";
    var latLngsForVehicle = [];
    
    // reset list of vehicles
    var vehicles = [];
    
    // For each AVL report...
    var vehicle;
    for (var i=0; i<jsonData.data.length; ++i) {
    	var avl = jsonData.data[i];
    	
    	// If getting data for new vehicle then need to draw out polyline 
    	// for the old vehicle
		if (avl.vehicleId != previousVehicleId) {		    
	    	// Create a line connecting the AVL locations as long as there are 
	    	// at least 2 AVL reports for the vehicle
	    	if (latLngsForVehicle.length >= 2)
	    		L.polyline(latLngsForVehicle, routePolylineOptions).addTo(map); //.bringToBack();
	    	latLngsForVehicle = [];
	    	vehicle = {id: avl.vehicleId, data: []}
	    	vehicles.push(vehicle);
		} 
		
    	// parse date string -> number
    	avl.timestamp = Date.parse(avl.time.replace(/-/g, '/').slice(0,-2))
    	
		vehicle.data.push(avl)
		previousVehicleId = avl.vehicleId;
		
		var latLng = drawAvlMarker(avl).getLatLng();

		latLngsForVehicle.push(latLng);
		latLngs.push(latLng);
    }
    
    // Draw polyline for the last vehicle in the AVL data
    if (latLngsForVehicle.length >= 2)
    	L.polyline(latLngsForVehicle, routePolylineOptions).addTo(vehicleGroup); //.bringToBack();
    
    // If actually read AVL data...
    if (latLngs.length > 0) {
    	// To make all AVL reports fit in bounds of map.
    	map.fitBounds(latLngs);
  	} else {
		alert("No AVL data for the criteria specified.")
  	}
	return vehicles;
}

  
/**
 * Reads in route data obtained via AJAX and draws route and stops on map.
 */
function routeConfigCallback(data, status) {
	// Draw the paths for the route
	
	var route = data.routes[0];
	
	for (var i=0; i<route.shape.length; ++i) {
		var shape = route.shape[i];
		L.polyline(shape.loc, routeOptions).addTo(routeGroup);
	}
	  
  	// Draw stops for the route. 
  	for (var i=0; i<route.direction.length; ++i) {
  		var direction = route.direction[i];
  		for (var j=0; j<direction.stop.length; ++j) {
  			var stop = direction.stop[j];
  			
  			// Create the stop Marker
  			var stopMarker = L.circleMarker([stop.lat,stop.lon], stopOptions).addTo(routeGroup);
  					
  			// Create popup for stop marker
  
  			var content = $("<table />").attr("class", "popupTable");
  			var labels = ["Stop ID", "Name"], keys = ["id", "name"];
  			for (var i = 0; i < labels.length; i++) {
  				var label = $("<td />").attr("class", "popupTableLabel").text(labels[i] + ": ");
  				var value = $("<td />").text(stop[keys[i]]);
  				content.append( $("<tr />").append(label, value) );
  			}
  			
  			stopMarker.bindPopup(content[0]);
  		}
   	 }
}
  
// Data in vehicles will be available as CSV when you click the `export' link.
// CSV should be the AVL CSV format used elsewhere in Transitime.
// org.transitclock.avl.AvlCsvWriter writes the following header:
// vehicleId,time,justTime,latitude,longitude,speed,heading,assignmentId,assignmentType
// org.transitclock.avl.AvlCsvRecord has required keys vehicleId, time, latitude, longitude
// all others optional
function createExport(vehicles) {
	
	var data = vehicles[0].data
  	
  	// set keys
	var keys = ["vehicleId", "time", "latitude", "longitude", "speed", "heading", "assignmentId"]
	// CSV key => JS object key
	function mapKey(k) {
		var o = {"latitude": "lat", "longitude": "lon"}
		return o[k] || k;
	}
	
  	// write header
  	var text = keys[0];
  	for (var i = 1; i < keys.length; i++)
  		text += "," + keys[i]
  	text += '\n'
  	
  	// write rows
  	for (var i = 0; i < data.length; i++) {
  		text += data[i][keys[0]]
  		for (var j = 1; j < keys.length; j++) {
  			var k = mapKey(keys[j])
  			text += "," + data[i][k]
  		}
  		text += "\n";
  	}
  	
  	var blob = new Blob([text], { type: 'text/plain' }); // change to text/csv for download prompt
  	$("#exportData")[0].href = window.URL.createObjectURL(blob);
 }
 
//Add a new layer for only route/bus markers, so that it can be refreshed
//when selections change without having to redraw tiles.
var map = L.map('map');
var mapTileUrl = <%=System.getProperty("transitclock.mapTileUrl")%>
L.control.scale({metric: false}).addTo(map);
L.tileLayer(mapTileUrl, {
 attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> &amp; <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
 maxZoom: 19
}).addTo(map);

//fit map to agency boundaries.
$.getJSON(apiUrlPrefix + "/command/agencyGroup", function(agencies) {
	var e = agencies.agency[0].extent;
	map.fitBounds([[e.minLat, e.minLon], [e.maxLat, e.maxLon]]);
})
.fail(function(request, status, error) {
  alert(error + '. ' + request.responseText);
});


var vehicleGroup = L.layerGroup().addTo(map);
var routeGroup = L.layerGroup().addTo(map);
var animationGroup = L.layerGroup().addTo(map);

//Set the CLIP_PADDING to a higher value so that when user pans on map
//the route path doesn't need to be redrawn. Note: leaflet documentation
//says that this could decrease drawing performance. But hey, it looks
//better.
L.Path.CLIP_PADDING = 0.8;0

if (request.v || request.r) {
	// Request exists; set all the controls to match the values in the request.
	$("#vehicle").val(request.v).trigger("change");
	$("#beginDate").val(request.beginDate).trigger("change");
	$("#numDays").val(parseInt(request.numDays)).trigger("change");
	$("#beginTime").val(request.beginTime).trigger("change");
	$("#endTime").val(request.endTime).trigger("change");
	$("#route").val(request.r).trigger("change");
	
	//draw vehicles if there is already request information
	if (request.v) {
		drawAvlData();
	}
	if (request.r && request.r != "")
		drawRoute(request.r);
	
}
else {
	// no request
	// set beginDate and endDate to defaults
	request.beginDate = $("#beginDate").val()
	request.numDays = $("#numDays").val()
}

// draw route data when dropdown is selected
$("#route").change(function(evt) { drawRoute(evt.target.value) });

// draw AVL data when submit button is clicked
$("#submit").on("click", function() {
	/* Set request object to match new values */
	request.v = $("#vehicle").val();
	request.beginDate = $("#beginDate").val();
	request.numDays = $("#numDays").val();
	request.beginTime = $("#beginTime").val();
	request.endTime = $("#endTime").val();
	request.r = $("#route").val();

	// Clear existing layer and draw new objects on map.
	drawAvlData();	
});


//Get the AVL data via AJAX and call processAvlCallback to draw it
function drawAvlData() {
	$.ajax({
	  	// The page being requested
	    url: contextPath + "/reports/avlJsonData.jsp",
		// Pass in query string parameters to page being requested
		data: request,
		// Needed so that parameters passed properly to page being requested
		traditional: true,
	    dataType:"json",
	    async: true,
	    // When successful process JSON data
	    success: function(resp) {
	    	vehicleGroup.clearLayers();
	    	var vehicles = processAvlCallback(resp);
	    	// connect export to link to csv creation.
	    	createExport(vehicles);
	    	if (vehicles.length)
	    		prepareAnimation(vehicles[0].data); // only animate first vehicle returned.
	    },
	    // When there is an AJAX problem alert the user
	    error: function(request, status, error) {
	      alert(error + '. ' + request.responseText);
	    },
	});
}

function drawRoute(route) {
	routeGroup.clearLayers();
	if (route != "") {
		var url = apiUrlPrefix + "/command/routesDetails?r=" + route;
		$.getJSON(url, routeConfigCallback);
	}
}

/* Animation controls */

var busIcon =  L.icon({
    iconUrl:  contextPath + "/reports/images/bus.png", 
    iconSize: [25,25]
});
var animate = avlAnimation(animationGroup, busIcon, $("#playbackTime")[0]);

var playButton = contextPath + "/reports/images/playback/media-playback-start.svg",
	pauseButton = contextPath + "/reports/images/playback/media-playback-pause.svg";

animate.onEnd(function() {
	$("#playbackPlay").attr("src", playButton);
})

// Given a list of AVL positions, initialize the animation object.
function prepareAnimation(avlData) {

	// Make sure animation controls are in their initial state.
	$("#playbackPlay").attr("src", playButton);
	$("#playbackRate").text("1X");
	
	animate(avlData);

}

$("#playbackNext").on("click", animate.next);

$("#playbackPrev").on("click", animate.prev);

$("#playbackPlay").on("click", function() {
	
	if (!animate.paused()) {
		animate.pause();
		$("#playbackPlay").attr("src", playButton);
	}
	else { // need to start it
		animate.start();
		$("#playbackPlay").attr("src", pauseButton);
	}
	
});

$("#playbackFF").on("click", function() {
	var rate = animate.rate()*2;
	animate.rate(rate);
	$("#playbackRate").text(rate + "X");
});

$("#playbackRew").on("click", function() {
	var rate = animate.rate()/2;
	animate.rate(rate);
	$("#playbackRate").text(rate + "X");
});
</script>
