import os
from collect_features import log_features, build_features_judgments_file
from load_features import init_default_store, load_features
from log_conf import Logger
from utils import elastic_connection, ES_HOST, ES_AUTH, JUDGMENTS_FILE, INDEX_NAME, JUDGMENTS_FILE_FEATURES, \
    FEATURE_SET_NAME, RANKLIB_JAR


def train_model(judgments_with_features_file, model_output, which_model=6):
    # java -jar RankLib-2.6.jar -ranker 6 -train sample_judgments_wfeatures.txt -save model.txt
    cmd = "java -jar %s -ranker %s -train %s -save %s -frate 1.0" % \
          (RANKLIB_JAR, which_model, judgments_with_features_file, model_output)
    Logger.logger.info("*********************************************************************")
    Logger.logger.info("*********************************************************************")
    Logger.logger.info("Running %s" % cmd)
    os.system(cmd)
    pass


def save_model(script_name, feature_set, model_fname):
    """ Save the ranklib model in Elasticsearch """
    import requests
    import json
    from urllib.parse import urljoin

    model_payload = {
        "model": {
            "name": script_name,
            "model": {
                "type": "model/ranklib",
                "definition": {
                }
            }
        }
    }

    with open(model_fname) as modelFile:
        model_content = modelFile.read()
        path = "_ltr/_featureset/%s/_createmodel" % feature_set
        full_path = urljoin(ES_HOST, path)
        model_payload['model']['model']['definition'] = model_content
        Logger.logger.info("POST %s" % full_path)
        head = {'Content-Type': 'application/json'}
        resp = requests.post(full_path, data=json.dumps(model_payload), headers=head, auth=ES_AUTH)
        Logger.logger.info(resp.status_code)
        if resp.status_code >= 300:
            Logger.logger.error(resp.text)


if __name__ == "__main__":
    from judgments import judgments_from_file, judgments_by_qid

    es = elastic_connection(timeout=1000)
    # Load features into Elasticsearch
    init_default_store()
    load_features(FEATURE_SET_NAME)
    # Parse a judgments
    movieJudgments = judgments_by_qid(judgments_from_file(filename=JUDGMENTS_FILE))
    # Use proposed Elasticsearch queries (1.json.jinja ... N.json.jinja) to generate a training set
    # output as "sample_judgments_wfeatures.txt"
    log_features(es, judgments_dict=movieJudgments, search_index=INDEX_NAME)
    build_features_judgments_file(movieJudgments, filename=JUDGMENTS_FILE_FEATURES)
    # Train each ranklib model type
    for modelType in [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]:
        # 0, MART
        # 1, RankNet
        # 2, RankBoost
        # 3, AdaRank
        # 4, coord Ascent
        # 6, LambdaMART
        # 7, ListNET
        # 8, Random Forests
        # 9, Linear Regression
        Logger.logger.info("*** Training %s " % modelType)
        train_model(judgments_with_features_file=JUDGMENTS_FILE_FEATURES, model_output='model.txt',
                    which_model=modelType)
        save_model(script_name="test_%s" % modelType, feature_set=FEATURE_SET_NAME, model_fname='model.txt')
