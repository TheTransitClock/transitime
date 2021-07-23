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
package org.transitclock.reports;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.transitclock.db.webstructs.WebAgency;
import org.transitclock.utils.Time;

/**
 * SQL utilities for creating SQL statements using parameters passed in to a
 * page. Intended to make creating sql for route and time based queries easier
 * to create.
 * 
 * @author Michael Smith
 *
 */
public class SqlUtils {
	
	/**
	 * To be called on request parameters to make sure they don't contain any
	 * SQL injection trickery.
	 * 
	 * @param parameter
	 * @throws RuntimeException if problem characters detected
	 * @return the parameter
	 */
	public static String throwOnSqlInjection(String parameter) {
		// If null then it is not a problem
		if (parameter == null)
			return parameter;
		
		// If parameter contains a ' or a ; then throw error to 
		// prevent possible SQL injection attack
		if (parameter.contains("'") || parameter.contains(";"))
			throw new IllegalArgumentException("Parameter \"" + parameter
					+ "\" not valid.");
		
		// Not a problem so return parameter
		return parameter;
	}
	
	/**
	 * Returns SQL list of route identifiers specified by the request parameter
	 * "r". Multiple routes can be specified. These route identifiers might be
	 * route_ids or route_short_names.
	 * 
	 * @param request
	 *            Http request containing parameters for the query
	 * @return an sql list such as "('21','5','5R')"
	 */
	public static String routeIdentifiersList(HttpServletRequest request) {
		String[] routeIdentifiers = request.getParameterValues("r");
		StringBuilder sb = new StringBuilder();
		sb.append(" (");
		boolean needComma = false;
		for (String routeIdentifier : routeIdentifiers) {
			throwOnSqlInjection(routeIdentifier);
			
			if (needComma)
				sb.append(',');
			needComma = true;
			
			sb.append('\'').append(routeIdentifier).append('\'');
		}
		sb.append(") ");
		
		return sb.toString();
	}
	
	/**
	 * Creates a SQL clause for specifying routes. Looks at the request
	 * parameter "r". It can be empty, signifying all routes, or can specify
	 * multiple routes. If "r" parameter not specified in request then empty
	 * string is returned such that data for all routes will be retrieved. Since
	 * "AND" is included in clause if it is not empty string this clause can't
	 * be put right after the WHERE clause
	 * 
	 * @param request
	 *            Http request containing parameters for the query
	 * @param tableAliasName
	 *            for when joins are used such that a table has an alias such as
	 *            "FROM arrivalsdepartures ad, routes r"
	 * @return SQL clause such as
	 *         "AND ad.routeshortname = r.shortname AND ad.scheduledtime IS NOT NULL AND (ad.routeshortname IN ('21','5') OR ad.routeid IN ('21','5') )"
	 */
	public static String routeClause(HttpServletRequest request,
			String tableAliasName) {
		// If no route specified then no SQL route clause
		if (request.getParameter("r") == null)
			return "";
		
		String routeIdentifiers = routeIdentifiersList(request);
		
		// For specifying table alias name for when doing more
		// complicated queries
		String tableAlias = "";
		if (tableAliasName != null && !tableAliasName.isEmpty())
			tableAlias = tableAliasName + ".";
		
		return " AND " + tableAlias + "routeShortName IN " + routeIdentifiers;
	}
	
