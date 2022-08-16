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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;

import net.codacloud.model.PaginatedRegistrationLightList;
import net.codacloud.model.RegistrationLight;
import org.junit.jupiter.api.Test;

class PaginatorTest {

	@Test
	void testThatAllItemsAreFetched() throws Throwable {
		final CodaClient client = Clients.cachingCodaClient.login();

		final PaginatedRegistrationLightList firstPage =
			Clients.simpleCodaClient.adminApi.adminRegistrationsLightRetrieve(
				null, 1, 1);

		final Set<Integer> ids =
			client.listRegistrations().stream().map(RegistrationLight::getId)
				.collect(Collectors.toSet());

		assertEquals(firstPage.getTotalCount(), ids.size());
	}

}
