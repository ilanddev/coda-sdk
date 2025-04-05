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

import static com.iland.coda.footprint.Registrations.toLight;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.AdminUser;
import net.codacloud.model.AgentlessScannerSrz;
import net.codacloud.model.ExtendMessageRequest;
import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationCreateRequest;
import net.codacloud.model.RegistrationEditRequest;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.RegistrationSignupDataRequest;
import net.codacloud.model.Scan;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import net.codacloud.model.ScanUuidScannerId;
import net.codacloud.model.Task;
import net.codacloud.model.TaskEditRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CachingCodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
final class CachingCodaClient implements CodaClient {

	private static final Logger logger =
		LoggerFactory.getLogger(CachingCodaClient.class);
	private static final int CONCURRENCY_LEVEL = 10;

	private static final String DEFAULT_CATEGORY = "*";
	private static final Integer DEFAULT_ACCOUNT_ID = 0;
	private static final String DEFAULT_USER_KEY = "*";

	private final CodaClient delegatee;

	private final LoadingCache<String, Set<RegistrationLight>>
		registrationsCache = CacheBuilder.newBuilder()
		.concurrencyLevel(CONCURRENCY_LEVEL)
		.expireAfterWrite(1, TimeUnit.DAYS)
		.removalListener(
			CachingCodaClient.<String, Set<RegistrationLight>>createRemovalListener(
				"Registration cache"))
		.build(new CacheLoader<>() {
			@NotNull
			@Override
			public Set<RegistrationLight> load(@NotNull final String category)
				throws Exception {
				return delegatee.listRegistrations(
					Objects.equals(category, DEFAULT_CATEGORY) ?
						null :
						category);
			}
		});

	private final LoadingCache<Integer, Set<Account>> accountCache =
		CacheBuilder.newBuilder()
			.concurrencyLevel(CONCURRENCY_LEVEL)
			.expireAfterWrite(1, TimeUnit.DAYS)
			.removalListener(
				CachingCodaClient.<Integer, Set<Account>>createRemovalListener(
					"Account cache"))
			.build(new CacheLoader<>() {
				@NotNull
				@Override
				public Set<Account> load(@NotNull final Integer accountId)
					throws Exception {
					return delegatee.listAccounts(
						Objects.equals(accountId, DEFAULT_ACCOUNT_ID) ?
							null :
							accountId);
				}
			});

	private final LoadingCache<Integer, List<AgentlessScannerSrz>>
		scannerCache = CacheBuilder.newBuilder()
		.concurrencyLevel(CONCURRENCY_LEVEL)
		.expireAfterWrite(1, TimeUnit.HOURS)
		.removalListener(
			CachingCodaClient.<Integer, List<AgentlessScannerSrz>>createRemovalListener(
				"Scanner cache"))
		.build(new CacheLoader<>() {
			@NotNull
			@Override
			public List<AgentlessScannerSrz> load(
				@NotNull final Integer accountId) throws Exception {
				return delegatee.getScanners(
					Objects.equals(accountId, DEFAULT_ACCOUNT_ID) ?
						null :
						accountId);
			}
		});
	private final LoadingCache<String, List<AdminUser>> userCache =
		CacheBuilder.newBuilder()
			.concurrencyLevel(CONCURRENCY_LEVEL)
			.expireAfterWrite(1, TimeUnit.HOURS)
			.removalListener(
				CachingCodaClient.<String, List<AdminUser>>createRemovalListener(
					"User cache"))
			.build(new CacheLoader<>() {
				@NotNull
				@Override
				public List<AdminUser> load(@NotNull final String value)
					throws Exception {
					return delegatee.listUsers();
				}
			});

	CachingCodaClient(final CodaClient delegatee) {
		this.delegatee =
			Preconditions.checkNotNull(delegatee, "delegatee must not be null");
	}

	@Override
	public CodaClient login() throws ApiException {
		delegatee.login();

		return this;
	}

	@Override
	public Set<RegistrationLight> listRegistrations(final String category)
		throws ApiException {
		return Collections.unmodifiableSet(getRegistrations(category));
	}

	/**
	 * Returns the registrations for a given category. If the default category
	 * is supplied then the results may be from the cache. <strong>Only the
	 * default category is cached to simplify cache validation!</strong> The
	 * results should be wrapped in an immutable collection before returning
	 * outside this class.
	 */
	private Set<RegistrationLight> getRegistrations(final String category)
		throws ApiException {
		if (category == null || DEFAULT_CATEGORY.equals(category)) {
			try {
				// only the default category is cached to simplify cache validation
				return registrationsCache.get(DEFAULT_CATEGORY);
			} catch (ExecutionException e) {
				Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);
				throw new ApiException(e);
			}
		}

