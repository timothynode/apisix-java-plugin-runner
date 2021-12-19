/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.apisix.plugin.runner.handler;

import com.google.common.cache.Cache;
import io.github.api7.A6.Err.Code;
import org.apache.apisix.plugin.runner.*;
import org.apache.apisix.plugin.runner.filter.PluginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.*;
import reactor.netty.NettyOutbound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Configuration
public class A6HandlerConfiguration {
    private final Logger logger = LoggerFactory.getLogger(A6HandlerConfiguration.class);

    @Bean
    public A6ConfigHandler createConfigHandler(Cache<Long, A6Conf> cache, ObjectProvider<PluginFilter> beanProvider) {
        List<PluginFilter> pluginFilterList = beanProvider.orderedStream().collect(Collectors.toList());
        Map<String, PluginFilter> filterMap = new HashMap<>();
        for (PluginFilter filter : pluginFilterList) {
            filterMap.put(filter.name(), filter);
        }
        return new A6ConfigHandler(cache, filterMap);
    }

    @Bean
    public A6HttpCallHandler createHttpHandler(Cache<Long, A6Conf> cache) {
        return new A6HttpCallHandler(cache);
    }

    @Bean
    public Dispatcher createDispatcher(A6ConfigHandler configHandler, A6HttpCallHandler httpCallHandler) {
        ConcurrentHashMap<Long, Sinks.One<A6ExtraResponse>> sinksMap = new ConcurrentHashMap<>();
        return new Dispatcher() {
            @Override
            public Mono<A6Response> dispatch(A6Request request, NettyOutbound outbound) {
                A6Response response;
                switch (request.getType()) {
                    case 0:
                        response = new A6ErrResponse(((A6ErrRequest) request).getCode());
                        return Mono.just(response);
                    case 1:
                        long confToken = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
                        response = new A6ConfigResponse(confToken);
                        configHandler.handle(request, response);
                        return Mono.just(response);
                    case 2:
                        ((HttpRequest) request).setDispatcher(this);
                        response = new HttpResponse(((HttpRequest) request).getRequestId());
                        httpCallHandler.handle(request, response);
                        return Mono.just(response);
                    case 3:
                        Sinks.One<A6ExtraResponse> a6ExtraResponseOne = sinksMap.get(1L);
                        //request to Resp
                        a6ExtraResponseOne.emitValue(new A6ExtraResponse(), (signalType, emitResult) -> false);
                        return Mono.just(new A6ExtraResponse());
                    default:
                        logger.warn("can not dispatch type: {}", request.getType());
                        response = new A6ErrResponse(Code.SERVICE_UNAVAILABLE);
                        return Mono.just(response);
                }
            }

            @Override
            public Mono<A6ExtraResponse> subscribe(long confToken) {
                Sinks.One<A6ExtraResponse> one = Sinks.one();
                sinksMap.put(confToken, one);

                return one.asMono();
            }
        };

    }
}
