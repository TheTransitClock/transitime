<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="language" value="" scope="session" />
<fmt:setLocale value="${not empty param.language ? param.language : not empty language ? language : pageContext.request.locale}" />
<fmt:requestEncoding value = "UTF-8" />
<fmt:setBundle basename="org.transitclock.i18n.text" />
<%-- For creating a vehicle selector parameter via a jsp include. 
     User can select all vehicles (v set to "") OR a single VEHICLE.
     Reads in routes via API for the agency specified by the "a" param. --%>

<style type="text/css">
/* Set font for vehicle selector. Need to use #select2-drop because of 
 * extra elements that select2 adds 
 */
#select2-drop, #vehicleDiv {
  font-family: sans-serif; font-size: large;
}
</style>

<script>

$.getJSON(apiUrlPrefix + "/command/vehicleConfigs", 
 		function(vehicles) {
	        // Generate list of routes for the selector
	 		var selectorData = [{id: '', text: '<fmt:message key="AllVehicles" />'}];
	 		for (var i in vehicles.vehicleConfig) {
	 			var id = vehicles.vehicleConfig[i].id;
	 			var name = (vehicles.vehicleConfig[i].name === undefined || vehicles.vehicleConfig[i].name == '') ? vehicles.vehicleConfig[i].id : vehicles.vehicleConfig[i].name;
	 			selectorData.push({id: id, text: name})
	 		}
	 		
	 		// Configure the selector to be a select2 one that has
	 		// search capability
 			$("#vehicle").select2({
 				data : selectorData
 			});
	 		
	 		// Set first value. Empty string value not set with select2.
	 		$("#vehicle option:first").attr("value", "");
	 		
	 		// Tooltips for a select2 widget don't automatically go away when 
	 		// item selected so remove the tooltip manually. This is a really 
	 		// complicated interaction between select2 and jquery UI tooltips.
	 		// First need to set the tooltip title content but getting the
	 		// originally configured title for the element.
 			var modifiedRouteElement = $( "#s2id_vehicle" );
	 		var configuredTitle = modifiedRouteElement.attr("title");
	 		$( "#s2id_vehicle" ).tooltip({ content: configuredTitle });
 			
	 		// Now that the title has set need to manually remove the tooltip
	 		// when a select2 item is selected. Sheesh!
 		 	$("#vehicle").on("change", function(e) { $("#s2id_vehicle").tooltip("close") }); 		 	
 	});
 	
</script>

    <div id="vehicleDiv"  class="param">
      <label for="vehicle"><fmt:message key="div.Vehicle" />:</label>
      <select id="vehicle" name="v" style="width: 200px" 
      	title="Select which vehicle you want data for."><!-- prevent jspx min --></select>
    </div>
    
