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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.CVR;
import net.codacloud.model.CVRMostVulnServer;
import net.codacloud.model.CVRVulnerability;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import net.codacloud.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SimpleCodaClientTest {

	private static CodaClient client;

	@BeforeAll
	static void createClient() throws ApiException {
		client = Clients.simpleCodaClient.login();

		Clients.deleteTestRegistrations(client);
	}

	@AfterAll
	static void afterAll() throws ApiException {
		Clients.deleteTestRegistrations(client);
	}

	@Test
	void testThatRegistrationsAreNotDuplicated() throws Throwable {
		final RegistrationLight registrationA =
			client.findOrCreateRegistration(TEST_LABEL, TEST_DESCRIPTION);
		final RegistrationLight registrationB =
			client.findOrCreateRegistration(TEST_LABEL, TEST_DESCRIPTION);

		assertEquals(registrationA.getId(), registrationB.getId(),
			"registration was duplicated");
	}

	@Test
	void testScanSurfaceAndRescan() throws ApiException, UnknownHostException {
		final RegistrationLight registration =
			client.createRegistration(TEST_LABEL, TEST_DESCRIPTION);
		final Integer accountId = client.registrationToAccountId(registration);

		final InetAddress[] addresses = InetAddress.getAllByName("iland.com");
		List<String> targets = Arrays.asList(addresses).stream()
			.filter(address -> address instanceof Inet4Address)
			.map(InetAddress::getHostAddress).collect(Collectors.toList());

		List<Integer> scannerIds =
			client.getScannerIdByLabel(accountId).values().stream()
				.collect(Collectors.toList());

		client.updateScanSurface(targets, scannerIds, accountId);

		final int expectedSize = targets.size() * scannerIds.size();
		final Set<ScanSurfaceEntry> scanSurface =
			client.getScanSurface(accountId);
		assertEquals(expectedSize, scanSurface.size());

		client.rescan(accountId);
	}

	@Test
	void testScanStatus() throws ApiException {
		final Optional<ScanStatus> anyScanStatus =
			client.listAccounts(null).stream().map(Account::getId)
				.map(accountId -> {
					try {
						return client.getScanStatus(accountId);
					} catch (ApiException e) {
						throw new RuntimeException(e);
					}
				}).findAny();
		assertTrue(anyScanStatus.isPresent(),
			"at least one scan status must be present");
	}

	@Test
	void testGetLatestReport() throws ApiException {
		final int accountId = findAnAccountWithAtLeastOneReport();
		final Optional<CVR> latestReport = client.getLatestReport(accountId);
		assertTrue(latestReport.isPresent(), "latest report must be present");
	}

	private int findAnAccountWithAtLeastOneReport() throws ApiException {
		final AtomicInteger atomicAccountId = new AtomicInteger();
		client.listAccounts(null).stream().map(Account::getId)
			.map(accountId -> {
				atomicAccountId.set(accountId);
				return getReportTimestamps(accountId);
			}).flatMap(List::stream).findFirst().get();

		return atomicAccountId.get();
	}

	@Test
	void testReportsJson() throws ApiException {
		final AtomicInteger atomicAccountId = new AtomicInteger();
		final LocalDateTime generationDate =
			client.listAccounts(null).stream().map(Account::getId)
				.map(accountId -> {
					atomicAccountId.set(accountId);
					return getReportTimestamps(accountId);
				}).flatMap(List::stream).findFirst()
				.map(GenerationDate::parse).get();

		final Map<LocalDateTime, CodaClient.LazyCvrJson> reportsJson =
			client.getReportsJson(CodaClient.ReportType.SNAPSHOT,
				atomicAccountId.get());
		assertTrue(reportsJson.containsKey(generationDate),
			"JSON reports map must contain generation date");

		final String json = reportsJson.get(generationDate).retrieveJson();
		assertNotNull(json, "json must not be null");
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
	void testThatTechnicalReportIsPopulated() throws Throwable {
		final CVR report =
			client.listAccounts(null).stream().map(Account::getId)
				.map(this::getReports).map(Map::values)
				.flatMap(Collection::stream).findFirst().get().retrieve();
		assertNotNull(report, "report must not be null");

		final List<CVRMostVulnServer> technicalReport =
			report.getTechnicalReport();
		assertFalse(technicalReport.isEmpty(),
			"technical reports must not be empty");

		technicalReport.forEach(techReport -> {
			assertNotNullOrEmpty(techReport.getHostname(), "hostname");
			assertNotNullOrEmpty(techReport.getIp(), "IP");

			final List<CVRVulnerability> vulnerabilities =
				techReport.getVulnerabilities();
			assertFalse(vulnerabilities.isEmpty());
			vulnerabilities.stream().map(CVRVulnerability::getSummary)
				.forEach(summary -> assertNotNullOrEmpty(summary, "summary"));
		});
	}

	private static void assertNotNullOrEmpty(final String value,
		final String name) {
		assertNotNull(value, String.format("%s must not be null", name));
		assertNotEquals("", value, String.format("%s must not be empty", name));
	}

	private Map<LocalDateTime, CodaClient.LazyCVR> getReports(
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

		assertNotNull(users, "users list must not be null");
		assertFalse(users.isEmpty(), "users list must not be empty");
	}

}
