# Learning to Rank Demo

This demo uses data from [TheMovieDB](http://themoviedb.org) (TMDB) to demonstrate using [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/) learning to rank models with Elasticsearch.

# Install Dependencies and prep data...

This demo requires

- Python 3+
- Python `elasticsearch` and `requests` libraries
- Python `elasticsearch_xpack` libraries if xpack support is necessary

## An aside: X Pack

Using the LTR plugin with xpack requires configuring appropriate roles. These
can be setup automatically by `prepare_xpack.py` which takes a username and
will prompt for a password.  After this is run `settings.cfg` must be edited to
uncomment the ESUser and ESPassword properties.

```
python prepare_xpack.py <xpack admin username>
```

## Download the TMDB Data & Ranklib Jar

The first time you run this demo, fetch RankLib.jar (used to train model) and tmdb.json (the dataset used)

```
python prepare.py
```

## Start Elasticsearch/install plugin

Start a supported version of Elasticsearch and follow the [instructions to install](https://github.com/o19s/elasticsearch-learning-to-rank#installing) the learning to rank plugin.

## Index to Elasticsearch

This script will create a 'tmdb' index with default/simple mappings. You can edit this file to play with mappings.

```
python index_ml_tmdb.py
```

# Onto the machine learning...

## TLDR

If you're actually going to build a learning to rank system, read past this section. But to sum up, the full Movie demo can be run by

```
python train.py
```

Then you can search using

```
python search.py Rambo
```

and search results can be printed to the console.

More on how all this actually works below:

## Create and upload features (load_features.py)

A "feature" in ES LTR corresponds to an Elasticsearch query. The score yielded by the query is used to train and evaluate the model. For example, if you feel that a TF\*IDF title score corresponds to higher relevance, then that's a feature you'd want to train on! Other features might include how old a movie is, the number of keywords in a query, or whatever else you suspect might correlate to your user's sense of relevance.

If you examine [load_features.py](load_features.py) you'll see how we create features. We first initialize the default feature store (`PUT /_ltr`). We create a feature set (`POST /_ltr/_featureset/movie_features`). Now we have a place to create features for both logging & use by our models!

In the demo features 1...n json are mustache templates that correspond to the features. In this case, the features are identified by *ordinal* (feature 1 is in 1.json). They are uploaded to Elasticsearch Learning to Rank with these ordinals as the feature name. In `eachFeature`, you'll see a loop where we access each mustache template an the file system and return a JSON body for adding the feature to Elasticsearch.

For traditional Ranklib models, the ordinal is the only way features are identified. Other models use feature *names* which make developing, logging, and managing features more maintainable.

## Gather Judgments (sample_judgments.txt)

The first part of the training data is the *judgment list*. We've provided one in [sample_judgments.txt](sample_judgments.txt). 

What's a judgment list? A judgment list tells us how relevant a document is for a search query. In other words, a three-tuple of 

```
<grade>,<docId>,<keywords>
```

Quality comes in the form of *grades*. For example if movie "First Blood" is considered extremely relevant for the query Rambo, we give it a grade of 4 ('exactly relevant'). The movie Bambi would receive a '0'. Instead of the notional CSV format above, Ranklib and other learning to rank systems use a format from LibSVM, shown below:

```
# qid:1: rambo
#
#
# grade (0-4)	queryid	 # docId	title
4	qid:1 #	7555	Rambo
```

You'll notice we bastardize this syntax to add comments identifying the keywords associated with each query id, and append metadata to each line. Code provided in [judgments.py](judgments.py) handles this syntax.

## Log features (collect_features.py)

You saw above how we created features, the next step is to log features for each judgment 3-tuple. This code is in [collect_features.py](collect_features.py). Logging features can be done in several different contexts. Of course, in a production system, you may wish to log features as users search. In other contexts, you may have a hand-created judgment list (as we do) and wish to simply ask Elasticsearch Learning to Rank for feature values for query/document pairs.

Is [collect_features.py](collect_features.py), you'll see an `sltr` query is included. This query points to a featureSet, not a model. So it does not influence the score. We filter down to needed document ids for each keyword and allow this `sltr` query to run.

You'll also notice an `ext` component in the request. This search extension is part of the Elasticsearch Learning to Rank plugin and allows you to configure feature logging. You'll noticed it refers to the query name of `sltr`, allowing it to pluck out the `sltr` query and perform logging associated with the feature set.

Once features are gathered, the judgment list is fleshed out with feature value, the ordinals below corresponding to the features in our 1..n.json files.

```
4	qid:1	1:12.318446	2:9.8376875 # 7555	rambo
```

## Train (train.py and RankLib.jar)

With training data in place, it's time to ask RankLib to train a model, and output to a test file. RankLib supports linear models, ListNet, and several tree-based models such as LambdaMART. In [train.py](train.py) you'll notice how RankLib is called with command line arguments. Models `test_N` are created in our feature store for each type of RankLib model. In the `saveModel` function, you can see how the model is uploaded to our "movie_features" feature set.

## Search using the model (search.py)

See what sort of search results you get! In `search.py` you'll see we execute the `sltr` query referring to a `test_N` model in the rescore phase. By default `test_6` is used (corresponding to LambdaMART), but you can change the used model at the command line.

Search with default LambdaMART:

```
python search.py rambo
```

Try a different model:

```
python search.py rambo test_8
```
## Use it on your own data

If you want to reuse these scripts on your own data, choose the names of files and models, change or copy the settings.cfg. If you want to use your own file, change the utils.py.
