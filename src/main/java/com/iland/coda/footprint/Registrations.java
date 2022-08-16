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

import net.codacloud.model.Registration;
import net.codacloud.model.RegistrationLight;

/**
 * {@link Registrations}.
 *
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public class Registrations {

	private Registrations() {
	}

	/**
	 * Maps the supplied {@link Registration registration} to a {@link RegistrationLight light registration}.
	 *
	 * @param registration a {@link Registration registration}
	 * @return a {@link RegistrationLight light registration}
	 */
	public static RegistrationLight toLight(final Registration registration) {
		return new RegistrationLight(registration.getId(),
			registration.getLabel(), RegistrationLight.StateEnum.fromValue(
			registration.getState().getValue()));
	}

}
