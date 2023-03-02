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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import net.codacloud.model.RegistrationEditRequest;
import net.codacloud.model.RegistrationLight;
import org.junit.jupiter.api.Test;

class CachingCodaClientTest {

	@Test
	void testThatRegistrationsAreCached() throws Throwable {
		final CodaClient client = Clients.cachingCodaClient.login();

		testThatResultWasCached(client::listRegistrations);
	}

	@Test
	void testThatCreateAndDeleteRegistrationInvalidatesCache()
		throws Throwable {
		final CachingCodaClient client =
			(CachingCodaClient) Clients.cachingCodaClient.login();
		final Runnable assertAccountCacheWasInvalidated =
			() -> assertTrue(client.getAccountCache().isEmpty(),
				"cached collection must be empty after being invalidated");

		final RegistrationLight registration =
			client.createRegistration(TEST_LABEL, TEST_DESCRIPTION);
		assertAccountCacheWasInvalidated.run();

		boolean containsRegistration = client.listRegistrations().stream()
			.anyMatch(r -> Objects.equals(r.getId(), registration.getId()));
		try {
			assertTrue(containsRegistration,
				"new registration must be present in cached collection");
		} finally {
			client.deleteRegistration(registration);
			assertAccountCacheWasInvalidated.run();
		}

		containsRegistration =
			client.listRegistrations().stream().map(RegistrationLight::getId)
				.anyMatch(id -> Objects.equals(id, registration.getId()));
		assertFalse(containsRegistration,
			"deleted registration must not be present in cached collection");
	}

	@Test
	void testThatAccountsAreCached() throws Throwable {
		final CodaClient client = Clients.cachingCodaClient.login();

		testThatResultWasCached(() -> client.listAccounts(null));
	}

	@Test
	void testThatScannersAreCached() throws Throwable {
		final CodaClient client = Clients.cachingCodaClient.login();

		testThatResultWasCached(() -> client.getScanners(null));
	}

	private static <R> void testThatResultWasCached(
		final Callable<Collection<R>> callable) throws Exception {
		final Collection<R> collection = callable.call();
		assertFalse(collection.isEmpty(),
			"cached collection must not be empty");

		final Stopwatch stopwatch = Stopwatch.createStarted();
		callable.call();
		final long elapsedMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
		assertEquals(0, elapsedMillis,
			"cached collection must return instantly");
	}

	@Test
	void testRegistrationEditReplacesCachedRegistration() throws Throwable {
		final CodaClient client = Clients.cachingCodaClient.login();

		final RegistrationLight registration =
			client.findOrCreateRegistration(TEST_LABEL, TEST_DESCRIPTION);

		final String description = "foo";
		client.updateRegistration(registration.getId(),
			new RegistrationEditRequest().description(description).manageType(
				RegistrationEditRequest.ManageTypeEnum.FULLY_MANAGED));

		final Optional<RegistrationLight> optionalRegistration =
			client.getRegistrationForLabel(TEST_LABEL);
		assertTrue(optionalRegistration.isPresent(),
			"test registration must be present");
		assertEquals(description, optionalRegistration.get().getDescription(),
			"cached registration description must match new description");
	}

}
