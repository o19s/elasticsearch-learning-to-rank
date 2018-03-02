package com.o19s.es.ltr.query;

import com.o19s.es.ltr.rescore.LtrRescorer;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.hamcrest.Matcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class NormalizerTests extends LuceneTestCase {
    static NamedWriteableRegistry B_REGISTRY = new NamedWriteableRegistry(Normalizer.getNamedWriteables());
    static NamedXContentRegistry X_REGISTRY = new NamedXContentRegistry(Normalizer.getNamedXContent());

    public void testNoop() {
        float v = random().nextFloat();
        assertEquals(v, Normalizer.NOOP.normalize(v), Math.ulp(v));
        assertSer(Normalizer.NOOP);
        assertRankOrder(Normalizer.NOOP, -random().nextInt(10), random().nextInt(1000)+1);
        assertExplanation(Normalizer.NOOP, random().nextFloat());
        assertSame(Normalizer.NOOP, writeAndReadStream(Normalizer.NOOP));
        assertSame(Normalizer.NOOP, writeAndReadXContent(Normalizer.NOOP));
    }

    public void testMinMax() {
        int min = -random().nextInt(100);
        int max = random().nextInt(100);
        Normalizer minMax = new Normalizer.MinMax(min, max);
        assertEquals(1F, minMax.normalize(max), Math.ulp(1F));
        assertEquals(0F, minMax.normalize(min), Math.ulp(0F));
        assertThat(minMax.normalize(random().nextFloat()), allOf(greaterThanOrEqualTo(0F), lessThanOrEqualTo(1F)));
        assertRankOrder(minMax, min, max);
        assertExplanation(minMax, random().nextFloat());
        assertSer(minMax);
    }

    public void testSaturation() {
        float k = random().nextFloat();
        float a = random().nextFloat();
        Normalizer satu = new Normalizer.Saturation(k, a);
        assertEquals(0F, satu.normalize(0F), Math.ulp(0F));
        assertEquals(0.5F, satu.normalize(k), Math.ulp(0.5F));
        assertThat(satu.normalize(random().nextFloat()), allOf(greaterThanOrEqualTo(0F), lessThanOrEqualTo(1F)));
        assertThat(satu.normalize(random().nextFloat()*random().nextInt()), allOf(greaterThanOrEqualTo(0F), lessThanOrEqualTo(1F)));
        assertRankOrder(satu, 0, random().nextInt(1000)+1); // non respected on negative values
        assertExplanation(satu, random().nextFloat());
        assertSer(satu);
    }

    public void testLogistic() {
        float k = random().nextFloat();
        float x0 = random().nextFloat();
        Normalizer logistic = new Normalizer.Logistic(k, x0);
        assertEquals(0.5F, logistic.normalize(x0), Math.ulp(0.5F));
        assertThat(logistic.normalize(random().nextFloat()), allOf(greaterThanOrEqualTo(0F), lessThanOrEqualTo(1F)));
        assertThat(logistic.normalize(random().nextFloat()*random().nextInt()), allOf(greaterThanOrEqualTo(0F), lessThanOrEqualTo(1F)));
        assertRankOrder(logistic, -random().nextInt(100), random().nextInt(100));
        assertExplanation(logistic, random().nextFloat());
        assertSer(logistic);
    }

    public void testIntervalNormalizer() {
        float minInput = random().nextFloat();
        float maxInput = minInput + random().nextFloat() + Math.ulp(minInput);
        Normalizer.UnitIntervalNormalizer minMax = new Normalizer.MinMax(minInput, maxInput);

        float min = -random().nextInt(100);
        float max = random().nextInt(100);

        boolean inclusive = random().nextBoolean();
        Matcher<Float> maxBound = inclusive ? lessThanOrEqualTo(max) : lessThan(max);
        Normalizer interval = new Normalizer.IntervalNormalizer(min, max, inclusive, minMax);
        assertEquals(min + ((max-min)/2), interval.normalize(minInput+((maxInput-minInput)/2)), 10*Math.ulp(max));
        assertThat(interval.normalize(random().nextFloat()), allOf(greaterThanOrEqualTo(min), maxBound));
        assertThat(interval.normalize(random().nextFloat()*random().nextInt()), allOf(greaterThanOrEqualTo(min), maxBound));
        assertRankOrder(interval, -random().nextInt(100), random().nextInt(100));

        float source = random().nextFloat();
        Explanation sourceExp = Explanation.match(source, "input");
        Explanation logisticExp = minMax.explain(source, sourceExp);
        Explanation intervalExp = interval.explain(source, sourceExp);
        assertEquals(interval.normalize(source), intervalExp.getValue(), Math.ulp(interval.normalize(source)));
        assertThat(Arrays.asList(intervalExp.getDetails()), contains(logisticExp));
        assertSer(interval);
    }

    private void assertExplanation(Normalizer n, float input) {
        assertExplanation(n, input, Explanation.match(input, "input"));
    }

    private void assertExplanation(Normalizer n, float input, Explanation source) {
        float output = n.normalize(input);
        Explanation normExp = n.explain(input, source);
        assertEquals(output, normExp.getValue(), Math.ulp(output));
        assertThat(Arrays.asList(normExp.getDetails()), contains(equalTo(source)));
    }

    private void assertRankOrder(Normalizer n, int from, int to) {
        assert from < to;
        ScoreDoc[] docs = IntStream.range(1, random().nextInt(1000))
                .mapToObj((i) -> new ScoreDoc(i, to + random().nextFloat() * (to - from)))
                .sorted(LtrRescorer.SCORE_DOC_COMPARATOR)
                .toArray(ScoreDoc[]::new);
        for(int i = 0; i < docs.length; i++) {
            docs[i].doc = i+1;
            docs[i].score = n.normalize(docs[i].score);
        }

        Arrays.sort(docs, LtrRescorer.SCORE_DOC_COMPARATOR);
        int last = 0;
        for (ScoreDoc d : docs) {
            assertEquals(d.doc, last+1);
            last = d.doc;
        }
    }

    private void assertSer(Normalizer norm) {
        assertEquals(norm, writeAndReadStream(norm));
        assertEquals(norm, writeAndReadXContent(norm));
    }

    private Normalizer writeAndReadStream(Normalizer normalizer) {
        BytesStreamOutput out = new BytesStreamOutput();
        try {
            out.writeNamedWriteable(normalizer);
            StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), B_REGISTRY);
            return in.readNamedWriteable(Normalizer.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Normalizer writeAndReadXContent(Normalizer normalizer) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XContentBuilder builder = new XContentBuilder(JsonXContent.jsonXContent, bos);
            normalizer.toXContent(builder, ToXContent.EMPTY_PARAMS);
            builder.close();
            XContentParser parser = JsonXContent.jsonXContent.createParser(X_REGISTRY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                    new ByteArrayInputStream(bos.toByteArray()));

            return Normalizer.parseBaseNormalizer(parser, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}