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

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.EvictingQueue;
import net.codacloud.ApiClient;
import net.codacloud.ApiException;
import net.codacloud.JSON;
import net.codacloud.api.AdminApi;
import net.codacloud.api.BrandingApi;
import net.codacloud.api.CommonApi;
import net.codacloud.api.ConsoleApi;
import net.codacloud.model.SessionLogin;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AbstractCodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
abstract class AbstractCodaClient implements CodaClient {

	private static final Logger logger =
		LoggerFactory.getLogger(AbstractCodaClient.class);

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
	private static final String CSRF_HEADER_NAME = "X-XSRF-Token";

	private final String username, password;

	protected final AdminApi adminApi;
	protected final BrandingApi brandingApi;
	protected final CommonApi commonApi;
	protected final ConsoleApi consoleApi;

	protected final AtomicReference<String> accessToken =
		new AtomicReference<>();
	protected final AtomicReference<String> xsrfToken = new AtomicReference<>();

	private static final Queue<String> rawJsonQueue = EvictingQueue.create(1);
	protected static final Lock jsonLock = new ReentrantLock();

	AbstractCodaClient(final String apiBasePath, final String username,
		final String password) {
		this.username = username;
		this.password = password;

		final OkHttpClient client =
			createClient(createBearerInterceptor(), createCsrfInterceptor(),
				createEmptyStringInterceptor(),
				createSchedulerConfigHackInterceptor(),
				createDateTimeInterceptor());

		final ApiClient apiClient = new ApiClient(client);
		apiClient.setBasePath(apiBasePath);
		apiClient.setJSON(createJSON(apiClient));

		adminApi = new AdminApi(apiClient);
		brandingApi = new BrandingApi(apiClient);
		commonApi = new CommonApi(apiClient);
		consoleApi = new ConsoleApi(apiClient);
	}

	private static OkHttpClient createClient(Interceptor... interceptors) {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		//builder.addNetworkInterceptor(getProgressInterceptor());
		Arrays.stream(interceptors).forEach(builder::addInterceptor);

		builder.connectTimeout(Duration.ofSeconds(30))
			.readTimeout(Duration.ofSeconds(120));

		return builder.build();
	}

	/**
	 * Extracts bearer access token to be used in future requests.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createBearerInterceptor() {
		return chain -> {
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
		};
	}

	/**
	 * Extracts XSRF token to be used in future requests.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createCsrfInterceptor() {
		return chain -> {
			final Request request = chain.request();
			if (xsrfToken.get() != null) {
				final Request.Builder builder = request.newBuilder();
				builder.addHeader(CSRF_HEADER_NAME, xsrfToken.get());

				return chain.proceed(builder.build());
			}

			final Response response = chain.proceed(request);
			response.headers("Set-Cookie").stream()
				.filter(cookie -> cookie.startsWith(CSRF_COOKIE_NAME))
				.findFirst().map(AbstractCodaClient::extractXsrfToken)
				.ifPresent(xsrfToken::set);

			return response;
		};
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

	/**
	 * Replaces empty strings with null values to work around an error in GSON.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createEmptyStringInterceptor() {
		// empty Strings cause a JSON parse exception
		return createBodyInterceptor(body -> body.replaceAll("\"\"", "null"));
	}

	/**
	 * Replaces schedulerConfig value to bypass invalid OpenAPI declaration.
	 * FIXME: parse scheduler config
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createSchedulerConfigHackInterceptor() {
		return createBodyInterceptor(
			body -> body.replaceAll("\"schedulerConfig\":\\s*\\{\\}",
				"\"schedulerConfig\":\"\""));
	}

	/**
	 * Replaces date-time values in the snapshot reports with properly formatted values.
	 * FIXME: parse scheduler config
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createDateTimeInterceptor() {
		return createBodyInterceptor(AbstractCodaClient::fixAllDates);
	}

	/**
	 * Replaces dates of the format "2018-07-19 01:29:00+00:00" or "2022-05-18 15:28:09.807554+00:00" with "2022-05-18T15:28:09...".
	 *
	 * @param body a {@link String}
	 * @return the body with all dates replaced with properly formatted values
	 */
	private static final String fixAllDates(final String body) {
		// e.g. replace "2018-07-19 01:29:00+00:00" or "2022-05-18 15:28:09.807554+00:00" with "2022-05-18T15:28:09..."
		return body.replaceAll(
			"(\\d{4}-\\d{2}-\\d{2})\\s*(\\d{2}:\\d{2}:\\d{2})(\\.\\d+)?(\\+00:00)",
			"$1T$2$3$4");
	}

	private static Interceptor createBodyInterceptor(
		final Function<String, String> function) {
		return chain -> {
			final Request request = chain.request();
			final Response response = chain.proceed(request);

			if (response.code() == 200) {
				final ResponseBody body = response.body();
				final String newResponseBody = function.apply(body.string());
				MediaType contentType = body.contentType();
				ResponseBody responseBody =
					ResponseBody.Companion.create(newResponseBody, contentType);
				return response.newBuilder().body(responseBody).build();
			}

			return response;
		};
	}

	private static JSON createJSON(final ApiClient client) {
		final JSON json = client.getJSON();

		return new JSON() {
			/**
			 * Some double quotes in the snapshot reports are being replaced
			 * with \null. I can't step through the code to find the cause
			 * because it's happening in Kotlin. Wasted many hours trying to fix
			 * it and settled on this hack to fix the problem. Please roll with
			 * it.
			 *
			 * @param body       The JSON string
			 * @param returnType The type to deserialize into
			 * @return
			 * @param <T>
			 */
			@Override
			public <T> T deserialize(final String body, final Type returnType) {
				final String fixedBody = body.replaceAll("\\\\null", "\"");

				jsonLock.lock();
				try {
					rawJsonQueue.add(fixedBody);
				} finally {
					jsonLock.unlock();
				}

				return json.deserialize(fixedBody, returnType);
			}
		};
	}

	@Override
	public final CodaClient login() throws ApiException {
		accessToken.set(null);
		xsrfToken.set(null);

		final SessionLogin credentials =
			new SessionLogin().username(username).password(password);
		commonApi.commonAuthSessionCreate(credentials, null);

		return this;
	}

	/**
	 * @return the {@link String raw JSON body} of the most recent request or <code>null</code> if there are no recent requests
	 */
	protected final String getRawJsonOfMostRecentCall() {
		jsonLock.lock();
		try {
			return rawJsonQueue.peek();
		} finally {
			jsonLock.unlock();
		}
	}

}
