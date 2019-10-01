[![Build Status](https://travis-ci.com/o19s/elasticsearch-learning-to-rank.svg?branch=master)](https://travis-ci.com/o19s/elasticsearch-learning-to-rank)

The Elasticsearch Learning to Rank plugin uses machine learning to improve search relevance ranking. It's powering search at places like Wikimedia Foundation and Snagajob!

# What this plugin does...

This plugin:

- Allows you to store features (Elasticsearch query templates) in Elasticsearch
- Logs features scores (relevance scores) to create a training set for offline model development
- Stores linear, xgboost, or ranklib ranking models in Elasticsearch that use features you've stored
- Ranks search results using a stored model

## Where's the docs?

We recommend taking time to [read the docs](http://elasticsearch-learning-to-rank.readthedocs.io). There's quite a bit of detailed information about learning to rank basics and how this plugin can ease learning to rank development.

## I want to jump in!

If you want to just jump in, go straight to the demo. The demo uses [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/), a relatively straightforward Java Learning to Rank library, to train models. Follow the directions in the [demo README](demo/README.md), edit code, and have fun!

# Installing

See the full list of [prebuilt versions](http://es-learn-to-rank.labs.o19s.com). If you don't see a version available, see the link below for building or file a request via [issues](https://github.com/o19s/elasticsearch-learning-to-rank/issues).

To install, you'd run a command such as:

`./bin/elasticsearch-plugin install http://es-learn-to-rank.labs.o19s.com/ltr-1.0.0-es6.1.2.zip`

(It's expected you'll confirm some security exceptions, you can pass `-b` to `elasticsearch-plugin` to automatically install)

If you already are running Elasticsearch, don't forget to restart!

## Know issues
As any other piece of software, this plugin is not exempt from issues. Please read the [known issues](KNOWN_ISSUES.md) to learn about the current issues that we are aware of. This file might include workarounds to mitigate them when possible.

# Development

Notes if you want to dig into the code or build for a version there's no build for.

### 1. Build with Gradle Wrapper

```
./gradlew clean check
```

This runs the tasks in the `esplugin` gradle plugin that builds, tests, generates a Elasticsearch plugin zip file available in ./build/distributions.

When building to support another version of Elasticsearch, versions should be modified in ./build.gradle, and possibly ./gradle/wrapper/gradle-wrapper.properties. See https://mvnrepository.com/artifact/org.elasticsearch.gradle/build-tools for available build tool versions.

### 2. Install with `./bin/elasticsearch-plugin`

```
./bin/elasticsearch-plugin install file:///path/to/project/build/distributions/ltr-<LTR-VER>-es<ES-VER>.zip
```

### Docs Development

[Docs](/docs) are built using Sphinx and written in reStructuredText. After [installing sphinx](https://www.sphinx-doc.org/en/master/index.html) (`pip install sphinx`) rebuild the docs with:

```
cd docs
make html
```

In another tab, you can simply run:

```
python -m http.server
```

Visit [localhost:8000](http://localhost:8000) and browse to the `_build/html` directory in your browser to view the built docs. 

Docs changes at master will be automatically built and deployed to readthedocs.

# Who built this?
- [Initially developed](http://opensourceconnections.com/blog/2017/02/14/elasticsearch-learning-to-rank/) at [OpenSource Connections](http://opensourceconnections.com).
- Significant contributions by [Wikimedia Foundation](https://wikimediafoundation.org/wiki/Home), [Snagajob Engineering](https://engineering.snagajob.com/), [Bonsai](https://bonsai.io/), and [Yelp Engineering](https://engineeringblog.yelp.com/)
- Thanks to [Jettro Coenradie](https://amsterdam.luminis.eu/author/jettro/) for porting to ES 6.1

## Other Acknowledgments & Stuff To Read
- Bloomberg's [Learning to Rank work for Solr](https://issues.apache.org/jira/browse/SOLR-8542)
- Our Berlin Buzzwords Talk, [We built an Elasticsearch Learning to Rank plugin. Then came the hard part](https://berlinbuzzwords.de/17/session/we-built-elasticsearch-learning-rank-plugin-then-came-hard-part)
- Blog article on [How is Search Different from Other Machine Learning Problems](http://opensourceconnections.com/blog/2017/08/03/search-as-machine-learning-prob/)
- Also check out our other relevance/search thingies: book [Relevant Search](http://manning.com/books/relevant-search), projects [Elyzer](http://github.com/o19s/elyzer), [Splainer](http://splainer.io), and [Quepid](http://quepid.com)
