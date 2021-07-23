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
package org.transitclock.core;

import java.io.Serializable;

import org.transitclock.configData.CoreConfig;
import org.transitclock.utils.Time;

/**
 * For keeping track of how far off a vehicle is from the expected time. For
 * schedule adherence and for determining temporal matches. Positive means ahead
 * of and traveling faster than expected.
 * <p>
 * Class made serializable so that schedule adherence data can be made available
 * through RMI calls.
 * 
 * @author SkiBu Smith
 * 
 */
public class TemporalDifference implements Serializable {

	// Positive means ahead of and traveling faster than expected. Negative 
	// means behind and traveling slower than expected.
	private int temporalDifferenceMsec;

	private static final long serialVersionUID = 3306638473792296250L;

	/********************** Member Functions **************************/

	/**
	 * @param temporalDifferenceMsec
	 *            Positive means vehicle is ahead of where expected, negative
	 *            means behind.
	 */
	public TemporalDifference(int temporalDifferenceMsec) {
		this.temporalDifferenceMsec = temporalDifferenceMsec;
	}

	/**
	 * Often working with longs for time so it is convenient to have a
	 * constructor that will accept a long as the temporal difference.
	 * 
	 * @param temporalDifferenceMsec
	 *            Positive means vehicle is ahead of where expected, negative
	 *            means behind.
	 */
	public TemporalDifference(long temporalDifferenceMsec) {
		this.temporalDifferenceMsec = (int) temporalDifferenceMsec;
	}

	/**
	 * Adds the latenessMsec to this. If the vehicle is early then the
	 * latenessMsec is simply subtracted. But if vehicle is late then
	 * latenessMsec is added, but only after dividing it by the early to late
	 * ratio since early vehicles are considered worse than late vehicles.
	 * 
	 * @param latenessMsec
	 *            How much lateness to add.
	 */
	public void addTime(int latenessMsec) {
		if (temporalDifferenceMsec < 0)
			temporalDifferenceMsec -= latenessMsec;
		else
			temporalDifferenceMsec += latenessMsec
					/ CoreConfig.getEarlyToLateRatio();
	}
	
	/**
	 * Returns true if the temporal difference is within the bounds specified by
	 * CoreConfig.getAllowableEarlySeconds() and
	 * CoreConfig.getAllowableLateSeconds().
	 * 
	 * @return true if within bounds
	 */
	public boolean isWithinBounds() {
		return temporalDifferenceMsec < 
					CoreConfig.getAllowableEarlySeconds() * Time.MS_PER_SEC 
				&& -temporalDifferenceMsec < 
					CoreConfig.getAllowableLateSeconds() * Time.MS_PER_SEC;
	}

	/**
	 * Returns true if the temporal difference is within the bounds specified by
	 * CoreConfig.getAllowableEarlySecondsForInitialMatching() and
	 * CoreConfig.getAllowableLateSecondsForInitialMatching(). For use when
	 * initially matching a vehicle. Need to be more restrictive than for other
	 * matching since the initial matching is more difficult. Need to only match
	 * if it is a reasonable match since vehicles do really peculiar things.
	 * 
	 * @return true if within bounds
	 */
	public boolean isWithinBoundsForInitialMatching() {
		return temporalDifferenceMsec < CoreConfig
				.getAllowableEarlySecondsForInitialMatching() * Time.MS_PER_SEC
				&& -temporalDifferenceMsec < CoreConfig
						.getAllowableLateSecondsForInitialMatching()
						* Time.MS_PER_SEC;
	}

	/**
	 * Returns whether the temporal difference is within the specified bounds.
	 * 
	 * @param allowableEarlySeconds
	 * @param allowableLateSeconds
	 * @return
	 */
	public boolean isWithinBounds(int allowableEarlySeconds,
			int allowableLateSeconds) {
		// Return whether it is not beyond the bounds
		return !isEarlierThan(allowableEarlySeconds) 
				&& !isLaterThan(allowableLateSeconds);
	}
	
	/**
	 * Returns true if vehicle is earlier than allowableEarlySeconds
	 * 
	 * @param allowableEarlySeconds
	 * @return
	 */
	public boolean isEarlierThan(int allowableEarlySeconds) {
		// Note: casting allowable seconds to a long since if use MAX_INTEGER
		// and then multiple by Time.MS_PER_SEC could end up exceeding what
		// an integer can handle.
		return temporalDifferenceMsec > 
				(long) allowableEarlySeconds * Time.MS_PER_SEC;
	}
	
	/**
	 * Returns true if vehicle is later than allowableLateSeconds
	 * 
	 * @param allowableLateSeconds
	 * @return
	 */
	public boolean isLaterThan(int allowableLateSeconds) {
		// Note: casting allowable seconds to a long since if use MAX_INTEGER
		// and then multiple by Time.MS_PER_SEC could end up exceeding what
		// an integer can handle.
		return -temporalDifferenceMsec > 
				(long) allowableLateSeconds * Time.MS_PER_SEC;
	}
	
	/**
	 * Returns an easily comparable positive value for the temporal difference
	 * that takes into account that being early is far worse and less likely
	 * than being late.
	 * 
	 * @return
	 */
	private int temporalDifferenceAbsoluteValue() {
		// Early or late?
		if (temporalDifferenceMsec > 0)
			// Early, so multiply by early to late ratio
			return (int) Math.round(temporalDifferenceMsec * 
					CoreConfig.getEarlyToLateRatio());
		else 
			// Late or on time, so return positive version of temporal different
			return -temporalDifferenceMsec;
	}
	
	/**
	 * If early then returns positive value indicating how many msec early. If
	 * late then returns negative value indicating how many msec late.
	 * 
	 * @return
	 */
	public int early() {
		return temporalDifferenceMsec;
	}
	
	/**
	 * Returns true if the temporal difference for this object is better than
	 * that for the TemporalDifference parameter other. If other param is null
	 * then will return true.
	 * 
	 * @param other
	 *            TemporalDifference to be compared to. Can be null.
	 * @return
	 */
	public boolean betterThan(TemporalDifference other) {
		// If other parameter not specified then temporal difference for
		// this object should be considered true.
		if (other == null)
			return true;
		
		// Compare using absolute values that are adjusted by the
		// getEarlyToLateRatio.
		return this.temporalDifferenceAbsoluteValue() < 
				other.temporalDifferenceAbsoluteValue();
	}
	
	/**
	 * Returns if the temporal difference for this object is better than or
	 * equal to that for the TemporalDifference parameter other.
	 * 
	 * @param other
	 *            TemporalDifference to be compared to
	 * @return
	 */
	public boolean betterThanOrEqualTo(TemporalDifference other) {
		// If other parameter not specified then temporal difference for
		// this object should be considered true.
		if (other == null)
			return true;
		
		// Compare using absolute values that are adjusted by the
		// getEarlyToLateRatio.
		return this.temporalDifferenceAbsoluteValue() <= 
				other.temporalDifferenceAbsoluteValue();
	}
	
	/**
	 * Returns the temporalDifferenceMsec in msec. Positive means vehicle
	 * is early and negative means that vehicle is late.
	 * @return
	 */
	public int getTemporalDifference() {
		return temporalDifferenceMsec;
	}

	@Override
	public String toString() {
		String str = Time.elapsedTimeStr(temporalDifferenceMsec);
		
		// Add early/ontime/late info
		if (temporalDifferenceMsec > 0)
			str += " (wcześniej)";
		else if (temporalDifferenceMsec == 0)
			str += " (na czas)";
		else
			str += " (później)";
		
		// Return the results
		return str;
	}
	
}
