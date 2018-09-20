Working with Features
***********************

In :doc:`core-concepts` , we mentioned the main roles you undertake building a learning to rank system. In :doc:`fits-in` we discussed at a high level what this plugin does to help you use Elasticsearch as a learning to rank system.

This section covers the functionality built into the Elasticsearch LTR plugin to build & upload features with the plugin.

====================================================
What is a feature in Elasticsearch LTR?
====================================================

Elasticsearch LTR features correspond to Elasticsearch queries. The score of an Elasticsearch query, when run using the user's search terms (and other parameters), are the values you use in your training set. 

Obvious features might include traditional search queries, like a simple "match" query on title::

    {
        "query": {
            "match": {
                "title": "{{keywords}}"
            }
        }
    }

Of course, properties of documents such as popularity can also be a feature. Function score queries can help access these values. For example, to access the average user rating of a movie::

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

One could also imagine a query based on the user's location::

    {
        "query": {
            "bool" : {
                "must" : {
                    "match_all" : {}
                },
                "filter" : {
                    "geo_distance" : {
                        "distance" : "200km",
                        "pin.location" : {
                            "lat" : {{users_lat}},
                            "lon" : {{users_lon}}
                        }
                    }
                }
            }
        }
    }

Similar to how you would develop queries like these to manually improve search relevance, the ranking function :code:`f` you're training also combines these queries mathematically to arrive at a relevance score. 

=====================================================
Features are Mustache Templated Elasticsearch Queries
=====================================================

You'll notice the :code:`{{keywords}}`, :code:`{{users_lat}}`, and :code:`{{users_lon}}` above. This syntax is the mustache templating system used in other parts of `Elasticsearch <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-template.html>`_. This lets you inject various query or user-specific variables into the search template. Perhaps information about the user for personalization? Or the location of the searcher's phone?

For now, we'll simply focus on typical keyword searches.

.. _derived-features:

=================
Derived Features
=================

Features that build on top of other features are called derived features.  These can be expressed as `lucene expressions <http://lucene.apache.org/core/7_1_0/expressions/index.html?org/apache/lucene/expressions/js/package-summary.html>`_. They are recognized by :code:`"template_language": "derived_expression"`. Besides these can also take in query time variables of type `Number <https://docs.oracle.com/javase/8/docs/api/java/lang/Number.html>`_ as explained in :ref:`create-feature-set`.

=================
Script Features
=================
These are essentially :ref:`derived-features`, having access to the :code:`feature_vector` but could be native or painless elasticsearch scripts rather than `lucene expressions <http://lucene.apache.org/core/7_1_0/expressions/index.html?org/apache/lucene/expressions/js/package-summary.html>`_. :code:`"template_language": "script_feature""` allows LTR to identify the templated script as a regular elasticsearch script e.g. native, painless, etc.

The custom script has access to the feature_vector via the java `Map <https://docs.oracle.com/javase/8/docs/api/java/util/Map.html>`_ interface as explained in :ref:`create-feature-set`.

============================
Script Features Parameters
============================
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
Uploading and Naming Features
=============================

Elasticsearch LTR gives you an interface for creating and manipulating features. Once created, then you can have access to a set of feature for logging. Logged features when combined with your judgment list, can be trained into a model. Finally, that model can then be uploaded to Elasticsearch LTR and executed as a search.

Let's look how to work with sets of features.

====================================
Initialize the default feature store
====================================

A *feature store* corresponds to an Elasticsearch index used to store metadata about the features and models. Typically, one feature store corresponds to a major search site/implementation. For example, `wikipedia <http://wikipedia.org>`_ vs `wikitravel <http://wikitravel.org>`_

For most use cases, you can simply get by with the single, default feature store and never think about feature stores ever again. This needs to be initialized the first time you use Elasticsearch Learning to Rank::

    PUT _ltr


You can restart from scratch by deleting the default feature store::

    DELETE _ltr

(WARNING this will blow everything away, use with caution!)

