# Feature Logging

To extract feature scores of a feature set or a model the `ltr_log` search extension.
The `ltr_log` extension is able to capture feature values of a `sltr` query use inside the main search query when
`named` or used inside a `rescore_query`.

Example to output the feature scores for the feature set `my_feature_set` and for the model `my_model`:

```json
{
  "query" : {
    "bool" : {
      "must" : {
        "sltr" : {
          "params" : {
            "query_string" : "a search query"
          },
          "featureset": "my_feature_set",
          "_name": "logged_featureset"
        },
        "filter": {
          "ids" : {
            "type" : "my_type",
            "values" : ["1", "4"]
          }
        }
      }
    }
  },
  "rescore": [
    {
      "window_size": 3,
      "query": {
        "rescore_query": {
          "sltr": {
            "params" : {
              "query_string" : "a search query"
            },
            "model": "my_model"
          }
        }
      }
    }
  ],
  "size": 3,
  "ext": {
    "ltr_log" : {
      "log_specs": [
        {
          "name": "log_entry1",
          "named_query": "logged_featureset",
          "missing_as_zero": true
        },
        {
          "name": "log_entry2",
          "rescore_index": 0,
          "missing_as_zero": false
        }
      ]
    }
  }
}
```

The log entries are appendend to a search hit field named `_ltrlog` with a sub field per log entry.

```json
{
  "took": 1,
  "timed_out": false,
  "_shards":{
    "total" : 2,
    "successful" : 3,
    "failed" : 0
  },
  "hits":{
    "total" : 3,
    "max_score": 1.3862944,
    "hits" : [
      {
        "_index" : "my_index",
        "_type" : "my_type",
        "_id" : "1",
        "_score": 1.3862944,
        "fields" : {
          "_ltrlog": {
            "log_entry1": {
              "feature1": 1.232,
              "feature2": 0,
              "feature3": 2.324,
              "feature4": 0.3234
            },
            "log_entry2": {
              "feature1": 1.232,
              "feature3": 2.324,
              "feature4": 0.3234
            }
          }
        }
      },
      {
        "_index" : "my_index",
        "_type" : "my_type",
        "_id" : "4",
        "_score": 1.2324,
        "fields" : {
          "_ltrlog": {
            "log_entry1": {
              "feature1": 1.112,
              "feature2": 3.234,
              "feature3": 0,
              "feature4": 0
            },
            "log_entry2": {
              "feature1": 1.112,
              "feature2": 3.234
            }
          }
        }
      }
    ]
  }
}

```

A `log_spec` is defined with the following fields:
- `name`: the name of the entry in the `_ltrlog` field (defaults to the name of the `named_query` or `rescore[index]`).
- `named_query`: the name of the `sltr` to capture
- `rescore_index`: the index of the query to capture in the list of rescore queries
- `missing_as_zero`: produce a 0 for missing features (when the feature does not match) (defaults to `false\`)

Either `named_query` or `rescore_index` must be set.

Note that feature collection happens during the fetch phase. The `sltr` feature queries are executed again on the list
of returned documents. When logging features on top of an existing `model` its ranker is disabled for performance
reasons.
