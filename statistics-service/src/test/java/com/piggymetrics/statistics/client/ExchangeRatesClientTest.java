package com.piggymetrics.statistics.client;

import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.ExchangeRatesContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
public class ExchangeRatesClientTest {

	private static final String RATES_JSON = """
			{
			  "base": "USD",
			  "date": "2020-01-01",
			  "rates": { "USD": 1, "EUR": 0.8, "RUB": 80 }
			}
			""";

	@Autowired
	private ExchangeRatesClient client;

	@Test
	public void shouldRetrieveExchangeRates() {

		stubFor(get(urlPathEqualTo("/latest"))
				.withQueryParam("base", equalTo(Currency.getBase().name()))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withBody(RATES_JSON)));

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(container.getDate(), LocalDate.now());
		assertEquals(container.getBase(), Currency.getBase());

		assertNotNull(container.getRates());
		assertNotNull(container.getRates().get(Currency.USD.name()));
		assertNotNull(container.getRates().get(Currency.EUR.name()));
		assertNotNull(container.getRates().get(Currency.RUB.name()));
	}

	@Test
	public void shouldFallbackToEmptyRatesWhenProviderFails() {

		stubFor(get(urlPathEqualTo("/latest"))
				.willReturn(aResponse().withStatus(500)));

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(container.getBase(), Currency.getBase());
		assertNotNull(container.getRates());
		assertTrue(container.getRates().isEmpty());
	}
}
