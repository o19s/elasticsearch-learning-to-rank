/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.PrecompiledExpressionFeature;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.joining;

public class ValidatingLtrQueryBuilder extends AbstractQueryBuilder<ValidatingLtrQueryBuilder> {
    public static final Set<String> SUPPORTED_TYPES = unmodifiableSet(new HashSet<>(asList(
            StoredFeature.TYPE,
            StoredFeatureSet.TYPE,
            StoredLtrModel.TYPE)));

    public static final String NAME = "validating_ltr_query";
    private static ObjectParser<ValidatingLtrQueryBuilder, QueryParseContext> PARSER = new ObjectParser<>(NAME);
    private static final ParseField VALIDATION = new ParseField("validation");

    static {
        BiConsumer<ValidatingLtrQueryBuilder, StorableElement> setElem = (b, v) -> {
            if (b.element != null) {
                throw new IllegalArgumentException("[" + b.element.type() + "] already set, only one element can be set at a time (" +
                        SUPPORTED_TYPES.stream().collect(joining(",")) + ").");
            }
            b.element = v;
        };

        PARSER.declareObject(setElem,
                (parser, ctx) -> StoredFeature.parse(parser),
                new ParseField(StoredFeature.TYPE));
        PARSER.declareObject(setElem,
                (parser, ctx) -> StoredFeatureSet.parse(parser),
                new ParseField(StoredFeatureSet.TYPE));
        PARSER.declareObject(setElem,
                (parser, ctx) -> StoredLtrModel.parse(parser),
                new ParseField(StoredLtrModel.TYPE));
        PARSER.declareObject((b, v) -> b.validation = v,
                (p, c) -> FeatureValidation.PARSER.apply(p, null),
                new ParseField("validation"));
        declareStandardFields(PARSER);
    }

    private StorableElement element;
    private FeatureValidation validation;
    private final transient LtrRankerParserFactory factory;

    private ValidatingLtrQueryBuilder(LtrRankerParserFactory factory) {
        this.factory = factory;
    }

    public ValidatingLtrQueryBuilder(StorableElement element, FeatureValidation validation, LtrRankerParserFactory factory) {
        this(factory);
        this.element = Objects.requireNonNull(element);
        this.validation = Objects.requireNonNull(validation);
    }

    public ValidatingLtrQueryBuilder(StreamInput input, LtrRankerParserFactory factory) throws IOException {
        super(input);
        // XXX: hack because AbstractQueryTest does not inject
        // our NamedWriteable to the context.
        String type = input.readString();
        final Reader<StorableElement> reader;
        switch(type) {
            case StoredFeature.TYPE:
                reader = StoredFeature::new;
                break;
            case StoredFeatureSet.TYPE:
                reader = StoredFeatureSet::new;
                break;
            case StoredLtrModel.TYPE:
                reader = StoredLtrModel::new;
                break;
            default: throw new IOException("Unsupported storable element [" + type + "]");
        }
        this.element = reader.read(input);
        this.validation = new FeatureValidation(input);
        this.factory = factory;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(element.getWriteableName());
        element.writeTo(out);
        validation.writeTo(out);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(element.type(), element);
        builder.field(VALIDATION.getPreferredName(), validation);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    public static Optional<ValidatingLtrQueryBuilder> fromXContent(QueryParseContext context,
                                                                   LtrRankerParserFactory factory) throws IOException {
        XContentParser parser = context.parser();
        try {
            ValidatingLtrQueryBuilder builder = new ValidatingLtrQueryBuilder(factory);
            PARSER.parse(parser, builder, context);
            if (builder.element == null) {
                throw new ParsingException(parser.getTokenLocation(), "Element of type [" + SUPPORTED_TYPES.stream().collect(joining(",")) +
                    "] is mandatory.");
            }
            if (builder.validation == null) {
                throw new ParsingException(parser.getTokenLocation(), "Expected field [" + VALIDATION.getPreferredName() + "]");
            }

            return Optional.of(builder);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (StoredFeature.TYPE.equals(element.type())) {
            Feature feature = ((StoredFeature)element).optimize();
            if (feature instanceof PrecompiledExpressionFeature) {
                // Derived features cannot be tested alone
                return new MatchAllDocsQuery();
            }
            return feature.doToQuery(context, null, validation.getParams());
        } else if (StoredFeatureSet.TYPE.equals(element.type())) {
            FeatureSet set = ((StoredFeatureSet)element).optimize();
            LinearRanker ranker = new LinearRanker(new float[set.size()]);
            CompiledLtrModel model = new CompiledLtrModel("validation", set, ranker);
            return RankerQuery.build(model, context, validation.getParams());
        } else if (StoredLtrModel.TYPE.equals(element.type())) {
            CompiledLtrModel model = ((StoredLtrModel)element).compile(factory);
            return RankerQuery.build(model, context, validation.getParams());
        } else {
            throw new QueryShardException(context, "Unknown element type [" + element.type() + "]" );
        }
    }

    @Override
    protected boolean doEquals(ValidatingLtrQueryBuilder other) {
        return Objects.equals(element, other.element) &&
                Objects.equals(validation, other.validation);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(element, validation);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public StorableElement getElement() {
        return element;
    }

    public FeatureValidation getValidation() {
        return validation;
    }
}
