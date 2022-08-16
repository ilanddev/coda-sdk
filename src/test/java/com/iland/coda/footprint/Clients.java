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

final class Clients {

	private static final String apiBasePath =
		createApiBasePath(System.getenv("FOOTPRINT_SUBDOMAIN"));
	private static final String username = System.getenv("FOOTPRINT_USERNAME");
	private static final String password = System.getenv("FOOTPRINT_PASSWORD");

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
