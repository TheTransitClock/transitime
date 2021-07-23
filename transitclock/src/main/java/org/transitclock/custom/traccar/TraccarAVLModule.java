/* 
 * This file is part of thetransitclock.org
 * 
 * thetransitclock.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * thetransitclock.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with thetransitclock.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitclock.custom.traccar;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.avl.AmigoCloudAvlModule;
import org.transitclock.avl.PollUrlAvlModule;
import org.transitclock.config.StringConfigValue;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.Location;
import org.transitclock.db.structs.AvlReport.AssignmentType;
import io.swagger.client.ApiClient;
import io.swagger.client.api.DefaultApi;
import io.swagger.client.model.Device;
import io.swagger.client.model.Position;
import io.swagger.client.model.User;

/**
 * @author Sean Óg Crudden This module integrates TheTransitClock with the API of a traccar
 *         server to get vehicle locations.
 * 
 *         See http://www.traccar.org
 * 
 *         It uses classes that where generated using the swagger file provided
 *         with traccar.
 *
 */
public class TraccarAVLModule extends PollUrlAvlModule {

	private User user = null;
	private DefaultApi api = null;
	

	private static StringConfigValue traccarEmail = new StringConfigValue("transitclock.avl.traccar.email", "admin",
			"This is the username for the traccar server api.");

	private static StringConfigValue traccarPassword = new StringConfigValue("transitclock.avl.traccar.password",
			"admin", "This is the password for the traccar server api");

	private static StringConfigValue traccarBaseUrl = new StringConfigValue("transitclock.avl.traccar.baseurl",
			"http://127.0.0.1:8082/api", "This is the url for the traccar server api.");

	private static StringConfigValue traccarSource = new StringConfigValue("transitclock.avl.traccar.source",
			"Traccar", "This is the value recorded in the source for the AVL Report.");

	private static final Logger logger = LoggerFactory.getLogger(TraccarAVLModule.class);

	public TraccarAVLModule(String agencyId) throws Throwable {
		super(agencyId);
		api = new DefaultApi();
		ApiClient client = new ApiClient();
		client.setBasePath(traccarBaseUrl.getValue());
		client.setUsername(traccarEmail.getValue());
		client.setPassword(traccarPassword.getValue());
		api.setApiClient(client);
		user = api.sessionPost(traccarEmail.getValue(), traccarPassword.getValue());
		user.getId();
		if (user != null)
			logger.debug("Traccar login succeeded.");
	}

	@Override
	protected void getAndProcessData() throws Exception {

		Collection<AvlReport> avlReportsReadIn = new ArrayList<AvlReport>();
		
		List<Device> devices = api.devicesGet(true, user.getId(), null, null);
		
		if (api != null && user != null) {
			List<Position> results = api.positionsGet(null, null, null, null);
			for (Position result : results) {											
				Device device=findDeviceById(devices, result.getDeviceId());
				
				AvlReport avlReport = null;
				
				// If have device details use name.
				if(device!=null && device.getUniqueId()!=null && !device.getUniqueId().isEmpty())
				{
					 avlReport = new AvlReport(device.getUniqueId().toString(), device.getName(),
							result.getDeviceTime().toDate().getTime(), result.getLatitude().doubleValue(),
							result.getLongitude().doubleValue(), result.getSpeed().floatValue(), result.getCourse().floatValue(), traccarSource.toString());
				}
				else
				{
					 avlReport = new AvlReport(result.getDeviceId().toString(),
						result.getDeviceTime().toDate().getTime(), result.getLatitude().doubleValue(),
						result.getLongitude().doubleValue(), result.getSpeed().floatValue(), result.getCourse().floatValue(), traccarSource.toString());
				}
				if(avlReport!=null)
					avlReportsReadIn.add(avlReport);
			}			
			forwardAvlReports(avlReportsReadIn);
		}
	}
	private Device findDeviceById(List<Device> devices, Integer id)
	{
		for(Device device:devices)
		{
			if(device.getId()==id)
				return device;
		}
		return null;
	}
	
	@Override
	protected Collection<AvlReport> processData(InputStream in) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}	
	
	protected void forwardAvlReports(Collection<AvlReport> avlReportsReadIn)
	{
		processAvlReports(avlReportsReadIn);
	}

}