import json

logQuery = {
    "size": 100,
    "query": {
        "bool": {
            "must": [
                {
                    "terms": {
                        "_id": ["7555"]

                    }
                }
            ],
            "should": [
                {"sltr": {
                    "_name": "logged_featureset",
                    "featureset": "movie_features",
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
                "name": "main",
                "named_query": "logged_featureset",
                "missing_as_zero": True

            }
        }
    }
}

def featureDictToList(ranklibLabeledFeatures):
    rVal = [0.0] * len(ranklibLabeledFeatures)
    for idx, logEntry in enumerate(ranklibLabeledFeatures):
        value = logEntry['value']
        try:
            rVal[idx] = value
        except IndexError:
            print("Out of range %s" % idx)
    return rVal



def logFeatures(es, judgmentsByQid):
    for qid, judgments in judgmentsByQid.items():
        keywords = judgments[0].keywords
        docIds = [judgment.docId for judgment in judgments]
        logQuery['query']['bool']['must'][0]['terms']['_id'] = docIds
        logQuery['query']['bool']['should'][0]['sltr']['params']['keywords'] = keywords
        print("POST")
        print(json.dumps(logQuery, indent=2))
        res = es.search(index='tmdb', body=logQuery)
        # Add feature back to each judgment
        featuresPerDoc = {}
        for doc in res['hits']['hits']:
            docId = doc['_id']
            features = doc['fields']['_ltrlog'][0]['main']
            featuresPerDoc[docId] = featureDictToList(features)

        # Append features from ES back to ranklib judgment list
        for judgment in judgments:
            try:
                features = featuresPerDoc[judgment.docId] # If KeyError, then we have a judgment but no movie in index
                judgment.features = features
            except KeyError:
                print("Missing movie %s" % judgment.docId)


def buildFeaturesJudgmentsFile(judgmentsWithFeatures, filename):
    with open(filename, 'w') as judgmentFile:
        for qid, judgmentList in judgmentsWithFeatures.items():
            for judgment in judgmentList:
                judgmentFile.write(judgment.toRanklibFormat() + "\n")


if __name__ == "__main__":
    from judgments import judgmentsFromFile, judgmentsByQid
    from elasticsearch import Elasticsearch
    es = Elasticsearch()
    judgmentsByQid = judgmentsByQid(judgmentsFromFile('sample_judgments.txt'))
    logFeatures(es, judgmentsByQid)
    buildFeaturesJudgmentsFile(judgmentsByQid, "sample_judgments_wfeatures.txt")

