package com.piggymetrics.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
public class GatewayApplicationTests {

	@Autowired
	private WebTestClient webTestClient;

	@Test
	public void contextLoads() {
	}

	@Test
	public void shouldServeStaticFrontend() {
		webTestClient.get().uri("/")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);

		webTestClient.get().uri("/index.html")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
	}

	@Test
	public void shouldRouteWithoutStrippingPrefixAndPassSensitiveHeaders() {
		stubFor(get(urlEqualTo("/uaa/users/current"))
				.willReturn(aResponse().withStatus(200).withBody("{\"user\":\"demo\"}")));

		webTestClient.get().uri("/uaa/users/current")
				.header(HttpHeaders.AUTHORIZATION, "Bearer token-123")
				.header(HttpHeaders.COOKIE, "session=abc")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.user").isEqualTo("demo");

		verify(getRequestedFor(urlEqualTo("/uaa/users/current"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token-123"))
				.withHeader(HttpHeaders.COOKIE, equalTo("session=abc")));
	}

	@Test
	public void shouldRouteAccountsWithFullPath() {
		stubFor(get(urlEqualTo("/accounts/demo"))
				.willReturn(aResponse().withStatus(200).withBody("{\"name\":\"demo\"}")));

		webTestClient.get().uri("/accounts/demo")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.name").isEqualTo("demo");
	}

	@Test
	public void shouldNotRouteUnknownServices() {
		webTestClient.get().uri("/unknown-service/foo")
				.exchange()
				.expectStatus().isNotFound();
	}
}
