<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<%@ page import="org.transitclock.utils.web.WebUtils" %>
<%@page import="java.util.ResourceBundle" %>
<%@page import="java.util.Locale" %>
<%@page import="org.transitclock.db.webstructs.WebAgency"%>

<%     
// Determine all the parameters from the query string
ResourceBundle labels = ResourceBundle.getBundle("org.transitclock.i18n.text", request.getLocale());

// Determine agency using "a" param
String agencyId = request.getParameter("a");

// Determine list of routes for title using "r" param.
// Note that can specify multiple routes.
String routeIds[] = request.getParameterValues("r");
String titleRoutes = "";
if (routeIds != null && !routeIds[0].isEmpty()) {
    titleRoutes += ", " + labels.getString("PredictionAccuracyRangeForRoute");
    if (routeIds.length > 1) 
        titleRoutes += "s";
    titleRoutes += routeIds[0];
    for (int i=1; i<routeIds.length; ++i) {
		String routeId = routeIds[i];
	    titleRoutes += " & " + routeId;
    }
}

String sourceParam = request.getParameter("source");
String source = (sourceParam != null && !sourceParam.isEmpty()) ? 
		request.getLocale().toString() == "pl_PL" ? 
				", " + labels.getString("Predictions") + " " + sourceParam 
				: ", " + sourceParam + " " + labels.getString("Predictions") 
			: "";  
String beginDate = request.getParameter("beginDate");
String numDays = request.getParameter("numDays");
String beginTime = request.getParameter("beginTime");
String endTime = request.getParameter("endTime");

String chartTitle = "Prediction Accuracy for "
	+ WebAgency.getCachedWebAgency(agencyId).getAgencyName()   
	+ titleRoutes 
	+ source 
	+ ", " + beginDate + " " + labels.getString("div.for") + " " + numDays + " " + (Integer.parseInt(numDays) > 1 ? labels.getString("Days") : labels.getString("DayGenitive"));

if ((beginTime != null && !beginTime.isEmpty()) || (endTime != null && !endTime.isEmpty())) {
	chartTitle += ", " + beginTime + " to " + endTime;
}

%>  
	  
    
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
  <head>
    <%@include file="/template/includes.jsp" %>
    
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title><fmt:message key="div.PredictionAccuracy" /></title>
    
    <style>
       #loading {
          position: fixed;
	      left: 0px;
	      top: 0px;
	      width: 100%;
	      height: 100%;
	      z-index: 9999;
	      background: url('images/page-loader.gif') 50% 50% no-repeat rgb(249,249,249);
      }

		#errorMessage {
          display: none;
          position: fixed;
	      top: 30px;
	      margin-left: 20%;
	      margin-right: 20%;
	      height: 100%;
	      text-align: center;
	      font-family: sans-serif;
	      font-size: large;
	      z-index: 9999;
		}
    </style>  
 </head>
 
  <body>
    <%@include file="/template/header.jsp" %>
    
    <!--  There seems to be a bug with a chart_lines chart where it
          doesn't properly handle a height specified as a percentage.
          Therefore need to use pixels for the height. -->
    <div id="chart_lines" style="width: 100%; height: 700px;"></div>
    <div id="loading"></div>
    <div id="errorMessage"></div>
  </body>

    <!-- Needed for Google Chart -->
    <script type="text/javascript" src="https://www.google.com/jsapi"></script>
    
    <script type="text/javascript">

    // Updates chart when page is resized. But only does so at most
    // every 200 msec so that don't bog system down trying to repeatedly
    // update the chart.
    var globalTimer;
    window.onresize = function () {
                 clearTimeout(globalTimer);
                 globalTimer = setTimeout(drawChart, 100)
    };
               
    var globalDataTable = null;

    function getDataTable() {
      var jsonTextData = $.ajax({
      	// The page being requested
        url: "predAccuracyIntervalsData.jsp",
    	// Pass in query string parameters to page being requested
    	data: {<%= WebUtils.getAjaxDataString(request) %>},
    	// Needed so that parameters passed properly to page being requested
    	traditional: true,
        dataType:"json",
        async: false,
        // When successful read in data into the JSON table used by the chart
        success: function(jsonData) {
          globalDataTable = new google.visualization.DataTable(jsonData);
          },
        // When there is an AJAX problem alert the user
        error: function(request, status, error) {
        	console.log(request.responseText)
        	var msg = $("<p>").html("<br><fmt:message key='NoDataForParameters' />")
     		$("#errorMessage").append(msg);
			$("#errorMessage").fadeIn("slow");
          },
        }).responseJSON;
    }
    
    function drawChart() {
        // The intervals data as narrow lines (useful for showing raw source data)
        var chartOptions = {
            title: '<%= chartTitle %>',
            titleTextStyle: {fontSize: 28},
            curveType: 'function',
            lineWidth: 4,
            intervals: { 'style':'area' },
            legend: 'bottom',
            // Usually will first be displaying Transitime predictions and 
            // those will get the first color. If both Transitime and Tther
            // predictions shown then the Other ones will get the second color.
            // But want color for the Other predictions to be consistent 
            // whether only Other predictions or both Other and Transitime ones
            // are shown. Therefore do something fancy here for consistency.
            series: [{'color': '<%= (sourceParam==null || !sourceParam.equals("Other")) ? "blue" : "red" %>'},{'color': 'red'}],
            chartArea: {
                // Use most of available area. But need to not use 100% or else 
                // labels won't appear
            	width:'90%', 
            	height:'80%', 
            	// Make chart a bit graay so that it stands out
            	backgroundColor: '#f2f2f2'},
            hAxis: {
            	title: '<fmt:message key="PredictionLengthInMinutes" />',
            	// So that last column is labeled
            	maxValue: 15,
            	// Want a gridline for every minute, not just the default of 5 gridlines
       	        gridlines: {count: 15},
       	        // Nice to show a faint line for every 30 seconds as well
            	minorGridlines: {count: 1}
       	    },
            vAxis: {title: '<fmt:message key="PredictionAccuracySecs" />',
            	// Try to show accuracy on a consistent vertical axis and 
            	// divide into minutes. This unfortunately won't work well
            	// if values are greater than 300 because then chart will
            	// autoscale but will still be using 8 gridlines
            	minValue: -120, 
            	maxValue: 300,
            	gridlines: {count: 8},
       	        // Nice to show a faint line for every 30 seconds as well
            	minorGridlines: {count: 1}
             },
         	// Sometimes won't have data in a prediction bucket. For this
         	// case want chart to interpolate instead of displaying nothing.
         	interpolateNulls: true,
         	
			lineWidth: 1.0
        };
  
        var chart = new google.visualization.LineChart(document.getElementById('chart_lines'));
        chart.draw(globalDataTable, chartOptions);
      }
      
      function getDataAndDrawChart() {
          getDataTable();
          if (globalDataTable != null)
        	  drawChart();

          // Now that chart has been drawn faceout the loading image
          $("#loading").fadeOut("slow");
        }

      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(getDataAndDrawChart);

    </script>
 </html>
 