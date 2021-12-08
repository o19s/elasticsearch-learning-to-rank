/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.o19s.es.explore;

import org.apache.lucene.search.Query;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.query.Rewriteable;

import java.io.IOException;
import java.util.Objects;

public class ExplorerQueryBuilder extends AbstractQueryBuilder<ExplorerQueryBuilder> implements NamedWriteable {
    public static final String NAME = "match_explorer";

    private static final ParseField QUERY_NAME = new ParseField("query");
    private static final ParseField TYPE_NAME = new ParseField("type");
    private static final ObjectParser<ExplorerQueryBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, ExplorerQueryBuilder::new);
        PARSER.declareObject(
                ExplorerQueryBuilder::query,
                (parser, context) -> parseInnerQueryBuilder(parser),
                QUERY_NAME
        );
        PARSER.declareString(ExplorerQueryBuilder::statsType, TYPE_NAME);
        declareStandardFields(PARSER);
    }

    private QueryBuilder query;
    private String type;

    public ExplorerQueryBuilder() {
    }


    public ExplorerQueryBuilder(StreamInput in) throws IOException {
        super(in);
        query = in.readNamedWriteable(QueryBuilder.class);
        type = in.readString();
    }

    public static ExplorerQueryBuilder fromXContent(XContentParser parser) throws IOException {
        final ExplorerQueryBuilder builder;

        try {
            builder = PARSER.parse(parser, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.query == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + QUERY_NAME + "] is mandatory.");
        }
        if (builder.statsType() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + TYPE_NAME + "] is mandatory.");
        }
        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(query);
        out.writeString(type);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(QUERY_NAME.getPreferredName(), query);
        builder.field(TYPE_NAME.getPreferredName(), type);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(SearchExecutionContext context) throws IOException {
        return new ExplorerQuery(query.toQuery(context), type);
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryRewriteContext) throws IOException {
        if (queryRewriteContext != null) {

            ExplorerQueryBuilder rewritten = new ExplorerQueryBuilder();
            rewritten.type = this.type;
            rewritten.query = Rewriteable.rewrite(query, queryRewriteContext);
            rewritten.boost(boost());
            rewritten.queryName(queryName());

            if (!rewritten.equals(this)) {
                return rewritten;
            }
        }
        return super.doRewrite(queryRewriteContext);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(query, type);
    }

    @Override
    protected boolean doEquals(ExplorerQueryBuilder other) {
        return Objects.equals(query, other.query)
                && Objects.equals(type, other.type);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public QueryBuilder query() {
        return query;
    }

    public ExplorerQueryBuilder query(QueryBuilder query) {
        this.query = query;
        return this;
    }

    public String statsType() {
        return type;
    }

    public ExplorerQueryBuilder statsType(String type) {
        this.type = type;
        return this;
    }
}
