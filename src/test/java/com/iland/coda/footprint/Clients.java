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

import static com.iland.coda.footprint.TestValues.TEST_LABEL;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;
import net.codacloud.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Clients} centralizes authentication for clients to be uses across
 * integration tests.
 */
final class Clients {

	private static final Logger logger = LoggerFactory.getLogger(Clients.class);

	private static final String apiBasePath, username, password;

	static {
		final String missingEnv =
			Arrays.asList("FOOTPRINT_SUBDOMAIN", "FOOTPRINT_USERNAME",
					"FOOTPRINT_PASSWORD").stream()
				.filter(Predicates.not(System.getenv()::containsKey))
				.collect(Collectors.joining(","));
		if (!missingEnv.isEmpty()) {
			logger.error("The following environment variables must be set: {}",
				missingEnv);
			throw new RuntimeException(
				"Missing environment variables: " + missingEnv);
		}

		apiBasePath = createApiBasePath(System.getenv("FOOTPRINT_SUBDOMAIN"));
		username = System.getenv("FOOTPRINT_USERNAME");
		password = System.getenv("FOOTPRINT_PASSWORD");
	}

	// share clients between tests to improve performance
	static final SimpleCodaClient simpleCodaClient =
		new SimpleCodaClient(apiBasePath, username, password);
	static final RetryCodaClient retryCodaClient =
		new RetryCodaClient(simpleCodaClient);
	static final CachingCodaClient cachingCodaClient =
		new CachingCodaClient(retryCodaClient);

	private Clients() {
	}

	private static String createApiBasePath(final String subdomain) {
		return String.format("https://%s.codacloud.net/api", subdomain);
	}

	/**
	 * Deletes test registrations. Should be run before and after all tests that create a test registration.
	 *
	 * @param client a {@link CodaClient coda client}
	 * @throws ApiException
	 */
	static void deleteTestRegistrations(final CodaClient client)
		throws ApiException {
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

}
