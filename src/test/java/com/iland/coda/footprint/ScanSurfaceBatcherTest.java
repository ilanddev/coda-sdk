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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.codacloud.model.ExtendMessageRequest;
import org.junit.jupiter.api.Test;

class ScanSurfaceBatcherTest {

	@Test
	void testThatTargetsAreBatched() {
		final Map<String, Integer> expectedRangeSizeByCidr =
			new LinkedHashMap<>();
		expectedRangeSizeByCidr.put("127.0.0.1", 1);
		expectedRangeSizeByCidr.put("localhost", 1);
		// 172.16.0.1-172.16.0.14
		expectedRangeSizeByCidr.put("172.16.0.0/28", 14);
		// 192.168.0.225-172.168.0.255
		expectedRangeSizeByCidr.put("192.168.0.255/27", 30);

		final int batchSize = 8;
		final ScanSurfaceBatcher scanSurfaceBatcher =
			new ScanSurfaceBatcher(batchSize);
		final Set<String> targets = expectedRangeSizeByCidr.keySet();
		final List<ExtendMessageRequest> batches =
			scanSurfaceBatcher.createBatches(targets);
		final Supplier<Integer> nextBatchSize =
			() -> batches.remove(0).getScanTargets().size();

		final int expectedIps =
			expectedRangeSizeByCidr.values().stream().mapToInt(i -> i).sum();
		final int expectedLastPageSize = expectedIps % batchSize;
		final int expectedPageCount =
			(expectedIps / batchSize) + (expectedLastPageSize == 0 ? 0 : 1);
		assertEquals(expectedPageCount, batches.size());
		while (!batches.isEmpty()) {
			assertEquals(batchSize, nextBatchSize.get());

			final boolean isLastBatch = batches.size() == 1;
			if (isLastBatch && expectedLastPageSize > 0) {
				assertEquals(expectedLastPageSize, nextBatchSize.get());
			}
		}
	}

}
