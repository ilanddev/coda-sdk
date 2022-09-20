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

import static java.time.ZoneOffset.UTC;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;

/**
 * {@link GenerationDate}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public final class GenerationDate {

	private static final String REPORT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

	private GenerationDate() {
	}

	/**
	 * Parses a timestamp and returns the {@link LocalDateTime}.
	 *
	 * @param timestamp e.g. "2022-05-16 21:33:29"
	 * @return the parsed {@link LocalDateTime}
	 */
	public static LocalDateTime parse(final String timestamp) {
		try {
			final Instant instant =
				createDateFormat().parse(timestamp).toInstant();
			return LocalDateTime.ofInstant(instant, UTC);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Formats a {@link LocalDateTime}.
	 *
	 * @param dateTime a {@link LocalDateTime}
	 * @return the formatted {@link LocalDateTime} e.g. "2022-05-16 21:33:29"
	 */
	public static String toString(final LocalDateTime dateTime) {
		final Instant instant = dateTime.toInstant(UTC);
		final Date date = Date.from(instant);

		return createDateFormat().format(date);
	}

	public static DateFormat createDateFormat() {
		final DateFormat dateFormat =
			new SimpleDateFormat(REPORT_TIMESTAMP_FORMAT);
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		return dateFormat;
	}

}
