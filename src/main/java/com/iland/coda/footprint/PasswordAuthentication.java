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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.codacloud.ApiClient;
import net.codacloud.ApiException;
import net.codacloud.api.CommonApi;
import net.codacloud.model.SessionLogin;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

/**
 * Extracts bearer access token to be used in future requests.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public final class PasswordAuthentication implements Authentication {

	private final String username, password;

	final AtomicReference<String> accessToken = new AtomicReference<>();

	public PasswordAuthentication(final String username,
		final String password) {
		this.username = requireNonNull(username, "username must not be null");
		this.password = requireNonNull(password, "password must not be null");
	}

	@Override
	public void authenticate(final ApiClient apiClient) throws ApiException {
		accessToken.set(null);

		final SessionLogin credentials =
			new SessionLogin().username(username).password(password);
		new CommonApi(apiClient).commonAuthSessionCreate(credentials, null);
	}

	@NotNull
	@Override
	public Response intercept(@NotNull final Chain chain) throws IOException {
		final Request request = chain.request();
		if (accessToken.get() != null) {
			final Request.Builder builder = request.newBuilder();
			builder.header("Authorization", "Bearer " + accessToken.get());

			return chain.proceed(builder.build());
		}

		final Response response = chain.proceed(request);
		final String body = response.peekBody(Long.MAX_VALUE).string();
		final String regex = "\"access\":\"([^\"]+)\"";
		final Pattern pattern = Pattern.compile(regex);
		final Matcher matcher = pattern.matcher(body);
		if (matcher.find()) {
			accessToken.set(matcher.group(1));
		}

		return response;
	}

}