	/**
	 * Creates a SQL clause for specifying a time range. Looks at the request
	 * parameters "beginDate", "numDays", "beginTime", and "endTime"
	 * 
	 * @param request
	 *            Http request containing parameters for the query
	 * @param timeColumnName
	 *            name of time column for that for query
	 * @param maxNumDays
	 *            maximum number of days for query. Request parameter numDays is
	 *            limited to this value in order to make sure that query doesn't
	 *            try to process too much data.
	 * @return SQL string such as
	 *         "AND ad.time BETWEEN '10/30/2015' AND TIMESTAMP '10/30/2015' + INTERVAL '1 day' AND time::time BETWEEN '12:00' AND '24:00'"
	 */
	public static String timeRangeClause(HttpServletRequest request,
			String timeColumnName, int maxNumDays) {
	  String agencyId = request.getParameter("a");
	  WebAgency agency = WebAgency.getCachedWebAgency(agencyId);
	  boolean isMysql = "mysql".equals(agency.getDbType());
		String beginTime = request.getParameter("beginTime");
		throwOnSqlInjection(beginTime);
		
		String endTime = request.getParameter("endTime");
		throwOnSqlInjection(endTime);
		
		//Determine the time portion of the SQL
		String timeSql = "";
		// If beginTime or endTime set but not both then use default values
		if ((beginTime != null && !beginTime.isEmpty())
				|| (endTime != null && !endTime.isEmpty())) {
			if (beginTime == null || beginTime.isEmpty())
				beginTime = "00:00";
			if (endTime == null || endTime.isEmpty())
				endTime = "24:00";
		}
		if (beginTime != null && !beginTime.isEmpty() 
				&& endTime != null && !endTime.isEmpty()) {
		  if (isMysql) {
        timeSql = " AND time(" + timeColumnName + ") BETWEEN '" 
            + beginTime + "' AND '" + endTime + "' ";
		  } else {
        timeSql = " AND " + timeColumnName + "::time BETWEEN '" 
            + beginTime + "' AND '" + endTime + "' ";
		  }
		}

		String dateRange = request.getParameter("dateRange");
		throwOnSqlInjection(dateRange);
		if (dateRange != null) {
			String fromToDates[] = dateRange.split(" to ");
			String beginDateStr, endDateStr;
			if (fromToDates.length == 1) {
				beginDateStr = endDateStr = fromToDates[0];
			} else {
				beginDateStr = fromToDates[0];
				endDateStr = fromToDates[1];
			}
			
			// Make sure not running report for too many days
			try {
				long beginDateTime = Time.parseDate(beginDateStr).getTime();
				long endDateTime = Time.parseDate(endDateStr).getTime();
				if (endDateTime - beginDateTime >= maxNumDays * Time.DAY_IN_MSECS) {					
					throw new IllegalArgumentException("Date range is limited to "
							+ maxNumDays + " days.");
				}
			} catch (ParseException e) {
				throw new IllegalArgumentException("Could not parse begin date \"" 
						+ beginDateStr + "\" or end date \"" 
						+ endDateStr + "\".");
			}
			
			String sql = null;
			if (isMysql) {
			  sql = " AND " + timeColumnName + " BETWEEN '" + beginDateStr
	          + "' " + " AND DATE_ADD(STR_TO_DATE('" + endDateStr + "', '%Y-%m-%d'), INTERVAL 1 day) " 
	          + timeSql + ' ';
			} else {
			  sql = " AND " + timeColumnName + " BETWEEN '" + beginDateStr
					+ "' " + " AND TIMESTAMP '" + endDateStr + "' + INTERVAL '1 day' " 
					+ timeSql + ' ';
			}
			return sql;
		} else { // Not using dateRange so must be using beginDate and numDays params
			String beginDate = request.getParameter("beginDate");
			throwOnSqlInjection(beginDate);
			
			String numDaysStr = request.getParameter("numDays");
			throwOnSqlInjection(numDaysStr);
			
			// Limit number of days to maxNumDays to prevent queries that are 
			// too big
			int numDays = Integer.parseInt(numDaysStr);
			if (numDays > maxNumDays)
				numDays = maxNumDays;
			String sql = null;
			if (isMysql) {
				String reformattedBeginDate = beginDate;
				SimpleDateFormat currentFormat = new SimpleDateFormat("MM-dd-yyyy");
				SimpleDateFormat requiredFormat = new SimpleDateFormat("yyyy-MM-dd");
				try {
				    reformattedBeginDate = requiredFormat.format(currentFormat.parse(beginDate));
				} catch (ParseException e) {
				    e.printStackTrace();
				}
			  sql = " AND " + timeColumnName + " BETWEEN '" + reformattedBeginDate
	          + "' " + " AND DATE_ADD(STR_TO_DATE('" + reformattedBeginDate + "', '%Y-%m-%d'), INTERVAL "
	          + numDays + " day) " + timeSql + ' ';
			} else {
			sql =" AND " + timeColumnName + " BETWEEN '" + beginDate
					+ "' " + " AND TIMESTAMP '" + beginDate + "' + INTERVAL '"
					+ numDays + " day' " + timeSql + ' ';
			}
			return sql;
		}

	}
	
	/**
	 * Converts minutes string to seconds.
	 * 
	 * @param minutes
	 * @return seconds
	 */
	public static int convertMinutesToSecs(String minutes) {
		return (int) Double.parseDouble(minutes) * Time.SEC_PER_MIN;
	}
}
