Rescoring using the LTR rescorer
********************************

=====
Usage
=====

The :code:`ltr_rescore` is a custom rescorer very similar to the :code:`query_rescorer` provided by elasticsearch.
Its main advantage is to provide a `replace` mode but also enables bulk scoring capabilities. ::

    {
        "window_size": 100,
        "ltr_rescore": {
            "query_weight": 1.0,
            "rescore_query_weight": 1.0,
            "query_normalizer": "noop",
            "rescore_query_normalizer": "noop",
            "score_mode": "replace",
            "scoring_batch_size": -1,
            "ltr_query": {
                "sltr" : {
                    "model": "my_model",
                    "params": {
                        "query_string" : "query test"
                    }
                }
            }
        }
    }


query_weight
    weight applied to the query score after normalization. Defaults to 1.
rescore_query_weight
    weight applied to the rescore query score after normalization. Defaults to 1.
query_normalizer
    type of normalization applied to the query score. Default to :code:`noop`.
rescore_query_normalizer
    type of normalization applied to the rescore query score. Default to :code:`noop`.
score_mode
    method to combine the query score and rescore query score. Possible values: :code:`avg`, :code:`multiply`, :code:`max`, :code:`min`, :code:`total` and :code:`replace`. Defaults to :code:`total`.
scoring_batch_size
    size of the batch when bulk scoring is supported by the ranker. Set to 1 to disable bulk scoring, -1 to use ranker preferred defaults. Defaults to -1.
ltr_query:
    the query to use for rescoring, bulk scoring is only supported with :code:`sltr` and :code:`ltr` queries.

===========
Normalizers
===========

minmax
------

:math:`\frac{ score - min }{ max - min }`

Usage ::

    {
        "minmax": { "min": 0, "max": "12" }
    }


Normalizes the input score in the unit interval [0,1] assuming all input values are in the [0,12] interval.

**NOTE**: input values outside the interval are set to the closest bound.

saturation
----------

:math:`\frac{ score^a }{ score^a + k^a }`

Usage ::

    {
        "saturation": { "k": 0.5, "a": "1" }
    }


Normalizes any positive values in the unit interval [0,1].

**NOTE**: Negative input values are set to 0.

logistic
--------

:math:`\frac{ 1 }{ 1 + e^{-k(score-x_0)} }`

Usage ::

    {
        "logistic": { "k": 0.5, "x0": "1" }
    }


Normalizes any input value in the unit interval [0,1].

interval
--------

Usage ::

    {
        "interval" : {
            "from": 1,
            "to": 2,
            "inclusive": false,
            "normalizer": {
                "minmax": { "min": 0, "max": 100 }
            }
        }
    }

Normalizes any input value in [from, to] interval using the given normalizer to first normalize within the unit interval.

from
    lower bound
to
    upper bound
inclusive
    whether or not the upper bound is a possible output value. Defaults to false.
normalizer:
    input normalizer to use.

noop
----

Usage ::

    {
        "noop" : {}
    }


Does nothing and return the input score.

============
Replace mode
============

A classic way to use this rescorer for replacing the main query score using you LTR model is to use the replace mode.

Example ::

    {
        "query": { "match" : { "field" : "user query" } } }
        "rescore" : [
            {
                "window_size": 100,
                "ltr_rescore": {
                    "query_normalizer" : {
                        "interval" : {
                            "from": 0,
                            "to" : 1,
                            "normalizer": { "saturation": { "k": 1, "a": 1 } }
                        }
                    },
                    "rescore_query_normalizer" : {
                        "interval" : {
                            "from": 1,
                            "to" : 2,
                            "normalizer": { "minmax": { "min": 0, "max": 1 } }
                        }
                    },
                    "score_mode" : "replace",
                    "ltr_query" : {
                        "sltr": {
                            "model": "mymodel/v1"
                            "params": {
                                "query_string": "user query"
                            }
                        }
                }
            }
        ]
    }

This will rescore the top-100 documents per shard returned by the retrieval query
:code:`{ "match" : { "field" : "user query" } }`.

The top-100 docs of every shards will have their scores normalized in the [1,2] interval, remaining documents (which are
not rescored using your LTR model) will see their scores normalized in the [0,1] interval ensuring that that documents
outside the top-100 are not ranked above rescored documents.

If had to *hack* your classic query rescorer using insane boost values or simply you had to forbid your application to paginate
over the :code:`window_size` result this `replace` mode is for you.