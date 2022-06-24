Uploading A Trained Model
********************************

Training models occurs outside Elasticsearch LTR. You use the plugin to log features (as mentioned in :doc:`logging-features`). Then with whichever technology you choose, you train a ranking model. You upload a model to Elasticsearch LTR in the available serialization formats (ranklib, xgboost, and others). Let's first urst talk briefly about training in supported technologies (though not at all an extensive overview) and dig into uploading a model.

====================
Ranklib training
====================

We provide two demos for training a model. A fully-fledged `Ranklib Demo <http://github.com/o19s/elasticsearch-learning-to-rank/tree/master/demo>`_ uses Ranklib to train a model from Elasticsearch queries. You can see how features are `logged <http://github.com/o19s/elasticsearch-learning-to-rank/tree/master/demo/collectFeatures.py>`_ and how models are `trained <http://github.com/o19s/elasticsearch-learning-to-rank/tree/master/demo/train.py>`_ . In particular, you'll note that logging create a ranklib consumable judgment file that looks like::

    4	qid:1	1:9.8376875	2:12.318446 # 7555	rambo
    3	qid:1	1:10.7808075	2:9.510193 # 1370	rambo
    3	qid:1	1:10.7808075	2:6.8449354 # 1369	rambo
    3	qid:1	1:10.7808075	2:0.0 # 1368	rambo

Here for query id 1 (Rambo) we've logged features 1 (a title TF\*IDF score) and feature 2 (a description TF\*IDF score) for a set of documents. In `train.py <http://github.com/o19s/elasticsearch-learning-to-rank/demo/train.py>`_ you'll see how we call Ranklib to train one of it's supported models on this line::

    cmd = "java -jar RankLib-2.8.jar -ranker %s -train%rs -save %s -frate 1.0" % (whichModel, judgmentsWithFeaturesFile, modelOutput)

Our "judgmentsWithFeatureFile" is the input to RankLib. Other parameters are passed, which you can read about in `Ranklib's documetration <https://sourceforge.net/p/lemur/wiki/RankLib/>`_.

Ranklib will output a model in it's own seiralization format. For example a LambdaMART model is an ensemble of regression trees. It looks like::

    ## LambdaMART
    ## No. of trees = 1000
    ## No. of leaves = 10
    ## No. of threshold candidates = 256
    ## Learning rate = 0.1
    ## Stop early = 100

    <ensemble>
        <tree id="1" weight="0.1">
            <split>
                <feature> 2 </feature>
                ...

Notice how each tree examines the value of features, makes a decision based on the value of a feature, then ultimately outputs the relevance score. You'll note features are referred to by ordinal, starting by "1" with Ranklib (this corresponds to the 0th feature in your feature set). Ranklib does not use feature names when training.

===================
XGBoost Example
===================

