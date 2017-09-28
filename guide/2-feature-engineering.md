# Feature Engineering for Learning to Rank

You've seen how to add features to feature sets. We want to show you how to address common feature engineering tasks that come up when developing a learning to rank solution. 

## Raw Term Statistics

A lot of LTR literature discusses using raw term statistics. Such as the total term frequency for a term, the document frequency, and other statistics. Luckily, we've added an Elasticsearch query primitive, `match_explorer`, that extracts these statistics for you. In it's simplest form, `match_explorer` you specify a statistic you're interested in and a match you`d like to explore. For example:

```
POST tmdb/_search
{
   "query": {
      "match_explorer": {
         "type": "max_raw_df",
         "query": {
             "match": {
                "title": "rambo rocky"
             }
         }
      }
   }
}
```

This query would return the highest document frequency between the two terms. 

A large number of statistics are available. The `type` field can be prepended with the operation to be performed across terms for the statistic `max`, `min`, `sum`, and `stddev`. 

The statistics available include:

- `raw_df` -- the direct document frequency for a term. So if rambo occurs in 3 movie titles, this is 3.
- `classic_idf` -- the IDF calculation of the classic similarity `log((NUM_DOCS+1)/(docFreq+1)) + 1`.
- `raw_ttf` -- the total term frequency for the term across the index. So if rambo is mentioned a total of 100 times in the overview field, this would be 100.

So to get stddev of classic_idf, you would write `stddev_classic_idf`. To get the minimum total term frequency, you'd write `min_raw_ttf`.

Finally a special stat exists for just counting the number of search terms. That stat is `unique_terms_count`.

## Document-specific features.

Another common case in Learning to Rank is features such as popularity or recency. Elasticsearch's `function_score` query has the functionality you need to pull this data out. You already saw an example when adding features in the last section:

```json
{
    "query": {
        "function_score": {
            "functions": {
                "field": "vote_average"
            },
            "query": {
                "match_all": {}
            }
        }
    }
}
```


## Your index may drift

If you have an index that updates regularly, trends that held true today, may not hold true tomorrow! On an e-commerce store, sandals might be very popular in the summer, but impossible to find in the winter. Features that drive purchases for one time period, may not hold true for another. It's always a good idea to monitor your model's performance regularly, retrain as needed.