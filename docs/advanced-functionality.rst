Advanced Functionality
***********************

This section documents some additional functionality you may find useful after you're comfortable with the primary capabilities of Elasticsearch LTR.

=============================
Reusable Features
=============================

In :doc:`building-features` we demonstrated creating feature sets by uploading a list of features. Instead of repeating common features in every feature set, you may want to keep a library of features around.

For example, perhaps a query on the title field is important to many of your feature sets, you can use the feature API to create a title query::

    POST _ltr/_feature/titleSearch
    {
        "feature":
        {
            "params": [
            "keywords"
            ],
            "template": {
            "match": {
                "title": "{{keywords}}"
            }
            }
        }
    }

As you'd expect, normal CRUD operations apply. You can DELETE a feature::

    DELETE _ltr/_feature/titleSearch

And fetch an individual feature::

    GET _ltr/_feature/titleSearch

Or look at all your features, optionally filtered by name prefix::

    GET /_ltr/_feature?prefix=t

You can create or update a feature set, you can refer to the titleSearch feature::

    POST /_ltr/_featureset/my_featureset/_addfeatures/titleSearch

This will place titleSearch at the next ordinal position under "my_feature_set"

.. _derived-features:

=================
Derived Features
=================

Features that build on top of other features are called derived features.  These can be expressed as `lucene expressions <http://lucene.apache.org/core/7_1_0/expressions/index.html?org/apache/lucene/expressions/js/package-summary.html>`_. They are recognized by :code:`"template_language": "derived_expression"`. Besides these can also take in query time variables of type `Number <https://docs.oracle.com/javase/8/docs/api/java/lang/Number.html>`_ as explained in :ref:`create-feature-set`.

Script Features
-----------------

These are essentially :ref:`derived-features`, having access to the :code:`feature_vector` but could be native or painless elasticsearch scripts rather than `lucene expressions <http://lucene.apache.org/core/7_1_0/expressions/index.html?org/apache/lucene/expressions/js/package-summary.html>`_. :code:`"template_language": "script_feature""` allows LTR to identify the templated script as a regular elasticsearch script e.g. native, painless, etc.

The custom script has access to the feature_vector via the java `Map <https://docs.oracle.com/javase/8/docs/api/java/util/Map.html>`_ interface as explained in :ref:`create-feature-set`.

(WARNING script features can cause the performance of your Elasticsearch cluster to degrade, if possible avoid using these for feature generation if you require your queries to be highly performant)

Script Features Parameters
--------------------------

Script features are essentially native/painless scripts and can accept parameters as per the `elasticsearch script documentation <https://www.elastic.co/guide/en/elasticsearch/reference/master/modules-scripting-using.html>`_. We can override parameter values and names to scripts within LTR scripts. Priority for parameterization in increasing order is as follows
 
 - parameter name, value passed in directly to source script but not in params in ltr script. These cannot be configured at query time.
 - parameter name passed in to sltr query and to source script, so the script parameter values can be overridden at query time.
 - ltr script parameter name to native script parameter name indirection. This allows ltr parameter name to be different from the underlying script parameter name. This allows same native script to be reused as different features within LTR by specifying different parameter names at query time::

    POST _ltr/_featureset/more_movie_features
    {
       "featureset": {
            "features": [
                {
                    "name": "title_query",
                    "params": [
                        "keywords"
                    ],
                    "template_language": "mustache",
                    "template": {
                        "match": {
                            "title": "{{keywords}}"
                        }
                    }
                },
                {
                    "name": "custom_title_query_boost",
                    "params": [
                        "some_multiplier",
                        "ltr_param_foo"
                    ],
                    "template_language": "script_feature",
                    "template": {
                        "lang": "painless",
                        "source": "(long)params.default_param * params.feature_vector.get('title_query') * (long)params.some_multiplier * (long) params.param_foo",
                        "params": {
                            "default_param" : 10.0,
                            "some_multiplier": "some_multiplier",
                            "extra_script_params": {"ltr_param_foo": "param_foo"}
                        }
                    }
                }
            ]
       }
    }



