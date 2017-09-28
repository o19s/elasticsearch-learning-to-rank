# Logging Features

To train a model, you need to log feature values. This is a major component of the learning to rank plugin: as users search, we log feature values from our feature sets so we can then train. Then we can discover models that work well to predict relevance with that set of features.

## Sltr Query

The `sltr` query is the primary way features are run and models are evaluated. When logging, we'll just use an `sltr` query for getting each value only. 

Outside, `sltr`, the logging response itself is controlled by a search extension. This extension must refer back to the `sltr` query by either the query's name, or it's index in the list of rescore queries.

For the sake of discussing logging, let's say we created a feature set like so that works with the TMDB data set from the demo.

```
PUT _ltr/_featureset/more_movie_features
{
   "name": "more_movie_features",
   "features": [
       {
           "name": "body_query",
           "params": [
              "keywords"
             ],
         "template": {
               "match": {
                  "overview": "{{keywords}}"
               }
         }            
       },
      {
         "name": "title_query",
         "params": [
            "keywords"
         ],
         "template": {
               "match": {
                  "title": "{{keywords}}"
               }
         }
      }
   ]
}
``` 

Let's see how to log this feature set in a couple common use cases.

## Logging 101 -- Logging basics with offline log capture

In many learning to rank cases, judgments are gathered separately than logging. This will let us look at basic logging without worrying about whether it's happening along side a live production search. But this is also a real use case: a classic example would be to gather a judgment list from domain experts. Many search applications, such as Enterprise Search do not have the amount of analytics needed to derive judgment lists from analytics. In these cases, we simply need to transform an expert created judgment list such as the one below into one annotated with feature values.

```
grade,keywords,docId
4,rambo,7555
3,rambo,1370
3,rambo,1369
```

In these cases, we want to get feature values for all documents that have judgment for search terms "rambo". To do this we create a filter for our ids:

```
"filter": [
    {
        "terms": {
            "_id": ["7555", "1370", "1369"]       
        }
    }
]
```

We'll use this as part of a larger boolean query. Combined with this filter is an `sltr` query that:

- Has a `_name` (the Elasticsearch named queries feature)
- Refers to the featureset we created above `more_movie_features`
- Passes our search keywords "rambo"

```
{"sltr": {
    "_name": "logged_featureset",
    "featureset": "more_movie_features",
    "params": {
        "keywords": "rambo"
    }
}}
```

You'll see future uses of `sltr` that apply a model. 

We want to use the `sltr` query above to log, but we don't want to influence the score. We need to sneak it into our query. The best way to do this is to make it a filter. As `sltr` doesn't actually exclude any search results, it can be used this way to get Elasticsearch to log our feature values.

```
  "query": {
        "bool": {
              "filter": [
                {
                    "terms": {
                        "_id": ["7555", "1370", "1369"]
                    
                    }
                },
                {
                    "sltr": {
                        "_name": "logged_featureset",
                        "featureset": "more_movie_features",
                        "params": {
                            "keywords": "rambo"
                        }
                }}
                
            ]
    }
  }
```

If you ran this, you'd notice 3 hits brought back. If you were to pry into the explain, you'd see the query is scored as a straight sum of the features in `more_movie_features`. We of course need more than just the total score, we need each feature query's value.

This is what the LTR logging extension gives you. As a top-level entry in the body sent to Elasticsearch, it refers to an Elasticsearch query and injects computed fields into each document.

```
    "ext": {
        "ltr_log": {
            "log_specs": {
                "name": "log_entry1",
                "named_query": "logged_featureset"
            }
        }
    }
```

