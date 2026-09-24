package com.piggymetrics.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
public class MonitoringApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void contextLoads() {
	}

	@Test
	public void exposesPrometheusMetrics() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("jvm_memory_used_bytes")));
	}

	@Test
	public void exposesCircuitBreakerEndpoint() throws Exception {
		mockMvc.perform(get("/actuator/circuitbreakers"))
				.andExpect(status().isOk());
	}

	@Test
	public void aggregatesNothingWhenNoServicesAreRegistered() throws Exception {
		mockMvc.perform(get("/circuitbreakers"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}
}