=============================
Multiple Feature Stores
=============================

We defined a feature store in :doc:`building-features`. A feature store corresponds to an independent LTR system: features, feature sets, models backed by a single index and cache. A feature store corresponds roughly to a single search problem, often tied to a single application. For example wikipedia might be backed by one feature store, but wiktionary would be backed by another. There's nothing that would be shared between the two.

Should your Elasticsearch cluster back multiple properties, you can use all the capabilities of this guide on named feature stores, simply by::

    PUT _ltr/wikipedia

Then the same API in this guide applies to this feature store, for example to create a feature set::

    POST _ltr/wikipedia/_featureset/attempt_1
    {
       "featureset": {
            "features": [
                {
                    "name": "title_query",
                    "params": [
                        "keywords"
                    ],
                    "template_language": "mustache",
                    "template": {
                        "match": {
                            "title": "{{keywords}}"
                        }
                    }
                }
            ]
       }
    }

And of course you can delete a featureset::

    DELETE _ltr/wikipedia/_featureset/attempt_1

=============================
Model Caching
=============================

The plugin uses an internal cache for compiled models.

Clear the cache for a feature store to force models to be recompiled::

    POST /_ltr/_clearcache

Get cluster wide cache statistics for this store::

    GET /_ltr/_cachestats

Characteristics of the internal cache can be controlled with these node settings::

    # limit cache usage to 12 megabytes (defaults to 10mb or max_heap/10 if lower)
    ltr.caches.max_mem: 12mb
    # Evict cache entries 10 minutes after insertion (defaults to 1hour, set to 0 to disable)
    ltr.caches.expire_after_write: 10m
    # Evict cache entries 10 minutes after access (defaults to 1hour, set to 0 to disable)
    ltr.caches.expire_after_read: 10m

=============================
Extra Logging
=============================

As described in :doc:`logging-features`, it is possible to use the logging extension to return the feature values with each document. For native scripts, it is also possible to return extra arbitrary information with the logged features.

For native scripts, the parameter :code:`extra_logging` is injected into the script parameters. The parameter value is a `Supplier <https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html>`_ <`Map <https://docs.oracle.com/javase/8/docs/api/java/util/Map.html>`_>, which provides a non-null :code:`Map<String,Object>` **only** during the logging fetch phase. Any values added to this Map will be returned with the logged features::

    @Override
    public double runAsDouble() {
    ...
        Map<String,Object> extraLoggingMap = ((Supplier<Map<String,Object>>) getParams().get("extra_logging")).get();
        if (extraLoggingMap != null) {
            extraLoggingMap.put("extra_float", 10.0f);
            extraLoggingMap.put("extra_string", "additional_info");
        }
    ...
    }

If (and only if) the extra logging Map is accessed, it will be returned as an additional entry with the logged features::

    {
        "log_entry1": [
            {
                "name": "title_query"
                "value": 9.510193
            },
            {
                "name": "body_query"
                "value": 10.7808075
            },
            {
                "name": "user_rating",
                "value": 7.8
            },
            {
                "name": "extra_logging",
                "value": {
                    "extra_float": 10.0,
                    "extra_string": "additional_info"
                }
            }
        ]
    }

