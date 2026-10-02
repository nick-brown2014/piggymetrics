package com.piggymetrics.statistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.piggymetrics.statistics.config.ResourceServerConfig;
import com.piggymetrics.statistics.domain.Account;
import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.Item;
import com.piggymetrics.statistics.domain.Saving;
import com.piggymetrics.statistics.domain.TimePeriod;
import com.piggymetrics.statistics.domain.timeseries.DataPoint;
import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import com.piggymetrics.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatisticsController.class)
@Import(ResourceServerConfig.class)
class StatisticsControllerTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private StatisticsService statisticsService;

	@Test
	void shouldGetStatisticsByAccountName() throws Exception {

		final DataPoint dataPoint = new DataPoint();
		dataPoint.setId(new DataPointId("test", new Date()));

		when(statisticsService.findByAccountName(dataPoint.getId().getAccount()))
				.thenReturn(ImmutableList.of(dataPoint));

		mockMvc.perform(get("/test").with(serverToken()))
				.andExpect(jsonPath("$[0].id.account").value(dataPoint.getId().getAccount()))
				.andExpect(status().isOk());
	}

	@Test
	void shouldGetDemoStatisticsWithoutServerScope() throws Exception {

		when(statisticsService.findByAccountName("demo")).thenReturn(ImmutableList.of());

		mockMvc.perform(get("/demo").with(userToken("someone")))
				.andExpect(status().isOk());
	}

	@Test
	void shouldNotGetStatisticsByAccountNameWithoutServerScope() throws Exception {

		mockMvc.perform(get("/test").with(userToken("someone")))
				.andExpect(status().isForbidden());

		verify(statisticsService, never()).findByAccountName(anyString());
	}

	@Test
	void shouldGetCurrentAccountStatistics() throws Exception {

		final DataPoint dataPoint = new DataPoint();
		dataPoint.setId(new DataPointId("test", new Date()));

		when(statisticsService.findByAccountName(dataPoint.getId().getAccount()))
				.thenReturn(ImmutableList.of(dataPoint));

		mockMvc.perform(get("/current").with(userToken(dataPoint.getId().getAccount())))
				.andExpect(jsonPath("$[0].id.account").value(dataPoint.getId().getAccount()))
				.andExpect(status().isOk());
	}

	@Test
	void shouldRequireAuthentication() throws Exception {

		mockMvc.perform(get("/current"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldSaveAccountStatistics() throws Exception {

		String json = mapper.writeValueAsString(account());

		mockMvc.perform(put("/test").with(serverToken()).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk());

		verify(statisticsService, times(1)).save(anyString(), any(Account.class));
	}

	@Test
	void shouldNotSaveAccountStatisticsWithoutServerScope() throws Exception {

		String json = mapper.writeValueAsString(account());

		mockMvc.perform(put("/test").with(userToken("test")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isForbidden());

		verify(statisticsService, never()).save(anyString(), any(Account.class));
	}

	private static Account account() {

		Saving saving = new Saving();
		saving.setAmount(new BigDecimal(1500));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal("3.32"));
		saving.setDeposit(true);
		saving.setCapitalization(false);

		Item grocery = new Item();
		grocery.setTitle("Grocery");
		grocery.setAmount(new BigDecimal(10));
		grocery.setCurrency(Currency.USD);
		grocery.setPeriod(TimePeriod.DAY);

		Item salary = new Item();
		salary.setTitle("Salary");
		salary.setAmount(new BigDecimal(9100));
		salary.setCurrency(Currency.USD);
		salary.setPeriod(TimePeriod.MONTH);

		final Account account = new Account();
		account.setSaving(saving);
		account.setExpenses(ImmutableList.of(grocery));
		account.setIncomes(ImmutableList.of(salary));
		return account;
	}

	private static RequestPostProcessor serverToken() {
		return opaqueToken()
				.attributes(attrs -> attrs.put("sub", "account-service"))
				.authorities(new SimpleGrantedAuthority("SCOPE_server"));
	}

	private static RequestPostProcessor userToken(String name) {
		return opaqueToken()
				.attributes(attrs -> attrs.put("sub", name))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("SCOPE_ui"));
	}
}