There's also a rexample of how to train a model `using XGBoost <http://github.com/o19s/elasticsearch-learning-to-rank/tree/master/demo/xgboost-demo>`_. Examining this demo, you'll see the difference in how Ranklib is executed vs XGBoost. XGBoost will output a serialization format for gradient boosted decision tree that looks like::

    [  { "nodeid": 0, "depth": 0, "split": "tmdb_multi", "split_condition": 11.2009, "yes": 1, "no": 2, "missing": 1, "children": [
        { "nodeid": 1, "depth": 1, "split": "tmdb_title", "split_condition": 2.20631, "yes": 3, "no": 4, "missing": 3, "children": [
        { "nodeid": 3, "leaf": -0.03125 },
        ...

===================
XGBoost Parameters
===================
Additional parameters can optionally be passed for an XGBoost model. This can be done by specifying the definition as an object, with the decision trees as the 'splits' field. See the example below.

Currently supported parameters:

**objective** - Defines the model learning objective as specified in the `XGBoost documentation <https://xgboost.readthedocs.io/en/latest/parameter.html#learning-task-parameters>`_. This parameter can transform the final model prediction. Using logistic objectives applies a sigmoid normalization.

Currently supported values: 'binary:logistic', 'binary:logitraw', 'rank:pairwise', 'reg:linear', 'reg:logistic'

===================
Simple linear models
===================

Many types of models simply output linear weights of each feature such as linear SVM. The LTR model supports simple linear weights for each features, such as those learned from an SVM model or linear regression::

    {
        "title_query" : 0.3,
        "body_query" : 0.5,
        "recency" : 0.1
    }

===================
Feature normalization
===================

`Feature Normalization <https://www.google.com/search?client=safari&rls=en&q=wikipedia+feature+normalization&ie=UTF-8&oe=UTF-8>`_ transforms feature values to a more consistent range (like 0 to 1 or -1 to 1) at training time to better understand their relative impact. Some models, especially linear ones (like `SVMRank <http://www.cs.cornell.edu/people/tj/svm_light/svm_rank.html>`_), rely on normalization to work correctly. 



===================
Uploading a model
===================

Once you have a model, you'll want to use it for search. You'll need to upload it to Elasticsearch LTR. Models are uploaded specifying the following arguments

- The feature set that was trained against
- The type of model (such as ranklib or xgboost)
- The model contents

Uploading a Ranklib model trained against :code:`more_movie_features` looks like::


    POST _ltr/_featureset/more_movie_features/_createmodel
    {
        "model": {
            "name": "my_ranklib_model",
            "model": {
                "type": "model/ranklib",
                "definition": "## LambdaMART\n
                                ## No. of trees = 1000
                                ## No. of leaves = 10
                                ## No. of threshold candidates = 256
                                ## Learning rate = 0.1
                                ## Stop early = 100

                                <ensemble>
                                    <tree id="1" weight="0.1">
                                        <split>
                                            <feature> 2 </feature>
                                            ...
                            "
            }
        }
    }

Or an xgboost model::

    POST _ltr/_featureset/more_movie_features/_createmodel
    {
        "model": {
            "name": "my_xgboost_model",
            "model": {
                "type": "model/xgboost+json",
                "definition": "[  { \"nodeid\": 0, \"depth\": 0, \"split\": \"tmdb_multi\", \"split_condition\": 11.2009, \"yes\": 1, \"no\": 2, \"missing\": 1, \"children\": [
                                    { \"nodeid\": 1, \"depth\": 1, \"split\": \"tmdb_title\", \"split_condition\": 2.20631, \"yes\": 3, \"no\": 4, \"missing\": 3, \"children\": [
                                      { \"nodeid\": 3, \"leaf\": -0.03125 },
                                    ..."
            }
        }
    }

Or an xgboost model with parameters::

    POST _ltr/_featureset/more_movie_features/_createmodel
    {
        "model": {
            "name": "my_xgboost_model",
            "model": {
                "type": "model/xgboost+json",
                "definition": "{
                                 \"objective\": \"reg:logistic\",
                                 \"splits\": [  { \"nodeid\": 0, \"depth\": 0, \"split\": \"tmdb_multi\", \"split_condition\": 11.2009, \"yes\": 1, \"no\": 2, \"missing\": 1, \"children\": [
                                                  { \"nodeid\": 1, \"depth\": 1, \"split\": \"tmdb_title\", \"split_condition\": 2.20631, \"yes\": 3, \"no\": 4, \"missing\": 3, \"children\": [
                                                    { \"nodeid\": 3, \"leaf\": -0.03125 },
                                                  ...
                                             ]
                               }"
            }
        }
    }

Or a simple linear model::

    POST _ltr/_featureset/more_movie_features/_createmodel
    {
        "model": {
            "name": "my_linear_model",
            "model": {
                "type": "model/linear",
                "definition": "
                                {
                                    \"title_query\" : 0.3,
                                    \"body_query\" : 0.5,
                                    \"recency\" : 0.1
                                }
                            "
            }
        }
    }


--------------------
Creating a model with Feature Normalization
--------------------

We can ask that features be normalized prior to evaluating the model. Elasticsearch Learning to Rank supports min max and standard feature normalization. 

With standard feature normalization, values corresponding to the mean will have a value of 0, one standard deviation above/below will have a value of -1 and 1 respectively::

    POST _ltr/_featureset/more_movie_features/_createmodel
    {
        "model": {
            "feature_normalizers": {
               "release_year": {
                  "standard": {
                    "mean": 1970,
                    "standard_deviation": 30
                  }
               }
            },
            "name": "my_linear_model",
            "model": {
                "type": "model/linear",
                "definition": "
                                {
                                    \"release_year\" : 0.3,
                                    \"body_query\" : 0.5,
                                    \"recency\" : 0.1
                                }
                            "
            }
        }
    }

Also supported is min-max normalization. Where values at the specified minimum receive 0, at the maximum turn into 1::

    "feature_normalizers": {
        "vote_average": {
            "standard": {
                "minimum": 0,
                "maximum": 10
            }
        }
    }


===================
Models aren't "owned by" featuresets
===================

Though models are created in reference to a feature set, it's importnrt to note after creation models are *top level* entities. For example, to fetch a model back, you use GET::

    GET _ltr/_model/my_linear_model

Similarly, to delete::

    DELETE _ltr/_model/my_linear_model

This of course means model names are globally unique across all feature sets.

The associated features are *copied into* the model. This is for your safety: modifying the feature set or deleting the feature set after model creation doesn't have an impact on a model in production. For example, if we delete the feature set above::

    DELETE _ltr/_featureset/more_movie_features

We can still access and search with "my_linear_model". The following still accesses the model and it's associated features::

    GET _ltr/_model/my_linear_model

You can expect a response that includes the features used to create the model (compare this with the more_movie_features in :doc:`logging-features`)::

    {
    "_index": ".ltrstore",
    "_type": "store",
    "_id": "model-my_linear_model",
    "_version": 1,
    "found": true,
    "_source": {
        "name": "my_linear_model",
        "type": "model",
        "model": {
            "name": "my_linear_model",
            "feature_set": {
                "name": "more_movie_features",
                "features": [
                {
                    "name": "body_query",
                    "params": [
                        "keywords"
                        ],
                     "template": {
                        "match": {
                            "overview": "{{keywords}}"
                        }
                    }
                },
                {
                    "name": "title_query",
                    "params": [
                        "keywords"
                    ],
                    "template": {
                        "match": {
                            "title": "{{keywords}}"
                        }
                    }
                }
        ]}}}

With a model uploaded to Elasticsearch, you're ready to search! Head to :doc:`searching-with-your-model` to see put model into action.
