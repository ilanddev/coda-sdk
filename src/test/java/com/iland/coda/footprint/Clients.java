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

import java.util.Arrays;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;

final class Clients {

	private static final String apiBasePath, username, password;

	static {
		final String missingEnv =
			Arrays.asList("FOOTPRINT_SUBDOMAIN", "FOOTPRINT_USERNAME",
					"FOOTPRINT_PASSWORD").stream()
				.filter(Predicates.not(System.getenv()::containsKey))
				.collect(Collectors.joining(","));
		if (!missingEnv.isEmpty()) {
			throw new RuntimeException(
				"The following environment variables must be set: "
					+ missingEnv);
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

}
