package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

import static com.o19s.es.ltr.query.ValidatingLtrQueryBuilder.SUPPORTED_TYPES;
import static java.util.stream.Collectors.joining;

class AutoDetectParser {
    private String expectedName;
    private StorableElement element;
    private FeatureValidation validation;

    private static final ObjectParser<AutoDetectParser, String> PARSER = new ObjectParser<>("storable_elements");

    static {
        PARSER.declareObject(AutoDetectParser::setElement,
                StoredFeature::parse,
                new ParseField(StoredFeature.TYPE));
        PARSER.declareObject(AutoDetectParser::setElement,
                StoredFeatureSet::parse,
                new ParseField(StoredFeatureSet.TYPE));
        PARSER.declareObject(AutoDetectParser::setElement,
                StoredLtrModel::parse,
                new ParseField(StoredLtrModel.TYPE));
        PARSER.declareObject((b, v) -> b.validation = v,
                (p, c) -> FeatureValidation.PARSER.apply(p, null),
                new ParseField("validation"));
    }

    AutoDetectParser(String name) {
        this.expectedName = name;
    }

    public void parse(XContentParser parser) throws IOException {
        PARSER.parse(parser, this, expectedName);
        if (element == null) {
            throw new ParsingException(parser.getTokenLocation(), "Element of type [" + SUPPORTED_TYPES.stream().collect(joining(",")) +
                    "] is mandatory.");
        }
    }

    public StorableElement getElement() {
        return element;
    }

    public void setElement(StorableElement element) {
        if (this.element != null) {
            throw new IllegalArgumentException("[" + element.type() + "] already set, only one element can be set at a time (" +
                    SUPPORTED_TYPES.stream().collect(joining(",")) + ").");
        }
        this.element = element;
    }

    public void setValidation(FeatureValidation validation) {
        this.validation = validation;
    }

    public FeatureValidation getValidation() {
        return validation;
    }
}
