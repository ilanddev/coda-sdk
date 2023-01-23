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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.codacloud.model.ExtendMessage;
import net.codacloud.model.PaginatedRegistrationLightList;
import net.codacloud.model.PaginatedScanSurfaceEntryList;
import net.codacloud.model.PatchedScanSurfaceRescan;
import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationCreate;
import net.codacloud.model.RegistrationEdit;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.RegistrationSignupData;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iland.coda.footprint.pagination.Paginator;

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
		return commonApi.commonAuthSessionAccountsList(accountId).stream()
			.collect(Collectors.toSet());
	}

	@Override
	public RegistrationLight createRegistration(
		final RegistrationCreate newRegistration) throws ApiException {
		// this is really a PUT, not an update
		return toLight(adminApi.adminRegistrationsUpdate(newRegistration));
	}

	@Override
	public Registration updateRegistration(final Integer registrationId,
		final RegistrationEdit edit) throws ApiException {
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
		consoleApi.consoleScanSurfaceCreate(message, accountId);
	}

	@Override
	public void rescan(final Integer accountId) throws ApiException {
		final PatchedScanSurfaceRescan rescan = new PatchedScanSurfaceRescan();
		consoleApi.consoleScanSurfaceRescanPartialUpdate(accountId, rescan);
	}


	@Override
	public void rescan(final Integer scannerId, final Integer accountId)
		throws ApiException {
		consoleApi.consoleScanSurfaceRescanPartialUpdate2(scannerId, accountId);
	}

	@Override
	public ScanStatus getScanStatus(final Integer accountId)
		throws ApiException {
		return consoleApi.consoleStatusScanRetrieve(accountId);
	}


	@Override
	public List<ScanSurfaceEntry> getScanSurface(final Integer scannerId,
		final Integer accountId) throws ApiException {
		logger.debug("Retrieving scan surface...");
		return new Paginator<>(
			pageNo -> consoleApi.consoleScanSurfaceRetrieve(pageNo, scannerId,
				accountId), PaginatedScanSurfaceEntryList::getPage,
			PaginatedScanSurfaceEntryList::getTotalPages,
			PaginatedScanSurfaceEntryList::getTotalCount,
			PaginatedScanSurfaceEntryList::getItems).fetchAll();
	}

	@Override
	public Map<LocalDateTime, LazyCVR> getReports(final ReportType reportType,
		final Integer accountId) throws ApiException {
		try {
			return getReportTimestamps(reportType, accountId).stream().collect(
				Collectors.toMap(GenerationDate::parse,
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
		return getReportTimestamps(reportType, accountId).stream().collect(
			Collectors.toMap(GenerationDate::parse,
				timestamp -> () -> getCvrJson(timestamp, reportType,
					accountId)));
	}

	String getCvrJson(final String timestamp, final ReportType reportType,
		final Integer accountId) throws ApiException {
		jsonLock.lock();
		try {
			getReport(timestamp, reportType, accountId);

			return getRawJsonOfMostRecentCall();
		} finally {
			jsonLock.unlock();
		}
	}

	@Override
	public List<String> getReportTimestamps(final ReportType reportType,
		final Integer accountId) throws ApiException {
		return consoleApi.consoleReportRetrieve(reportType.value(), accountId);
	}

	CVR getReport(final String timestamp, final ReportType reportType,
		final Integer accountId) throws ApiException {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		try {
			return consoleApi.consoleReportRetrieve2(timestamp,
				reportType.value(), accountId);
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
	 * @see {@link ConsoleApi#consoleReportingCyberRiskReportRetrieve(Integer)}
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
				final OutputStream out = new FileOutputStream(file)) {
				ByteStreams.copy(in, out);
			}

			return file;
		} catch (IOException e) {
			throw new ApiException(e);
		}
	}

	@Override
	public RegistrationCreate createFullyManagedRegistration(final String label,
		final String description,
		final RegistrationSignupData registrationSignupData,
		final List<Integer> associatedMspUserIds) {
		final RegistrationCreate allMspAccessible =
			new RegistrationCreate().label(label).description(description)
				.manageType(RegistrationCreate.ManageTypeEnum.FULLY_MANAGED)
				.signupData(registrationSignupData)
				.associatedMspGroupIds(Collections.emptyList())
				.isAllMspAccessible(true);

		allMspAccessible.setAssociatedMspUserIds(associatedMspUserIds);

		return allMspAccessible;
	}

	@Override
	public List<AdminUser> listUsers() throws ApiException {
		return adminApi.adminUsersRetrieve();
	}

}
