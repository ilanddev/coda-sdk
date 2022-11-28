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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

/**
 * Extracts XSRF token to be used in future requests.
 */
final class XsrfInterceptor implements Interceptor {

	private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
	private static final String CSRF_HEADER_NAME = "X-XSRF-Token";

	final AtomicReference<String> xsrfToken = new AtomicReference<>();

	XsrfInterceptor() {
	}

	/**
	 * Clear the XSRF token.
	 */
	public void clearXsrfToken() {
		xsrfToken.set(null);
	}

	/**
	 * Get the XSRF token.
	 *
	 * @return the XSRF token
	 */
	public String getXsrfToken() {
		return xsrfToken.get();
	}

	@NotNull
	@Override
	public Response intercept(@NotNull final Chain chain) throws IOException {
		final Request request = chain.request();
		if (xsrfToken.get() != null) {
			final Request.Builder builder = request.newBuilder();
			builder.addHeader(CSRF_HEADER_NAME, xsrfToken.get());

			return chain.proceed(builder.build());
		}

		final Response response = chain.proceed(request);
		response.headers("Set-Cookie").stream()
			.filter(cookie -> cookie.startsWith(CSRF_COOKIE_NAME)).findFirst()
			.map(XsrfInterceptor::extractXsrfToken).ifPresent(xsrfToken::set);

		return response;
	}

	/**
	 * Extracts XSRF token.
	 *
	 * @param cookie The cookie header value
	 * @return an {@link String XSRF token}
	 */
	private static String extractXsrfToken(final String cookie) {
		final String regex = "XSRF-TOKEN=([^;]+)";
		final Pattern pattern = Pattern.compile(regex);
		final Matcher matcher = pattern.matcher(cookie);
		return matcher.find() ? matcher.group(1) : null;
	}

}
