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

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class GenerationDateTest {

	@Test
	void testParseAndToString() {
		final String generationDate = "2022-05-16 21:33:29";

		final LocalDateTime localDateTime =
			GenerationDate.parse(generationDate);
		final String formatted = GenerationDate.toString(localDateTime);

		assertEquals(generationDate, formatted);
	}

}
