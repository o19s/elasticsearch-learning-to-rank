# Learning to Rank Demo

This demo uses data from [TheMovieDB](http://themoviedb.org) (TMDB) to demonstrate Learning to Rank in Elasticsearch

## Running the demo

### Grab the TMDB data (run once)

The steps below let you get a `tmdb.json` file full of movie data we can then use with Elasticserach

#### 1. Get a TMDB Key

Visit the TMDB site and get [an API key](https://www.themoviedb.org/faq/api?language=en)

#### 2. Set the TMDB API key in your shell

```
export TMDB_API_KEY=<key from step 1>
```

#### 3. Pull down the TMDB data

Run the script that prepares the shell data. This will create a file called "tmdb.json." The requests are rate limited as specified by TMDB's API params, so expect it to take a while.

```
./prepareData.sh
```

## Index to Elasticsearch

## Train the model

```
python train.py
```

## Search using the model

```
python search.py <keyword>
```
