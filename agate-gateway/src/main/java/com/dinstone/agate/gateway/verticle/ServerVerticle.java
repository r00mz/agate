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
package com.dinstone.agate.gateway.verticle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinstone.agate.gateway.context.ApplicationContext;
import com.dinstone.agate.gateway.deploy.Deployer;
import com.dinstone.agate.gateway.handler.AccessLogHandler;
import com.dinstone.agate.gateway.handler.RateLimitHandler;
import com.dinstone.agate.gateway.handler.RouteProxyHandler;
import com.dinstone.agate.gateway.options.ApiOptions;
import com.dinstone.agate.gateway.options.AppOptions;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

/**
 * the server is APP runtime, it proxy API to backend service.
 * 
 * @author dinstone
 *
 */
public class ServerVerticle extends AbstractVerticle implements Deployer {

	private static final Logger LOG = LoggerFactory.getLogger(ServerVerticle.class);

	private Map<String, Route> apiRouteMap = new ConcurrentHashMap<>();

	private ApplicationContext applicationContext;

	private HttpServerOptions serverOptions;

	private HttpClientOptions clientOptions;

	private HttpClient httpClient;

	private Router mainRouter;

	private String appName;

	public ServerVerticle(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);

		AppOptions appOptions = new AppOptions(config());
		appName = appOptions.getAppName();

		serverOptions = appOptions.getServerOptions();
		if (serverOptions == null) {
			serverOptions = new HttpServerOptions();
		}

		clientOptions = appOptions.getClientOptions();
		if (clientOptions == null) {
			clientOptions = new HttpClientOptions();
		}
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		httpClient = vertx.createHttpClient(clientOptions);

		mainRouter = createHttpServerRouter();
		vertx.createHttpServer(serverOptions).requestHandler(mainRouter).listen(ar -> {
			if (ar.succeeded()) {
				// register api deployer
				registApiDeployer();

				LOG.info("server verticle start success, {}/{}:{}", appName, serverOptions.getHost(),
						serverOptions.getPort());
				startPromise.complete();
			} else {
				LOG.error("server verticle start failed, {}/{}:{}", appName, serverOptions.getHost(),
						serverOptions.getPort());
				startPromise.fail(ar.cause());
			}
		});

	}

	@Override
	public void stop() throws Exception {
		removeApiDeployer();

		LOG.info("server verticle stop success, {}/{}:{}", appName, serverOptions.getHost(), serverOptions.getPort());
	}

	@Override
	public Future<Void> deployApi(ApiOptions api) {
		Promise<Void> promise = Promise.promise();

		context.runOnContext(ar -> {
			try {
				if (apiRouteMap.containsKey(api.getApiName())) {
					promise.complete();
					return;
				}

				// create sub router for api
				Router subRouter = Router.router(vertx);

				// create api route
				Route route = null;
				if (pathIsRegex(api.getPath())) {
					route = subRouter.routeWithRegex(api.getPath());
				} else {
					route = subRouter.route(api.getPath());
				}
				// method
				String method = api.getMethod();
				if (method != null && method.length() > 0) {
					route.method(HttpMethod.valueOf(method.toUpperCase()));
				}
				// consumes
				if (api.getConsumes() != null) {
					for (String consume : api.getConsumes()) {
						route.consumes(consume);
					}
				}
				// produces
				if (api.getProduces() != null) {
					for (String produce : api.getProduces()) {
						route.produces(produce);
					}
				}
				// rate limit hanlder
				if (api.getRateLimit() != null) {
					route.handler(new RateLimitHandler(api));
				}
				// route proxy handler
				route.handler(new RouteProxyHandler(api, false, httpClient));
				// mount sub router to main
				Route mountRoute = mountRoute(api.getPrefix(), subRouter);

				// cache api route
				apiRouteMap.put(api.getApiName(), mountRoute);

				promise.complete();
			} catch (Exception e) {
				promise.fail(e);
			}
		});

		return promise.future();
	}

	public boolean pathIsRegex(String routePath) {
		return routePath.indexOf("(") > 0 || routePath.indexOf("?<") > 0;
	}

	private Route mountRoute(String path, Router subRouter) {
		return mainRouter.route(path).subRouter(subRouter);
	}

	@Override
	public Future<Void> removeApi(ApiOptions api) {
		Promise<Void> promise = Promise.promise();

		context.runOnContext(ar -> {
			try {
				Route route = apiRouteMap.remove(api.getApiName());
				if (route != null) {
					route.disable().remove();
				}
				promise.complete();
			} catch (Exception e) {
				promise.fail(e);
			}
		});

		return promise.future();
	}

	private void registApiDeployer() {
		applicationContext.getDeployment().get(appName).regist(this);
	}

	private void removeApiDeployer() {
		applicationContext.getDeployment().get(appName).remove(this);
	}

	private SessionHandler sessionHandler() {
		return SessionHandler.create(LocalSessionStore.create(vertx, LocalSessionStore.DEFAULT_SESSION_MAP_NAME, 60000))
				.setNagHttps(false);
	}

	private Router createHttpServerRouter() {
		Router mainRouter = Router.router(vertx);
		mainRouter.route().handler(new AccessLogHandler()).handler(sessionHandler());
		return mainRouter;
	}

}