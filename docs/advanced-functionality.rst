Advanced Functionality
***********************

Most people can safely operate within the confines of the guide, this section documents some additional functionality you may find useful after you're comfortable with the primary capabilities.

=============================
Reusable Features
=============================

In :doc:`building-features` we demonstrated creating feature sets by uploading a list of features. Instead of repeating common features in every feature set, you may want to keep a library of features around.

For example, perhaps a query on the title field is important to many of your feature sets, you can use the feature API to create a title query::

    POST _ltr/_feature/titleSearch
    {
        "feature":
        {
            "name": "titleSearch",
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

=============================
Multiple Feature Stores
=============================

We defined a feature store in :doc:`building-features`. A feature store corresponds to an independent LTR system: features, feature sets, models backed by a single index and cache. A feature store corresponds roughly to a single search problem, often tied to a single application. For example wikipedia might be backed by one feature store, but wiktionary would be backed by another. There's nothing that would be shared between the two.

Should your Elasticsearch cluster back multiple properties, you can use all the capabilities of this guide on named feature stores, simply by::

    PUT _ltr/wikipedia

Then the same API in this guide applies to this feature store, for example to create a feature set::

    POST _ltr/wikipedia/_featureset
    {
       "featureset": {
            "name": "attempt_1",
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

    DELETE _ltr/wikipedia/_featureset