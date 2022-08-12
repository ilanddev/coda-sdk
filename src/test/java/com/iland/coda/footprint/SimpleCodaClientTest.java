/*
 * Copyright (c) 2022, iland Internet Solutions, Corp
 *
 * This software is licensed under the Terms and Conditions contained within the
 * "LICENSE.txt" file that accompanied this software. Any inquiries concerning
 * the scope or enforceability of the license should be addressed to:
 *
 * iland Internet Solutions, Corp
 * 1235 North Loop West, Suite 800
 * Houston, TX 77008
 * USA
 *
 * http://www.iland.com
 */

package com.iland.coda.footprint;

import static com.iland.coda.footprint.TestValues.TEST_DESCRIPTION;
import static com.iland.coda.footprint.TestValues.TEST_LABEL;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.CVR;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SimpleCodaClientTest {

	private static final Logger logger =
		LoggerFactory.getLogger(SimpleCodaClientTest.class);

	private static CodaClient client;

	@BeforeAll
	static void createClient() throws ApiException {
		client = Clients.simpleCodaClient.login();

		deleteTestRegistrations();
	}

	@AfterAll
	static void afterAll() throws ApiException {
		deleteTestRegistrations();
	}

	static void deleteTestRegistrations() throws ApiException {
		client.listRegistrations().stream()
			.filter(r -> TEST_LABEL.equals(r.getLabel()))
			.forEach(registration -> {
				try {
					client.deleteRegistration(registration);
				} catch (ApiException e) {
					logger.error(e.getMessage(), e);
				}
			});
	}

	@Test
	void testThatRegistrationsAreNotDuplicated() throws Throwable {
		final RegistrationLight registrationA =
			client.findOrCreateRegistration(TEST_LABEL, TEST_DESCRIPTION);
		final RegistrationLight registrationB =
			client.findOrCreateRegistration(TEST_LABEL, TEST_DESCRIPTION);

		Assertions.assertEquals(registrationA.getId(), registrationB.getId());
	}

	@Test
	void testReportsJson() throws ApiException {
		final AtomicInteger atomicAccountId = new AtomicInteger();
		final String generationDate =
			client.listAccounts(null).stream().map(Account::getId)
				.map(accountId -> {
					atomicAccountId.set(accountId);
					return getReportTimestamps(accountId);
				}).flatMap(List::stream).findFirst().get();

		final Map<String, CodaClient.LazyCvrJson> reportsJson =
			client.getReportsJson(CodaClient.ReportType.SNAPSHOT,
				atomicAccountId.get());
		assertTrue(reportsJson.containsKey(generationDate));

		final String json = reportsJson.get(generationDate).retrieveJson();
		assertNotNull(json);
	}

	private List<String> getReportTimestamps(final Integer accountId) {
		try {
			return client.getReportTimestamps(CodaClient.ReportType.SNAPSHOT,
				accountId);
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void testThatReportIsParsedWithoutException() throws Throwable {
		final CVR report =
			client.listAccounts(null).stream().map(Account::getId)
				.map(this::getReports).map(Map::values)
				.flatMap(Collection::stream).findFirst().get().retrieve();

		assertNotNull(report);
	}


	private Map<String, CodaClient.LazyCVR> getReports(
		final Integer accountId) {
		try {
			return client.getReports(CodaClient.ReportType.SNAPSHOT, accountId);
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void testListUsers() throws Throwable {
		final List<User> users = client.listUsers();

		assertNotNull(users);
		assertNotEquals(0, users.size());
	}

}
