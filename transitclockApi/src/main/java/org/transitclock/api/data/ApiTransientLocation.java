/*
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License (GPL) as published by the
 * Free Software Foundation, either version 3 of the License, or any later
 * version.
 * 
 * Transitime.org is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * Transitime.org . If not, see <http://www.gnu.org/licenses/>.
 */

package org.transitclock.api.data;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.transitclock.utils.ChinaGpsOffset;
import org.transitclock.utils.MathUtils;

/**
 * A simple latitude/longitude:
 * <p>
 * Note: this class marked as @XmlTransient so that ApiLocations are not part of
 * the domain model. This means that can't instantiate as an element. The reason
 * doing this is so that the subclass can set propOrder for all elements in the
 * subclass, including lat & lon. Explanation of this is in
 * http://blog.bdoughan.com/2011/06/ignoring-inheritance-with-xmltransient.html
 *
 * @author SkiBu Smith
 *
 */
@XmlTransient
public class ApiTransientLocation implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1997973510120089077L;

	@XmlAttribute
	private double lat;

	@XmlAttribute
	private double lon;

	/********************** Member Functions **************************/

	/**
	 * Need a no-arg constructor for Jersey. Otherwise get really obtuse
	 * "MessageBodyWriter not found for media type=application/json" exception.
	 */
	protected ApiTransientLocation() {
	}

	public ApiTransientLocation(double lat, double lon) {
		// If location is in China (approximately) then adjust lat & lon so
		// that will be displayed properly on map.
		ChinaGpsOffset.LatLon latLon = ChinaGpsOffset.transform(lat, lon);

		// Output only 5 digits past decimal point
		this.lat = MathUtils.round(latLon.getLat(), 5);
		// Output only 5 digits past decimal point
		this.lon = MathUtils.round(latLon.getLon(), 5);
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}
	
}
