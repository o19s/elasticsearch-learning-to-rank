import json
from judgments import judgments_from_file, judgments_by_qid
from log_conf import Logger
from utils import elastic_connection, JUDGMENTS_FILE, JUDGMENTS_FILE_FEATURES, FEATURE_SET_NAME, INDEX_NAME

logQuery = {
    "size": 100,
    "query": {
        "bool": {
            "filter": [
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


def feature_dict_to_list(ranklib_labeled_features):
    r_val = [0.0] * len(ranklib_labeled_features)
    for idx, logEntry in enumerate(ranklib_labeled_features):
        value = logEntry['value']
        try:
            r_val[idx] = value
        except IndexError:
            Logger.logger.info("Out of range %s" % idx)
    return r_val


def log_features(es, judgments_dict, search_index):
    for qid, judgments in judgments_dict.items():
        keywords = judgments[0].keywords
        doc_ids = [judgment.docId for judgment in judgments]
        logQuery['query']['bool']['filter'][0]['terms']['_id'] = doc_ids
        logQuery['query']['bool']['should'][0]['sltr']['params']['keywords'] = keywords
        logQuery['query']['bool']['should'][0]['sltr']['featureset'] = FEATURE_SET_NAME
        Logger.logger.info("POST")
        Logger.logger.info(json.dumps(logQuery, indent=2))
        res = es.search(index=search_index, body=logQuery)
        # Add feature back to each judgment
        features_per_doc = {}
        for doc in res['hits']['hits']:
            docId = doc['_id']
            features = doc['fields']['_ltrlog'][0]['main']
            features_per_doc[docId] = feature_dict_to_list(features)

        # Append features from ES back to ranklib judgment list
        for judgment in judgments:
            try:
                features = features_per_doc[
                    judgment.docId]  # If KeyError, then we have a judgment but no movie in index
                judgment.features = features
            except KeyError:
                Logger.logger.info("Missing movie %s" % judgment.docId)


def build_features_judgments_file(judgments_with_features, filename):
    with open(filename, 'w') as judgmentFile:
        for qid, judgmentList in judgments_with_features.items():
            for judgment in judgmentList:
                judgmentFile.write(judgment.to_ranklib_format() + "\n")


if __name__ == "__main__":
    es_connection = elastic_connection()
    judgmentsByQid = judgments_by_qid(judgments_from_file(JUDGMENTS_FILE))
    log_features(es_connection, judgmentsByQid, INDEX_NAME)
    build_features_judgments_file(judgmentsByQid, JUDGMENTS_FILE_FEATURES)
