Searching with LTR
**************************

Now that you have a model, what can you do with it? As you saw in :doc:`logging-features`, the Elasticsearch LTR plugin comes with the `sltr` query. This query is also what you use to execute models::

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

.. warning:: you almost certainly don't want to run `sltr` this way :)

=========================
Rescore top N with `sltr`
=========================

In reality you would never want to use the :code:`sltr` query this way. Why? This model executes on *every result in your index*. These models are CPU intensive. You'll quickly make your Elasticsearch cluster crawl with the query above.

More often, you'll execute your model on the top N of a baseline relevance query. You can do this using Elasticsearch's built in `rescore functionality <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-rescore.html>`_::

    POST tmdb/_search
    {
        "query": {
            "match": {
                "_all": "rambo"
            }
        },
        "rescore": {
            "window_size": 1000,
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

Here we execute a query that limits the result set to documents that match "rambo". All the documents are scored based on Elasticsearch's default similarity (BM25). On top of those already reasonably relevant results we apply our model over the top 1000. 

Viola!

===========================
Models! Filters! Even more!
===========================

One advantage of having :code:`sltr` as just another Elasticsearch query is you can mix/match it with business logic and other. We won't dive into these examples here, but we want to invite you to think creatively about scenarios, such as

- Filtering out results based on business rules, using Elasticsearch filters before applying the model
- Chaining multiple rescores, perhaps with increasingly sophisticated models
- Rescoring once for relevance (with `sltr`), and a second time for business concerns
- Forcing "bad" but relevant content out of the rescore window by downboosting it in the baseline query
