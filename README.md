[![CircleCI](https://circleci.com/gh/o19s/elasticsearch-learning-to-rank.svg?style=svg)](https://circleci.com/gh/o19s/elasticsearch-learning-to-rank)

With this plugin you:

- Store features (Elasticsearch query templates) in Elasticsearch 
- Implement query primitives that go beyond the Elasticsearch Query DSL
- Log features scores (relevance scores) for a set of documents to prime training
- Upload an xgboost or ranklib ranking model to Elasticsearch
- Rank search results using an xgboost or ranklib model

[Read the docs](http://elasticsearch-learning-to-rank.readthedocs.io) to learn more about this plugin and learning to rank!


# How the plugin works

You create *feature stores* to back your model. Within the feature store, features are grouped into feature sets. What are features in this context? Features are mustache templated Elasticsearch queries. For example a template that takes the search terms as an argument. Several new Elasticsearch query primitives are introduced by the plugin to assist with feature development.

Feature sets can be logged either during a live user query or after-the-fact using the `sltr` query. You gather judgment lists (what docs are good/bad for a query) based on domain-specific factors. With logged features and judgment lists, you can train a model offline. The model learns to generalize the ranking expressed in the judgment list as a function of the features. 

You store a model into the learning to rank plugin, associating it with the feature set used during training. The features from the feature set are copied into the model, and the model cannot be changed. The features within a model are also frozen. You search with this model using the `sltr` query.

Improve your model by improving features, retraining with new feature sets, and then creating new models.

## Great, I want to jump in!

If you want to just jump in, go straight to the demo. The demo uses Ranklib, a Java Learning to Rank library, to train models. Follow the directions in the [demo README](demo/README.md), edit code, and have fun!

## I need more detailed documentation

Learning to Rank can be tremendously powerful. However, building a real-life learning to rank system is non-trivial. We've developed [complete documentation](http://elasticsearch-learning-to-rank.readthedocs.io) with plenty of background on learning to rank.

# Installing

Generally new features follow the latest ES version, but you can get older versions on older ES versions. Checkout the full list of builds [here](http://es-learn-to-rank.labs.o19s.com). Then to install, you'd run a command such as:

`./bin/elasticsearch-plugin install http://es-learn-to-rank.labs.o19s.com/ltr-1.0.0-RC1-es5.6.2.zip`

(It's expected you'll confirm some security exceptions, you can pass `-b` to `elasticsearch-plugin` to automatically install)

# Development

Notes if you want to dig into the code.

### 1. Build with Gradle Wrapper

```
./gradlew clean check
```

This runs the tasks in the `esplugin` gradle plugin that builds, tests, generates a Elasticsearch plugin zip file.

### 2. Install with `./bin/elasticsearch-plugin`

```
./bin/elasticsearch-plugin install file:///path/to/project/build/distributions/ltr-query-0.1.2-es5.4.0.zip
```

# Who built this?
- [Initially developed](http://opensourceconnections.com/blog/2017/02/14/elasticsearch-learning-to-rank/) at [OpenSource Connections](http://opensourceconnections.com). 
- Significant contributions by [Wikimedia Foundation](https://wikimediafoundation.org/wiki/Home), [Snagajob Engineering](https://engineering.snagajob.com/), and [Bonsai](https://bonsai.io/)

## Other Acknowledgments & Stuff To Read
- Bloomberg's [Learning to Rank work for Solr](https://issues.apache.org/jira/browse/SOLR-8542)
- Our Berlin Buzzwords Talk, [We built an Elasticsearch Learning to Rank plugin. Then came the hard part](https://berlinbuzzwords.de/17/session/we-built-elasticsearch-learning-rank-plugin-then-came-hard-part)
- Blog article on [How is Search Different from Other Machine Learning Problems](http://opensourceconnections.com/blog/2017/08/03/search-as-machine-learning-prob/)
- Also check out our other relevance/search thingies: book [Relevant Search](http://manning.com/books/relevant-search), projects [Elyzer](http://github.com/o19s/elyzer), [Splainer](http://splainer.io), and [Quepid](http://quepid.com)