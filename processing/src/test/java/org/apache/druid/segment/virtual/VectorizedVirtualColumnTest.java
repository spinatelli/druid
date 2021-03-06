/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.virtual;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.query.Druids;
import org.apache.druid.query.QueryContexts;
import org.apache.druid.query.QueryRunnerTestHelper;
import org.apache.druid.query.Result;
import org.apache.druid.query.aggregation.AggregationTestHelper;
import org.apache.druid.query.dimension.DefaultDimensionSpec;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.GroupByQueryConfig;
import org.apache.druid.query.groupby.GroupByQueryRunnerTestHelper;
import org.apache.druid.query.groupby.ResultRow;
import org.apache.druid.query.timeseries.TimeseriesQuery;
import org.apache.druid.query.timeseries.TimeseriesResultValue;
import org.apache.druid.segment.QueryableIndexSegment;
import org.apache.druid.segment.Segment;
import org.apache.druid.segment.TestHelper;
import org.apache.druid.segment.TestIndex;
import org.apache.druid.segment.column.ColumnCapabilities;
import org.apache.druid.segment.column.ColumnCapabilitiesImpl;
import org.apache.druid.segment.column.ValueType;
import org.apache.druid.timeline.SegmentId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VectorizedVirtualColumnTest
{
  private static final String ALWAYS_TWO = "two";
  private static final String COUNT = "count";
  private static final Map<String, Object> CONTEXT = ImmutableMap.of(QueryContexts.VECTORIZE_KEY, "force");

  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private AggregationTestHelper groupByTestHelper;
  private AggregationTestHelper timeseriesTestHelper;
  private List<Segment> segments = null;

  @Before
  public void setup()
  {
    groupByTestHelper = AggregationTestHelper.createGroupByQueryAggregationTestHelper(
        Collections.emptyList(),
        new GroupByQueryConfig(),
        tmpFolder
    );
    timeseriesTestHelper = AggregationTestHelper.createTimeseriesQueryAggregationTestHelper(
        Collections.emptyList(),
        tmpFolder
    );
    QueryableIndexSegment queryableIndexSegment = new QueryableIndexSegment(
        TestIndex.getMMappedTestIndex(),
        SegmentId.dummy(QueryRunnerTestHelper.DATA_SOURCE)
    );

    segments = Lists.newArrayList(queryableIndexSegment, queryableIndexSegment);
  }

  @Test
  public void testGroupBySingleValueString()
  {
    testGroupBy(new ColumnCapabilitiesImpl()
                    .setType(ValueType.STRING)
                    .setDictionaryEncoded(true)
                    .setDictionaryValuesUnique(true)
                    .setHasMultipleValues(false)
    );
  }

  @Test
  public void testGroupByMultiValueString()
  {
    // cannot currently group by string columns that might be multi valued
    cannotVectorize();
    testGroupBy(new ColumnCapabilitiesImpl()
                    .setType(ValueType.STRING)
                    .setDictionaryEncoded(true)
                    .setDictionaryValuesUnique(true)
                    .setHasMultipleValues(true)
    );
  }

  @Test
  public void testGroupByMultiValueStringUnknown()
  {
    // cannot currently group by string columns that might be multi valued
    cannotVectorize();
    testGroupBy(new ColumnCapabilitiesImpl()
                    .setType(ValueType.STRING)
                    .setDictionaryEncoded(true)
                    .setDictionaryValuesUnique(true)
    );
  }

  @Test
  public void testGroupBySingleValueStringNotDictionaryEncoded()
  {
    // cannot currently group by string columns that are not dictionary encoded
    cannotVectorize();
    testGroupBy(new ColumnCapabilitiesImpl()
                    .setType(ValueType.STRING)
                    .setDictionaryEncoded(false)
                    .setDictionaryValuesUnique(false)
                    .setHasMultipleValues(false)
    );
  }

  @Test
  public void testGroupByMultiValueStringNotDictionaryEncoded()
  {
    // cannot currently group by string columns that might be multi valued
    cannotVectorize();
    testGroupBy(new ColumnCapabilitiesImpl()
                    .setType(ValueType.STRING)
                    .setDictionaryEncoded(false)
                    .setDictionaryValuesUnique(false)
                    .setHasMultipleValues(true)
    );
  }

  @Test
  public void testGroupByLong()
  {
    // vectorized group by does not work for null numeric columns
    if (NullHandling.sqlCompatible()) {
      cannotVectorize();
    }
    testGroupBy(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.LONG));
  }

  @Test
  public void testGroupByDouble()
  {
    // vectorized group by does not work for null numeric columns
    if (NullHandling.sqlCompatible()) {
      cannotVectorize();
    }
    testGroupBy(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.DOUBLE));
  }

  @Test
  public void testGroupByFloat()
  {
    // vectorized group by does not work for null numeric columns
    if (NullHandling.sqlCompatible()) {
      cannotVectorize();
    }
    testGroupBy(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.FLOAT));
  }

  @Test
  public void testTimeseriesSingleValueString()
  {
    testTimeseries(new ColumnCapabilitiesImpl()
                       .setType(ValueType.STRING)
                       .setDictionaryEncoded(true)
                       .setDictionaryValuesUnique(true)
                       .setHasMultipleValues(false)
    );
  }

  @Test
  public void testTimeseriesMultiValueString()
  {
    testTimeseries(new ColumnCapabilitiesImpl()
                       .setType(ValueType.STRING)
                       .setDictionaryEncoded(true)
                       .setDictionaryValuesUnique(true)
                       .setHasMultipleValues(true)
    );
  }

  @Test
  public void testTimeseriesMultiValueStringUnknown()
  {
    testTimeseries(new ColumnCapabilitiesImpl()
                       .setType(ValueType.STRING)
                       .setDictionaryEncoded(true)
                       .setDictionaryValuesUnique(true)
    );
  }

  @Test
  public void testTimeseriesSingleValueStringNotDictionaryEncoded()
  {
    testTimeseries(new ColumnCapabilitiesImpl()
                       .setType(ValueType.STRING)
                       .setDictionaryEncoded(false)
                       .setDictionaryValuesUnique(false)
                       .setHasMultipleValues(false)
    );
  }

  @Test
  public void testTimeseriesMultiValueStringNotDictionaryEncoded()
  {
    testTimeseries(new ColumnCapabilitiesImpl()
                       .setType(ValueType.STRING)
                       .setDictionaryEncoded(false)
                       .setDictionaryValuesUnique(false)
                       .setHasMultipleValues(true)
    );
  }

  @Test
  public void testTimeseriesLong()
  {
    testTimeseries(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.LONG));
  }

  @Test
  public void testTimeseriesDouble()
  {
    testTimeseries(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.DOUBLE));
  }

  @Test
  public void testTimeseriesFloat()
  {
    testTimeseries(ColumnCapabilitiesImpl.createSimpleNumericColumnCapabilities(ValueType.FLOAT));
  }

  private void testTimeseries(ColumnCapabilities capabilities)
  {
    TimeseriesQuery query = Druids.newTimeseriesQueryBuilder()
                                  .intervals("2000/2030")
                                  .dataSource(QueryRunnerTestHelper.DATA_SOURCE)
                                  .granularity(Granularities.ALL)
                                  .virtualColumns(new AlwaysTwoVectorizedVirtualColumn(ALWAYS_TWO, capabilities))
                                  .aggregators(new AlwaysTwoCounterAggregatorFactory(COUNT, ALWAYS_TWO))
                                  .context(CONTEXT)
                                  .build();

    Sequence seq = timeseriesTestHelper.runQueryOnSegmentsObjs(segments, query);

    List<Result<TimeseriesResultValue>> expectedResults = ImmutableList.of(
        new Result<>(
            DateTimes.of("2011-01-12T00:00:00.000Z"),
            new TimeseriesResultValue(
                ImmutableMap.of(COUNT, getCount(capabilities))
            )
        )
    );

    TestHelper.assertExpectedObjects(expectedResults, seq.toList(), "failed");
  }

  private void testGroupBy(ColumnCapabilities capabilities)
  {
    GroupByQuery query = new GroupByQuery.Builder()
        .setDataSource(QueryRunnerTestHelper.DATA_SOURCE)
        .setGranularity(Granularities.ALL)
        .setVirtualColumns(
            new AlwaysTwoVectorizedVirtualColumn(ALWAYS_TWO, capabilities)
        )
        .addDimension(new DefaultDimensionSpec(ALWAYS_TWO, ALWAYS_TWO, capabilities.getType()))
        .setAggregatorSpecs(new AlwaysTwoCounterAggregatorFactory(COUNT, ALWAYS_TWO))
        .setInterval("2000/2030")
        .setContext(CONTEXT)
        .addOrderByColumn(ALWAYS_TWO)
        .build();

    List<ResultRow> rows = groupByTestHelper.runQueryOnSegmentsObjs(segments, query).toList();

    List<ResultRow> expectedRows = Collections.singletonList(
        GroupByQueryRunnerTestHelper.createExpectedRow(
            query,
            "2000",
            COUNT,
            getCount(capabilities),
            ALWAYS_TWO,
            getTwo(capabilities)
        )
    );

    TestHelper.assertExpectedObjects(expectedRows, rows, "failed");
  }

  private long getCount(ColumnCapabilities capabilities)
  {
    long modifier = 1L;
    if (capabilities.hasMultipleValues().isTrue()) {
      modifier = 2L;
    }
    return 2418L * modifier;
  }

  private Object getTwo(ColumnCapabilities capabilities)
  {
    switch (capabilities.getType()) {
      case LONG:
        return 2L;
      case DOUBLE:
        return 2.0;
      case FLOAT:
        return 2.0f;
      case STRING:
      default:
        if (capabilities.hasMultipleValues().isTrue()) {
          return ImmutableList.of("2", "2");
        }
        return "2";
    }
  }

  private void cannotVectorize()
  {
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage("Cannot vectorize!");
  }
}