		return delegatee.listRegistrations(category);
	}

	@Override
	public Set<Account> listAccounts(final Integer accountId)
		throws ApiException {
		try {
			final Integer accountIdKey =
				Optional.ofNullable(accountId).orElse(DEFAULT_ACCOUNT_ID);

			final Set<Account> accounts = accountCache.get(accountIdKey);

			return Collections.unmodifiableSet(accounts);
		} catch (ExecutionException e) {
			Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);
			throw new ApiException(e);
		}
	}

	@Override
	public RegistrationLight createRegistration(
		final RegistrationCreateRequest registration) throws ApiException {
		final RegistrationLight newRegistration =
			delegatee.createRegistration(registration);

		getRegistrations(DEFAULT_CATEGORY).add(newRegistration);
		accountCache.invalidateAll();

		return newRegistration;
	}

	@Override
	public Registration updateRegistration(final Integer registrationId,
		final RegistrationEditRequest edit) throws ApiException {
		final Registration updatedRegistration =
			delegatee.updateRegistration(registrationId, edit);

		final Set<RegistrationLight> registrations =
			getRegistrations(DEFAULT_CATEGORY);
		registrations.stream()
			.filter(r -> registrationId.equals(r.getId()))
			.findFirst()
			.ifPresent(r -> {
				registrations.remove(r);
				registrations.add(toLight(updatedRegistration));
			});

		accountCache.invalidateAll();

		return updatedRegistration;
	}

	@Override
	public void deleteRegistration(final RegistrationLight registration)
		throws ApiException {
		delegatee.deleteRegistration(registration);
		accountCache.invalidateAll();

		final Set<RegistrationLight> registrations =
			getRegistrations(DEFAULT_CATEGORY);
		registrations.stream()
			.filter(r -> Objects.equals(r.getId(), registration.getId()))
			.findFirst()
			.ifPresent(registrations::remove);
	}

	@Override
	public List<AgentlessScannerSrz> getScanners(final Integer accountId)
		throws ApiException {
		try {
			final Integer accountIdKey =
				Optional.ofNullable(accountId).orElse(DEFAULT_ACCOUNT_ID);

			final List<AgentlessScannerSrz> scanners =
				scannerCache.get(accountIdKey);

			return Collections.unmodifiableList(scanners);
		} catch (ExecutionException e) {
			Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);
			throw new ApiException(e);
		}
	}

	@Override
	public List<ScanUuidScannerId> updateScanSurface(final List<String> targets,
		final List<Integer> scanners, final boolean isNoScanRequest,
		final Integer accountId) throws ApiException {
		// TODO: invalidate scan surface cache
		return delegatee.updateScanSurface(targets, scanners, isNoScanRequest,
			accountId);
	}

	@Override
	public List<ScanUuidScannerId> updateScanSurface(
		final ExtendMessageRequest message, final boolean isNoScanRequest,
		final Integer accountId) throws ApiException {
		return delegatee.updateScanSurface(message, isNoScanRequest, accountId);
	}

	@Override
	public void deleteScanSurfaceEntry(final ScanSurfaceEntry entry,
		final boolean deleteAssets, final Integer accountId)
		throws ApiException {
		delegatee.deleteScanSurfaceEntry(entry, deleteAssets, accountId);
	}

	@Override
	public List<ScanUuidScannerId> rescan(final Integer accountId)
		throws ApiException {
		return delegatee.rescan(accountId);
	}

	@Override
	public ScanUuidScannerId rescan(final Integer scannerId,
		final Integer accountId) throws ApiException {
		return delegatee.rescan(scannerId, accountId);
	}

	@Override
	public ScanStatus getScanStatus(final Integer accountId)
		throws ApiException {
		return delegatee.getScanStatus(accountId);
	}

	@Override
	public Scan getScanStatus(final String scanId, final Integer accountId)
		throws ApiException {
		return delegatee.getScanStatus(scanId, accountId);
	}

	@Override
	public List<ScanSurfaceEntry> getScanSurface(final Integer scannerId,
		final String textFilter, final Integer accountId) throws ApiException {
		return delegatee.getScanSurface(scannerId, textFilter, accountId);
	}

	@Override
	public Map<LocalDateTime, LazyCVR> getReports(final ReportType reportType,
		final Integer accountId) throws ApiException {
		return delegatee.getReports(reportType, accountId);
	}

	@Override
	public Map<LocalDateTime, LazyCvrJson> getReportsJson(
		final ReportType reportType, final Integer accountId)
		throws ApiException {
		return delegatee.getReportsJson(reportType, accountId);
	}

	@Override
	public List<String> getReportTimestamps(final ReportType reportType,
		final Boolean isXlsxDownload, final Integer accountId)
		throws ApiException {
		return delegatee.getReportTimestamps(reportType, isXlsxDownload,
			accountId);
	}

	@Override
	public File getCyberRiskReport(final Integer accountId)
		throws ApiException {
		return delegatee.getCyberRiskReport(accountId);
	}

	@Override
	public RegistrationCreateRequest createFullyManagedRegistration(
		final String label, final String description,
		final RegistrationSignupDataRequest request,
		final List<Integer> associatedMspUserIds) throws ApiException {
		return delegatee.createFullyManagedRegistration(label, description,
			request, associatedMspUserIds);
	}

	@Override
	public List<AdminUser> listUsers() throws ApiException {
		try {
			final List<AdminUser> users = userCache.get(DEFAULT_USER_KEY);

			return Collections.unmodifiableList(users);
		} catch (ExecutionException e) {
			Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);
			throw new ApiException(e);
		}
	}

	@Override
	public List<Task> listScheduledTasks(final String scannerId,
		final Integer accountId) throws ApiException {
		return delegatee.listScheduledTasks(scannerId, accountId);
	}

	@Override
	public Task updateSchedule(final String taskId, final String action,
		final Integer accountId) throws ApiException {
		return delegatee.updateSchedule(taskId, action, accountId);
	}

	@Override
	public Task updateSchedule(final String taskId,
		final TaskEditRequest taskEditRequest, final Integer accountId)
		throws ApiException {
		return delegatee.updateSchedule(taskId, taskEditRequest, accountId);
	}

	Map<Integer, Set<Account>> getAccountCache() {
		return Collections.unmodifiableMap(accountCache.asMap());
	}

	private static <K, V> RemovalListener<K, V> createRemovalListener(
		final String name) {
		return notification -> logger.debug("{}: '{}' was {} because it was {}",
			name, notification.getKey(),
			notification.wasEvicted() ? "evicted" : "removed",
			notification.getCause());
	}

}
