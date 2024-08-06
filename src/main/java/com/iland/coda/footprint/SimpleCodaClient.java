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

import static com.google.common.base.Predicates.not;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.iland.coda.footprint.Registrations.toLight;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;
import com.google.common.io.ByteStreams;
import net.codacloud.ApiException;
import net.codacloud.api.ConsoleApi;
import net.codacloud.model.Account;
import net.codacloud.model.AdminUser;
import net.codacloud.model.AgentlessScannerSrz;
import net.codacloud.model.CVR;
import net.codacloud.model.ExtendMessageRequest;
import net.codacloud.model.PaginatedAccountList;
import net.codacloud.model.PaginatedRegistrationLightList;
import net.codacloud.model.PaginatedScanSurfaceEntryList;
import net.codacloud.model.PatchedScanSurfaceRescanRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iland.coda.footprint.pagination.Paginator;
import com.iland.networking.NetworkUtils;

/**
 * {@link SimpleCodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
final class SimpleCodaClient extends AbstractCodaClient {

	private static final Logger logger =
		LoggerFactory.getLogger(SimpleCodaClient.class);

	SimpleCodaClient(final String apiBasePath,
		final Authentication authentication) {
		super(apiBasePath, authentication);
	}

	@Override
	public Set<RegistrationLight> listRegistrations(final String category)
		throws ApiException {
		logger.debug("Retrieving registrations...");
		final Stopwatch stopwatch = Stopwatch.createStarted();
		try {
			return new Paginator<>(
				pageNo -> adminApi.adminRegistrationsLightRetrieve(category,
					pageNo, DEFAULT_PAGE_SIZE),
				PaginatedRegistrationLightList::getPage,
				PaginatedRegistrationLightList::getTotalPages,
				PaginatedRegistrationLightList::getTotalCount,
				PaginatedRegistrationLightList::getItems).fetchAllAsync();
		} finally {
			logger.debug("...registrations retrieved in {}", stopwatch);
		}
	}

	@Override
	public Set<Account> listAccounts(final Integer accountId)
		throws ApiException {
		logger.debug("Retrieving accounts...");
		final Stopwatch stopwatch = Stopwatch.createStarted();
		try {
			return new Paginator<>(
				pageNo -> commonApi.getAccounts(null, pageNo, DEFAULT_PAGE_SIZE,
					accountId), PaginatedAccountList::getPage,
				PaginatedAccountList::getTotalPages,
				PaginatedAccountList::getTotalCount,
				PaginatedAccountList::getItems).fetchAllAsync();
		} finally {
			logger.debug("...registrations retrieved in {}", stopwatch);
		}
	}

	@Override
	public RegistrationLight createRegistration(
		final RegistrationCreateRequest newRegistration) throws ApiException {
		// this is really a PUT, not an update
		return toLight(adminApi.adminRegistrationsUpdate(newRegistration));
	}

	@Override
	public Registration updateRegistration(final Integer registrationId,
		final RegistrationEditRequest edit) throws ApiException {
		return adminApi.adminRegistrationsCreate(registrationId, edit);
	}

	@Override
	public void deleteRegistration(final RegistrationLight registration)
		throws ApiException {
		adminApi.adminRegistrationsDestroy(registration.getId());
	}

	@Override
	public List<AgentlessScannerSrz> getScanners(final Integer accountId)
		throws ApiException {
		return consoleApi.consoleScanSurfaceScannersList(accountId);
	}

	@Override
	public List<ScanUuidScannerId> updateScanSurface(final List<String> targets,
		final List<Integer> scanners, final boolean isNoScanRequest,
		final Integer accountId) throws ApiException {

		final List<String> filteredTargets = NetworkUtils.toStream(targets)
			.filter(not(NetworkUtils::isRFC1918IpAddress))
			.collect(Collectors.toList());

		try {
			return new ScanSurfaceBatcher().createBatches(filteredTargets)
				.stream()
				.map(batch -> batch.scanners(scanners))
				.map(batch -> {
					try {
						return updateScanSurface(batch, isNoScanRequest,
							accountId);
					} catch (final ApiException e) {
						throw new RuntimeException(e);
					}
				})
				.flatMap(List::stream)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		} catch (final RuntimeException e) {
			throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	@Override
	public List<ScanUuidScannerId> updateScanSurface(
		final ExtendMessageRequest message, final boolean isNoScanRequest,
		final Integer accountId) throws ApiException {
		return consoleApi.consoleScanSurfaceCreate(message, isNoScanRequest,
			accountId);
	}

	@Override
	public void deleteScanSurfaceEntry(final ScanSurfaceEntry entry,
		final boolean deleteAssets, final Integer accountId)
		throws ApiException {
		consoleApi.consoleScanSurfaceEntryDestroy(entry.getId(), deleteAssets,
			accountId);
	}

	@Override
	public List<ScanUuidScannerId> rescan(final Integer accountId)
		throws ApiException {
		final PatchedScanSurfaceRescanRequest request =
			new PatchedScanSurfaceRescanRequest();
		return consoleApi.consoleScanSurfaceRescanPartialUpdate(accountId,
			request);
	}

	@Override
	public ScanUuidScannerId rescan(final Integer scannerId,
		final Integer accountId) throws ApiException {
		return consoleApi.consoleScanSurfaceRescanPartialUpdate2(scannerId,
			accountId);
	}

	@Override
	public ScanStatus getScanStatus(final Integer accountId)
		throws ApiException {
		return consoleApi.consoleStatusScanRetrieve(accountId);
	}

	@Override
	public Scan getScanStatus(final String scanId, final Integer accountId)
		throws ApiException {
		return consoleApi.consoleScansRetrieve(scanId, accountId);
	}

	@Override
	public List<ScanSurfaceEntry> getScanSurface(final Integer scannerId,
		final String textFilter, final Integer accountId) throws ApiException {
		logger.debug("Retrieving scan surface...");
		return new Paginator<>(
			pageNo -> consoleApi.consoleScanSurfaceRetrieve(pageNo, scannerId,
				textFilter, accountId), PaginatedScanSurfaceEntryList::getPage,
			PaginatedScanSurfaceEntryList::getTotalPages,
			PaginatedScanSurfaceEntryList::getTotalCount,
			PaginatedScanSurfaceEntryList::getItems).fetchAll();
	}

	@Override
	public Map<LocalDateTime, LazyCVR> getReports(final ReportType reportType,
		final Integer accountId) throws ApiException {
		try {
			return getReportTimestamps(reportType, accountId).stream()
				.collect(Collectors.toMap(GenerationDate::parse,
					timestamp -> () -> getReport(timestamp, reportType,
						accountId), throwDuplicateKeyException(),
					LinkedHashMap::new));
		} catch (RuntimeException e) {
			throw new ApiException(e.getCause());
		}
	}

	static <T> BinaryOperator<T> throwDuplicateKeyException() {
		return (k, v) -> {
			throw new IllegalStateException(
				String.format("Duplicate key %s", k));
		};
	}

	@Override
	public Map<LocalDateTime, LazyCvrJson> getReportsJson(
		final ReportType reportType, final Integer accountId)
		throws ApiException {
		return getReportTimestamps(reportType, accountId).stream()
			.collect(Collectors.toMap(GenerationDate::parse,
				timestamp -> () -> getCvrJson(timestamp, reportType,
					accountId)));
	}

	String getCvrJson(final String timestamp, final ReportType reportType,
		final Integer accountId) throws ApiException {
		jsonLock.lock();
		try {
			getReport(timestamp, reportType, accountId);

			final List<String> recentJson = getRawJsonOfRecentCalls();
			final String cvr = recentJson.remove(0);
			final String techReport = recentJson.remove(0);

			if (cvr.contains("\"technicalReport\":[]")) {
				final String technicalReport =
					techReport.substring(1, techReport.length() - 1);

				return cvr.replace("\"technicalReport\":[]", technicalReport);
			}

			return cvr;
		} finally {
			jsonLock.unlock();
		}
	}

	@Override
	public List<String> getReportTimestamps(final ReportType reportType,
		final Boolean isXlsxDownload, final Integer accountId)
		throws ApiException {
		return consoleApi.allCvrDatesRetrieve(reportType.value(),
			isXlsxDownload, accountId);
	}

	CVR getReport(final String timestamp, final ReportType reportType,
		final Integer accountId) throws ApiException {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		try {
			final CVR cvr =
				consoleApi.cvrRetrieve(timestamp, reportType.value(), null,
					accountId);

			if (cvr.getTechnicalReport().isEmpty()) {
				final CVR techReport =
					consoleApi.cvrRetrieve(timestamp, reportType.value(), true,
						accountId);
				cvr.setTechnicalReport(techReport.getTechnicalReport());
			}

			return cvr;
		} finally {
			logger.debug("Retrieved report after {}", stopwatch);
		}
	}

	@Override
	public File getCyberRiskReport(final Integer accountId)
		throws ApiException {
		return getCyberRiskReportViaUrlConnection(accountId);
	}

	/**
	 * For reasons beyond my comprehension the generated SDK returns a mangled
	 * PDF that is much larger than it should be. After hours wasted trying to
	 * track down the problem I opted to use {@link URLConnection} instead.
	 *
	 * @see {@link ConsoleApi#consoleReportingCyberRiskReportRetrieve(Boolean, Integer)}
	 */
	private File getCyberRiskReportViaUrlConnection(final Integer accountId)
		throws ApiException {
		final String basePath = apiClient.getBasePath();
		try {
			URL url = new URL(basePath + "/console/reporting/cyberRiskReport/");
			final HttpURLConnection urlConnection =
				(HttpURLConnection) url.openConnection();
			urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(60));
			urlConnection.setRequestProperty("Accept", "application/pdf");
			urlConnection.setRequestProperty("FootprintTenantId",
				accountId.toString());
			if (authentication instanceof KeyAuthentication) {
				final KeyAuthentication keyAuthentication =
					(KeyAuthentication) authentication;
				urlConnection.setRequestProperty("FootprintApiKey",
					keyAuthentication.getApiKey());
			}
			urlConnection.connect();

			int code = urlConnection.getResponseCode();
			final String contentType = urlConnection.getContentType();
			if (code != 200 || !"application/pdf".equals(contentType)) {
				throw new ApiException(String.format(
					"Failed to retrieve cyber risk report with code=%d", code));
			}

			final String contentDispositionUnchecked =
				urlConnection.getHeaderField("Content-Disposition");
			final Optional<String> contentDisposition =
				Optional.ofNullable(contentDispositionUnchecked);
			final String attachment = "attachment;filename=";
			final String defaultName = "Cyber Risk Report.pdf";
			final String filename =
				contentDisposition.filter(cd -> cd.startsWith(attachment))
					.map(cd -> cd.substring(attachment.length()))
					.map(cd -> cd.replaceAll("\\W+", "_")) // sanitize filename
					.orElse(defaultName);
			final File file = new File("/tmp/" + filename);
			file.deleteOnExit();

			try (final InputStream in = urlConnection.getInputStream();
				final OutputStream out = Files.newOutputStream(file.toPath())) {
				ByteStreams.copy(in, out);
			}

			return file;
		} catch (IOException e) {
			throw new ApiException(e);
		}
	}

	@Override
	public RegistrationCreateRequest createFullyManagedRegistration(
		final String label, final String description,
		final RegistrationSignupDataRequest request,
		final List<Integer> associatedMspUserIds) {
		final RegistrationCreateRequest allMspAccessible =
			new RegistrationCreateRequest().label(label)
				.description(description)
				.manageType(
					RegistrationCreateRequest.ManageTypeEnum.FULLY_MANAGED)
				.signupData(request)
				.associatedMspGroupIds(Collections.emptyList())
				.isAllMspAccessible(true);

		allMspAccessible.setAssociatedMspUserIds(associatedMspUserIds);

		return allMspAccessible;
	}

	@Override
	public List<AdminUser> listUsers() throws ApiException {
		return adminApi.adminUsersRetrieve();
	}

	@Override
	public List<Task> listScheduledTasks(final String scannerId,
		final Integer accountId) throws ApiException {
		return consoleApi.consoleSchedulerList(scannerId, accountId);
	}

	@Override
	public Task updateSchedule(final String taskId, final String action,
		final Integer accountId) throws ApiException {
		return consoleApi.consoleSchedulerCreate2(action, taskId, accountId,
			"");
	}

	@Override
	public Task updateSchedule(final String taskId,
		final TaskEditRequest taskEditRequest, final Integer accountId)
		throws ApiException {
		return consoleApi.consoleSchedulerCreate(taskId, taskEditRequest,
			accountId);
	}

}
