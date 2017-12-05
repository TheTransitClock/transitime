<%@ page language="java" contentType="application/json; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="org.transitime.reports.GenericJsonQuery" %>
<%
String sql =
	"SELECT vehicleId, max(time) AS maxTime "
	+ "FROM avlreports WHERE time > now() + '-24 hours' "
	+ "GROUP BY vehicleId;";
String agencyId = request.getParameter("a");
String jsonString = GenericJsonQuery.getJsonString(agencyId, sql);
response.getWriter().write(jsonString);
%>