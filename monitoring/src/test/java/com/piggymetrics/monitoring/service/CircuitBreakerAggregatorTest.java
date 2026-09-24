package com.piggymetrics.monitoring.service;

import com.piggymetrics.monitoring.service.CircuitBreakerAggregator.InstanceCircuitBreakers;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
public class CircuitBreakerAggregatorTest {

	@Mock
	private DiscoveryClient discoveryClient;

	private MockRestServiceServer server;
	private CircuitBreakerAggregator aggregator;

	@BeforeEach
	public void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		aggregator = new CircuitBreakerAggregator(discoveryClient, restTemplate, CircuitBreakerRegistry.ofDefaults());
	}

	@Test
	public void shouldCollectCircuitBreakersFromEveryInstance() {
		when(discoveryClient.getServices()).thenReturn(List.of("statistics-service"));
		when(discoveryClient.getInstances("statistics-service")).thenReturn(List.of(
				new DefaultServiceInstance("stats-1", "statistics-service", "stats-1", 7000, false)));

		server.expect(requestTo("http://stats-1:7000/actuator/circuitbreakers"))
				.andRespond(withSuccess("{\"circuitBreakers\":{\"ExchangeRatesClient\":{\"state\":\"CLOSED\"}}}",
						MediaType.APPLICATION_JSON));

		List<InstanceCircuitBreakers> result = aggregator.aggregate();

		assertThat(result).hasSize(1);
		InstanceCircuitBreakers entry = result.get(0);
		assertThat(entry.serviceId()).isEqualTo("statistics-service");
		assertThat(entry.instanceId()).isEqualTo("stats-1");
		assertThat(entry.error()).isNull();
		assertThat(entry.circuitBreakers()).containsKey("circuitBreakers");
		Map<?, ?> breakers = (Map<?, ?>) entry.circuitBreakers().get("circuitBreakers");
		assertThat(breakers.containsKey("ExchangeRatesClient")).isTrue();
		server.verify();
	}

	@Test
	public void shouldReportErrorInsteadOfFailingWhenInstanceIsUnreachable() {
		when(discoveryClient.getServices()).thenReturn(List.of("account-service"));
		when(discoveryClient.getInstances("account-service")).thenReturn(List.of(
				new DefaultServiceInstance("acc-1", "account-service", "acc-1", 6000, false)));

		server.expect(requestTo("http://acc-1:6000/actuator/circuitbreakers"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		List<InstanceCircuitBreakers> result = aggregator.aggregate();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).error()).contains("401");
		assertThat(result.get(0).circuitBreakers()).isEmpty();
	}
}
