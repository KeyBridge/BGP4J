/**
 * 
 */
package org.bgp4j.extension.snmp4j.web;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.StringUtils;
import org.bgp4j.extension.snmp4j.service.EasyboxInstance;
import org.bgp4j.extension.snmp4j.service.EasyboxService;

/**
 * @author rainer
 *
 */
@Path("/easybox")
@ApplicationScoped
@Produces("application/*+json")
public class EasyboxWebApplication {

	private EasyboxService service;

	/**
	 * @param service the service to set
	 */
	public void setService(EasyboxService service) {
		this.service = service;
	}
	
	@GET
	@Path("/list")
	public ListInstancesResult listInstances() {
		ListInstancesResult result = new ListInstancesResult();
		
		for(EasyboxInstance instance : service.getInstances())
			result.addName(instance.getName());
		
		return result;
	}

	@GET
	@Path("/status/{name}")
	public StatusResult status(@PathParam("name") String name) {
		StatusResult result = null;

		for(EasyboxInstance instance : service.getInstances()) {
			if(StringUtils.equals(name, instance.getName())) {
				long uptime = instance.getUptime();
				UptimeResult up = null;
				
				result = new StatusResult();
				
				if(uptime > 0) {
					up = new UptimeResult();
					up.setStamp(uptime);
					
					result.setRunning(true);
					result.setUptime(up);
				}
				
				break;
			}
		}
		
		if(result == null)
			throw new IllegalArgumentException();
		
		return result;
	}
}
