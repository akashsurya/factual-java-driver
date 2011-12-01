package com.factual;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Represents a top level Factual query. Knows how to represent the query as URL
 * encoded key value pairs, ready for the query string in a GET request. (See
 * {@link #toUrlQuery()})
 * 
 * @author aaron
 */
public class Query {
  private String fullTextSearch;
  private int limit;
  private int offset;
  private boolean includeRowCount;
  private Circle circle;

  /**
   * Holds all row filters for this Query. Implicit top-level AND.
   */
  private final List<Filter> rowFilters = Lists.newArrayList();


  /**
   * Sets a full text search query. Factual will use this value to perform a
   * full text search against various attributes of the underlying table, such
   * as entity name, address, etc.
   * 
   * @param fullTextSearch
   *          the text for which to perform a full text search.
   * @return this Query
   */
  public Query fullTextSearch(String fullTextSearch) {
    this.fullTextSearch = fullTextSearch;
    return this;
  }

  /**
   * Sets the maximum amount of records to return from this Query.
   * @param limit the maximum amount of records to return from this Query.
   * @return this Query
   */
  public Query limit(int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Sets how many records in to start getting results (i.e., the page offset)
   * for this Query.
   * 
   * @param offset
   *          the page offset for this Query.
   * @return this Query
   */
  public Query offset(int offset) {
    this.offset = offset;
    return this;
  }

  /**
   * The response will include a count of the total number of rows in the table
   * that conform to the request based on included filters. This will increase
   * the time required to return a response. The default behavior is to NOT
   * include a row count.
   * 
   * @return this Query, marked to return total row count when run.
   */
  public Query includeRowCount() {
    return includeRowCount(true);
  }

  /**
   * When true, the response will include a count of the total number of rows in
   * the table that conform to the request based on included filters.
   * Requesting the row count will increase the time required to return a
   * response. The default behavior is to NOT include a row count.
   * 
   * @param includeRowCount
   *          true if you want the results to include a count of the total
   *          number of rows in the table that conform to the request based on
   *          included filters.
   * @return this Query.
   */
  public Query includeRowCount(boolean includeRowCount) {
    this.includeRowCount = includeRowCount;
    return this;
  }

  /**
   * Begins construction of a new row filter.
   * 
   * @param fieldName
   *          the name of the field on which to filter.
   * @return A partial representation of the new row filter.
   */
  public FilterBuilder criteria(String fieldName) {
    return new FilterBuilder(fieldName);
  }

  /**
   * Begins construction of a new row filter for this Query.
   * 
   * @param fieldName
   *          the name of the field on which to filter.
   * @return A partial representation of the new row filter.
   */
  public QueryBuilder field(String fieldName) {
    return new QueryBuilder(this, fieldName);
  }

  /**
   * Adds a filter so that results can only be (roughly) within the specified
   * geographic circle.
   * 
   * @param circle The circle within which to bound the results.
   * @return this Query.
   */
  public Query within(Circle circle) {
    this.circle = circle;
    return this;
  }

  /**
   * Adds <tt>filters</tt> to this Query, grouped into a logical AND.
   */
  public Query and(Filter... filters) {
    rowFilters.add(new FilterGroup(filters));
    return this;
  }

  /**
   * Used to nest AND'ed predicates.
   */
  public Query and(Query... queries) {
    return popFilters("$and", queries);
  }

  /**
   * Adds <tt>filters</tt> to this Query, grouped into a logical OR.
   */
  public Query or(Filter... filters) {
    rowFilters.add(new FilterGroup(filters).asOR());
    return this;
  }

  /**
   * Used to nest OR'ed predicates.
   */
  public Query or(Query... queries) {
    return popFilters("$or", queries);
  }

  /**
   * Adds <tt>filter</tt> to this Query.
   */
  public void add(Filter filter) {
    rowFilters.add(filter);
  }

  /**
   * Builds and returns the query string to represent this Query when talking to
   * Factual's API. Provides proper URL encoding and escaping.
   * <p>
   * Example output:
   * <pre>
   * filters=%7B%22%24and%22%3A%5B%7B%22region%22%3A%7B%22%24in%22%3A%22MA%2CVT%2CNH%22%7D%7D%2C%7B%22%24or%22%3A%5B%7B%22first_name%22%3A%7B%22%24eq%22%3A%22Chun%22%7D%7D%2C%7B%22last_name%22%3A%7B%22%24eq%22%3A%22Kok%22%7D%7D%5D%7D%5D%7D
   * </pre>
   * <p>
   * (After decoding, the above example would be used by the server as:)
   * <pre>
   * filters={"$and":[{"region":{"$in":"MA,VT,NH"}},{"$or":[{"first_name":{"$eq":"Chun"}},{"last_name":{"$eq":"Kok"}}]}]}
   * </pre>
   * 
   * @return the query string to represent this Query when talking to Factual's
   *         API.
   */
  protected String toUrlQuery() {
    return Joiner.on("&").skipNulls().join(
        urlPair("q", fullTextSearch),
        (limit > 0 ? urlPair("limit", limit) : null),
        (offset > 0 ? urlPair("offset", offset) : null),
        (includeRowCount ? urlPair("include_count", true) : null),
        urlPair("filters", rowFiltersJsonOrNull()),
        urlPair("geo", geoBoundsJsonOrNull()));
  }

  private String urlPair(String name, Object val) {
    if(val != null) {
      try {
        return name + "=" + (val instanceof String ? URLEncoder.encode(val.toString(), "UTF-8") : val);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    } else {
      return null;
    }
  }

  private String geoBoundsJsonOrNull() {
    if(circle != null) {
      return circle.toJsonStr();
    } else {
      return null;
    }
  }

  private String rowFiltersJsonOrNull() {
    if(rowFilters.isEmpty()) {
      return null;
    } else if(rowFilters.size() == 1) {
      return rowFilters.get(0).toJsonStr();
    } else {
      return new FilterGroup(rowFilters).toJsonStr();
    }
  }

  /**
   * Pops the newest Filter from each of <tt>queries</tt>,
   * grouping each popped Filter into one new FilterGroup.
   * Adds that new FilterGroup as the newest Filter in this
   * Query.
   * <p>
   * The FilterGroup's logic will be determined by <tt>op</tt>.
   */
  private Query popFilters(String op, Query... queries) {
    FilterGroup group = new FilterGroup().op(op);
    for(Query q : queries) {
      group.add(pop(q.rowFilters));
    }
    add(group);
    return this;
  }

  private Filter pop(List<Filter> list) {
    return list.remove(list.size()-1);
  }

}
