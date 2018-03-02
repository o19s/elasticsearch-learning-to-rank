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
        double v = random().nextDouble();
        assertEquals(v, Normalizer.NOOP.normalize(v), Math.ulp(v));
        assertSer(Normalizer.NOOP);
        assertRankOrder(Normalizer.NOOP, -random().nextInt(10), random().nextInt(1000)+1);
        assertExplanation(Normalizer.NOOP, random().nextDouble());
        assertSame(Normalizer.NOOP, writeAndReadStream(Normalizer.NOOP));
        assertSame(Normalizer.NOOP, writeAndReadXContent(Normalizer.NOOP));
    }

    public void testMinMax() {
        int min = -random().nextInt(100);
        int max = random().nextInt(100);
        Normalizer minMax = new Normalizer.MinMax(min, max);
        assertEquals(1D, minMax.normalize(max), Math.ulp(1D));
        assertEquals(0D, minMax.normalize(min), Math.ulp(0D));
        assertThat(minMax.normalize(random().nextDouble()), allOf(greaterThanOrEqualTo(0D), lessThanOrEqualTo(1D)));
        assertRankOrder(minMax, min, max);
        assertExplanation(minMax, random().nextDouble());
        assertSer(minMax);
    }

    public void testSaturation() {
        double k = random().nextDouble();
        double a = random().nextDouble();
        Normalizer satu = new Normalizer.Saturation(k, a);
        assertEquals(0D, satu.normalize(0D), Math.ulp(0D));
        assertEquals(0.5D, satu.normalize(k), Math.ulp(0.5D));
        assertThat(satu.normalize(random().nextDouble()), allOf(greaterThanOrEqualTo(0D), lessThanOrEqualTo(1D)));
        assertThat(satu.normalize(random().nextDouble()*random().nextInt()), allOf(greaterThanOrEqualTo(0D), lessThanOrEqualTo(1D)));
        assertRankOrder(satu, 0, random().nextInt(1000)+1); // non respected on negative values
        assertExplanation(satu, random().nextDouble());
        assertSer(satu);
    }

    public void testLogistic() {
        double k = random().nextDouble();
        double x0 = random().nextDouble();
        Normalizer logistic = new Normalizer.Logistic(k, x0);
        assertEquals(0.5D, logistic.normalize(x0), Math.ulp(0.5D));
        assertThat(logistic.normalize(random().nextDouble()), allOf(greaterThanOrEqualTo(0D), lessThanOrEqualTo(1D)));
        assertThat(logistic.normalize(random().nextDouble()*random().nextInt()), allOf(greaterThanOrEqualTo(0D), lessThanOrEqualTo(1D)));
        assertRankOrder(logistic, -random().nextInt(100), random().nextInt(100));
        assertExplanation(logistic, random().nextDouble());
        assertSer(logistic);
    }

    public void testIntervalNormalizer() {
        double minInput = random().nextDouble();
        double maxInput = minInput + random().nextDouble() + Math.ulp(minInput);
        Normalizer.UnitIntervalNormalizer minMax = new Normalizer.MinMax(minInput, maxInput);

        double min = -random().nextInt(100);
        double max = random().nextInt(100);

        boolean inclusive = random().nextBoolean();
        Matcher<Double> maxBound = inclusive ? lessThanOrEqualTo(max) : lessThan(max);
        Normalizer interval = new Normalizer.IntervalNormalizer(min, max, inclusive, minMax);
        assertEquals(min + ((max-min)/2), interval.normalize(minInput+((maxInput-minInput)/2)), 10*Math.ulp(max));
        assertThat(interval.normalize(random().nextDouble()), allOf(greaterThanOrEqualTo(min), maxBound));
        assertThat(interval.normalize(random().nextDouble()*random().nextInt()), allOf(greaterThanOrEqualTo(min), maxBound));
        assertRankOrder(interval, -random().nextInt(100), random().nextInt(100));

        double source = random().nextDouble();
        Explanation sourceExp = Explanation.match((float) source, "input");
        Explanation logisticExp = minMax.explain(source, sourceExp);
        Explanation intervalExp = interval.explain(source, sourceExp);
        assertEquals((float) interval.normalize(source), intervalExp.getValue(), Math.ulp((float) interval.normalize(source)));
        assertThat(Arrays.asList(intervalExp.getDetails()), contains(logisticExp));
        assertSer(interval);
    }

    private void assertExplanation(Normalizer n, double input) {
        assertExplanation(n, input, Explanation.match((float) input, "input"));
    }

    private void assertExplanation(Normalizer n, double input, Explanation source) {
        double output = n.normalize(input);
        Explanation normExp = n.explain(input, source);
        assertEquals((float) output, normExp.getValue(), Math.ulp((float)output));
        assertThat(Arrays.asList(normExp.getDetails()), contains(equalTo(source)));
    }

    private void assertRankOrder(Normalizer n, int from, int to) {
        assert from < to;
        ScoreDoc[] docs = IntStream.range(1, random().nextInt(1000))
                .mapToObj((i) -> new ScoreDoc(i, (float) (to + random().nextDouble() * (to - from))))
                .sorted(LtrRescorer.SCORE_DOC_COMPARATOR)
                .toArray(ScoreDoc[]::new);
        for(int i = 0; i < docs.length; i++) {
            docs[i].doc = i+1;
            docs[i].score = (float) n.normalize(docs[i].score);
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