In the rest of this guide, we'll work with the default feature store.

=========================
Features and feature sets
=========================

Feature sets are where the action really happens in Elasticsearch LTR. 

A *feature set* is a set of features that has been grouped together for logging & model evaluation. You'll refer to feature sets when you want to log multiple feature values for offline training. You'll also create a model from a feature set, copying the feature set into model.

.. _create-feature-set:

====================
Create a feature set 
====================

You can create a feature set simply by using a POST. To create it, you give a feature set a name and optionally a list of features::


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
                    "name": "title_query_boost",
                    "params": [
                        "some_multiplier"
                    ],
                    "template_language": "derived_expressions",
                    "template": "title_query * some_multiplier"
                },
                {
                    "name": "custom_title_query_boost",
                    "params": [
                        "some_multiplier"
                    ],
                    "template_language": "script_feature",
                    "template": {
                        "lang": "painless",
                        "source": "params.feature_vector.get('title_query') * (long)params.some_multiplier",
                        "params": {
                            "some_multiplier": "some_multiplier"
                        }
                    }
                }
            ]
       }
    }

=================
Feature set CRUD
=================

Fetching a feature set works as you'd expect::

    GET _ltr/_featureset/more_movie_features

You can list all your feature sets::

    GET _ltr/_featureset

Or filter by prefix in case you have many feature sets::

    GET _ltr/_featureset?prefix=mor

You can also delete a featureset to start over::

    DELETE _ltr/_featurset/more_movie_features


===================
Validating features
===================

When adding features, we recommend sanity checking that the features work as expected. Adding a "validation" block to your feature creation let's Elasticsearch LTR run the query before adding it. If you don't run this validation, you may find out only much later that the query, while valid JSON, was a malformed Elasticsearch query. You can imagine, batching dozens of features to log, only to have one of them fail in production can be quite annoying!

To run validation, you simply specify test parameters and a test index to run:: 

     "validation": {
        "params": {
            "keywords": "rambo"
        },
        "index": "tmdb"
     },

Place this alongside the feature set. You'll see below we have a malformed :code:`match` query. The example below should return an error that validation failed. An indicator you should take a closer look at the query::

    {
       "validation": {
         "params": {
             "keywords": "rambo"
         },
         "index": "tmdb"
        },
        "featureset": {
            "features": [
                {
                    "name": "title_query",
                    "params": [
                        "keywords"
                    ],
                    "template_language": "mustache",
                    "template": {
                        "mooch": {
                            "title": "{{keywords}}"
                        }
                    }
                }
            ]
        }
    }

=================================
Adding to an existing feature set
=================================

Of course you may not know upfront what features could be useful. You may wish to append a new feature later for logging and model evaluation. For example, creating the `user_rating` feature, we could create it using the feature set append API, like below::


    POST /_ltr/_featureset/my_featureset/_addfeatures
    {
        "features": [{
            "name": "user_rating",
            "params": [],
            "template_language": "mustache",
            "template" : {
                "function_score": {
                    "functions": {
                        "field": "vote_average"
                    },
                    "query": {
                        "match_all": {}
                    }
                }
            }
        }]
    }


========================
Feature Names are Unique
========================

Because some model training libraries refer to features by name, Elasticsearch LTR enforces unique names for each features. In the example above, we could not add a new `user_rating` feature without creating an error. 

==========================
Feature Sets are Lists
==========================

You'll notice we *appended* to the feature set. Feature sets perhaps ought to be really called "lists." Each feature has an ordinal (its place in the list) in addition to a name. Some LTR training applications, such as Ranklib, refer to a feature by ordinal (the "1st" feature, the "2nd" feature). Others more conveniently refer to the name. So you may need both/either. You'll see that when features are logged, they give you a list of features back to preserve the ordinal.


Next-up, we'll talk about some unique features the Elasticsearch LTR plugin allows with a few extra custom queries in :doc:`feature-engineering`.
