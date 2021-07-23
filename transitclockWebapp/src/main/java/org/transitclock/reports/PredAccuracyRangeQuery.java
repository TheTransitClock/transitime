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

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.reports.ChartJsonBuilder.RowBuilder;
import org.transitclock.utils.StringUtils;
import org.transitclock.utils.Time;
import java.util.ResourceBundle;
import java.util.Locale;

/**
 * For generating the JSON data for a Google chart for showing percent of
 * predictions that lie between an error range.
 *
 * @author SkiBu Smith
 *
 */
public class PredAccuracyRangeQuery extends PredictionAccuracyQuery {

	private static final Logger logger = LoggerFactory
			.getLogger(PredAccuracyRangeQuery.class);
	
	ResourceBundle labels = ResourceBundle.getBundle("org.transitclock.i18n.text", Locale.getDefault());

	/********************** Member Functions **************************/

	/**
	 * Creates connection to database.
	 * 
	 * @param dbType
	 * @param dbHost
	 * @param dbName
	 * @param dbUserName
	 * @param dbPassword
	 * @throws SQLException
	 */
	public PredAccuracyRangeQuery(String dbType, String dbHost, String dbName,
			String dbUserName, String dbPassword) throws SQLException {
		super(dbType, dbHost, dbName, dbUserName, dbPassword);
	}
	
	/**
	 * Creates connection to database specified by the agencyId.
	 * 
	 * @param agencyId
	 * @throws SQLException
	 */
	public PredAccuracyRangeQuery(String agencyId) throws SQLException {
		super(agencyId);
	}


	/**
	 * Adds the column definition in JSON string format so that chart the data
	 * using Google charts. The column definition describes the contents of each
	 * column but doesn't actually contain the data itself.
	 * 
	 * @param builder
	 * @param maxEarlySec
	 * @param maxLateSec
	 */
	private void addCols(ChartJsonBuilder builder, int maxEarlySec,
			int maxLateSec) {
		if (map.isEmpty()) {
			logger.error("Called PredAccuracyStackQuery.addCols() but there "
					+ "is no data in the map.");
			return;
		}

		builder.addNumberColumn();
		builder.addNumberColumn(labels.getString("EarlierThanPredicted_MoreThan") + " " + maxEarlySec + " " + labels.getString("SecsEarly_"));
		builder.addTooltipColumn();
		builder.addNumberColumn(labels.getString("WithinBounds_") + " " + maxEarlySec + " " + labels.getString("SecsEarlyTo") + " "
				+ maxLateSec + " " + labels.getString("SecsLate_"));
		builder.addTooltipColumn();
		builder.addNumberColumn(labels.getString("LaterThanPredicted_MoreThan") + maxLateSec + " " + labels.getString("SecsLate_"));
		builder.addTooltipColumn();
	}
	
	/**
	 * Adds the row definition in JSON string format so that chart the data
	 * using Google charts. The row definition contains the actual data.
	 * 
	 * @param builder
	 * @param maxEarlySec
	 * @param maxLateSec
	 */
	private void addRows(ChartJsonBuilder builder, int maxEarlySec,
			int maxLateSec) {
		if (map.isEmpty()) {
			logger.error("Called PredAccuracyStackQuery.getCols() but there "
					+ "is no data in the map.");
			return;
		}

		// Only dealing with a single source so get data for that source
		List<List<Integer>> dataForSource = null;
		for (String source : map.keySet()) {
			dataForSource = map.get(source);
		}

		// For each prediction length bucket...
		for (int predBucketIdx = 0; predBucketIdx <= MAX_PRED_LENGTH
				/ PREDICTION_LENGTH_BUCKET_SIZE; ++predBucketIdx) {
			// Prediction length in seconds
			double predBucketSecs = predBucketIdx
					* PREDICTION_LENGTH_BUCKET_SIZE / 60.0;

			List<Integer> listForPredBucket = null;
			if (dataForSource != null && dataForSource.size() > predBucketIdx) {
				listForPredBucket = dataForSource.get(predBucketIdx);

				// For this prediction bucket track whether prediction below
				// min,
				// between min and max, and above max.
				int tooEarly = 0, ok = 0, tooLate = 0;
				for (int accuracyInSecs : listForPredBucket) {
					if (accuracyInSecs < -maxEarlySec)
						++tooEarly;
					else if (accuracyInSecs < maxLateSec)
						++ok;
					else
						++tooLate;
				}

				// If no data for this prediction bucket then continue to next
				// one
				int numPreds = listForPredBucket.size();
				if (numPreds == 0)
					continue;

				double tooEarlyPercentage = 100.0 * tooEarly / numPreds;
				double okPercentage = 100.0 * ok / numPreds;
				double tooLatePercentage = 100.0 * tooLate / numPreds;

				RowBuilder rowBuilder = builder.newRow();
				rowBuilder.addRowElement(predBucketSecs);

				rowBuilder.addRowElement(tooEarlyPercentage);
				
				
				ResourceBundle labels = ResourceBundle.getBundle("org.transitclock.i18n.text", Locale.getDefault());

				rowBuilder.addRowElement(labels.getString("EarlierThanPredicted") + ": " + tooEarly + " " + labels.getString("Points") + ", "
						+ StringUtils.oneDigitFormat(tooEarlyPercentage) + "%");

				rowBuilder.addRowElement(okPercentage);
				rowBuilder.addRowElement(labels.getString("WithinBounds") + ": " + ok + " " + labels.getString("Points") + ", "
						+ StringUtils.oneDigitFormat(okPercentage) + "%");

				rowBuilder.addRowElement(tooLatePercentage);
				rowBuilder.addRowElement(labels.getString("TooLate") + ": " + tooLate + " " + labels.getString("Points") + ", "
				    + StringUtils.oneDigitFormat(tooLatePercentage) + "%");
				rowBuilder.addRowElement(labels.getString("LaterThanPredicted") + ": " + tooLate + " " + labels.getString("Points") + ", "
						+ StringUtils.oneDigitFormat(tooLatePercentage) + "%");
			}
		}
	}

