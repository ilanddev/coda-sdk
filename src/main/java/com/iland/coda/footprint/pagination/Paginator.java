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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import net.codacloud.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction for the retrieval of paginated data from the SDK.
 *
 * @param <I> the paginated SDK type
 * @param <V> the item value type
 * @author <a href="mailto:tagspilman@1111systems.com">Tag Spilman</a>
 */
public final class Paginator<I, V> {

	private static final Logger logger =
		LoggerFactory.getLogger(Paginator.class);

	private final PageFetcher<I> fetcher;
	private final Function<I, Page<V>> pageMapper;

	public Paginator(final PageFetcher<I> fetcher,
		final Function<I, Integer> pageNoMapper,
		final Function<I, Integer> totalPageMapper,
		final Function<I, Integer> totalCountMapper,
		final Function<I, List<V>> itemsMapper) {
		this.fetcher = fetcher;
		this.pageMapper =
			i -> new Page<>(pageNoMapper.apply(i), totalPageMapper.apply(i),
				totalCountMapper.apply(i), itemsMapper.apply(i));
	}

	/**
	 * Fetch and return all items from all pages.
	 *
	 * @return a {@link List} of {@link V items} from all pages
	 * @throws ApiException
	 */
	public List<V> fetchAll() throws ApiException {
		return fetchAll(Function.identity(), ArrayList::new);
	}

	/**
	 * Fetch and return all items from all pages.
	 *
	 * @return a {@link Set} of {@link V items} from all pages
	 * @throws ApiException
	 */
	public Set<V> fetchAllAsync() throws ApiException {
		return fetchAll(IntStream::parallel, HashSet::new);
	}

	private <C extends Collection<V>> C fetchAll(
		final Function<IntStream, IntStream> streamMapper,
		final Supplier<C> supplier) throws ApiException {
		final I pageOfItems = fetcher.fetch(1);
		final Page<V> firstPage = pageMapper.apply(pageOfItems);
		final AtomicInteger count = new AtomicInteger(0);
		try {
			final C items = streamMapper.apply(
					IntStream.range(2, firstPage.getTotalPages() + 1))
				.mapToObj(pageNo -> fetch(pageNo, count)).map(pageMapper::apply)
				.map(Page::getItems).flatMap(List::stream)
				.collect(Collectors.toCollection(supplier));
			items.addAll(firstPage.getItems());
			return items;
		} catch (RuntimeException e) {
			Throwables.throwIfInstanceOf(e.getCause(), ApiException.class);
			throw e;
		}
	}

	private I fetch(final Integer pageNo, final AtomicInteger count) {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		try {
			final I fetch = fetcher.fetch(pageNo);

			if (logger.isDebugEnabled()) {
				final Page<V> page = pageMapper.apply(fetch);
				final Integer totalPages = page.getTotalPages();
				final String percent =
					calculatePercentage(count.incrementAndGet(), totalPages);
				logger.debug("Page {}/{} ({} items) retrieved in {} ({}%)",
					pageNo, totalPages, page.getItems().size(), stopwatch,
					percent);
			}

			return fetch;
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	private static String calculatePercentage(final int a, final int b) {
		return new BigDecimal(a).divide(BigDecimal.valueOf(b), 3,
				RoundingMode.FLOOR).multiply(BigDecimal.valueOf(100)).setScale(1)
			.toString();
	}

}
