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

import static com.iland.coda.footprint.Clients.simpleCodaClientPassword;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.codacloud.ApiException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RetryCodaClientTest {

	@Test
	@Disabled("We no longer use password-based authentication.")
	void testReauthentication() throws ApiException {
		final RetryCodaClient retryCodaClient =
			new RetryCodaClient(simpleCodaClientPassword);

		// test for proper authentication
		retryCodaClient.getScanners(null);
		// invalidate access token
		simpleCodaClientPassword.xsrfInterceptor.xsrfToken.set("foo");
		// re-authentication happens silently in the background
		retryCodaClient.getScanners(null);

		final PasswordAuthentication authentication =
			(PasswordAuthentication) simpleCodaClientPassword.authentication;
		assertNotNull(authentication.accessToken.get(),
			"access token must not be null");
		assertNotNull(simpleCodaClientPassword.xsrfInterceptor.getXsrfToken(),
			"XSRF token must not be null");
	}

}
