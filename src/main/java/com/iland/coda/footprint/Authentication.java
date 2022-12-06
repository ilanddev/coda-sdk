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

import net.codacloud.ApiClient;
import net.codacloud.ApiException;
import okhttp3.Interceptor;

/**
 * {@link Authentication}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public interface Authentication extends Interceptor {

	/**
	 * Authenticate against the supplied {@link ApiClient API client}.
	 *
	 * @param apiClient an {@link ApiClient API client}
	 * @throws ApiException
	 */
	void authenticate(ApiClient apiClient) throws ApiException;

}

