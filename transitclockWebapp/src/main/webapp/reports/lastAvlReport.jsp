<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="org.transitclock.utils.web.WebUtils" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
String agencyId = request.getParameter("a");
if (agencyId == null || agencyId.isEmpty()) {
    response.getWriter().write("You must specify agency in query string (e.g. ?a=mbta)");
    return;
}
%>
<html>
<head>
  <%@include file="/template/includes.jsp" %>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title><fmt:message key="div.lastgpsreports" /></title>

<style>

</style>

<script>

/* Programatically create contents of table */
function dataReadCallback(jsonData) {
	var table = document.getElementById("dataTable");
	
	for (var i=0; i<jsonData.data.length; ++i) {
		var vehicleInfo = jsonData.data[i];

		// Insert row (after the header)
		var row = table.insertRow(i+1);
		row.insertCell(0).innerHTML = (vehicleInfo.name === undefined || vehicleInfo.name == '') ? vehicleInfo.vehicleId : vehicleInfo.name;
		row.insertCell(1).innerHTML = vehicleInfo.maxTime;
		row.insertCell(2).innerHTML = vehicleInfo.lat;
		row.insertCell(3).innerHTML = vehicleInfo.lon;
	}
}

// Initiate AJAX call to get data to put into table
$( document ).ready(function() {
  $.ajax({
   	// The page being requested
  	url: "/web/reports/lastAvlJsonData.jsp",
   	// Pass in query string parameters to page being requested
   	data: {<%= WebUtils.getAjaxDataString(request) %>},
  	// Needed so that parameters passed properly to page being requested
   	traditional: true,
    dataType:"json",
	success: dataReadCallback
  });
});
</script>
</head>
<body>
<%@include file="/template/header.jsp" %>
<div id="title"><fmt:message key="div.lgpsr" /></div>
<table id="dataTable">
  <tr><th><fmt:message key="div.Vehicle" /></th><th><fmt:message key="div.lgps" /></th><th><fmt:message key="div.lat" /></th><th><fmt:message key="div.lon" /></th></tr>
  </table>
</body>
</html>