	/**
	 * Performs the query and returns the data in an JSON string so that it can
	 * be used for a chart.
	 *
	 * @param beginDateStr
	 *            Begin date for date range of data to use.
	 * @param endDateStr
	 *            End date for date range of data to use. Since want to include
	 *            data for the end date, 1 day is added to the end date for the
	 *            query.
	 * @param beginTimeStr
	 *            For specifying time of day between the begin and end date to
	 *            use data for. Can thereby specify a date range of a week but
	 *            then just look at data for particular time of day, such as 7am
	 *            to 9am, for those days. Set to null or empty string to use
	 *            data for entire day.
	 * @param numDays
	 *            How long query should be run for.
	 * @param routeIds
	 *            Specifies which routes to do the query for. Can be null for
	 *            all routes or an array of route IDs.
	 * @param predSource
	 *            The source of the predictions. Can be null or "" (for all),
	 *            "Transitime", or "Other"
	 * @param predType
	 *            Whether predictions are affected by wait stop. Can be "" (for
	 *            all), "AffectedByWaitStop", or "NotAffectedByWaitStop".
	 * @param maxEarlySec
	 *            How early in msec a prediction is allowed to be. Should be a
	 *            positive value.
	 * @param maxLateSec
	 *            How late a in msec a prediction is allowed to be. Should be a
	 *            positive value.
	 * @return the full JSON string contain both cols and rows info, or null if
	 *         no data returned from query
	 * @throws SQLException
	 * @throws ParseException
	 */
	public String getJson(String beginDateStr, String numDays,
			String beginTimeStr, String endTimeStr, String routeIds[],
			String predSource, String predType, int maxEarlySec, int maxLateSec)
			throws SQLException, ParseException {
		// Actually perform the query
		doQuery(beginDateStr, numDays, beginTimeStr, endTimeStr, routeIds,
				predSource, predType);

		// If query returned no data then simply return null so that
		// can easily see that there is a problem
		if (map.isEmpty()) {
			return null;
		}

		ChartJsonBuilder builder = new ChartJsonBuilder();
		addCols(builder, maxEarlySec, maxLateSec);
		addRows(builder, maxEarlySec, maxLateSec);

		String jsonString = builder.getJson();
		return jsonString;
	}

	/**
	 * For debugging
	 * 
	 * @param args
	 */
	public static void main(String args[]) {

		String beginDate = "11-25-2014";
		String beginTime = null;
		String endTime = null;
		String numDays = "1";
		String routeIds[] = { "CR-Fairmount" };

		String source = "Transitime";
			

		String dbType = "postgresql";// "mysql";
		String dbHost = "192.168.99.100";// "localhost";
		String dbName = "GOHART";
		String dbUserName = "postgres";// "root";
		String dbPassword = "transitime";

		try {
			PredAccuracyRangeQuery query = new PredAccuracyRangeQuery(dbType,
					dbHost, dbName, dbUserName, dbPassword);
			String jsonString = query.getJson(beginDate, numDays, beginTime,
					endTime, routeIds, source, null, -60 * Time.MS_PER_SEC,
					3 * Time.MS_PER_SEC);
			System.out.println(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
