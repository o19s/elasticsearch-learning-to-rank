# REST API

`TODO`: better doc.
## Features Stores

The plugin will store its components in an elasticsearch index:

Initialize the default store:
```
PUT /_ltr
```

Initialize a custom store:
```
PUT /_ltr/custom
```

Stores can be deleted:

```
DELETE /_ltr
```

or a custom store:
```
DELETE /_ltr/custom
```

### Internals

A feature store is a normal elasticsearch index, the default store is named `.ltrstore`
and a store named `my_store` will be named `.ltrstore_my_store`.

While all normal operations are allowed on these indices we strongly suggest not to interact directly with these indices
and always use the `_ltr` API endpoints to update their contents.

## Features

`/_ltr/_feature/feature_name` supports `PUT`, `POST`, `GET` and `DELETE` operations.

The format of a feature is :
```json
{
  "name": "my_feature",
  "params": ["query_string"],
  "template_language": "mustache",
  "template" : {
    "match": {
      "field": "{{query_string}}"
    }
  }
}
```

To use a custom store simply prefix with the name of the store:
`/_ltr/custom_store/_feature/feature_name`

## Feature Sets

`/_ltr/_featureset/featureset_name` supports `PUT`, `POST`, `GET` and `DELETE` operations.

The format of a feature set is :
```json
{
  "name": "my_feature_set",
  "features" : [
    {
      "name": "my_feature",
      "params": ["query_string"],
      "template_language": "mustache",
      "template" : {
        "match": {
          "field": "{{query_string}}"
        }
      }
    }
  ]
}
```

Feature names in the set must be unique.

To use a custom store simply prefix with the name of the store:
`/_ltr/custom_store/_feature/feature_name`

### Append features to a set
A set can also be created/updated from existing features:

`POST /_ltr/_featureset/my_featureset/_addfeatures/my_feature*`

Will create or update the `my_featureset` by collecting all features named with the prefix `my_feature`.

### Models

`/_ltr/_model/model_name` supports `PUT`, `GET` and `DELETE` operations.

The format of a model is:
```json
{
  "name" : "my_model",
  "feature_set": { 
    "name": "my_feature_set",
    "features" : [
      {
        "name": "my_feature",
        "params": ["query_string"],
        "template_language": "mustache",
        "template" : {
          "match": {
            "field": "{{query_string}}"
          }
        }
      }
    ]
  },
  "model": {
    "type": "model/ranklib",
    "definition" : "definition of the model in the model/ranklib format"
  }
}
```

To use a custom store simply prefix with the name of the store:
`/_ltr/custom_store/_model/model_name`

### Create a model from an existing set

A model can be created by using an existing feature set:

`POST /_ltr/_featureset/my_featureset/_createmodel`
```json
{
  "name": "my_model_name",
  "model": {
    "type": "model/ranklib",
    "definition": "definition of the model in the model/ranklib format"
  }
}
```

A `version` query param can be added to ensure that the set used to create the model is the version expected:
`POST /_ltr/_featureset/my_featureset/_createmodel?version=23`

# Caches
The plugin uses an internal cache not to compile the models on every request.

Clear the cache for a feature store:
```
POST /_ltr/_clearcache
POST /_ltr/custom_store/_clearcache
```

Get cluster wide cache statistics:
```
GET /_ltr/_cachestats
```

Will display cache usage of the plugin for the cluster. The details on a per node and per store basis is shown.
```json
{
  "_nodes": {
    "total": 1,
    "successful": 1,
    "failed": 0
  },
  "cluster_name": "nomoa",
  "all": {
    "total": {
      "ram": 634,
      "count": 1
    },
    "features": {
      "ram": 0,
      "count": 0
    },
    "featuresets": {
      "ram": 0,
      "count": 0
    },
    "models": {
      "ram": 634,
      "count": 1
    }
  },
  "stores": {
    ".ltrstore": {
      "total": {
        "ram": 634,
        "count": 1
      },
      "features": {
        "ram": 0,
        "count": 0
      },
      "featuresets": {
        "ram": 0,
        "count": 0
      },
      "models": {
        "ram": 634,
        "count": 1
      }
    }
  },
  "nodes": {
    "at7Isa5OSSSPOnhpqUdL6w": {
      "name": "at7Isa5",
      "hostname": "192.168.0.21",
      "stats": {
        "total": {
          "ram": 634,
          "count": 1
        },
        "features": {
          "ram": 0,
          "count": 0
        },
        "featuresets": {
          "ram": 0,
          "count": 0
        },
        "models": {
          "ram": 634,
          "count": 1
        }
      }
    }
  }
}

```