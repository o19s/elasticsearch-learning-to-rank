Feature Engineering 
****************************************************

You've seen how to add features to feature sets. We want to show you how to address common feature engineering tasks that come up when developing a learning to rank solution. 

======================
Getting Raw Term Statistics
======================

Many learning to rank solutions use raw term statistics in training. For example, the total term frequency for a term, the document frequency, and other statistics. Luckily, Elasticsearch LTR comes with a query primitive, :code:`match_explorer`, that extracts these statistics for you for a set of terms. In it's simplest form, :code:`match_explorer` you specify a statistic you're interested in and a match you'd like to explore. For example::

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


This query returns the highest document frequency between the two terms. 

A large number of statistics are available. The :code:`type` parameter can be prepended with the operation to be performed across terms for the statistic :code:`max`, :code:`min`, :code:`sum`, and :code:`stddev`. 

The statistics available include:

- :code:`raw_df` -- the direct document frequency for a term. So if rambo occurs in 3 movie titles, this is 3.
- :code:`classic_idf` -- the IDF calculation of the classic similarity :code:`log((NUM_DOCS+1)/(raw_df+1)) + 1`.
- :code:`raw_ttf` -- the total term frequency for the term across the index. So if rambo is mentioned a total of 100 times in the overview field, this would be 100.

Putting the operation and the statistic together, you can see some examples. To get stddev of classic_idf, you would write :code:`stddev_classic_idf`. To get the minimum total term frequency, you'd write :code:`min_raw_ttf`.

Finally a special stat exists for just counting the number of search terms. That stat is :code:`unique_terms_count`.

===========================
Document-specific features
===========================

Another common case in learning to rank is features such as popularity or recency, tied only to the document. Elasticsearch's :code:`function_score` query has the functionality you need to pull this data out. You already saw an example when adding features in the last section::

    {
        "query": {
            "function_score": {
                "functions": [{
                    "field_value_factor": {
                        "field": "vote_average",
                        "missing": 0
                    }
                }],
                "query": {
                    "match_all": {}
                }
            }
        }
    }


The score for this query corresponds to the value of the :code:`vote_average` field.

=======================
Your index may drift
=======================

If you have an index that updates regularly, trends that held true today, may not hold true tomorrow! On an e-commerce store, sandals might be very popular in the summer, but impossible to find in the winter. Features that drive purchases for one time period, may not hold true for another. It's always a good idea to monitor your model's performance regularly, retrain as needed.

Next up, we discuss the all-important task of logging features in :doc:`logging-features`.