This log extension comes with several arguments:
- `name`: The name of this log entry to fetch from each document 
- `named_query` the named query which corresponds to an `sltr` query
- `rescore_index`: if `sltr` is in a rescore phase, this is the index of the query
- `missing_as_zero`: produce a 0 for missing features (when the feature does not match) (defaults to `false\`)

Either `named_query` or `rescore_index` must be set so that logging can locate an `sltr` query for logging.

Finally the full request

```
POST tmdb/_search
{
    "query": {
        "bool": {
            "filter": [
                {
                    "terms": {
                        "_id": ["7555", "1370", "1369"]
                    
                    }
                },
                {
                    "sltr": {
                        "_name": "logged_featureset",
                        "featureset": "more_movie_features",
                        "params": {
                            "keywords": "rambo"
                        }
                }}
                
            ]
        }
    },
    "ext": {
        "ltr_log": {
            "log_specs": {
                "name": "log_entry1",
                "named_query": "logged_featureset"
            }
        }
    }
}
```

And now each document contains a log entry:

```
    {
        "_index": "tmdb",
        "_type": "movie",
        "_id": "1370",
        "_score": 20.291,
        "_source": {
            ...
        },
        "fields": {
            "_ltrlog": [
                {
                    "log_entry1": {
                        "title_query": 9.510193,
                        "body_query": 10.7808075
                    }
                }
            ]
        },
        "matched_queries": [
            "logged_featureset"
        ]
    }
```

We can use those values to flesh out our judgment list with feature values.

## Logging values for a live feature set

With the last section in mind, let's say you're running in production with a model being executed in an `sltr` query. Something like:

```
POST tmdb/_search
{
  "query": {
      "match": {
          "_all": "rambo"
       }
   },
  "rescore": {
      "query": {
        "rescore_query": {
            "sltr": {
                "params": {
                    "keywords": "rambo"
                },
                "model": "my_model"
            }
         }
      }
   }
}
```

In this query, your main "query" section (the match all query) is what we refer to as a "baseline query" -- a "good enough" query to promote reasonably relevant results to the top.

Simply applying the correct logging spec to refer to the `sltr` query does the trick to let us log feature values for our query.

```
    "ext": {
        "ltr_log": {
            "log_specs": {
                "name": "log_entry1",
                "rescore_index": 0
            }
        }
    }
```

This will log features to the Elasticsearch response, giving you an ability to retrain a model with the same featureset later.

## Modifying an existing feature set and logging

Feature sets can be appended to. As mentioned in the last chapter, if you want to incorporate a new feature, such as `user_rating`, we can append that query to our featureset `more_movie_features`:

```
PUT _ltr/_feature/user_rating
{
  "name": "user_rating",
  "params": [],
  "template_language": "mustache",
  "template" : {
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
}
```

```
POST /_ltr/_featureset/more_movie_features/_addfeatures/user_rating
```

We don't have to worry about feature names being overwritten or changing, as features copied into a feature set cannot be changed and have unique names. Then finally, when we log as the examples above, we'll have our new feature in our output: 

```
"log_entry1": {
            "title_query": 9.510193,
            "body_query": 10.7808075,
            "user_rating": 7.8
        }
```

## Logging values for a proposed feature set

You might create a completely new feature set for experimental purposes. For example, let's say you create a brand new feature set, `other_movie_features`:

```
PUT _ltr/_featureset/other_movie_features
{
   "name": "other_movie_features",
   "features": [
       {
           "name": "cast_query",
           "params": [
              "keywords"
             ],
              "template": {
               "match": {
                  "cast.name": "{{keywords}}"
               }
           }            
       },
      {
         "name": "genre_query",
         "params": [
            "keywords"
         ],
         "template": {
               "match": {
                  "genres.name": "{{keywords}}"
               }
         }
      }
   ]
}
```

We can log `other_movie_features` alongside a live production `more_movie_features` by simply appending it as another filter, just like the first example above

```
POST tmdb/_search
{
  "query": {
      "bool": {
          "filter": [
              {"sltr": {
                    "_name": "logged_featureset",
                    "featureset": "other_movie_features",
                    "params": {
                        "keywords": "rambo"
                    }
                }}
          ],
          "must": [
            {"match": {
                "_all": "rambo"
            }}
          ]
      }
   },
  "rescore": {
      "query": {
        "rescore_query": {
            "sltr": {
                "params": {
                    "keywords": "rambo"
                },
                "model": "my_model"
            }
         }
      }
   }
}
```

Continue with as many feature sets as you care to log!