baseQuery = {
  "query": {
      "match": {
          "_all": "test"
       }
   },
  "rescore": {
      "query": {
        "rescore_query": {
            "sltr": {
                "params": {
                    "keywords": ""
                },
                "model": "",
            }
         }
      }
   }
}

def ltrQuery(keywords, modelName):
    import json
    baseQuery['rescore']['query']['rescore_query']['sltr']['model'] = modelName
    baseQuery['query']['match']['_all'] = keywords
    baseQuery['rescore']['query']['rescore_query']['sltr']['params']['keywords'] = keywords
    print("%s" % json.dumps(baseQuery))
    return baseQuery

def search(es, model, keyword):
    results = es.search(index='tmdb', doc_type='movie', body=ltrQuery(keyword, model))
    for result in results['hits']['hits']:
             print(result['_source']['title'])


if __name__ == "__main__":
    import configparser
    from sys import argv
    from elasticsearch import Elasticsearch

    config = configparser.ConfigParser()
    config.read('settings.cfg')
    esUrl=config['DEFAULT']['ESHost']

    es = Elasticsearch(esUrl, timeout=1000)
    model = "test_6"
    if len(argv) > 2:
        model = argv[2]
    search(es, model, keyword=argv[1])

