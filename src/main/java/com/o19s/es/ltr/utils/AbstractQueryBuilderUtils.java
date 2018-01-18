package com.o19s.es.ltr.utils;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.AbstractObjectParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AbstractQueryBuilderUtils {

    /**
     * Method copied from the {@link org.elasticsearch.index.query.AbstractQueryBuilder}. Scope was reduced to
     * package private. But we use it in multiple subclasses.
     *
     * An issue is send to elastic to ask for a change to make it available again:
     * https://github.com/elastic/elasticsearch/issues/27865
     *
     * @param parser Instance of a parser declare some default values for fields
     */
    public static void declareStandardFields(AbstractObjectParser<? extends QueryBuilder, ?> parser) {
        parser.declareFloat(QueryBuilder::boost, AbstractQueryBuilder.BOOST_FIELD);
        parser.declareString(QueryBuilder::queryName, AbstractQueryBuilder.NAME_FIELD);
    }

    public static void writeQueries(StreamOutput out, List<? extends QueryBuilder> queries) throws IOException {
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
