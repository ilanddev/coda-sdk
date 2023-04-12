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

import static java.util.Objects.requireNonNull;

import java.io.IOException;

import net.codacloud.ApiClient;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

/**
 * {@link KeyAuthentication}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public final class KeyAuthentication implements Authentication {

	private static final String FOOTPRINT_API_KEY = "FootprintApiKey";

	private String apiKey;

	public KeyAuthentication(final String apiKey) {
		this.apiKey = requireNonNull(apiKey, "apiKey must not be null");
	}

	@Override
	public void authenticate(final ApiClient apiClient) {

	}

	/**
	 * Adds the {@literal FootprintApiKey} header to the request.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	@NotNull
	@Override
	public Response intercept(@NotNull final Chain chain) throws IOException {
		final Request request = chain.request();
		final Request.Builder builder = request.newBuilder();
		builder.header(FOOTPRINT_API_KEY, apiKey);

		return chain.proceed(builder.build());
	}

	/**
	 * Returns the API key.
	 *
	 * @return the API key
	 */
	String getApiKey() {
		return this.apiKey;
	}

}
