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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.codacloud.ApiException;
import net.codacloud.model.Account;
import net.codacloud.model.AgentlessScannerSrz;
import net.codacloud.model.CVR;
import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationCreate;
import net.codacloud.model.RegistrationEdit;
import net.codacloud.model.RegistrationLight;
import net.codacloud.model.RegistrationSignupData;
import net.codacloud.model.ScanStatus;
import net.codacloud.model.ScanSurfaceEntry;
import net.codacloud.model.User;
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
	 * @throws ApiException
	 */
	CodaClient login() throws ApiException;

	/**
	 * Find or create a {@link RegistrationLight registration} for the supplied {@link String label} and {@link String description}.
	 *
	 * @param label       the {@link String label}
	 * @param description the {@link String description}
	 * @return the {@link RegistrationLight registration}
	 * @throws ApiException
	 */
	default RegistrationLight findOrCreateRegistration(final String label,
		final String description) throws ApiException {
		return getRegistrationForLabel(label).orElseGet(() -> {
			try {
				return createRegistration(label, description);
			} catch (ApiException e) {
				throw new CodaException(e);
			}
		});
	}

	/**
	 * Returns an {@link Optional} {@link RegistrationLight registration} with the supplied {@link String label}.
	 *
	 * @param label the {@link String label} for a {@link Registration}
	 * @return an {@link Optional} {@link RegistrationLight registration}
	 * @throws ApiException
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
	 * @throws ApiException
	 */
	default Set<RegistrationLight> listRegistrations() throws ApiException {
		return listRegistrations(null);
	}

	/**
	 * Provides a listing of active registrations for a specific category.
	 *
	 * @param category the category of registrations you want
	 * @return a listing of active registrations for a specific category
	 * @throws ApiException
	 */
	Set<RegistrationLight> listRegistrations(final String category)
		throws ApiException;

	/**
	 * Lists accounts into which the current user can sign in to.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint
	 * @return a list of accounts that the current user can sign in to
	 * @throws ApiException
	 */
	Set<Account> listAccounts(Integer accountId) throws ApiException;

	/**
	 * Crates a registration with blank {@link RegistrationSignupData registration signup data} accessible to all active users with the <code>"Global Admin"</code> role.
	 *
	 * @param label       the label of the registration
	 * @param description a description
	 * @return the created {@link RegistrationLight registration}
	 * @throws ApiException
	 */
	default RegistrationLight createRegistration(final String label,
		final String description) throws ApiException {
		logger().info("{}: Registering account '{}'", label, description);

		final RegistrationSignupData registrationSignupData =
			new RegistrationSignupData().firstName("").lastName("").email("")
				.companyWebsite("");
		final List<Integer> activeGlobalAdminIds = getActiveGlobalAdminIds();
		final RegistrationCreate newRegistration =
			createFullyManagedRegistration(label, description,
				registrationSignupData, activeGlobalAdminIds);

		return createRegistration(newRegistration);
	}

	/**
	 * Creates a new registration. The registration will automatically be marked as managed by the current logged-in account.
	 *
	 * @param registration the {@link RegistrationCreate registration}
	 * @return a {@link Registration registration}
	 * @throws ApiException
	 */
	RegistrationLight createRegistration(final RegistrationCreate registration)
		throws ApiException;

	/**
	 * Updates a registration.
	 *
	 * @param registrationId the registration ID
	 * @param edit           the {@link RegistrationEdit edit}
	 * @return a {@link Registration registration}
	 * @throws ApiException
	 */
	Registration updateRegistration(final Integer registrationId,
		final RegistrationEdit edit) throws ApiException;

	/**
	 * Deletes a registration.
	 *
	 * @param registration the {@link Registration} to delete
	 * @throws ApiException
	 */
	void deleteRegistration(RegistrationLight registration) throws ApiException;

	/**
	 * Get list of available scanners. This includes the default Cloud Scanner, as well as internal scanners.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a {@link List} of available {@link AgentlessScannerSrz scanners}
	 * @throws ApiException
	 */
	List<AgentlessScannerSrz> getScanners(Integer accountId)
		throws ApiException;

	/**
	 * Updates the scan surface with new data (extend scan surface modal). <strong>This is an idempotent operation!</strong>
	 *
	 * @param targets   a {@link List} of targets, e.g. hostname, IP address, or CIDR notation
	 * @param scanners  There's no documentation on this value. The scanners to use for the scan?
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @throws ApiException
	 */
	void triggerScan(List<String> targets, List<Integer> scanners,
		Integer accountId) throws ApiException;

	/**
	 * Rescans all user inputs from Scan Surface.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @throws ApiException
	 */
	void rescan(final Integer accountId) throws ApiException;

	/**
	 * Rescans all user inputs from Scan Surface for requested Agentless Scanner.
	 *
	 * @param scannerId the scanner ID
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @throws ApiException
	 */
	void rescan(Integer scannerId, Integer accountId) throws ApiException;

	/**
	 * Provides information regarding currently active scans.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return the {@link ScanStatus scan status}
	 * @throws ApiException
	 */
	ScanStatus getScanStatus(Integer accountId) throws ApiException;

	/**
	 * Retrieve list of user inputs and the resulting assets.
	 *
	 * @param scannerId Optional scanner ID filter. If not set or invalid, falls back on all scanners
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return
	 * @throws ApiException
	 */
	List<ScanSurfaceEntry> getScanSurface(Integer scannerId, Integer accountId)
		throws ApiException;

	/**
	 * Return a {@link Map}, keyed by report day, of lazily loadable {@link CVR snapshot reports}.
	 *
	 * @param accountId Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCVR lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException
	 */
	default Map<String, LazyCVR> getSnapshotReports(Integer accountId)
		throws ApiException {
		return getReports(ReportType.SNAPSHOT, accountId);
	}

	/**
	 * Return a {@link Map}, keyed by report day, of lazily loadable {@link CVR reports}.
	 *
	 * @param reportType the {@link ReportType report type}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCVR lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException
	 */
	Map<String, LazyCVR> getReports(ReportType reportType, Integer accountId)
		throws ApiException;

	/**
	 * Return a {@link Map}, keyed by report day, of {@link LazyCvrJson lazily-loadable reports}.
	 *
	 * @param reportType the {@link ReportType report type}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return a map of {@link LazyCvrJson lazily-loadable reports} keyed by date, e.g. "2022-05-16 21:33:29"
	 * @throws ApiException
	 */
	Map<String, LazyCvrJson> getReportsJson(ReportType reportType,
		Integer accountId) throws ApiException;

	/**
	 * Returns available report dates.
	 *
	 * @param reportType the {@link ReportType}
	 * @param accountId  Account ID you want to receive request for. If not provided, falls back on <code>original_account_id</code> from the auth endpoint.
	 * @return available report dates
	 * @throws ApiException
	 */
	List<String> getReportTimestamps(ReportType reportType, Integer accountId)
		throws ApiException;

	/**
	 * Creates a fully managed {@link Registration} accessible by every MSP user.
	 *
	 * @param label                  the label of the registration
	 * @param description            a description
	 * @param registrationSignupData the signup data
	 * @param associatedMspUserIds   the {@link User user IDs}
	 * @return the {@link RegistrationCreate created registration}
	 * @throws ApiException
	 */
	RegistrationCreate createFullyManagedRegistration(final String label,
		final String description,
		final RegistrationSignupData registrationSignupData,
		final List<Integer> associatedMspUserIds) throws ApiException;

	/**
	 * Returns a {@link List} of IDs for active users with the <code>"Global Admin"</code> role.
	 *
	 * @return a {@link List} of IDs for active users with the <code>"Global Admin"</code> role
	 * @throws ApiException
	 */
	default List<Integer> getActiveGlobalAdminIds() throws ApiException {
		return listUsers().stream().filter(
				u -> u.getIsActive() && Objects.equals("Global Admin", u.getRole()))
			.map(User::getId).collect(Collectors.toList());
	}

	/**
	 * Lists all users.
	 *
	 * @return a {@link List} of {@link User users}
	 * @throws ApiException
	 */
	List<User> listUsers() throws ApiException;

}
