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

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Predicates;
import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NetworkUtils}.
 *
 * @author <a href="mailto:tspilman@1111systems.com">Tag Spilman</a>
 */
public final class NetworkUtils {

	private static final String CIDR_REGEX =
		"\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}";

	private static final Logger logger =
		LoggerFactory.getLogger(NetworkUtils.class);

	private NetworkUtils() {
	}

	/**
	 * Breaks up targets into individual hostnames and IP addresses.
	 *
	 * @param targets a {@link Collection collection} of targets, e.g. hostname, IP address, or CIDR notation
	 * @return a {@link Stream stream} of individual hostnames and IP addresses
	 */
	public static Stream<String> toStream(final Collection<String> targets) {
		final List<String> addresses = targets.stream().map(String::trim)
			.filter(Predicates.not(NetworkUtils::isCidr))
			.collect(Collectors.toList());

		final List<String> cidrAddresses =
			targets.stream().map(String::trim).filter(NetworkUtils::isCidr)
				.map(SubnetUtils::new).map(SubnetUtils::getInfo)
				.map(SubnetUtils.SubnetInfo::getAllAddresses)
				.flatMap(Stream::of).collect(Collectors.toList());

		return Stream.of(addresses, cidrAddresses).flatMap(List::stream);
	}

	/**
	 * Returns whether the target is in CIDR notation.
	 *
	 * @param target a hostname, IP address, or CIDR notation
	 * @return whether the target is in CIDR notation
	 */
	public static boolean isCidr(final String target) {
		return Pattern.matches(CIDR_REGEX, target);
	}

	/**
	 * Returned whether the supplied ip is an RFC1918 IP address.
	 *
	 * @param ip an IP address
	 * @return whether the supplied ip is an RFC1918 IP address
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1918">RFC1918: Address Allocation for Private Internets</a>
	 */
	public static boolean isRFC1918IpAddress(final String ip) {
		try {
			return Inet4Address.getByName(ip).isSiteLocalAddress();
		} catch (final UnknownHostException e) {
			final String errorMessage = String.format(
				"Error determining if \"%s\" is an RFC1918 local address", ip);
			logger.error(errorMessage, e);
		}

		return false;
	}

}
