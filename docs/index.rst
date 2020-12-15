.. Elasticsearch Learning to Rank documentation master file, created by
   sphinx-quickstart on Thu Sep 28 14:00:10 2017.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Elasticsearch Learning to Rank: the documentation
==========================================================

`Learning to Rank <http://opensourceconnections.com/blog/2017/02/24/what-is-learning-to-rank/>`_ applies machine learning to  relevance ranking. The `Elasticsearch Learning to Rank plugin <http://github.com/o19s/elasticsearch-learning-to-rank>`_ (Elasticsearch LTR) gives you tools to train and use ranking models in Elasticsearch. This plugin powers search at places like Wikimedia Foundation and Snagajob.

Get started
-------------------------------

- Want a quickstart? Check out the demo in `hello-ltr <https://github.com/o19s/hello-ltr>`_. 
- Brand new to learning to rank? head to :doc:`core-concepts`. 
- Otherwise, start with :doc:`fits-in`

Installing
-----------

Pre-built versions can be found `here <http://es-learn-to-rank.labs.o19s.com/>`_. Want a build for an ES version? Follow the instructions in the `README for building <https://github.com/o19s/elasticsearch-learning-to-rank#development>`_ or `create an issue <https://github.com/o19s/elasticsearch-learning-to-rank/issues>`_. Once you've found a version compatible with your Elasticsearch, you'd run a command such as::

    ./bin/elasticsearch-plugin install \ 
    http://es-learn-to-rank.labs.o19s.com/ltr-1.1.0-es6.5.4.zip

(It's expected you'll confirm some security exceptions, you can pass -b to elasticsearch-plugin to automatically install)

Are you using `x-pack security <https://www.elastic.co/products/x-pack/security>`_ in your cluster? we got you covered, check :doc:`x-pack` for specific configuration details.

HEEELP!
------------------------------

The plugin and guide was built by the search relevance consultants at `OpenSource Connections <http://opensourceconnections.com>`_ in partnership with the Wikimedia Foundation and Snagajob Engineering. Please `contact OpenSource Connections <mailto:hello@o19s.com>`_ or `create an issue <https://github.com/o19s/elasticsearch-learning-to-rank/issues>`_ if you have any questions or feedback.


Contents
-------------------------------

.. toctree::
   :maxdepth: 2
   
   core-concepts
   fits-in
   building-features
   feature-engineering
   logging-features
   training-models
   searching-with-your-model
   x-pack
   advanced-functionality
   faq
   :caption: Contents:


Indices and tables
==================

* :ref:`genindex`
* :ref:`search`
