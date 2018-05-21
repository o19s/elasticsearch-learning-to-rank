This folder contains some helper files for interacting with the REST API to manage features and models.  The following curl commands will setup a simple feature set and model and demonstrate usage of the LTR plugin.

## Initialize the feature store
```
curl -XPUT http://localhost:9200/_ltr
```

## Add features to the store
```
curl -XPUT http://localhost:9200/_ltr/_feature/tmdb_title -d @1.json \
--header "Content-Type: application/json"
```

```
curl -XPUT http://localhost:9200/_ltr/_feature/tmdb_multi -d @2.json \
--header "Content-Type: application/json"
```

## Create feature set using existing features
```
curl -XPOST http://localhost:9200/_ltr/_featureset/example/_addfeatures/tmdb_*
```

## Create model using feature set
```
curl -XPOST http://localhost:9200/_ltr/_featureset/example/_createmodel -d @model.json \
--header "Content-Type: application/json"
```


Note: The following steps rely on the tmdb index being created from scripts in the above demo folder.

## Run a query
```
curl -XPOST http://localhost:9200/tmdb/_search?pretty -d @query.json \
--header "Content-Type: application/json"
```

## Compare against using no rescore
```
curl -XPOST http://localhost:9200/tmdb/_search?pretty -d @vanilla-query.json \
--header "Content-Type: application/json"
```


