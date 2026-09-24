package com.piggymetrics.monitoring.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class CircuitBreakerAggregator {

	private static final Logger log = LoggerFactory.getLogger(CircuitBreakerAggregator.class);

	static final String ENDPOINT = "/actuator/circuitbreakers";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<>() {};

	private final DiscoveryClient discoveryClient;
	private final RestTemplate restTemplate;
	private final CircuitBreakerRegistry registry;

	public CircuitBreakerAggregator(DiscoveryClient discoveryClient, RestTemplate restTemplate,
									CircuitBreakerRegistry registry) {
		this.discoveryClient = discoveryClient;
		this.restTemplate = restTemplate;
		this.registry = registry;
	}

	public List<InstanceCircuitBreakers> aggregate() {
		List<InstanceCircuitBreakers> result = new ArrayList<>();
		for (String serviceId : discoveryClient.getServices()) {
			for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
				result.add(fetch(serviceId, instance));
			}
		}
		return result;
	}

	private InstanceCircuitBreakers fetch(String serviceId, ServiceInstance instance) {
		URI uri = instance.getUri().resolve(ENDPOINT);
		CircuitBreaker breaker = registry.circuitBreaker(serviceId);
		try {
			Map<String, Object> body = breaker.executeCallable(() ->
					restTemplate.exchange(uri, HttpMethod.GET, null, MAP_TYPE).getBody());
			return new InstanceCircuitBreakers(serviceId, instance.getInstanceId(), uri.toString(),
					body == null ? Collections.emptyMap() : body, null);
		} catch (Exception e) {
			log.debug("Unable to read circuit breakers from {}: {}", uri, e.getMessage());
			return new InstanceCircuitBreakers(serviceId, instance.getInstanceId(), uri.toString(),
					Collections.emptyMap(), e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public record InstanceCircuitBreakers(String serviceId, String instanceId, String source,
										  Map<String, Object> circuitBreakers, String error) {
	}
}
