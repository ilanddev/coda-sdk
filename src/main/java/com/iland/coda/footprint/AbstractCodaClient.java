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

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.google.common.collect.EvictingQueue;
import net.codacloud.ApiClient;
import net.codacloud.ApiException;
import net.codacloud.JSON;
import net.codacloud.api.AdminApi;
import net.codacloud.api.BrandingApi;
import net.codacloud.api.CommonApi;
import net.codacloud.api.ConsoleApi;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * {@link AbstractCodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
abstract class AbstractCodaClient implements CodaClient {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	final Authentication authentication;
	final XsrfInterceptor xsrfInterceptor;

	protected final ApiClient apiClient;
	protected final AdminApi adminApi;
	protected final BrandingApi brandingApi;
	protected final CommonApi commonApi;
	protected final ConsoleApi consoleApi;

	private static final Queue<String> rawJsonQueue = EvictingQueue.create(2);
	protected static final Lock jsonLock = new ReentrantLock();

	AbstractCodaClient(final String apiBasePath,
		final Authentication authentication) {
		this.authentication =
			requireNonNull(authentication, "authentication must not be null");
		this.xsrfInterceptor = new XsrfInterceptor();

		final OkHttpClient client =
			createClient(authentication, xsrfInterceptor,
				createEmptyStringInterceptor(),
				createReportDataFromNoneInterceptor(),
				createSchedulerConfigHackInterceptor(),
				createDateTimeInterceptor());

		final ApiClient apiClient = new ApiClient(client);
		apiClient.setBasePath(apiBasePath);
		apiClient.setJSON(createJSON(apiClient));

		this.apiClient = apiClient;
		this.adminApi = new AdminApi(apiClient);
		this.brandingApi = new BrandingApi(apiClient);
		this.commonApi = new CommonApi(apiClient);
		this.consoleApi = new ConsoleApi(apiClient);
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
	 * Replaces empty strings with null values to work around an error in GSON.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createEmptyStringInterceptor() {
		// empty Strings cause a JSON parse exception
		return createBodyInterceptor(body -> body.replaceAll("\"\"", "null"));
	}

	/**
	 * Replaces reportDataFrom value to bypass invalid OpenAPI declaration.
	 *
	 * @return an {@link Interceptor interceptor}
	 */
	private Interceptor createReportDataFromNoneInterceptor() {
		return createBodyInterceptor(
			body -> body.replaceAll("\"reportDataFrom\":\\s*\"None\"",
				"\"reportDataFrom\":null"));
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
	private static String fixAllDates(final String body) {
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
			 * @param <T>        the return type
			 * @return an instance of {@link T}
			 */
			@Override
			public <T> T deserialize(final String body, final Type returnType) {
				final String fixedBody = body.replaceAll("\\\\null", "\"")
					.replaceAll("\"criticalLevel\":\"(\\w+)\"",
						"\"criticalLevel\":-1");

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
		xsrfInterceptor.clearXsrfToken();

		authentication.authenticate(apiClient);

		return this;
	}

	/**
	 * @return a {@link List list} of the {@link String raw JSON body} of the most recent requests in FIFO order
	 */
	protected final List<String> getRawJsonOfRecentCalls() {
		jsonLock.lock();
		try {
			final List<String> list = new ArrayList<>();
			list.addAll(rawJsonQueue);

			return list;
		} finally {
			jsonLock.unlock();
		}
	}

}
