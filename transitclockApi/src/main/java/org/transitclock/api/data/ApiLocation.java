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

import org.transitclock.db.structs.Location;
import org.transitclock.ipc.data.IpcLocation;

/**
 * A simple latitude/longitude.
 * <p>
 * This is a non-transient implementation of ApiTransientLocation. By not being
 * transient this class can be used to output a location as an element (as
 * opposed to an attribute). By inheriting from ApiTransientLocation don't need
 * to duplicate any code.
 *
 * @author SkiBu Smith
 *
 */
public class ApiLocation extends ApiTransientLocation {

	/********************** Member Functions **************************/

	/**
	 * Need a no-arg constructor for Jersey. Otherwise get really obtuse
	 * "MessageBodyWriter not found for media type=application/json" exception.
	 */
	protected ApiLocation() {
	}

	public ApiLocation(double lat, double lon) {
		super(lat, lon);
	}

	public ApiLocation(Location loc) {
		super(loc.getLat(), loc.getLon());
	}
	public ApiLocation(IpcLocation loc) {
		super(loc.getLat(), loc.getLon());
	}
}
