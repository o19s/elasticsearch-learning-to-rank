package com.o19s.es.ltr.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.query.QueryBuilder;

/**
 * Contains a few methods copied from the AbstractQueryBuilder class. These methods are not
 * accessible from sub classes that do not reside in the same package.
 */
public class AbstractQueryBuilderUtils {

  private AbstractQueryBuilderUtils() {
    // Utility class with static methods only
  }

  public static void writeQueries(StreamOutput out, List<? extends QueryBuilder> queries)
      throws IOException {
    out.writeVInt(queries.size());
    for (QueryBuilder query : queries) {
      out.writeNamedWriteable(query);
    }
  }

  public static List<QueryBuilder> readQueries(StreamInput in) throws IOException {
    int size = in.readVInt();
    List<QueryBuilder> queries = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      queries.add(in.readNamedWriteable(QueryBuilder.class));
    }
    return queries;
  }
}
