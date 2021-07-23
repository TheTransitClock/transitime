<%-- Provides schedule adherence data in JSON format. Provides
     count of times for each time bucket. The json time_period is 
     the floor of the time span span. So it is -60 for -60 to -30 seconds 
     late, -30 for -30 seconds to 0 seconds late,
     0 for 0 seconds to 30 seconds early, 30 for 30 seconds to 60 seconds
     early, 60 for 60 seconds to 90 seconds late, etc
     
     Request parameters are:
       a - agency ID
       r - route ID or route short name. Can specify multiple routes. 
           Not specifying route provides data for all routes.
       dateRange - in format "xx/xx/xx to yy/yy/yy"
       beginDate - date to begin query. For if dateRange not used.
       numDays - number of days can do query. Limited to 31 days. For if dateRange not used.
       beginTime - for optionally specifying time of day for query for each day
       endTime - for optionally specifying time of day for query for each day
       allowableEarly - how early vehicle can be and still be OK.  Decimal format OK. 
       allowableLate - how early vehicle can be and still be OK. Decimal format OK.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="org.transitclock.db.webstructs.WebAgency" %>
<%@ page import="org.transitclock.reports.GenericJsonQuery" %>
<%@ page import="org.transitclock.reports.SqlUtils" %>
<%
try {
 String agencyId = request.getParameter("a");
 WebAgency agency = WebAgency.getCachedWebAgency(agencyId);
 String dbtype = agency.getDbType();
 boolean isMysql = "mysql".equals(dbtype);

String allowableEarlyStr = request.getParameter("allowableEarly");
if (allowableEarlyStr == null || allowableEarlyStr.isEmpty())
	allowableEarlyStr = "1.0";
String allowableEarlyMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarlyStr) + " seconds'";

String allowableLateStr = request.getParameter("allowableLate");
if (allowableLateStr == null || allowableLateStr.isEmpty())
	allowableLateStr = "4.0";
String allowableLateMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableLateStr) + " seconds'";

// Group into timebuckets of 30 seconds
int BUCKET_TIME = 30;
String epochCommandPre = "EXTRACT (EPOCH FROM ";
String epochCommandPost = ")";
if (isMysql) {
  epochCommandPre = "";
  epochCommandPost = "";
}
String sql =
	"SELECT " 
	+ "  COUNT(*) AS counts_per_time_period, \n"
	// Put into time buckets of every BUCKET_TIME seconds. 
	+ "  FLOOR(" + epochCommandPre + " (scheduledTime-time)" + epochCommandPost + " / " + BUCKET_TIME + ")*" + BUCKET_TIME + " AS time_period \n"
	+ "FROM ArrivalsDepartures ad\n"
    + "WHERE "
    // Only need arrivals/departures that have a schedule time
    + " ad.scheduledTime IS NOT NULL \n"
    // Ignore stops where schedule adherence really far off
    + " AND ABS(" + epochCommandPre + " (scheduledTime-time)" + epochCommandPost + ") < 3600\n"
    // Specifies which routes to provide data for
    + SqlUtils.routeClause(request, "ad") + "\n"
    + SqlUtils.timeRangeClause(request, "ad.time", 7) + "\n"
    // Grouping needed to put times in time buckets
    + " GROUP BY time_period \n"
    // Order by lateness so can easily understand results
    + " ORDER BY time_period;";

// Just for debugging
System.out.println("\nFor schedule adherence by time buckets query sql=\n" + sql);
    		
// Do the query and return result in JSON format    
String jsonString = GenericJsonQuery.getJsonString(agencyId, sql);
response.setContentType("application/json");
response.setHeader("Access-Control-Allow-Origin", "*");
response.getWriter().write(jsonString);
} catch (Exception e) {
	response.setStatus(400);
	response.getWriter().write(e.getMessage());
	return;
}
%>