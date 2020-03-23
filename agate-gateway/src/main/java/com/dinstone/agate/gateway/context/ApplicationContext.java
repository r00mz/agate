/*
 * Copyright (C) ${year} dinstone<dinstone@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.dinstone.agate.gateway.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinstone.agate.gateway.deploy.Deployment;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.ConsulClientOptions;

public class ApplicationContext {

	private static final Logger LOG = LoggerFactory.getLogger(ApplicationContext.class);

	private static final String DEFAULT_CLUSTER = "default";

	private JsonObject config;

	private String clusterId;

	private Deployment deployment;

	private ConsulClientOptions consulOptions;

	public ApplicationContext(JsonObject config) throws Exception {
		this.config = config;

		init();
	}

	private void init() throws Exception {
		LOG.debug("init application context start");

		// cluster id
		clusterId = config.getString("cluster", DEFAULT_CLUSTER);

		// consul options
		JsonObject consulJson = config.getJsonObject("consul");
		if (consulJson != null) {
			consulOptions = new ConsulClientOptions(consulJson);
		} else {
			consulOptions = new ConsulClientOptions();
		}

		// deployment
		deployment = new Deployment(clusterId);

		LOG.debug("init application context ended");
	}

	public void destroy() {
		deployment.destroy();
	}

	public JsonObject getConfig() {
		return config;
	}

	public Deployment getDeployment() {
		return deployment;
	}

	public String getClusterId() {
		return clusterId;
	}

	public ConsulClientOptions getConsulOptions() {
		return consulOptions;
	}

}