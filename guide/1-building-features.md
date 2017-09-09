
# Building Features in Elasticsearch Learning to Rank

Feature development is one of the core activities of learning to rank work. This section covers the functionality built into the Elasticsearch Learning to Rank plugin to build & upload features with the plugin.

## What is a feature in Elasticsearch Learning to Rank?

Elasticsearch LTR features correspond to Elasticsearch queries. Elasticsearch queries contain the expressive needs for most feature development. 

Obvious features might include traditional search queries, like a simple "match" query on title:

```json
{
    "query": {
        "match": {
            "title": "{{keywords}}"
        }
    }
}
```

Of course, properties of documents such as popularity can also be a feature. Function score queries can help access these values. For example, to access the average user rating of a movie:

```json
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
```

One could also imagine a query based on the user's location:

```json
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
```

Similar to how you would develop queries like these to manually improve search relevance, the ranking function `f` you're training also combines these queries mathematically to arrive at a relevance score. 

## Features are Mustache Templated Elasticsearch Queries

You'll notice the `{{keywords}}`, `{{users_lat}}`, and `{{users_lon}}` above. This syntax is the mustache templating system used in other parts of Elasticsearch. This lets you inject various query or user-specific variables into the search template. Perhaps information about the user for personalization? Or the location of the searcher's phone? 

For now, we'll simply focus on typical keyword searches.

## Uploading and Naming Features

Elasticsearch LTR gives you an interface for creating and manipulating features.

### Initialize the Default Feature Store

A *feature store* corresponds to an Elasticsearch index used to store metadata about the features and models. Typically, one feature store corresponds to a major search site/implementation. For example, [wikipedia](http://wikipedia.org) vs [wikitravel](http://wikitravel.org).

For most use cases, you can simply get by with the single, default feature store and never think about feature stores ever again. This needs to be initialized the first time you use Elasticsearch Learning to Rank:

```
PUT _ltr
```

You can restart from scratch by deleting the default feature store:

```
DELETE _ltr
```
(WARNING this will blow everything away, use with caution!)

In the examples below, we'll work with the default feature store.

### Features and Feature Sets

Feature sets are where all the action is in Elasticsearch LTR. 

A *feature set* is a set of features that has been grouped together for logging & model evaluation. You'll refer to feature sets when you want to log multiple feature values for offline training. You'll also create a model from a feature set, copying the feature set into model.


#### Create a feature Sets 

You can create a feature set simply by using a PUT. To create it, you give a feature set a name and optionally a list of features.

```
PUT _ltr/_featureset
{
   "name": "more_movie_features",
   "features": [
      {
         "name": "title_query",
         "params": [
            "keywords"
         ],
         "template_language": "mustache",
         "template": {
            "query": {
               "match": {
                  "title": "{{keywords}}"
               }
            }
         }
      }
   ]
}
```

Fetching a feature set works as you'd expect:

```
GET _ltr/_featureset/more_movie_features
```

You can list all your feature sets:

```
GET _ltr/_featureset
```

Or filter by prefix in case you have many feature sets:

```
GET _ltr/_featureset?prefix=mor
```

### Validating features

When adding features, wouldn't you like to sanity check that the features work as expected? Adding a "validation" block to your feature creation let's Elasticsearch LTR run the query before adding it. If you don't run this validation, you may find out only much later that the query, while valid JSON, was a malformed Elasticsearch query.

To run validation, you simply specify test parameters and a test index to run: 

```
     "validation": {
               "params": {
                 "keywords": "rambo"
               },
               "index": "tmdb"
           },
```

Place this alongside the feature list, as in this request:

```
"validation": {
        "params": {
            "keywords": "rambo"
        },
        "index": "tmdb"
},
 "features": [
      {
         "name": "title_query",
         "params": [
            "keywords"
         ],
         "template_language": "mustache",
         "template": {
            "query": {
               "match": {
                  "title": "{{keywords}}"
               }
            }
         }
      }
   ]
```

### Adding to an existing feature set

As mentioned above, features are mustache-templated Elasticsearch queries. You can create features independently of feature sets. For example, if we want to create the `user_rating` feature, we could create it using the feature set append API, like below:


```
POST /_ltr/_featureset/my_featureset/_addfeatures
{
  "features": [
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
  ]
}
```

### Feature Sets are Lists

You'll notice we *appended* to the feature set. Feature sets perhaps ought to be really called "lists." Each feature has an ordinal (it's place in the list) in addition to a name. Some LTR training applications, such as Ranklib, refer to a feature by ordinal (the "1st" feature, the "2nd" feature). Others more conveniently refer to the name. So you may need both/either. You'll see that when features are logged, they give you a list of features back to preserve the ordinal.