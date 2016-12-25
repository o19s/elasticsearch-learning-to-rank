# elasticsearch-learning-to-rank
Plugin to Rescore Elasticsearch results with Learning to Rank Model. Uses models generated offline via [RankLib](https://sourceforge.net/p/lemur/wiki/RankLib/).

Work in progress, tasks remaining:

- Add Test for Lucene Query
- Fleshout Elasticsearch query parser that takes a RankLib model file and reranks
- Tests for Elasticsearch query
- Document offline model training process and how the query should be used
- Add Lucene Query Explain to help debug