=============================
Stats
=============================
The stats API gives the overall plugin status and statistics::

    GET /_ltr/_stats

    {
        "_nodes": {
            "total": 1,
            "successful": 1,
            "failed": 0
        },
        "cluster_name": "es-cluster",
        "stores": {
            "_default_": {
                "model_count": 10,
                "featureset_count": 1,
                "feature_count": 0,
                "status": "green"
            }
        },
        "status": "green",
        "nodes": {
            "2QtMvxMvRoOTymAsoQbxhw": {
                "cache": {
                    "feature": {
                        "eviction_count": 0,
                        "miss_count": 0,
                        "hit_count": 0,
                        "entry_count": 0,
                        "memory_usage_in_bytes": 0
                    },
                    "featureset": {
                        "eviction_count": 0,
                        "miss_count": 0,
                        "hit_count": 0,
                        "entry_count": 0,
                        "memory_usage_in_bytes": 0
                    },
                    "model": {
                        "eviction_count": 0,
                        "miss_count": 0,
                        "hit_count": 0,
                        "entry_count": 0,
                        "memory_usage_in_bytes": 0
                    }
                }
            }
        }
    }

You can also use filters to retrieve a single stat::

    GET /_ltr/_stats/{stat}

Also you can limit the information to a single node in the cluster::

    GET /_ltr/_stats/nodes/{nodeId}

    GET /_ltr/_stats/{stat}/nodes/{nodeId}


=============================
TermStat Query
=============================

The :code:`TermStatQuery` is a re-imagination of the legacy :code:`ExplorerQuery` which offers clearer specification of terms and more freedom to experiment.  This query surfaces the same data as the `ExplorerQuery` but it allows the user to specify a custom Lucene expression for the type of data they would like to retrieve.  For example::

    POST tmdb/_search
    {
        "query": {
            "term_stat": {
                "expr": "df",
                "aggr": "max",
                "terms": ["rambo,  "rocky"],
                "fields": ["title"]
            }
        }
    }


The :code:`expr` parameter is the Lucene expression you want to run on a per term basis.  This can simply be a stat type, or a custom formula containing multiple stat types, for example: :code:`(tf * idf) / 2`.  The following stat types are injected into the Lucene expression context for your usage:

- :code:`df` -- the direct document frequency for a term. So if rambo occurs in 3 movie titles across multiple documents, this is 3.
- :code:`idf` -- the IDF calculation of the classic similarity :code:`log((NUM_DOCS+1)/(raw_df+1)) + 1`.
- :code:`tf` -- the term frequency for a document. So if rambo occurs in 3x in movie synopsis in same document, this is 3.
- :code:`tp` -- the term positions for a document. Because multiple positions can come back for a single term, review the behavior of :code:`pos_aggr` 
- :code:`ttf` -- the total term frequency for the term across the index. So if rambo is mentioned a total of 100 times in the overview field across all documents, this would be 100.

The :code:`aggr` parameter tells the query what type of aggregation you want over the collected statistics from the :code:`expr`.  For the example terms of :code:`rambo rocky` we will get stats for both terms.  Since we can only return one value you need to decide what statistical calculation you would like.

Supported aggregation types are:
- :code:`min` -- the minimum 
- :code:`max` -- the maximum
- :code:`avg` -- the mean
- :code:`stddev` -- the standard deviation 

The :code:`terms` parameter is array of terms to gather statistics for.  Currently only single terms are supported, there is not support for phrases or span queries. Note: If your field is tokenized you can pass multiple terms in one string in the array.

The :code:`fields` parameter specifies which fields to check for the specified :code:`terms`.  Note if no :code:`analyzer` is specified then we use the analyzer specified for the field.

Optional Parameters
-----------------

- :code:`analyzer` -- if specified this analyzer will be used instead of the configured :code:`search_analyzer` for each field
- :code:`pos_aggr` -- Since each term by itself can have multiple positions, you need to decide which aggregation to apply.  This supports the same values as :code:`aggr` and defaults to AVG

Script Injection
----------------

Finally, one last addition that this functionality provides is the ability to inject term statistics into a scripting context.  When working with :code:`ScriptFeatures` if you pass a :code:`term_stat` object in with the :code:`terms`, :code:`fields` and :code:`analyzer` parameters you can access the raw values directly in a custom script via an injected variable named :code:`terms`.  This provides for advanced feature engineering when you need to look at all the data to make decisions. 
