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

import static com.iland.coda.footprint.AbstractCodaClient.MAX_PAGE_SIZE;
import static com.iland.coda.footprint.SimpleCodaClient.throwDuplicateKeyException;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.AdminUser;
import net.codacloud.model.AgentlessScannerSrz;
import net.codacloud.model.ExtendMessage;
import net.codacloud.model.PaginatedRegistrationLightList;
import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationCreate;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.RegistrationSignupData;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iland.coda.footprint.pagination.Paginator;

/**
 * {@link RetryCodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
final class RetryCodaClient implements CodaClient {

	private static final Logger logger =
		LoggerFactory.getLogger(RetryCodaClient.class);

	private static final Set<Integer> retryCodes;
	static {
		final Set<Integer> set = new HashSet<>();
		set.add(401);
		set.add(403);

		retryCodes = Collections.unmodifiableSet(set);
	}

	private final CodaClient delegatee;
	private final Retryer retryer = RetryerBuilder.newBuilder()
		.retryIfException(t -> t instanceof ApiException && retryCodes.contains(
			((ApiException) t).getCode()))
		.retryIfException(t -> t.getCause() instanceof SocketTimeoutException)
		.withWaitStrategy(WaitStrategies.fibonacciWait(1L, TimeUnit.MINUTES))
		.withStopStrategy(StopStrategies.stopAfterDelay(3L, TimeUnit.MINUTES))
		.build();

	RetryCodaClient(final CodaClient delegatee) {
		this.delegatee =
			Preconditions.checkNotNull(delegatee, "delegatee must not be null");
	}

	@Override
	public CodaClient login() throws ApiException {
		retryIfNecessary(() -> delegatee.login());

		return this;
	}

	@Override
	public Set<RegistrationLight> listRegistrations(final String category)
		throws ApiException {
		if (delegatee instanceof SimpleCodaClient) {
			final SimpleCodaClient simpleCodaClient =
				(SimpleCodaClient) delegatee;

			return new Paginator<>(pageNo -> retryIfNecessary(
				() -> simpleCodaClient.adminApi.adminRegistrationsLightRetrieve(
					category, pageNo, MAX_PAGE_SIZE)),
				PaginatedRegistrationLightList::getPage,
				PaginatedRegistrationLightList::getTotalPages,
				PaginatedRegistrationLightList::getTotalCount,
				PaginatedRegistrationLightList::getItems).fetchAllAsync();
		}

		return retryIfNecessary(() -> delegatee.listRegistrations(category));
	}

	@Override
	public Set<Account> listAccounts(final Integer accountId)
		throws ApiException {
		return retryIfNecessary(() -> delegatee.listAccounts(accountId));
	}

	@Override
	public RegistrationLight createRegistration(
		final RegistrationCreate registration) throws ApiException {
		return retryIfNecessary(
			() -> delegatee.createRegistration(registration));
	}

	public Registration updateRegistration(final Integer registrationId,
		final Registration registration) throws ApiException {
		return retryIfNecessary(
			() -> delegatee.updateRegistration(registrationId, registration));
	}

	@Override
	public void deleteRegistration(final RegistrationLight registration)
		throws ApiException {
		retryIfNecessary(() -> {
			delegatee.deleteRegistration(registration);

			return null;
		});
	}

	@Override
	public List<AgentlessScannerSrz> getScanners(final Integer accountId)
		throws ApiException {
		return retryIfNecessary(() -> delegatee.getScanners(accountId));
	}

	@Override
	public void updateScanSurface(final List<String> targets,
		final List<Integer> scanners, final Integer accountId)
		throws ApiException {
		final List<ExtendMessage> batches =
			new ScanSurfaceBatcher().createBatches(targets).stream()
				.map(batch -> batch.scanners(scanners))
				.collect(Collectors.toList());
		for (final ExtendMessage batch : batches) {
			updateScanSurface(batch, accountId);
		}
	}

	@Override
	public void updateScanSurface(final ExtendMessage message,
		final Integer accountId) throws ApiException {
		retryIfNecessary(() -> {
			delegatee.updateScanSurface(message, accountId);

			return null;
		});
	}

	@Override
	public void rescan(final Integer accountId) throws ApiException {
		retryIfNecessary(() -> {
			delegatee.rescan(accountId);

			return null;
		});
	}

	@Override
	public void rescan(final Integer scannerId, final Integer accountId)
		throws ApiException {
		retryIfNecessary(() -> {
			delegatee.rescan(scannerId, accountId);

			return null;
		});
	}

	@Override
	public ScanStatus getScanStatus(final Integer accountId)
		throws ApiException {
		return retryIfNecessary(() -> delegatee.getScanStatus(accountId));
	}


	@Override
	public List<ScanSurfaceEntry> getScanSurface(final Integer scannerId,
		final Integer accountId) throws ApiException {
		return retryIfNecessary(
			() -> delegatee.getScanSurface(scannerId, accountId));
	}

	@Override
	public Map<LocalDateTime, LazyCVR> getReports(final ReportType reportType,
		final Integer accountId) throws ApiException {
		if (delegatee instanceof SimpleCodaClient) {
			final SimpleCodaClient simpleCodaClient =
				(SimpleCodaClient) delegatee;

			return getReportTimestamps(reportType, accountId).stream().collect(
				Collectors.toMap(GenerationDate::parse,
					timestamp -> () -> retryIfNecessary(
						() -> simpleCodaClient.getReport(timestamp, reportType,
							accountId)), throwDuplicateKeyException(),
					LinkedHashMap::new));
		}

		return retryIfNecessary(
			() -> delegatee.getReports(reportType, accountId));
	}

	@Override
	public Map<LocalDateTime, LazyCvrJson> getReportsJson(
		final ReportType reportType, final Integer accountId)
		throws ApiException {
		if (delegatee instanceof SimpleCodaClient) {
			final SimpleCodaClient simpleCodaClient =
				(SimpleCodaClient) delegatee;

			return getReportTimestamps(reportType, accountId).stream().collect(
				Collectors.toMap(GenerationDate::parse,
					timestamp -> () -> retryIfNecessary(
						() -> simpleCodaClient.getCvrJson(timestamp, reportType,
							accountId))));
		}

		return retryIfNecessary(
			() -> delegatee.getReportsJson(reportType, accountId));
	}

	@Override
	public List<String> getReportTimestamps(final ReportType reportType,
		final Integer accountId) throws ApiException {
		return retryIfNecessary(
			() -> delegatee.getReportTimestamps(reportType, accountId));
	}

	@Override
	public RegistrationCreate createFullyManagedRegistration(final String label,
		final String description,
		final RegistrationSignupData registrationSignupData,
		final List<Integer> associatedMspUserIds) throws ApiException {
		return retryIfNecessary(
			() -> delegatee.createFullyManagedRegistration(label, description,
				registrationSignupData, associatedMspUserIds));
	}

	@Override
	public List<AdminUser> listUsers() throws ApiException {
		return retryIfNecessary(() -> delegatee.listUsers());
	}

	@SuppressWarnings({"unchecked"})
	private <V> V retryIfNecessary(final Callable<V> retryable)
		throws ApiException {
		try {
			return (V) retryer.call(() -> {
				try {
					return retryable.call();
				} catch (ApiException e) {
					switch (e.getCode()) {
						case 401:
						case 403:
							login();
							break;
					}

					if (e.getCause() instanceof SocketTimeoutException) {
						logger.warn("Yet another timeout...");
					}

					throw e;
				}
			});
		} catch (ExecutionException | RetryException e) {
			Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);

			throw new ApiException(e);
		}
	}

}
