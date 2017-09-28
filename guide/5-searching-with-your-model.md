# Searching with Your Model

Now that you have a model, what can you do with it? As you've seen, the Elasticsearch LTR plugin comes with the `sltr` query for executing models given a feature set. 

Executing a model is simply a search away:

```
POST tmdb/_search
{
    "query": {
         "sltr": {
                "params": {
                    "keywords": "rambo"
                },
                "model": "my_model",
            }
    }
}
```

## Don't run `sltr` directly, instead rescore!!!

In reality you would never want to use the `sltr` query this way. Why? This model executes on *every result in your index*. These models are CPU intensive. You'll quickly make your Elasticsearch cluster crawl with this query.

More normally you'll execute your model on the top N of a baseline relevance query. Such as:

```
POST tmdb/_search
{
    "query": {
        "match": {
            "_all": "rambo"
        }
    },
    "rescore": {
      "window_size": 100,
      "query": {
        "rescore_query": {
            "sltr": {
                "params": {
                    "keywords": "rambo"
                },
                "model": "my_model",
            }
         }
      }
   }
}
```

Here we execute a query that limits the result set to documents that match "rambo". All the documents are scored based on Elasticsearch's default similarity (BM25). On top of those already reasonably relevant results we apply our model over the top 100, using Elasticsearch's existing [rescore functionality](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-rescore.html). Injecting our custom `sltr` query, we execute our model only over the top 100 results.

Viola!

## Models! Filters! Even more!

One advantage of having `sltr` as just another Elasticsearch query is you can mix/match it with business logic and other. I want to invite you to think about other scenarios, such as

- Filtering out results based on business rules
- Rescoring once for relevance (with `sltr`), and a second time for inventory
- Forcing "bad" but relevant content out of the rescore window by downboosting it in the baseline query