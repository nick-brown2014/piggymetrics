package com.piggymetrics.monitoring.controller;

import com.piggymetrics.monitoring.service.CircuitBreakerAggregator;
import com.piggymetrics.monitoring.service.CircuitBreakerAggregator.InstanceCircuitBreakers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CircuitBreakerController {

	private final CircuitBreakerAggregator aggregator;

	public CircuitBreakerController(CircuitBreakerAggregator aggregator) {
		this.aggregator = aggregator;
	}

	@GetMapping({"/", "/circuitbreakers"})
	public List<InstanceCircuitBreakers> circuitBreakers() {
		return aggregator.aggregate();
	}
}
