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
package org.transitclock.db.hibernate;

import org.hibernate.cfg.Configuration;
import org.transitclock.db.structs.ActiveRevisions;
import org.transitclock.db.structs.Agency;
import org.transitclock.db.structs.Arrival;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Block;
import org.transitclock.db.structs.Calendar;
import org.transitclock.db.structs.CalendarDate;
import org.transitclock.db.structs.ConfigRevision;
import org.transitclock.db.structs.DbTest;
import org.transitclock.db.structs.Departure;
import org.transitclock.db.structs.Diversion;
import org.transitclock.db.structs.FareAttribute;
import org.transitclock.db.structs.FareRule;
import org.transitclock.db.structs.Frequency;
import org.transitclock.db.structs.Headway;
import org.transitclock.db.structs.HoldingTime;
import org.transitclock.db.structs.Match;
import org.transitclock.db.structs.MeasuredArrivalTime;
import org.transitclock.db.structs.MonitoringEvent;
import org.transitclock.db.structs.Prediction;
import org.transitclock.db.structs.PredictionAccuracy;
import org.transitclock.db.structs.PredictionEvent;
import org.transitclock.db.structs.PredictionForStopPath;
import org.transitclock.db.structs.Route;
import org.transitclock.db.structs.Stop;
import org.transitclock.db.structs.StopPath;
import org.transitclock.db.structs.Transfer;
import org.transitclock.db.structs.TravelTimesForStopPath;
import org.transitclock.db.structs.TravelTimesForTrip;
import org.transitclock.db.structs.Trip;
import org.transitclock.db.structs.TripPattern;
import org.transitclock.db.structs.VehicleConfig;
import org.transitclock.db.structs.VehicleEvent;
import org.transitclock.db.structs.VehicleState;
import org.transitclock.db.webstructs.ApiKey;
import org.transitclock.db.webstructs.WebAgency;

/**
 * 
 * Yes, this is a nuisance. If Hibernate Session class is used instead of the
 * JPA EntityManager class then all annotated classes have to be explicitly
 * added to the Configuration object. Don't want to get into using the JPA
 * EntityManager class for now (just trying to learn Hibernate based on books
 * such as Harnessing Hibernate). So need to do this.
 * 
 * These annotated classes could also be listed in the hibernate.cfg.xml config
 * file but that would be difficult to maintain. Each application might already
 * have a modified hibernate config file. Don't want to have to have each app
 * modify their config file when new annotated classes are added. Therefore it
 * is best to configure these classes programmatically.
 * 
 * @author SkiBu Smith
 * 
 */
public class AnnotatedClassesList {

	// List here all the annotated classes that can be stored in the db
	private static Class<?>[] classList = new Class[] {
		ActiveRevisions.class,
		Agency.class,
		Arrival.class,
		AvlReport.class,
		Block.class,
		Calendar.class,
		CalendarDate.class,
		ConfigRevision.class,
		DbTest.class,
		Prediction.class,
		Departure.class,
		FareAttribute.class,
		FareRule.class,
		Frequency.class,
		Match.class,
		MeasuredArrivalTime.class,
		MonitoringEvent.class,
		PredictionAccuracy.class,
		Route.class,
		Stop.class,
		StopPath.class,
		Transfer.class,
		TravelTimesForStopPath.class,
		PredictionForStopPath.class,		
		TravelTimesForTrip.class,
		Trip.class,
		TripPattern.class,
		VehicleEvent.class,
		PredictionEvent.class,
		VehicleConfig.class,
		VehicleState.class,
		HoldingTime.class,
		Headway.class,
		Diversion.class,
		// For website
		ApiKey.class,
		WebAgency.class,
	};
	
	/********************** Member Functions **************************/
	
	/**
	 * Adds the classes listed within this method to the Hibernate
	 * configuration. Needed so that the class can be used with
	 * Hibernate.
	 * 
	 * @param config
	 */
	public static void addAnnotatedClasses(Configuration config) {
		// Add all the annotated classes to the config
		for (@SuppressWarnings("rawtypes") Class c : classList) {
			HibernateUtils.logger.debug("Adding to Hibernate config the annotated class {}", 
					c.getName());
			config.addAnnotatedClass(c);
		}
	}
}
