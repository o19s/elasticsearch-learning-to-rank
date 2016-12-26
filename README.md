# elasticsearch-learning-to-rank
Plugin to Rescore Elasticsearch results with Learning to Rank Model. Uses models generated offline via [RankLib](https://sourceforge.net/p/lemur/wiki/RankLib/).

Work in progress, tasks remaining:

- Add Test for Lucene Query
- Fleshout Elasticsearch query parser that takes a RankLib model file and reranks
- Tests for Elasticsearch query
- Document offline model training process and how the query should be used
- Add Lucene Query Explain to help debug


## Vision

RankLib is a handy Java Learning to Rank library with a variety of models under the hood. If you have some judgments (perhaps expert annotated, perhaps from clicklogs/conversions). You take those judgements (in a file format akin to what Quepid produces) alongside features (TF\*IDF, title term freq, whatever) and you use RankLib at the command line to train a model that predicts a relevance score based on tohse features. RankLib outputs a model file with the model encoded. You can read this model back in to rescore documents.

Now for Elasticsearch a feature is a Query DSL query, letting you use everything available in Elasticsearch's query library
 We can then score documents by executing those query DSL queries, getting a score, and running our model.
 
So this plugin implements an ltr-query that takes as input
- An array of query DSL queries called `features`
- A string specifying a model called `model`.

You could score *everything* with a learning to rank query, but the idea here is you've wrapped this query in a rescoring query. Otherwise you'll scan all your documents and get poor relevance.

Now this query is likely to get quite large, so we recommend using search templates to reencode your features/model after training.
