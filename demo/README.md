# Learning to Rank Demo

This demo uses data from [TheMovieDB](http://themoviedb.org) (TMDB) to demonstrate using [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/) learning to rank models with Elasticsearch.

# Install Dependencies and prep...

This demo requires

- Python 3+
- Python elasticsearch and requests libraries

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
python indexMlTmdb.py
```

# Onto the machine learning...

## Create and upload features

A "feature" in ES LTR corresponds to an Elasticsearch query. The score yielded by the query is used to train and evaluate the model. For example, if you feel that a TF\*IDF title score corresponds to higher relevance, then that's a feature you'd want to train on!

In the demo features 1...n json are mustache templates that correspond to the features. In this case, the features are identified by *ordinal* (feature 1 is in 1.json). For traditional Ranklib models, the ordinal is the only way features are identified. Other models use feature *names* which make developing, logging, and managing features more maintainable.

Run the following script to load Features into Elasticsearch, under a 'movie_features' feature set.

```
python loadFeatures.py
```

## Gather Judgments

The first part of the training data is the *judgment list*. That's what you'll find in [sample_judgments.txt](sample_judgments.txt). This includes labels for document and query pairs. Labels come in the form of *grades*. For example if movie "First Blood" is considered extremely relevant for the query Rambo, we give it a grade of 4 ('exactly relevant'). The movie Bambi would recieve a '0'. The syntax used is LibSVM format, with comments used to track the keywords for each `qid`, the document identifier, and other metadata:

```
# qid:1: rambo
#
#
# grade (0-4)	queryid	 # docId	title
4	qid:1 #	7555	Rambo
```

You don't need to do anything to gather judgments, unless you want to manipulate the provided judgments file to add/modify movie grades for queries.


## Log features

The second part of the training data are the feature values for each individual judgment. For example, what's the relevance score of a match query applying the keywords to the title field? How old is the movie? How many keywords are in the query? The ES LTR plugin views queries as features. This is what the `collectFeatures.py` file does. It runs a learning-to-rank query for each query/document pair to extract feature values, creating a `sample_judgments_wfeatures.txt` which contains feature values for each ordinal.

The line above is transformed into:

```
4	qid:1	1:12.318446	2:9.8376875 # 7555	rambo
```

You can run `python collectFeatures.py` directly to do this, or simply run `train.py` to rebuild features and fully train (see below).

## Train and upload the model

With training data in place, it's time to ask RankLib to train a model, and output to a test file. RankLib supports linear models, ListNet, and several tree-based models such as LambdaMART. The script below iterates through all of Ranklibs models, and stores models of type "ranklib" in the 

Run this script with:

```
python train.py
```

## Search using the model

See what sort of search results you get! There's a Query running the `ltr` query in a rescore phase in the python script. This script runs the LambdaMART model trained against this data:

```
python search.py rambo
```
