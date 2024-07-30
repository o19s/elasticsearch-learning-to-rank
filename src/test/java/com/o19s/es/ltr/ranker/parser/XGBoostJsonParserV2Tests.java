package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.SparseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker.FeatureVector;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.elasticsearch.common.io.Streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Array;

import static com.o19s.es.ltr.LtrTestUtils.randomFeatureSet;

public class XGBoostJsonParserV2Tests extends LuceneTestCase {
    private final XGBoostJsonParserV2 parser = new XGBoostJsonParserV2();

    public void testReadLeaf() throws IOException {
        String model =
                "{\"learner\":{" +
                    "\"attributes\":{}," +
                    "\"feature_names\":[]," +
                    "\"feature_types\":[]," +
                    "\"gradient_booster\":{" +
                        "\"model\":{" +
                        "\"gbtree_model_param\":{" +
                        "\"num_parallel_tree\":\"1\"," +
                        "\"num_trees\":\"1\"}," +
                        "\"iteration_indptr\":[0,1]," +
                        "\"tree_info\":[0]," +
                        "\"trees\":[{" +
                            "\"base_weights\":[-0E0]," +
                            "\"categories\":[]," +
                            "\"categories_nodes\":[]," +
                            "\"categories_segments\":[]," +
                            "\"categories_sizes\":[]," +
                            "\"default_left\":[0]," +
                            "\"id\":0," +
                            "\"left_children\":[-1]," +
                            "\"loss_changes\":[0E0]," +
                            "\"parents\":[2147483647]," +
                            "\"right_children\":[-1]," +
                            "\"split_conditions\":[-0E0]," +
                            "\"split_indices\":[0]," +
                            "\"split_type\":[0]," +
                            "\"sum_hessian\":[1E0]," +
                            "\"tree_param\":{\"num_deleted\":\"0\",\"num_feature\":\"2\",\"num_nodes\":\"1\",\"size_leaf_vector\":\"1\"}}" +
                        "]}," +
                        "\"name\":\"gbtree\"" +
                    "}," +
                "\"learner_model_param\":{" +
                        "\"base_score\":\"5E-1\"," +
                        "\"boost_from_average\":\"1\"," +
                        "\"num_class\":\"0\"," +
                        "\"num_feature\":\"2\"," +
                        "\"num_target\":\"1\"}," +
                        "\"objective\":{" +
                            "\"name\":\"binary:logistic\"," +
                            "\"reg_loss_param\":{\"scale_pos_weight\":\"1\"}" +
                        "}" +
                "}," +
                "\"version\":[2,1,0]}";
        FeatureSet set = randomFeatureSet();
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector featureVector = new SparseFeatureVector(2);
        featureVector.setFeatureScore(0, 2);
        featureVector.setFeatureScore(1, 3);
        assertEquals(0.0, tree.score(featureVector), Math.ulp(0.1F));
    }

    private String readModel(String model) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(model)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Streams.copy(is.readAllBytes(),  bos);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
