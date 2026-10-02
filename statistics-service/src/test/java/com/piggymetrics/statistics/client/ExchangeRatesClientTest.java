package com.piggymetrics.statistics.client;

import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.ExchangeRatesContainer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ExchangeRatesClientTest {

	private static final String RATES_RESPONSE =
			"{\"base\":\"USD\",\"date\":\"2018-01-01\",\"rates\":{\"USD\":1.0,\"EUR\":0.8,\"RUB\":80.0}}";

	private static final HttpServer server = startServer();

	private static final AtomicInteger status = new AtomicInteger(200);

	private static final AtomicReference<String> lastQuery = new AtomicReference<>();

	@Autowired
	private ExchangeRatesClient client;

	@DynamicPropertySource
	static void ratesUrl(DynamicPropertyRegistry registry) {
		registry.add("rates.url", () -> "http://localhost:" + server.getAddress().getPort());
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@BeforeEach
	void reset() {
		status.set(200);
		lastQuery.set(null);
	}

	@Test
	void shouldRetrieveExchangeRates() {

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(container.getDate(), LocalDate.now());
		assertEquals(container.getBase(), Currency.getBase());

		assertNotNull(container.getRates());
		assertNotNull(container.getRates().get(Currency.USD.name()));
		assertNotNull(container.getRates().get(Currency.EUR.name()));
		assertNotNull(container.getRates().get(Currency.RUB.name()));
	}

	@Test
	void shouldRetrieveExchangeRatesForSpecifiedCurrency() {

		Currency requestedCurrency = Currency.EUR;
		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals("base=" + Currency.getBase().name(), lastQuery.get());
		assertEquals(container.getDate(), LocalDate.now());
		assertEquals(container.getBase(), Currency.getBase());

		assertNotNull(container.getRates());
		assertNotNull(container.getRates().get(requestedCurrency.name()));
	}

	@Test
	void shouldFallbackWhenRatesProviderFails() {

		status.set(500);

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(Currency.getBase(), container.getBase());
		assertNotNull(container.getRates());
		assertTrue(container.getRates().isEmpty());
	}

	private static HttpServer startServer() {
		try {
			HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			httpServer.createContext("/latest", exchange -> {
				lastQuery.set(exchange.getRequestURI().getQuery());
				byte[] body = RATES_RESPONSE.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(status.get(), body.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(body);
				}
			});
			httpServer.start();
			return httpServer;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
