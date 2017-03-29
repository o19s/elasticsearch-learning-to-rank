# Learning to Rank Demo

This demo uses data from [TheMovieDB](http://themoviedb.org) (TMDB) to demonstrate Learning to Rank in Elasticsearch. This demo is a set of Python 3 scripts that use the elasticsearch client library, requests for HTTP requests, and jinja2 templates to help format Elasticsearch queries given a keyword.

## Install Dependencies

See requirements.txt for a set of dependencies. You can install those with pip with:

```
pip install -r requirements.txt
```

Optionally, load these into a virtual environment by running these commands first:

```
pyvenv venv
source venv/bin/activate
```

## Download TMDB Data

The steps below create a `tmdb.json` file full of movie data we can then use with Elasticserach

#### 1. Get a TMDB Key

Visit the TMDB site and get [an API key](https://www.themoviedb.org/faq/api?language=en)

#### 2. Set the TMDB API key in your shell

```
export TMDB_API_KEY=<key from step 1>
```

#### 3. Pull down the TMDB data

Run the script that prepares the movie data. This will create a file called "tmdb.json." The requests are rate limited as specified by TMDB's API params, so expect it to take a while.

```
./prepareData.sh
```

## Start Elasticsearch/install plugin

Start a supported version of Elasticsearch and follow the instructions to install the learning to rank plugin.


## Index to Elasticsearch

This script will create a 'tmdb' index with default/simple mappings. You can edit this file to play with mappings.

```
python indexMlTmdb.py
```

## Train and upload the model

This script will run through all the steps of training using the downloaded Ranklib.jar. First it parses `sample_judgements.txt`. Using the keywords in the header comment, and the ES queries in `1.json.jinja...N.json.jinja` it batches up queries to Elasticsearch via `_msearch` to gather feature values 1..N. It creates a second file consumable by Ranklib called `sample_judgments_wfeatures.txt` where each feature value is listed next to each judgment `grade qid:N 1:<val> 2:<val> ...` etc. It then asks Ranklib to train all the supported models (linear, lambdamart, random forest, etc) outputing a model to a text file. It loads these models into Elasticsearch as ranklib scripts

```
python train.py
```

## Search using the model

See what sort of search results you get! There's a Query running the `ltr` query in a rescore phase in the python script.

```
python search.py <keyword>
```
