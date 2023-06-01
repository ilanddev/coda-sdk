/*
 * Copyright (c) 2023, iland Internet Solutions, Corp
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

package com.iland.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class NetworkUtilsTest {

	@Test
	void toStream() {
		final List<String> expected =
			Arrays.asList("localhost", "127.0.0.1", "127.0.0.253",
				"127.0.0.254");

		final Collection<String> targets =
			Arrays.asList("localhost", "127.0.0.1", "127.0.0.255/30");
		final List<String> actual =
			NetworkUtils.toStream(targets).collect(Collectors.toList());

		assertEquals(expected, actual);
	}

	@Test
	void isRFC1918IpAddress() {
		assertFalse(NetworkUtils.isRFC1918IpAddress("98.159.144.99"));

		Stream.of("10.0.32.0", "172.16.0.9", "192.168.0.0")
			.map(NetworkUtils::isRFC1918IpAddress)
			.forEach(Assertions::assertTrue);
	}

}
