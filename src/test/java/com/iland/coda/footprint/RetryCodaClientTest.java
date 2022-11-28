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
import org.junit.jupiter.api.Test;

class RetryCodaClientTest {

	@Test
	void testReauthentication() throws ApiException {
		final RetryCodaClient retryCodaClient =
			new RetryCodaClient(simpleCodaClientPassword);

		retryCodaClient.getScanners(null); // test for proper authentication
		simpleCodaClientPassword.xsrfInterceptor.xsrfToken.set(
			"foo"); // invalidate access token
		retryCodaClient.getScanners(
			null); // re-authentication happens silently in the background


		final PasswordAuthentication authentication =
			(PasswordAuthentication) simpleCodaClientPassword.authentication;
		assertNotNull(authentication.accessToken.get(),
			"access token must not be null");
		assertNotNull(simpleCodaClientPassword.xsrfInterceptor.getXsrfToken(),
			"XSRF token must not be null");
	}

}
