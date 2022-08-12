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

package com.iland.coda.footprint.pagination;

import java.util.List;

/**
 * {@link Page}.
 *
 * @param <V> the value type
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
final class Page<V> {

	private final Integer pageNo, totalPages, totalCount;
	private final List<V> items;

	Page(final Integer pageNo, final Integer totalPages,
		final Integer totalCount, final List<V> items) {
		this.pageNo = pageNo;
		this.totalPages = totalPages;
		this.totalCount = totalCount;
		this.items = items;
	}

	public Integer getPageNo() {
		return pageNo;
	}

	public Integer getTotalPages() {
		return totalPages;
	}

	public Integer getTotalCount() {
		return totalCount;
	}

	public List<V> getItems() {
		return items;
	}

}
