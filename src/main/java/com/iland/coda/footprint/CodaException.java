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

/**
 * {@link CodaException}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public final class CodaException extends RuntimeException {

	public CodaException() {
	}

	public CodaException(final String message) {
		super(message);
	}

	public CodaException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public CodaException(final Throwable cause) {
		super(cause);
	}

}
