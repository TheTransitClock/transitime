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

package org.transitclock.api;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * Declares that all classes in package org.transitclock.api.rootResources will be
 * searched for being a root-resource class with methods annotated with @Path
 * indicating that it handles requests.
 *
 * Uses "v1" for the @ApplicationPath to specify the version of the feed.
 * 
 * @author SkiBu Smith
 *
 */
@ApplicationPath("v1")
public class ApiApplication extends ResourceConfig {

	public ApiApplication() {
		// Register all root-resource classes in package that handle @Path
		// requests
		packages("org.transitclock.api.rootResources");
		//packages("org.transitclock.api.data.customwriter");
		
	}
}