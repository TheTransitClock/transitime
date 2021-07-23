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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.transitclock.utils.StringUtils;

/**
 * For creating the JSON data string used to power a Google Chart via AJAX.
 * <p>
 * One constructs a ChartJsonBuilder and then adds the columns using
 * addNumberColumn(), addTooltipColumn() or addIntervalColumn().
 * <p>
 * For each row one first calls newRow() to get a RowBuilder. Then one calls
 * RowBuilder.addRowElement() to add each element to the row. Then one calls
 * 
 * @author SkiBu Smith
 *
 */
public class ChartJsonBuilder {

	// Contains all the column data in JSON format
	private List<String> columnList = new ArrayList<String>();

	// Contains all the row data in JSON format
	private List<RowBuilder> rowList = new ArrayList<RowBuilder>();

	/**
	 * For building a row, which consists of multiple data elements.
	 */
	public static class RowBuilder {
		private List<String> rowElementsList = new ArrayList<String>();

		public void addRowElement(Object o) {
			if (o instanceof Double || o instanceof Float) {
				addRowElement(((Number) o).doubleValue());
			} else if (o instanceof Number) {
				addRowElement(((Number) o).longValue());
			} else if (o instanceof String) {
				addRowElement((String) o);
			}
		}
		
		/**
		 * Adds a row element with a string value. The string value is quoted.
		 * 
		 * @param value
		 *            The value of the element to be added to the row
		 */
		public void addRowElement(String value) {
			String escapedValue = StringEscapeUtils.escapeJson(value);
			String rowElement = "{\"v\": \"" + escapedValue + "\"}";
			rowElementsList.add(rowElement);
		}

		/**
		 * Adds a row element with a double value. Since it is a double it is
		 * not quoted. Outputs just one digit after the decimal point.
		 * 
		 * @param value
		 *            The value of the element to be added to the row
		 */
		public void addRowElement(double value) {
			String rowElement = "{\"v\": " + StringUtils.oneDigitFormat(value).replace(",", ".")
					+ "}";
			rowElementsList.add(rowElement);
		}

		/**
		 * Adds a row element with a long value. Since it is a numeric it is not
		 * quoted.
		 * 
		 * @param value
		 *            The value of the element to be added to the row
		 */
		public void addRowElement(long value) {
			String rowElement = "{\"v\": " + value + "}";
			rowElementsList.add(rowElement);
		}

		/**
		 * Adds a row element with a Number value. Since it is a numeric it is
		 * not quoted.
		 * 
		 * @param value
		 *            The value of the element to be added to the row
		 */
		public void addRowElement(Number value) {
			String rowElement = "{\"v\": " + value + "}";
			rowElementsList.add(rowElement);
		}

		/**
		 * For when need to add null element to indicate that don't have data
		 * for a cell. Useful for when there is now data for a row.
		 */
		public void addRowNullElement() {
			rowElementsList.add("{\"v\": null}");
		}

		/**
		 * Returns the JSON for this row
		 * 
		 * @return
		 */
		private String getJson() {
			// Start the JSON string
			StringBuilder sb = new StringBuilder();
			sb.append("  {\"c\": [");

			boolean first = true;
			for (String rowElement : rowElementsList) {
				if (first)
					first = false;
				else
					sb.append(",");

				sb.append(rowElement);
			}

			// Wrap up the row
			sb.append("] }");

			// Return the row
			return sb.toString();
		}
	}

	/********************** Member Functions **************************/

	/**
	 * Add a numeric column without a label
	 */
	public void addNumberColumn() {
		columnList.add("{\"type\": \"number\"}");
	}

	/**
	 * Add a numeric column with a label
	 * 
	 * @param label
	 */
	public void addNumberColumn(String label) {
		columnList.add("{\"type\": \"number\", \"label\":\"" + label + "\"}");
	}

	/**
	 * Add a string/varchar column without a label
	 */
	public void addStringColumn() {
		columnList.add("{\"type\": \"string\"}");
	}

	/**
	 * Add a string/varchar column with a label
	 * 
	 * @param label
	 */
	public void addStringColumn(String label) {
		columnList.add("{\"type\": \"string\", \"label\":\"" + label + "\"}");
	}

	/**
	 * Add a tooltip column
	 */
	public void addTooltipColumn() {
		columnList.add("{\"type\": \"string\", \"p\":{\"role\":\"tooltip\"} }");
	}

	/**
	 * Add a interval column
	 */
	public void addIntervalColumn() {
		columnList
				.add("{\"type\": \"string\", \"p\":{\"role\":\"interval\"} }");
	}

	/**
	 * For adding a new row of data. The returned RowBuilder needs to be
	 * populated by calling RowBuilder.addRowElement().
	 * 
	 * @return The RowBuilder for the row being added.
	 */
	public RowBuilder newRow() {
		RowBuilder rowBuilder = new RowBuilder();
		rowList.add(rowBuilder);
		return rowBuilder;
	}

	/**
	 * Once done building a JSON object then this method is used to return it as
	 * a string.
	 * 
	 * @return The complete JSON object
	 */
	public String getJson() {
		// Start the JSON string
		StringBuilder sb = new StringBuilder();
		sb.append("{");

		// Add the column data
		sb.append("\n \"cols\": [");
		boolean first = true;
		for (String column : columnList) {
			if (first)
				first = false;
			else
				sb.append(",");

			sb.append("\n  ").append(column);

		}
		sb.append("\n  ],");

		// Add the row data
		sb.append("\n \"rows\": [");
		first = true;
		for (RowBuilder row : rowList) {
			if (first)
				first = false;
			else
				sb.append(",");

			sb.append("\n  ").append(row.getJson());

		}
		sb.append("\n  ]");

		// Wrap up the JSON string
		sb.append("\n}");

		// Return the results as a string
		return sb.toString();
	}
}
