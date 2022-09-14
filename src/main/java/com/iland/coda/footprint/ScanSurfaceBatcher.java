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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.codacloud.model.ExtendMessage;
import org.apache.commons.net.util.SubnetUtils;

/**
 * {@link ScanSurfaceBatcher} batches a list of targets into one or more {@link ExtendMessage messages} with a configurable maximum size.
 */
final class ScanSurfaceBatcher {

	/**
	 * components.schemas.ExtendMessage.properties.scanTargets.maxItems in swagger.yml
	 */
	private static final int MAX_IPS_PER_SCAN_SURFACE_UPDATE = 1024;

	private final int maxIpsPerBatch;

	ScanSurfaceBatcher() {
		this(MAX_IPS_PER_SCAN_SURFACE_UPDATE);
	}

	ScanSurfaceBatcher(final int maxIpsPerBatch) {
		this.maxIpsPerBatch = maxIpsPerBatch;
	}

	/**
	 * Creates batches with a maximum size from a list of targets into one or more {@link ExtendMessage messages}.
	 *
	 * @param targets a {@link Collection} of targets, e.g. hostname, IP address, or CIDR notation
	 * @return a {@link List} of {@link ExtendMessage messages} without the scanners initialized
	 */
	List<ExtendMessage> createBatches(final Collection<String> targets) {
		final String cidrRegex =
			"\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}";
		final List<String> addresses = targets.stream().map(String::trim)
			.filter(target -> !Pattern.matches(cidrRegex, target))
			.collect(Collectors.toList());
		final List<String> cidrAddresses = targets.stream().map(String::trim)
			.filter(target -> Pattern.matches(cidrRegex, target))
			.map(SubnetUtils::new).map(SubnetUtils::getInfo)
			.map(SubnetUtils.SubnetInfo::getAllAddresses).map(Arrays::asList)
			.flatMap(List::stream).collect(Collectors.toList());

		final AtomicInteger counter = new AtomicInteger();
		return Stream.of(addresses, cidrAddresses).flatMap(List::stream)
			.collect(Collectors.groupingBy(
				it -> counter.getAndIncrement() / maxIpsPerBatch)).values()
			.stream().map(batch -> new ExtendMessage().scanTargets(batch))
			.collect(Collectors.toList());
	}

}
