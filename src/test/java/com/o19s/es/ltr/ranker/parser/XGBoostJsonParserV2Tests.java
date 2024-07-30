package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
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

import static com.o19s.es.ltr.LtrTestUtils.randomFeature;
import static com.o19s.es.ltr.LtrTestUtils.randomFeatureSet;
import static java.util.Collections.singletonList;

public class XGBoostJsonParserV2Tests extends LuceneTestCase {
    private final XGBoostJsonParserV2 parser = new XGBoostJsonParserV2();

    public void testReadLeaf() throws IOException {
        String model =
                "{\"learner\":" +
                    "{" +
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
                                "\"base_weights\":[1E0, 10E0, 0E0]," +
                                "\"categories\":[]," +
                                "\"categories_nodes\":[]," +
                                "\"categories_segments\":[]," +
                                "\"categories_sizes\":[]," +
                                "\"default_left\":[0, 0, 0]," +
                                "\"id\":0," +
                                "\"left_children\":[2, -1, -1]," +
                                "\"loss_changes\":[0E0, 0E0, 0E0]," +
                                "\"parents\":[2147483647, 0, 0]," +
                                "\"right_children\":[1, -1, -1]," +
                                "\"split_conditions\":[3E0, -1E0, -1E0]," +
                                "\"split_indices\":[0, 0, 0]," +
                                "\"split_type\":[0, 0, 0]," +
                                "\"sum_hessian\":[1E0, 1E0, 1E0]," +
                                "\"tree_param\":{\"num_deleted\":\"0\",\"num_feature\":\"1\",\"num_nodes\":\"3\",\"size_leaf_vector\":\"1\"}}" +
                            "]}," +
                            "\"name\":\"gbtree\"" +
                        "}," +
                        "\"learner_model_param\":{" +
                                "\"base_score\":\"5E-1\"," +
                                "\"boost_from_average\":\"1\"," +
                                "\"num_class\":\"0\"," +
                                "\"num_feature\":\"2\"," +
                                "\"num_target\":\"1\"" +
                        "}," +
                        "\"objective\":{" +
                            "\"name\":\"reg:linear\"," +
                            "\"reg_loss_param\":{\"scale_pos_weight\":\"1\"}" +
                          "}" +
                    "}," +
                    "\"version\":[2,1,0]" +
                "}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector featureVector = new SparseFeatureVector(1);
        featureVector.setFeatureScore(0, 2);
        assertEquals(0.0, tree.score(featureVector), Math.ulp(0.1F));

        featureVector.setFeatureScore(0, 4);
        assertEquals(10.0, tree.score(featureVector), Math.ulp(0.1F));
    }

    // todo: more tests
}
