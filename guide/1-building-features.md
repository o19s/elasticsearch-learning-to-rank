# Building Features in Elasticsearch Learning to Rank

Feature development is one of the core activities of learning to rank work. This section covers the functionality built into the Elasticsearch Learning to Rank plugin to build & upload features with the plugin.

## What is a feature in Elasticsearch Learning to Rank?

Elasticsearch LTR Features correspond to Elasticsearch queries. Elasticsearch queries contain the expressive needs for most feature development. The plugin has added query types that enhance Elasticsearch further for other types of features.

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

Similar to how you would develop queries like these to manually improve search relevance, your ranking function `f` also combines these queries mathematically to arrive at a relevance score. 

## Features are Mustache Templates

You'll notice the `{{keywords}}` in the match query above. This syntax is the mustache templating system used in other parts of Elasticsearch. This lets you inject various query or user-specific variables into the search template. Perhaps information about the user for personalization? Or the location of the searcher's phone? 

For now, we'll simply focus on typical keyword searches.

## Uploading and Naming Features

Elasticsearch Learning to Rank creates a CRUD interface for features (Elasticsearch queries), creating models that use those features, and exposing query primitives for logging and searching with those features.

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

### Feature Sets 

A *feature set* is a set of features. Feature sets tend to correspond to an iteration of solving a specific search problem. Feature sets


### Features