/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.hendrix.api.rest;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.symcpe.hendrix.api.ApplicationManager;
import io.symcpe.hendrix.api.storage.Point;

/**
 * To report rule performance characteristics
 * 
 * @author ambud_sharma
 */
@Path("/perf")
public class PerfMonEndpoint {

	private ApplicationManager am;

	public PerfMonEndpoint(ApplicationManager applicationManager) {
		this.am = applicationManager;
	}

	@Path("{tenantId}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public Map<String, List<Point>> getRulePerformance(@PathParam("tenantId") String tenantId) {
		return am.getPerfMonitor().getRuleEfficiencySeries(tenantId);
	}

}
