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
