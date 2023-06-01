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

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.AdminUser;
import net.codacloud.model.AgentlessScannerSrz;
import net.codacloud.model.CVR;
import net.codacloud.model.ExtendMessageRequest;
import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationCreateRequest;
import net.codacloud.model.RegistrationEditRequest;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.RegistrationSignupData;
import net.codacloud.model.RegistrationSignupDataRequest;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import net.codacloud.model.ScanUuidScannerId;
import net.codacloud.model.Task;
import net.codacloud.model.TaskEditRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CodaClient}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public interface CodaClient {

	enum ReportType {
		HISTORIC("historic"),
		SNAPSHOT("snapshot");
		private final String reportType;

		ReportType(final String reportType) {
			this.reportType = reportType;
		}

		public String value() {
			return reportType;
		}
	}


	/**
	 * Lazily retrieve a {@link CVR report}.
	 */
	@FunctionalInterface
	interface LazyCVR {

		CVR retrieve() throws ApiException;

		/**
		 * @return the {@link CVR} or {@literal null} in the event of an {@link ApiException}
		 */
		default CVR retrieveUnchecked() {
			try {
				return retrieve();
			} catch (final ApiException e) {
				return null;
			}
		}

	}


	/**
	 * Lazily retrieve a {@link CVR report} as {@link String JSON}.
	 */
	@FunctionalInterface
	interface LazyCvrJson {

		String retrieveJson() throws ApiException;

	}

	default Logger logger() {
		return LoggerFactory.getLogger(CodaClient.class);
	}

	/**
	 * Authenticate against the CODA service.
	 *
	 * @return {@link CodaClient this}
	 * @throws ApiException ...
	 */
	CodaClient login() throws ApiException;

	/**
	 * Find or create a {@link RegistrationLight registration} for the supplied {@link String label} and {@link String description}.
	 *
	 * @param label       the {@link String label}
	 * @param description the {@link String description}
	 * @return the {@link RegistrationLight registration}
	 * @throws ApiException ...
	 */
	default RegistrationLight findOrCreateRegistration(final String label,
		final String description) throws ApiException {
		try {
			return getRegistrationForLabel(label).orElseGet(() -> {
				try {
					return createRegistration(label, description);
				} catch (final ApiException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (final RuntimeException e) {
			throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	/**
	 * Returns an {@link Optional} {@link RegistrationLight registration} with the supplied {@link String label}.
	 *
	 * @param label the {@link String label} for a {@link Registration}
	 * @return an {@link Optional} {@link RegistrationLight registration}
	 * @throws ApiException ...
	 */
	default Optional<RegistrationLight> getRegistrationForLabel(
		final String label) throws ApiException {
		return listRegistrations().stream()
			.sorted(Comparator.comparingLong(RegistrationLight::getId))
			.filter(r -> label.startsWith(r.getLabel())).findFirst();
	}

	/**
	 * Provides a listing of active registrations.
	 *
	 * @return a listing of active registrations
	 * @throws ApiException ...
	 */
	default Set<RegistrationLight> listRegistrations() throws ApiException {
		return listRegistrations(null);
	}

	/**
	 * Provides a listing of active registrations for a specific category.
	 *
	 * @param category the category of registrations you want
	 * @return a listing of active registrations for a specific category
	 * @throws ApiException ...
	 */
	Set<RegistrationLight> listRegistrations(final String category)
		throws ApiException;

	/**
	 * Returns the {@link Integer accountId} for the supplied {@link String label}.
	 *
	 * @param label a {@link String label}
	 * @return the {@link Integer accountId} for the supplied {@link String label}
	 * @throws ApiException ...
	 */
	default Integer labelToAccountId(final String label) throws ApiException {
		return findAccountWithName(label).map(Account::getId).orElseThrow(
			() -> new ApiException(
				String.format("no account exists for name '%s'", label)));
	}

	/**
	 * Returns the {@link Integer accountId} for the supplied {@link RegistrationLight registration}.
	 *
	 * @param registration a {@link RegistrationLight registration}
	 * @return the {@link Integer accountId} for the supplied {@link RegistrationLight registration}
	 * @throws ApiException ...
	 */
	default Integer registrationToAccountId(
		final RegistrationLight registration) throws ApiException {
		final String label = registration.getLabel();
		return findAccountWithName(label).map(Account::getId).orElseThrow(
			() -> new ApiException(
				String.format("no account exists for name '%s'", label)));
	}

	/**
	 * Returns an {@link Optional} {@link Account} with the supplied name.
	 *
	 * @param name the name of the {@link Account}
	 * @return an {@link Optional} {@link Account} with the supplied name
	 * @throws ApiException ...
	 */
	default Optional<Account> findAccountWithName(final String name)
		throws ApiException {
		return listAccounts(null).stream()
			.filter(account -> Objects.equals(account.getName(), name))
			.findFirst();
	}

	/**
	 * Lists {@link Account accounts} into which the current user can sign in to.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint
	 * @return a {@link Set set} of {@link Account accounts} that the current user can sign in to
	 * @throws ApiException ...
	 */
	Set<Account> listAccounts(Integer accountId) throws ApiException;

	/**
	 * Crates a registration with blank {@link RegistrationSignupData registration signup data} accessible to all active users with the <code>"Global Admin"</code> role.
	 *
	 * @param label       the label of the registration
	 * @param description a description
	 * @return the created {@link RegistrationLight registration}
	 * @throws ApiException ...
	 */
	default RegistrationLight createRegistration(final String label,
		final String description) throws ApiException {
		logger().info("{}: Registering account '{}'", label, description);

		final RegistrationSignupDataRequest request =
			new RegistrationSignupDataRequest().firstName("").lastName("")
				.email("").companyWebsite("");
		final List<Integer> activeGlobalAdminIds = getActiveGlobalAdminIds();
		final RegistrationCreateRequest newRegistration =
			createFullyManagedRegistration(label, description, request,
				activeGlobalAdminIds);

		return createRegistration(newRegistration);
	}

	/**
	 * Creates a new registration. The registration will automatically be marked as managed by the current logged-in account.
	 *
	 * @param registration the {@link RegistrationCreateRequest registration}
	 * @return a {@link Registration registration}
	 * @throws ApiException ...
	 */
	RegistrationLight createRegistration(
		final RegistrationCreateRequest registration) throws ApiException;

	/**
	 * Updates a registration.
	 *
	 * @param registrationId the registration ID
	 * @param edit           the {@link RegistrationEditRequest edit}
	 * @return a {@link Registration registration}
	 * @throws ApiException ...
	 */
	Registration updateRegistration(final Integer registrationId,
		final RegistrationEditRequest edit) throws ApiException;

	/**
	 * Deletes a registration.
	 *
	 * @param registration the {@link Registration} to delete
	 * @throws ApiException ...
	 */
	void deleteRegistration(RegistrationLight registration) throws ApiException;

	/**
	 * Returns the {@link AgentlessScannerSrz default cloud scanner}.
	 *
	 * @param accountId Account ID you want to receive request for.
	 * @return the {@link AgentlessScannerSrz default cloud scanner}
	 * @throws ApiException if the default cloud scanner is missing
	 */
	default AgentlessScannerSrz getDefaultCloudScanner(final Integer accountId)
		throws ApiException {
		return getScanners(accountId).stream()
			.filter(AgentlessScannerSrz::getIsDefaultCloudScanner).findFirst()
			.orElseThrow(
				() -> new ApiException("default cloud scanner is missing"));
	}

	/**
	 * Returns a {@link Map} of scanner IDs keyed by scanner label.
	 *
	 * @param accountId Account ID you want to receive request for.
	 * @return a {@link Map} of scanner IDs keyed by scanner label
	 * @throws ApiException ...
	 */
	default Map<String, Integer> getScannerIdByLabel(final Integer accountId)
		throws ApiException {
		return getScanners(accountId).stream().collect(
			toMap(AgentlessScannerSrz::getLabel, AgentlessScannerSrz::getId,
				/* in the event of a duplicate key use the newer scanner */
				Math::max));
	}

	/**
	 * Get list of available scanners. This includes the default Cloud Scanner, as well as internal scanners.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List} of available {@link AgentlessScannerSrz scanners}
	 * @throws ApiException ...
	 */
	List<AgentlessScannerSrz> getScanners(Integer accountId)
		throws ApiException;

	/**
	 * An alias of {@link CodaClient#updateScanSurface(List, List, Integer)}.
	 */
	default void triggerScan(List<String> targets, List<Integer> scanners,
		Integer accountId) throws ApiException {
		updateScanSurface(targets, scanners, accountId);
	}

	/**
	 * Updates the scan surface with new data (extend scan surface modal). <strong>This is an idempotent operation!</strong>
	 *
	 * @param targets   a {@link List} of targets, e.g. hostname, IP address, or CIDR notation
	 * @param scanners  There's no documentation on this value. The scanners to use for the scan?
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List} of {@link ScanUuidScannerId scan UUIDs}
	 * @throws ApiException ...
	 */
	List<ScanUuidScannerId> updateScanSurface(List<String> targets,
		List<Integer> scanners, Integer accountId) throws ApiException;

	/**
	 * Updates the scan surface with new data (extend scan surface modal). <strong>This is an idempotent operation!</strong>
	 *
	 * @param message   a {@link ExtendMessageRequest message} of targets and scanners
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List list} of {@link ScanUuidScannerId scan UUIDs}
	 * @throws ApiException ...
	 */
	List<ScanUuidScannerId> updateScanSurface(
		final ExtendMessageRequest message, final Integer accountId)
		throws ApiException;

	/**
	 * Deletes a {@link ScanSurfaceEntry scan surface entry}.
	 *
	 * @param entry        the {@link ScanSurfaceEntry} to delete
	 * @param deleteAssets delete associated devices and applications
	 * @param accountId    Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @throws ApiException ...
	 */
	void deleteScanSurfaceEntry(ScanSurfaceEntry entry, boolean deleteAssets,
		Integer accountId) throws ApiException;

	/**
	 * Rescans all user inputs from Scan Surface.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the {@link RescanScannerScanUuid scan UUID}
	 * @throws ApiException ...
	 */
	List<ScanUuidScannerId> rescan(final Integer accountId) throws ApiException;

	/**
	 * Rescans all user inputs from Scan Surface for requested Agentless Scanner.
	 *
	 * @param scannerId the scanner ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the {@link ScanUuidScannerId scan UUID}
	 * @throws ApiException ...
	 */
	ScanUuidScannerId rescan(Integer scannerId, Integer accountId)
		throws ApiException;

	/**
	 * Provides information regarding currently active scans.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the {@link ScanStatus scan status}
	 * @throws ApiException ...
	 * @deprecated use {@link #getScanStatus(String, Integer)} instead!
	 */
	ScanStatus getScanStatus(Integer accountId) throws ApiException;

	/**
	 * Provides information regarding currently active scans.
	 *
	 * @param scanId    the scan ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the {@link ScanStatus scan status}
	 * @throws ApiException ...
	 */
	ScanStatus getScanStatus(String scanId, Integer accountId)
		throws ApiException;

	/**
	 * Retrieve the collated {@link ScanSurfaceEntry scan surface entries} for all scanners for the given {@link Integer accountId}.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a collated {@link ScanSurfaceEntry scan surface entries} for all scanners for the given {@link Integer accountId}
	 * @throws ApiException ...
	 */
	default Set<ScanSurfaceEntry> getScanSurface(final Integer accountId)
		throws ApiException {
		try {
			return getScannerIdByLabel(accountId).values().stream()
				.map(scannerId -> {
					try {
						return getScanSurface(scannerId, accountId);
					} catch (ApiException e) {
						throw new RuntimeException(e);
					}
				}).flatMap(Collection::stream).collect(Collectors.toSet());
		} catch (RuntimeException e) {
			throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	/**
	 * Retrieve the collated {@link ScanSurfaceEntry scan surface entries} for the given scanners and {@link Integer accountId}.
	 *
	 * @param scannerIds a {@link List} of scanner IDs
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a collated {@link ScanSurfaceEntry scan surface entries} for all scanners for the given {@link Integer accountId}
	 * @throws ApiException ...
	 */
	default Set<ScanSurfaceEntry> getScanSurface(final List<Integer> scannerIds,
		final Integer accountId) throws ApiException {
		try {
			return scannerIds.stream().map(scannerId -> {
				try {
					return getScanSurface(scannerId, accountId);
				} catch (ApiException e) {
					throw new RuntimeException(e);
				}
			}).flatMap(Collection::stream).collect(Collectors.toSet());
		} catch (RuntimeException e) {
			throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	/**
	 * Retrieve {@link List list} of user inputs and the resulting assets.
	 *
	 * @param scannerId Optional scanner ID filter. If not set or invalid, falls back on all scanners
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List list} of {@link ScanSurfaceEntry entries}
	 * @throws ApiException ...
	 */
	List<ScanSurfaceEntry> getScanSurface(Integer scannerId, Integer accountId)
		throws ApiException;

	/**
	 * Retrieve an {@link Optional} containing the latest (i.e. newest) {@link CVR report}.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return an {@link Optional} containing the latest (i.e. newest) {@link CVR report}
	 * @throws ApiException ...
	 */
	default Optional<CVR> getLatestReport(Integer accountId)
		throws ApiException {
		final Map<LocalDateTime, CodaClient.LazyCVR> reports =
			getSnapshotReports(accountId);

		try {
			return reports.keySet().stream().max(
					Comparator.comparing(ldt -> ldt.toEpochSecond(ZoneOffset.UTC)))
				.map(reports::get).map(lazyCvr -> {
					try {
						return lazyCvr.retrieve();
					} catch (ApiException e) {
						throw new RuntimeException(e);
					}
				});
		} catch (final RuntimeException e) {
			throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	/**
	 * Return a {@link Map}, keyed by report day, of lazily loadable {@link CVR snapshot reports}.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCVR lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException ...
	 */
	default Map<LocalDateTime, LazyCVR> getSnapshotReports(Integer accountId)
		throws ApiException {
		return getReports(ReportType.SNAPSHOT, accountId);
	}

	/**
	 * Return a {@link Map}, keyed by report date, of lazily loadable {@link CVR reports}.
	 *
	 * @param reportType the {@link ReportType report type}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCVR lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException ...
	 */
	Map<LocalDateTime, LazyCVR> getReports(ReportType reportType,
		Integer accountId) throws ApiException;

	/**
	 * Return a {@link Map}, keyed by report day, of {@link LazyCvrJson lazily-loadable reports}.
	 *
	 * @param reportType the {@link ReportType report type}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCvrJson lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException ...
	 */
	Map<LocalDateTime, LazyCvrJson> getReportsJson(ReportType reportType,
		Integer accountId) throws ApiException;

	/**
	 * Returns available report dates.
	 *
	 * @param reportType the {@link ReportType}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return available report dates
	 * @throws ApiException ...
	 */
	List<String> getReportTimestamps(ReportType reportType, Integer accountId)
		throws ApiException;

	/**
	 * Retrieve a {@link File pdf} containing the cyber risk report.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link File pdf} containing the cyber risk report
	 * @throws ApiException ...
	 */
	File getCyberRiskReport(Integer accountId) throws ApiException;

	/**
	 * Creates a fully managed {@link Registration} accessible by every MSP user.
	 *
	 * @param label                the label of the registration
	 * @param description          a description
	 * @param request              the signup data
	 * @param associatedMspUserIds the {@link AdminUser user IDs}
	 * @return the {@link RegistrationCreateRequest created registration}
	 * @throws ApiException ...
	 */
	RegistrationCreateRequest createFullyManagedRegistration(final String label,
		final String description, final RegistrationSignupDataRequest request,
		final List<Integer> associatedMspUserIds) throws ApiException;

	/**
	 * Returns a {@link List} of IDs for active users with the <code>"Global Admin"</code> role.
	 *
	 * @return a {@link List} of IDs for active users with the <code>"Global Admin"</code> role
	 * @throws ApiException ...
	 */
	default List<Integer> getActiveGlobalAdminIds() throws ApiException {
		return listUsers().stream().filter(
				u -> u.getIsActive() && Objects.equals("Global Admin", u.getRole()))
			.map(AdminUser::getId).collect(Collectors.toList());
	}

	/**
	 * Lists all users.
	 *
	 * @return a {@link List} of {@link AdminUser users}
	 * @throws ApiException ...
	 */
	List<AdminUser> listUsers() throws ApiException;

	/**
	 * Lists {@link Task scheduled tasks} for the provided {@link String scannerId} and {@link Integer accountId}.
	 *
	 * @param scannerId a {@link AgentlessScannerSrz scanner} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List} of {@link Task schedules}
	 * @throws ApiException ...
	 */
	List<Task> listScheduledTasks(String scannerId, Integer accountId)
		throws ApiException;

	/**
	 * Disables the {@link Task scheduled task} specified by {@link String taskId}.
	 *
	 * @param taskId    a {@link Task task} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the disabled {@link Task scheduled task}
	 * @throws ApiException ...
	 */
	default Task disableScheduledTask(String taskId, Integer accountId)
		throws ApiException {
		return updateSchedule(taskId, "disable", accountId);
	}

	/**
	 * Enables the {@link Task scheduled task} specified by {@link String taskId}.
	 *
	 * @param taskId    a {@link Task task} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the enabled {@link Task scheduled task}
	 * @throws ApiException ...
	 */
	default Task enableScheduledTask(String taskId, Integer accountId)
		throws ApiException {
		return updateSchedule(taskId, "enable", accountId);
	}

	/**
	 * Resets the {@link Task scheduled task} specified by {@link String taskId}.
	 *
	 * @param taskId    a {@link Task task} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the reset {@link Task scheduled task}
	 * @throws ApiException ...
	 */
	default Task resetScheduledTask(String taskId, Integer accountId)
		throws ApiException {
		return updateSchedule(taskId, "reset", accountId);
	}

	/**
	 * Starts the {@link Task scheduled task} specified by {@link String taskId}.
	 *
	 * @param taskId    a {@link Task task} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the started {@link Task scheduled task}
	 * @throws ApiException ...
	 */
	default Task startScheduledTask(String taskId, Integer accountId)
		throws ApiException {
		return updateSchedule(taskId, "start", accountId);
	}

	/**
	 * Stops the {@link Task scheduled task} specified by {@link String taskId}.
	 *
	 * @param taskId    a {@link Task task} ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the stopped {@link Task scheduled task}
	 * @throws ApiException ...
	 */
	default Task stopScheduledTask(String taskId, Integer accountId)
		throws ApiException {
		return updateSchedule(taskId, "stop", accountId);
	}

	/**
	 * Modifies a schedule and returns the updated schedule.
	 *
	 * @param taskId    the {@link Task schedule} ID
	 * @param action    one of "disable", "enable", "reset", "start", and "stop"
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the updated {@link Task schedule}
	 * @throws ApiException ...
	 */
	Task updateSchedule(String taskId, String action, Integer accountId)
		throws ApiException;

	/**
	 * Modifies a schedule and returns the updated schedule.
	 *
	 * @param taskId          the {@link Task schedule} ID
	 * @param taskEditRequest one of "disable", "enable", "reset", "start", and "stop"
	 * @param accountId       Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the updated {@link Task schedule}
	 * @throws ApiException ...
	 */
	Task updateSchedule(String taskId, TaskEditRequest taskEditRequest,
		Integer accountId) throws ApiException;

}
