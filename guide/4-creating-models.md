# Creating models

Training models occurs outside Elasticsearch. You use the plugin to log features. Then with whichever technology you choose, you train a ranking model. You upload a model to Elasticsearch LTR in the approved serialization formats (ranklib and xgboost).

## Training Examples

We provide two demos for training a model. A fully-gledged [Ranklib Demo](/demo) which interacts with the library. You can see how features are [logged](/demo/collectFeatures.py) and how models are [trained](/demo/train.py). In particular, you'll note that logging create a ranklib consumable judgment file that looks like:

```
4	qid:1	1:9.8376875	2:12.318446 # 7555	rambo
3	qid:1	1:10.7808075	2:9.510193 # 1370	rambo
3	qid:1	1:10.7808075	2:6.8449354 # 1369	rambo
3	qid:1	1:10.7808075	2:0.0 # 1368	rambo
```

Here for query id 1 (Rambo) we've logged features 1 (a title TF\*IDF score) and feature 2 (an overview TF\*IDF score) for a set of documents. In [train.py](/demo/train.py) you'll see how we call Ranklib to train one of it's supported models on this line:

```
    cmd = "java -jar RankLib-2.8.jar -ranker %s -train %s -save %s -frate 1.0" % (whichModel, judgmentsWithFeaturesFile, modelOutput)
```

Our "judgmentsWithFeatureFile" is the input to RankLib. Other parameters are passed, which you can read about in [Ranklib's documentation]().

Ranklib will output a model in it's own serialization format. For example a LambdaMART model is an ensemble of regression trees. It looks like

```
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
```

Notice how each tree examines the value of features, makes a decision based on the value of a feature, then ultimately outputs the relevance score. You'll note features are referred to by ordinal, starting by "1" with Ranklib (this corresponds to the 0th feature in your feature set). Ranklib does not use feature names when training.

### XGBoost Example

There's also a small example of how to train a model using XGboost [here](/demo/xgboost-demo). Examining this demo, you'll see the difference in how Ranklib is executed vs XGBoost. XGBoost will output a serialization format for gradient boosted decision tree that looks like:

```
[  { "nodeid": 0, "depth": 0, "split": "tmdb_multi", "split_condition": 11.2009, "yes": 1, "no": 2, "missing": 1, "children": [
    { "nodeid": 1, "depth": 1, "split": "tmdb_title", "split_condition": 2.20631, "yes": 3, "no": 4, "missing": 3, "children": [
      { "nodeid": 3, "leaf": -0.03125 },
      ...
```

### Simple linear models

Many types of models simply output linear weights of each feature. The LTR model supports simple linear weights for each features, such as those learned from an SVM model or linear regression:


## Uploading a model

Once you have a model, you'll want to use it for search. You'll need to upload it to Elasticsearch LTR. Models are uploaded specifying the following arguments

- The feature set that was trained against
- The type of model (such as ranklib or xgboost)
- The model contents

Uploading a Ranklib model trained against `more_movie_features` looks like:

```
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
```

Or an xgboost model:

```
POST _ltr/_featureset/more_movie_features/_createmodel
{
    "model": {
        "name": "dougs_ranklib_model",
        "model": {
            "type": "model/xgboost",
            "definition": "[  { "nodeid": 0, "depth": 0, "split": "tmdb_multi", "split_condition": 11.2009,                     "yes": 1, "no": 2, "missing": 1, "children": [
                                { "nodeid": 1, "depth": 1, "split": "tmdb_title", "split_condition": 2.20631, "yes": 3, "no": 4, "missing": 3, "children": [
                                { "nodeid": 3, "leaf": -0.03125 },
                                ..."
        }
    }
}
```

With a model uploaded to Elasticsearch, you're ready to search!

